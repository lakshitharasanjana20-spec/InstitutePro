<?php
require_once __DIR__.'/includes/bootstrap.php';
require_once __DIR__.'/includes/payments_lib.php';
require_menu('payments'); if(!can('take_payment')){ http_response_code(403); exit('Access denied.'); } refresh_batches();
$s=branch_scope(); $canPay=can('take_payment');
function load_student(int $id,?int $s): ?array {
  $q=db()->prepare('SELECT s.*,br.name brname,br.code brcode FROM students s JOIN branches br ON br.id=s.branch_id WHERE s.id=?'.($s!==null?' AND s.branch_id='.(int)$s:'')); $q->execute([$id]); return $q->fetch()?:null;
}
function verified(int $sid): bool { $v=$_SESSION['pay_ok']??null; return $v && (int)$v[0]===$sid && time()-$v[1]<900; }

if ($_SERVER['REQUEST_METHOD']==='POST') {
  csrf_check();
  if (!$canPay) { http_response_code(403); exit('Access denied.'); }
  $act=$_POST['act']??''; $sid=(int)($_POST['student']??0); $stu=load_student($sid,$s);
  if (!$stu) { flash_set('msg','Student not found.'); header('Location: payments.php'); exit; }
  if ($act==='verify') { $_SESSION['pay_ok']=[$sid,time()]; log_activity('payment_verify',$stu['student_code']); header("Location: payments.php?student=$sid"); exit; }
  if (!verified($sid)) { flash_set('msg','Verification expired. Please verify the student again.'); header('Location: payments.php'); exit; }
  $aid=(int)($_POST['account']??0);
  $q=db()->prepare('SELECT * FROM fee_accounts WHERE id=? AND student_id=?'); $q->execute([$aid,$sid]); $acc=$q->fetch();
  if (!$acc) { flash_set('msg','Fee account not found.'); header("Location: payments.php?student=$sid"); exit; }
  if ($act==='pay') {
    $r=record_payment($acc,(float)($_POST['amount']??0),$_POST['method']??'cash',trim($_POST['reference']??''),$_POST['paid_on']??date('Y-m-d'),trim($_POST['note']??''),(int)$_SESSION['user']['id'],$stu['brcode'],(int)$stu['branch_id']);
    if (is_int($r)) { log_activity('payment',$stu['student_code'].' '.number_format((float)$_POST['amount'],2)); header("Location: receipt.php?id=$r&new=1"); exit; }
    flash_set('msg',$r);
  } elseif ($act==='scholarship' && can('set_scholarship')) {
    $r=apply_scholarship($aid,(float)($_POST['amount']??0));
    if ($r===null) { log_activity('scholarship',$stu['student_code'].' '.number_format((float)$_POST['amount'],2)); flash_set('msg','Scholarship applied.'); } else flash_set('msg',$r);
  }
  header("Location: payments.php?student=$sid"); exit;
}
$pageKey='payments'; require __DIR__.'/includes/header.php';
$msg=flash_get('msg');
?>
<?php if($msg):?><div class="alert alert-info rounded-4"><?=e($msg)?></div><?php endif;?>
<?php
$sid=(int)($_GET['student']??0); $vid=(int)($_GET['verify']??0);
if ($sid && ($stu=load_student($sid,$s)) && verified($sid)):
  $en=db()->prepare('SELECT e.id,b.name bname,b.batch_year,c.name cname FROM enrolments e JOIN batches b ON b.id=e.batch_id JOIN courses c ON c.id=b.course_id WHERE e.student_id=? ORDER BY e.id'); $en->execute([$sid]); $enrs=$en->fetchAll();
?>
<a href="payments.php" class="lbl text-decoration-none">← New payment</a>
<div class="card-ui my-3"><span class="badge rounded-pill text-bg-success mb-2"><i class="bi bi-patch-check-fill"></i> Verified</span>
 <div class="stat" style="font-size:1.3rem"><?=e($stu['full_name'])?></div><div class="lbl"><?=e($stu['student_code'])?> · <?=e($stu['phone'])?></div></div>
<?php if(!$enrs):?><div class="card-ui lbl">This student is not enrolled in any class.</div><?php endif;?>
<?php foreach($enrs as $en1): $acc=ensure_account((int)$en1['id']); if(!$acc) continue; $sum=account_summary((int)$acc['id']);
  $iq=db()->prepare('SELECT * FROM installments WHERE account_id=? ORDER BY due_date,no'); $iq->execute([$acc['id']]); $ins=$iq->fetchAll();
  $next=0; foreach($ins as $i){ if($i['amount']-$i['paid']>0.004){ $next=round($i['amount']-$i['paid'],2); break; } } ?>
