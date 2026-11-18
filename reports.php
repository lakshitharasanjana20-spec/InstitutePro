<?php
require_once __DIR__.'/includes/bootstrap.php';
require_once __DIR__.'/includes/payments_lib.php';
require_menu('reports'); if(!can('manage_expenses')){ http_response_code(403); exit('Access denied.'); } refresh_batches();
$s=branch_scope(); $manage=can('manage_expenses');
const EXP_CATS=['Rent','Utilities','Marketing','Supplies','Maintenance','Other'];
if ($_SERVER['REQUEST_METHOD']==='POST') {
  csrf_check(); if(!$manage){ http_response_code(403); exit('Access denied.'); }
  $act=$_POST['act']??'';
  if ($act==='add_expense') {
    $bid=is_owner()?(int)($_POST['branch_id']??0):(int)$s; $cat=$_POST['category']??''; $amt=round((float)($_POST['amount']??0),2); $d=$_POST['spent_on']??'';
    if(!$bid||!in_array($cat,EXP_CATS,true)||$amt<=0||!strtotime($d)||$d>date('Y-m-d')) flash_set('msg','Branch, category, amount and a valid date are required.');
    else { db()->prepare('INSERT INTO expenses (branch_id,category,description,amount,spent_on,created_by) VALUES (?,?,?,?,?,?)')->execute([$bid,$cat,trim($_POST['description']??'')?:null,$amt,$d,$_SESSION['user']['id']]); log_activity('expense_add',"$cat $amt"); flash_set('msg','Expense recorded.'); }
    header('Location: reports.php?tab=expenses'); exit;
  }
  if ($act==='del_expense' && is_owner()) {
    db()->prepare('DELETE FROM expenses WHERE id=?')->execute([(int)$_POST['id']]); log_activity('expense_delete','#'.(int)$_POST['id']); flash_set('msg','Expense deleted.'); header('Location: reports.php?tab=expenses'); exit;
  }
}
$pageKey='reports'; require __DIR__.'/includes/header.php';
$msg=flash_get('msg'); $tab=($_GET['tab']??'')==='expenses'?'expenses':'overview';
$branches=db()->query("SELECT id,name FROM branches ORDER BY name")->fetchAll();
$bf=$s; if($s===null && (int)($_GET['branch']??0)) $bf=(int)$_GET['branch'];
$from=$_GET['from']??date('Y-m-01'); $to=$_GET['to']??date('Y-m-d');
if(!strtotime($from)) $from=date('Y-m-01'); if(!strtotime($to)) $to=date('Y-m-d'); if($from>$to) [$from,$to]=[$to,$from];
$bc=fn($col)=>$bf!==null?" AND $col=".(int)$bf:'';
function one(string $sql,array $p=[]): float { $q=db()->prepare($sql); $q->execute($p); return (float)$q->fetchColumn(); }
ensure_all_accounts($bf);
$rev=one('SELECT COALESCE(SUM(p.amount),0) FROM payments p WHERE p.voided_at IS NULL AND p.paid_on BETWEEN ? AND ?'.$bc('p.branch_id'),[$from,$to]);
$exp=one('SELECT COALESCE(SUM(x.amount),0) FROM expenses x WHERE x.spent_on BETWEEN ? AND ?'.$bc('x.branch_id'),[$from,$to]);
$pay=one("SELECT COALESCE(SUM(r.net),0) FROM payroll r WHERE r.status='paid' AND r.paid_on BETWEEN ? AND ?".$bc('r.branch_id'),[$from,$to]);
$net=$rev-$exp-$pay;
$scopeS=$bf!==null?' AND s.branch_id='.(int)$bf:'';
$overdue=one("SELECT COALESCE(SUM(i.amount-i.paid),0) FROM installments i JOIN fee_accounts a ON a.id=i.account_id JOIN students s ON s.id=a.student_id WHERE i.due_date<CURDATE() AND i.amount-i.paid>0.004$scopeS");
$outstanding=one("SELECT COALESCE(SUM(i.amount-i.paid),0) FROM installments i JOIN fee_accounts a ON a.id=i.account_id JOIN students s ON s.id=a.student_id WHERE i.amount-i.paid>0.004$scopeS");
$cards=[['Revenue',$rev,'bi-arrow-down-circle-fill','#1e9e5a'],['Expenses',$exp,'bi-arrow-up-circle-fill','#d9534f'],['Payroll paid',$pay,'bi-cash-stack','#e08a00'],['Net profit',$net,'bi-graph-up-arrow',$net>=0?'#3e91ff':'#d9534f'],['Overdue fees',$overdue,'bi-exclamation-triangle-fill','#d9534f'],['Outstanding fees',$outstanding,'bi-hourglass-split','#6b7280']];
?>
<?php if($msg):?><div class="alert alert-info rounded-4"><?=e($msg)?></div><?php endif;?>
<ul class="nav nav-pills mb-3"><li class="nav-item"><a class="nav-link <?=$tab==='overview'?'active':''?>" href="reports.php">Overview</a></li><li class="nav-item"><a class="nav-link <?=$tab==='expenses'?'active':''?>" href="reports.php?tab=expenses">Expenses</a></li></ul>
<?php if($tab==='overview'):?>
<form method="get" class="card-ui mb-3 row g-2 align-items-end">
 <?php if($s===null):?><div class="col-12 col-md-3"><label class="lbl">Branch</label><select class="form-select" name="branch"><option value="">All branches</option><?php foreach($branches as $b):?><option value="<?=$b['id']?>" <?=$bf===(int)$b['id']?'selected':''?>><?=e($b['name'])?></option><?php endforeach;?></select></div><?php endif;?>
 <div class="col-6 col-md-3"><label class="lbl">From</label><input class="form-control" type="date" name="from" value="<?=e($from)?>"></div>
 <div class="col-6 col-md-3"><label class="lbl">To</label><input class="form-control" type="date" name="to" value="<?=e($to)?>"></div>
 <div class="col-12 col-md-2"><button class="btn-ui w-100">Apply</button></div></form>
