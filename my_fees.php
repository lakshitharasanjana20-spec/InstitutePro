<?php
require_once __DIR__.'/includes/bootstrap.php';
require_once __DIR__.'/includes/student_lib.php';
require_once __DIR__.'/includes/payments_lib.php';
stu_guard('sfees'); $pageKey='sfees'; require __DIR__.'/includes/header.php';
$sid=stu_id(); $en=db()->prepare('SELECT e.id,b.name bname,b.batch_year,c.name cname FROM enrolments e JOIN batches b ON b.id=e.batch_id JOIN courses c ON c.id=b.course_id WHERE e.student_id=? ORDER BY e.id'); $en->execute([$sid]); $enrs=$en->fetchAll();
?>
<?php if(!$enrs):?><div class="card-ui lbl">You are not enrolled in any class yet.</div><?php endif;?>
<?php foreach($enrs as $e1): $acc=ensure_account((int)$e1['id']); if(!$acc) continue; $sum=account_summary((int)$acc['id']);
 $iq=db()->prepare('SELECT * FROM installments WHERE account_id=? ORDER BY due_date,no'); $iq->execute([$acc['id']]); $ins=$iq->fetchAll();?>
<div class="card-ui mb-3"><div class="fw-bold"><?=e($e1['cname'])?> · <?=e($e1['bname'])?> (<?=$e1['batch_year']?>)</div>
 <div class="row g-2 text-center my-2"><div class="col-6 col-md-3"><div class="lbl">Fee</div><b><?=money($acc['total_fee'])?></b></div><div class="col-6 col-md-3"><div class="lbl">Scholarship</div><b><?=money($acc['scholarship'])?></b></div><div class="col-6 col-md-3"><div class="lbl">Paid</div><b style="color:#1e9e5a"><?=money($sum['paid'])?></b></div><div class="col-6 col-md-3"><div class="lbl">Balance</div><b style="color:<?=$sum['balance']>0.004?'#d9534f':'#1e9e5a'?>"><?=money($sum['balance'])?></b></div></div>
 <?php if($sum['net']>0):?><div class="progress mb-2" style="height:6px"><div class="progress-bar bg-success" style="width:<?=min(100,round($sum['paid']/$sum['net']*100))?>%"></div></div><?php endif;?>
 <div class="table-wrap"><table class="table table-sm align-middle mb-0"><thead><tr><th>Installment</th><th>Due</th><th>Amount</th><th>Paid</th><th></th></tr></thead><tbody>
 <?php foreach($ins as $i): $done=$i['paid']>=$i['amount']-0.004; $late=!$done&&$i['due_date']<date('Y-m-d');?><tr><td><?=e($i['label'])?></td><td><?=e(date('j M Y',strtotime($i['due_date'])))?></td><td><?=number_format((float)$i['amount'],2)?></td><td><?=number_format((float)$i['paid'],2)?></td><td><span class="badge rounded-pill text-bg-<?=$done?'success':($late?'danger':'secondary')?>"><?=$done?'Paid':($late?'Overdue':'Upcoming')?></span></td></tr><?php endforeach;?></tbody></table></div></div>
<?php endforeach;
$cq=db()->prepare('SELECT COUNT(*) FROM payments WHERE student_id=?'); $cq->execute([$sid]); $total=(int)$cq->fetchColumn(); $per=pg_per(); [$pg,$pages,$off]=pg_page($total,$per);
$q=db()->prepare("SELECT p.id,p.receipt_no,p.paid_on,p.amount,p.method,p.voided_at,b.name bname FROM payments p JOIN batches b ON b.id=p.batch_id WHERE p.student_id=? ORDER BY p.id DESC LIMIT $per OFFSET $off"); $q->execute([$sid]); $pays=$q->fetchAll();?>
<div class="card-ui table-wrap"><div class="fw-bold mb-2">Payment history</div><table class="table align-middle"><thead><tr><th>Receipt</th><th>Date</th><th>Class</th><th>Amount</th></tr></thead><tbody>
<?php foreach($pays as $y):?><tr><td><a href="receipt.php?id=<?=$y['id']?>" target="_blank"><?=e($y['receipt_no'])?></a></td><td><?=e(date('j M Y',strtotime($y['paid_on'])))?></td><td><?=e($y['bname'])?></td><td><?php if($y['voided_at']):?><s class="lbl"><?=number_format((float)$y['amount'],2)?></s> <span class="badge rounded-pill text-bg-danger">cancelled</span><?php else:?><?=number_format((float)$y['amount'],2)?><?php endif;?></td></tr>
<?php endforeach; if(!$pays):?><tr><td colspan="4" class="lbl text-center py-3">No payments yet.</td></tr><?php endif;?></tbody></table></div>
<?php pg_render($total,$per); require __DIR__.'/includes/footer.php';