<div class="card-ui mb-3">
 <div class="fw-bold"><?=e($en1['cname'])?> · <?=e($en1['bname'])?> (<?=$en1['batch_year']?>)</div>
 <div class="row g-2 my-2 text-center">
  <div class="col-3"><div class="lbl">Fee</div><div><?=number_format((float)$acc['total_fee'],2)?></div></div>
  <div class="col-3"><div class="lbl">Scholarship</div><div><?=number_format((float)$acc['scholarship'],2)?></div></div>
  <div class="col-3"><div class="lbl">Paid</div><div><?=number_format($sum['paid'],2)?></div></div>
  <div class="col-3"><div class="lbl">Balance</div><div class="fw-bold"><?=number_format($sum['balance'],2)?></div></div></div>
 <div class="table-wrap"><table class="table table-sm align-middle"><thead><tr><th>Installment</th><th>Due</th><th>Amount</th><th>Paid</th><th></th></tr></thead><tbody>
 <?php foreach($ins as $i): $done=$i['paid']>=$i['amount']-0.004; $late=!$done && $i['due_date']<date('Y-m-d'); ?>
 <tr><td><?=e($i['label'])?></td><td><?=e($i['due_date'])?></td><td><?=number_format((float)$i['amount'],2)?></td><td><?=number_format((float)$i['paid'],2)?></td>
 <td><span class="badge rounded-pill text-bg-<?=$done?'success':($late?'danger':'secondary')?>"><?=$done?'paid':($late?'overdue':'pending')?></span></td></tr><?php endforeach;?></tbody></table></div>
 <?php if($sum['balance']>0.004):?>
 <form method="post" class="row g-2 mt-1"><input type="hidden" name="csrf" value="<?=csrf()?>"><input type="hidden" name="act" value="pay"><input type="hidden" name="student" value="<?=$sid?>"><input type="hidden" name="account" value="<?=$acc['id']?>">
  <div class="col-6 col-md-3"><label class="lbl">Amount</label><input class="form-control" type="number" step="0.01" min="0.01" max="<?=$sum['balance']?>" name="amount" value="<?=$next?>" required></div>
  <div class="col-6 col-md-3"><label class="lbl">Method</label><select class="form-select" name="method"><option value="cash">Cash</option><option value="card">Card</option><option value="bank_transfer">Bank transfer</option><option value="other">Other</option></select></div>
  <div class="col-6 col-md-3"><label class="lbl">Date</label><input class="form-control" type="date" name="paid_on" value="<?=date('Y-m-d')?>" max="<?=date('Y-m-d')?>" required></div>
  <div class="col-6 col-md-3"><label class="lbl">Reference</label><input class="form-control" name="reference" placeholder="Optional"></div>
  <div class="col-12 col-md-9"><input class="form-control" name="note" placeholder="Note (optional)"></div>
  <div class="col-12 col-md-3"><button class="btn-ui w-100">Record payment</button></div></form>
 <?php if(can('set_scholarship') && (float)$acc['scholarship']==0):?>
 <form method="post" class="d-flex gap-2 mt-3"><input type="hidden" name="csrf" value="<?=csrf()?>"><input type="hidden" name="act" value="scholarship"><input type="hidden" name="student" value="<?=$sid?>"><input type="hidden" name="account" value="<?=$acc['id']?>">
  <input class="form-control" type="number" step="0.01" min="0.01" name="amount" placeholder="Scholarship amount (one-time)" required><button class="btn btn-light rounded-pill text-nowrap">Apply</button></form>
 <?php endif; else:?><div class="alert alert-success rounded-4 mt-2 mb-0 py-2">Fully paid.</div><?php endif;?>
</div>
<?php endforeach;
elseif ($vid && ($stu=load_student($vid,$s))):?>
<div class="card-ui text-center"><h5>Confirm student identity</h5>
 <div class="stat" style="font-size:1.3rem"><?=e($stu['full_name'])?></div>
 <div class="lbl"><?=e($stu['student_code'])?></div><div><?=e($stu['phone'])?><?=$stu['email']?' · '.e($stu['email']):''?></div>
 <p class="lbl mt-2">Check these details with the student or guardian before taking payment.</p>
 <form method="post"><input type="hidden" name="csrf" value="<?=csrf()?>"><input type="hidden" name="act" value="verify"><input type="hidden" name="student" value="<?=$vid?>">
 <button class="btn-ui">Identity verified – continue</button> <a href="payments.php" class="btn btn-light rounded-pill">Cancel</a></form></div>
<?php else:
  $f=['id'=>trim($_GET['f_id']??''),'name'=>trim($_GET['f_name']??''),'phone'=>trim($_GET['f_phone']??''),'email'=>trim($_GET['f_email']??'')]; $found=null;
  if (array_filter($f)) {
    $w=[];$p=[]; if($s!==null){$w[]='branch_id=?';$p[]=$s;}
    if($f['id']!==''){$w[]='student_code=?';$p[]=strtoupper($f['id']);}
    if($f['name']!==''){$w[]='full_name LIKE ?';$p[]='%'.$f['name'].'%';}
    if($f['phone']!==''){$w[]='phone LIKE ?';$p[]='%'.$f['phone'].'%';}
    if($f['email']!==''){$w[]='email=?';$p[]=$f['email'];}
    $q=db()->prepare('SELECT id,student_code,full_name,phone FROM students WHERE '.implode(' AND ',$w).' ORDER BY full_name LIMIT 10'); $q->execute($p); $found=$q->fetchAll();
  }
  $pw=$s!==null?' AND p.branch_id='.(int)$s:''; $rq=gstr('q'); $params=[]; $qw='';
  if($rq!==''){ $qw=' AND (p.receipt_no LIKE ? OR s.full_name LIKE ? OR s.student_code LIKE ?)'; $params=["%$rq%","%$rq%","%$rq%"]; }
  $fm=genum('method',['cash','card','bank_transfer','other']); $fst=genum('pstatus',['valid','void']); $fbr=$s===null?gint('branch'):0; $d1=gdate('from'); $d2=gdate('to'); if($d1&&$d2&&$d1>$d2)[$d1,$d2]=[$d2,$d1];