<div class="row g-3 mb-3"><?php foreach($cards as $c):?><div class="col-6 col-xl-4"><div class="card-ui h-100"><div class="d-flex align-items-center gap-2 lbl"><i class="bi <?=$c[2]?>" style="color:<?=$c[3]?>"></i><?=$c[0]?></div><div class="stat" style="font-size:1.35rem;color:<?=$c[3]?>"><?=money($c[1])?></div></div></div><?php endforeach;?></div>
<div class="lbl mb-3">Revenue, expenses and payroll cover <?=e($from)?> to <?=e($to)?>. Overdue and outstanding fees are as of today.</div>
<?php
$bt=db()->prepare("SELECT t.name,COALESCE(SUM(p.amount),0) amt FROM payments p JOIN batches b ON b.id=p.batch_id JOIN courses c ON c.id=b.course_id JOIN course_types t ON t.id=c.type_id WHERE p.voided_at IS NULL AND p.paid_on BETWEEN ? AND ?".$bc('p.branch_id')." GROUP BY t.id,t.name ORDER BY amt DESC"); $bt->execute([$from,$to]); $byType=$bt->fetchAll();
$bm=db()->prepare("SELECT p.method,COALESCE(SUM(p.amount),0) amt FROM payments p WHERE p.voided_at IS NULL AND p.paid_on BETWEEN ? AND ?".$bc('p.branch_id')." GROUP BY p.method ORDER BY amt DESC"); $bm->execute([$from,$to]); $byMethod=$bm->fetchAll();
$start=date('Y-m-01',strtotime('-5 months')); $tr=[];
for($i=5;$i>=0;$i--){ $m=date('Y-m',strtotime("-$i months")); $tr[$m]=[0,0,0]; }
foreach(db()->query("SELECT DATE_FORMAT(p.paid_on,'%Y-%m') m,SUM(p.amount) v FROM payments p WHERE p.voided_at IS NULL AND p.paid_on>='$start'".$bc('p.branch_id')." GROUP BY m")->fetchAll() as $r) if(isset($tr[$r['m']])) $tr[$r['m']][0]=(float)$r['v'];
foreach(db()->query("SELECT DATE_FORMAT(x.spent_on,'%Y-%m') m,SUM(x.amount) v FROM expenses x WHERE x.spent_on>='$start'".$bc('x.branch_id')." GROUP BY m")->fetchAll() as $r) if(isset($tr[$r['m']])) $tr[$r['m']][1]=(float)$r['v'];
foreach(db()->query("SELECT DATE_FORMAT(r.paid_on,'%Y-%m') m,SUM(r.net) v FROM payroll r WHERE r.status='paid' AND r.paid_on>='$start'".$bc('r.branch_id')." GROUP BY m")->fetchAll() as $r) if(isset($tr[$r['m']])) $tr[$r['m']][2]=(float)$r['v'];
$oq=gstr('oq'); $op=[];
$odSql=" FROM installments i JOIN fee_accounts a ON a.id=i.account_id JOIN students s ON s.id=a.student_id JOIN batches b ON b.id=a.batch_id WHERE i.due_date<CURDATE() AND i.amount-i.paid>0.004$scopeS";
if($oq!==''){ $odSql.=' AND (s.full_name LIKE ? OR s.student_code LIKE ? OR s.phone LIKE ?)'; $op=["%$oq%","%$oq%","%$oq%"]; }
$cq=db()->prepare('SELECT COUNT(*) FROM (SELECT 1'.$odSql.' GROUP BY s.id,b.id) z'); $cq->execute($op); $odTotal=(int)$cq->fetchColumn();
$odPer=pg_per(); [$odPg,$odPages,$odOff]=pg_page($odTotal,$odPer,'op');
$q3=db()->prepare('SELECT s.id,s.student_code,s.full_name,s.phone,b.name bname,SUM(i.amount-i.paid) due,MIN(i.due_date) since'.$odSql.' GROUP BY s.id,b.id ORDER BY due DESC LIMIT '.$odPer.' OFFSET '.$odOff); $q3->execute($op); $od=$q3->fetchAll();
$cnt=fn($sql)=>(int)db()->query($sql)->fetchColumn();
$activeStu=$cnt("SELECT COUNT(*) FROM students s WHERE s.status='active'$scopeS"); $inactiveStu=$cnt("SELECT COUNT(*) FROM students s WHERE s.status='inactive'$scopeS");
$activeB=$cnt("SELECT COUNT(*) FROM batches b WHERE b.status='active'".$bc('b.branch_id'));
?>
<div class="row g-3 mb-3">
 <div class="col-4"><div class="card-ui text-center"><div class="stat" style="font-size:1.3rem"><?=$activeStu?></div><div class="lbl">Active students</div></div></div>
 <div class="col-4"><div class="card-ui text-center"><div class="stat" style="font-size:1.3rem"><?=$inactiveStu?></div><div class="lbl">Inactive students</div></div></div>
 <div class="col-4"><div class="card-ui text-center"><div class="stat" style="font-size:1.3rem"><?=$activeB?></div><div class="lbl">Active batches</div></div></div></div>
<div class="row g-3 mb-3">
 <div class="col-md-6"><div class="card-ui h-100"><div class="fw-bold mb-2">Revenue by course type</div><?php foreach($byType as $r):?><div class="d-flex justify-content-between py-1 border-top"><span><?=e($r['name'])?></span><span><?=money($r['amt'])?></span></div><?php endforeach; if(!$byType):?><div class="lbl">No payments in this period.</div><?php endif;?></div></div>
 <div class="col-md-6"><div class="card-ui h-100"><div class="fw-bold mb-2">Revenue by payment method</div><?php foreach($byMethod as $r):?><div class="d-flex justify-content-between py-1 border-top"><span><?=e(ucwords(str_replace('_',' ',$r['method'])))?></span><span><?=money($r['amt'])?></span></div><?php endforeach; if(!$byMethod):?><div class="lbl">No payments in this period.</div><?php endif;?></div></div></div>
<div class="card-ui table-wrap mb-3"><div class="fw-bold mb-2">Last 6 months</div><table class="table align-middle"><thead><tr><th>Month</th><th>Revenue</th><th>Expenses</th><th>Payroll</th><th>Net</th></tr></thead><tbody>
<?php foreach($tr as $m=>$v): $n=$v[0]-$v[1]-$v[2];?><tr><td><?=e(date('M Y',strtotime($m.'-01')))?></td><td><?=number_format($v[0],2)?></td><td><?=number_format($v[1],2)?></td><td><?=number_format($v[2],2)?></td><td class="fw-bold" style="color:<?=$n>=0?'#1e9e5a':'#d9534f'?>"><?=number_format($n,2)?></td></tr><?php endforeach;?></tbody></table></div>
<div class="card-ui table-wrap"><div class="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-2"><div class="fw-bold">Overdue fees (<?=$odTotal?>)</div>
<form method="get" class="d-flex gap-2"><?php foreach(['branch','from','to'] as $k): if(gstr($k)!==''):?><input type="hidden" name="<?=$k?>" value="<?=e(gstr($k))?>"><?php endif; endforeach;?><input class="form-control form-control-sm" name="oq" value="<?=e($oq)?>" placeholder="Search student"><button class="btn btn-sm btn-light rounded-pill">Search</button></form></div><table class="table align-middle"><thead><tr><th>Student</th><th>Class</th><th>Overdue since</th><th>Amount</th></tr></thead><tbody>
<?php foreach($od as $r):?><tr><td><a href="student_view.php?id=<?=$r['id']?>"><?=e($r['full_name'])?></a><div class="lbl"><?=e($r['student_code'])?> · <?=e($r['phone'])?></div></td><td><?=e($r['bname'])?></td><td><?=e($r['since'])?></td><td class="fw-bold"><?=number_format((float)$r['due'],2)?></td></tr>
<?php endforeach; if(!$od):?><tr><td colspan="4" class="lbl text-center py-3">No overdue fees.</td></tr><?php endif;?></tbody></table></div>
<?php pg_render($odTotal,$odPer,'op');?>
<?php else:
  $fq=gstr('q'); $fc=genum('cat',EXP_CATS); $fbr=$s===null?gint('branch'):0; $d1=gdate('from'); $d2=gdate('to'); if($d1&&$d2&&$d1>$d2)[$d1,$d2]=[$d2,$d1];
  $exw=($s!==null?' AND x.branch_id='.(int)$s:''); $xp=[];
  if($fq!==''){$exw.=' AND x.description LIKE ?';$xp[]="%$fq%";} if($fc){$exw.=' AND x.category=?';$xp[]=$fc;} if($fbr){$exw.=' AND x.branch_id=?';$xp[]=$fbr;}
  if($d1){$exw.=' AND x.spent_on>=?';$xp[]=$d1;} if($d2){$exw.=' AND x.spent_on<=?';$xp[]=$d2;}
  $cq=db()->prepare('SELECT COUNT(*),COALESCE(SUM(x.amount),0) FROM expenses x WHERE 1=1'.$exw); $cq->execute($xp); [$total,$sumAmt]=$cq->fetch(PDO::FETCH_NUM); $total=(int)$total;
  $per=pg_per(); [$pg,$pages,$off]=pg_page($total,$per);
  $eq=db()->prepare("SELECT x.*,b.name brname,u.full_name by_name FROM expenses x JOIN branches b ON b.id=x.branch_id LEFT JOIN users u ON u.id=x.created_by WHERE 1=1".$exw." ORDER BY x.spent_on DESC,x.id DESC LIMIT $per OFFSET $off"); $eq->execute($xp); $ex=$eq->fetchAll(); ?>