if($fm){$qw.=' AND p.method=?';$params[]=$fm;} if($fst==='valid')$qw.=' AND p.voided_at IS NULL'; if($fst==='void')$qw.=' AND p.voided_at IS NOT NULL';
if($fbr){$qw.=' AND p.branch_id=?';$params[]=$fbr;} if($d1){$qw.=' AND p.paid_on>=?';$params[]=$d1;} if($d2){$qw.=' AND p.paid_on<=?';$params[]=$d2;}
$base=" FROM payments p JOIN students s ON s.id=p.student_id JOIN batches b ON b.id=p.batch_id WHERE 1=1$pw$qw";
$cq=db()->prepare('SELECT COUNT(*)'.$base); $cq->execute($params); $total=(int)$cq->fetchColumn(); $per=pg_per(); [$pg,$pages,$off]=pg_page($total,$per);
$hist=db()->prepare('SELECT p.id,p.voided_at,p.receipt_no,p.paid_on,p.amount,p.method,s.full_name,s.student_code,b.name bname'.$base.' ORDER BY p.id DESC LIMIT '.$per.' OFFSET '.$off); $hist->execute($params); $rows=$hist->fetchAll();
$brs=db()->query("SELECT id,name FROM branches ORDER BY name")->fetchAll();
?>
<?php if($canPay):?>
<div class="card-ui mb-3"><div class="fw-bold mb-2">Step 1 · Verify the student</div>
<form method="get" class="row g-2">
 <div class="col-6 col-md-3"><input class="form-control" name="f_id" placeholder="Student ID" value="<?=e($f['id'])?>"></div>
 <div class="col-6 col-md-3"><input class="form-control" name="f_name" placeholder="Name" value="<?=e($f['name'])?>"></div>
 <div class="col-6 col-md-3"><input class="form-control" name="f_phone" placeholder="Contact no" value="<?=e($f['phone'])?>"></div>
 <div class="col-6 col-md-3"><input class="form-control" name="f_email" placeholder="Email" value="<?=e($f['email'])?>"></div>
 <div class="col-12"><button class="btn-ui"><i class="bi bi-search"></i> Find student</button></div></form>
<?php if($found!==null):?><div class="mt-3"><?php foreach($found as $r):?>
 <a class="d-flex justify-content-between align-items-center border-top py-2 text-decoration-none text-reset" href="payments.php?verify=<?=$r['id']?>"><span><?=e($r['full_name'])?><span class="lbl d-block"><?=e($r['student_code'])?> · <?=e($r['phone'])?></span></span><i class="bi bi-chevron-right"></i></a>
 <?php endforeach; if(!$found):?><p class="lbl mb-0">No matching student.</p><?php endif;?></div><?php endif;?></div>
<?php endif;?>
<?php f_open(); f_input('q','Search receipt, name or ID','text','col-12 col-md-4');
 f_select('method',['cash'=>'Cash','card'=>'Card','bank_transfer'=>'Bank transfer','other'=>'Other'],'Any method','col-6 col-md-2');
 f_select('pstatus',['valid'=>'Valid','void'=>'Cancelled'],'Any status','col-6 col-md-2');
 if($s===null) f_select('branch',array_column($brs,'name','id'),'All branches','col-6 col-md-3');
 f_input('from','From','date','col-6 col-md-3'); f_input('to','To','date','col-6 col-md-3'); f_close();?>
<div class="card-ui table-wrap">
<table class="table align-middle"><thead><tr><th>Receipt</th><th>Date</th><th>Student</th><th>Class</th><th>Amount</th><th>Method</th></tr></thead><tbody>
<?php foreach($rows as $r):?><tr><td><a href="receipt.php?id=<?=$r['id']?>" target="_blank"><?=e($r['receipt_no'])?></a></td><td><?=e($r['paid_on'])?></td><td><?=e($r['full_name'])?><div class="lbl"><?=e($r['student_code'])?></div></td><td><?=e($r['bname'])?></td><td><?php if($r['voided_at']):?><s class="lbl"><?=number_format((float)$r['amount'],2)?></s> <span class="badge rounded-pill text-bg-danger">void</span><?php else:?><?=number_format((float)$r['amount'],2)?><?php endif;?></td><td><?=e($r['method'])?></td></tr>
<?php endforeach; if(!$rows):?><tr><td colspan="6" class="lbl text-center py-4">No payments found.</td></tr><?php endif;?></tbody></table></div>
<?php pg_render($total,$per);?>
<?php endif;?>
<?php require __DIR__.'/includes/footer.php';