<?php if($manage):?><div class="card-ui mb-3"><div class="fw-bold mb-2">Record expense</div><form method="post" class="row g-2"><input type="hidden" name="csrf" value="<?=csrf()?>"><input type="hidden" name="act" value="add_expense">
 <?php if(is_owner()):?><div class="col-6 col-md-3"><select class="form-select" name="branch_id" required><option value="">Branch</option><?php foreach($branches as $b):?><option value="<?=$b['id']?>"><?=e($b['name'])?></option><?php endforeach;?></select></div><?php endif;?>
 <div class="col-6 col-md-3"><select class="form-select" name="category"><?php foreach(EXP_CATS as $c):?><option><?=$c?></option><?php endforeach;?></select></div>
 <div class="col-6 col-md-2"><input class="form-control" type="number" step="0.01" min="0.01" name="amount" placeholder="Amount" required></div>
 <div class="col-6 col-md-2"><input class="form-control" type="date" name="spent_on" value="<?=date('Y-m-d')?>" max="<?=date('Y-m-d')?>" required></div>
 <div class="col-12 col-md-6"><input class="form-control" name="description" placeholder="Description (optional)"></div>
 <div class="col-12 col-md-2"><button class="btn-ui w-100">Add</button></div></form></div><?php endif;?>
<?php f_open(['tab']); f_input('q','Search description','text','col-12 col-md-3');
 f_select('cat',array_combine(EXP_CATS,EXP_CATS),'Any category','col-6 col-md-2');
 if($s===null) f_select('branch',array_column($branches,'name','id'),'All branches','col-6 col-md-3');
 f_input('from','From','date','col-6 col-md-2'); f_input('to','To','date','col-6 col-md-2'); f_close(['tab']);?>
<div class="lbl mb-2"><?=$total?> record(s) · Total <?=money($sumAmt)?></div>
<div class="card-ui table-wrap"><table class="table align-middle"><thead><tr><th>Date</th><th>Category</th><th>Branch</th><th>Amount</th><th></th></tr></thead><tbody>
<?php foreach($ex as $r):?><tr><td><?=e($r['spent_on'])?></td><td><?=e($r['category'])?><div class="lbl"><?=e($r['description'])?></div></td><td><?=e($r['brname'])?></td><td><?=number_format((float)$r['amount'],2)?></td>
<td><?php if(is_owner()):?><form method="post" onsubmit="return confirm('Delete this expense?')"><input type="hidden" name="csrf" value="<?=csrf()?>"><input type="hidden" name="act" value="del_expense"><input type="hidden" name="id" value="<?=$r['id']?>"><button class="btn btn-sm btn-light rounded-pill">Delete</button></form><?php endif;?></td></tr>
<?php endforeach; if(!$ex):?><tr><td colspan="5" class="lbl text-center py-4">No expenses found.</td></tr><?php endif;?></tbody></table></div>
<?php pg_render($total,$per);?>
<?php endif;?>
<?php require __DIR__.'/includes/footer.php';
