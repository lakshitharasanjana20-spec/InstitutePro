<?php
require_once __DIR__.'/includes/bootstrap.php';
require_once __DIR__.'/includes/payments_lib.php';
if (is_student_user()) { require_login(); } else { require_menu('payments'); if(!can('take_payment')){ http_response_code(403); exit('Access denied.'); } } $s=branch_scope(); $id=(int)($_GET['id']??0);
$q=db()->prepare('SELECT p.*,s.full_name,s.student_code,s.phone,b.name bname,b.batch_year,c.name cname,br.name brname,br.address braddr,br.phone brphone,u.full_name rby,(SELECT full_name FROM users WHERE id=p.voided_by) vby
  FROM payments p JOIN students s ON s.id=p.student_id JOIN batches b ON b.id=p.batch_id JOIN courses c ON c.id=b.course_id JOIN branches br ON br.id=p.branch_id LEFT JOIN users u ON u.id=p.received_by WHERE p.id=?');
$q->execute([$id]); $p=$q->fetch();
if (!$p || ($s!==null && (int)$p['branch_id']!==$s) || (is_student_user() && (int)$p['student_id']!==(int)($_SESSION['user']['student_id']??0))) { http_response_code(404); exit('Receipt not found.'); }
if ($_SERVER['REQUEST_METHOD']==='POST' && ($_POST['act']??'')==='void') {
  csrf_check(); if (!can('void_payment')) { http_response_code(403); exit('Access denied.'); }
  $r=void_payment($id,trim($_POST['reason']??''),(int)$_SESSION['user']['id']);
  if ($r===null) log_activity('payment_void',$p['receipt_no'].': '.trim($_POST['reason']));
  header('Location: receipt.php?id='.$id.'&v='.($r===null?'ok':urlencode($r))); exit;
}
$al=db()->prepare('SELECT i.label,a.amount FROM payment_allocations a JOIN installments i ON i.id=a.installment_id WHERE a.payment_id=? ORDER BY i.due_date,i.no'); $al->execute([$id]); $lines=$al->fetchAll();
$acc=db()->prepare('SELECT * FROM fee_accounts WHERE id=?'); $acc->execute([$p['account_id']]); $a=$acc->fetch(); $sum=account_summary((int)$p['account_id']);
$inst=db()->query("SELECT v FROM settings WHERE k='institute_name'")->fetchColumn() ?: APP_NAME;
?><!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Receipt <?=e($p['receipt_no'])?></title>
<style>
body{font-family:"Segoe UI",Roboto,Arial,sans-serif;background:#f2f3f7;margin:0;padding:16px;color:#1a1c20}
.r{max-width:560px;margin:0 auto;background:#fff;border-radius:16px;padding:28px}
h1{margin:0;font-size:1.3rem}.m{color:#6b7280;font-size:.85rem}.row{display:flex;justify-content:space-between;gap:12px;padding:6px 0}
hr{border:0;border-top:1px solid #e5e7eb;margin:14px 0}.tot{font-size:1.2rem;font-weight:700}
.btns{max-width:560px;margin:0 auto 12px;display:flex;gap:8px}.btns a,.btns button{border:0;border-radius:50px;padding:10px 20px;background:#3e91ff;color:#fff;font-weight:600;text-decoration:none;cursor:pointer;font-size:.95rem}
.btns a.g{background:#fff;color:#1a1c20}
@media print{.noprint{display:none}body{background:#fff;padding:0}.r{border-radius:0;max-width:none}.btns{display:none}}
</style></head><body>
<div class="btns"><button onclick="window.print()">Print / Save as PDF</button><a class="g" href="<?=is_student_user()?'my_fees.php':'payments.php'?>">Back</a></div>
<?php if(isset($_GET['v'])):?><div class="noprint" style="max-width:560px;margin:0 auto 12px;padding:10px 14px;border-radius:12px;background:<?=$_GET['v']==='ok'?'#e3f6ec':'#fdecea'?>"><?=$_GET['v']==='ok'?'Payment cancelled. The amount has been added back to the student\'s balance.':e($_GET['v'])?></div><?php endif;?>
<?php if(!$p['voided_at'] && can('void_payment')):?><details class="noprint" style="max-width:560px;margin:0 auto 12px;background:#fff;border-radius:12px;padding:10px 14px"><summary style="cursor:pointer">Cancel this payment (mistake or refund)</summary>
<form method="post" style="margin-top:10px" onsubmit="return confirm('Cancel this payment? This cannot be undone.')"><input type="hidden" name="csrf" value="<?=csrf()?>"><input type="hidden" name="act" value="void">
<input name="reason" required minlength="5" maxlength="255" placeholder="Reason (e.g. entered twice / refunded in cash)" style="width:100%;padding:10px;border:1px solid #ddd;border-radius:10px;box-sizing:border-box;margin-bottom:8px">
<button style="border:0;border-radius:50px;padding:10px 20px;background:#d9534f;color:#fff;font-weight:600;cursor:pointer">Cancel payment</button></form>
<div class="m" style="margin-top:6px">The receipt stays on record marked VOID. If part of the money was refunded, cancel this payment and record the correct amount again.</div></details><?php endif;?>
<div class="r">
 <h1><?=e($inst)?></h1><div class="m"><?=e($p['brname'])?><?=$p['braddr']?' · '.e($p['braddr']):''?><?=$p['brphone']?' · '.e($p['brphone']):''?></div>
 <?php if($p['voided_at']):?><div style="border:2px solid #d9534f;color:#d9534f;border-radius:10px;padding:10px;margin:12px 0;text-align:center"><b style="font-size:1.2rem">VOID – CANCELLED</b><div class="m" style="color:#d9534f"><?=e(substr($p['voided_at'],0,10))?><?=$p['vby']?' by '.e($p['vby']):''?> · <?=e($p['void_reason'])?></div></div><?php endif;?>
 <hr><div class="row"><b>PAYMENT RECEIPT</b><span><?=e($p['receipt_no'])?></span></div>
 <div class="row"><span class="m">Date</span><span><?=e($p['paid_on'])?></span></div>
 <div class="row"><span class="m">Student</span><span><?=e($p['full_name'])?> (<?=e($p['student_code'])?>)</span></div>
 <div class="row"><span class="m">Course / class</span><span><?=e($p['cname'])?> · <?=e($p['bname'])?> (<?=$p['batch_year']?>)</span></div>
 <div class="row"><span class="m">Method</span><span><?=e(ucwords(str_replace('_',' ',$p['method'])))?><?=$p['reference']?' · '.e($p['reference']):''?></span></div>
 <hr><?php foreach($lines as $l):?><div class="row"><span><?=e($l['label'])?></span><span><?=money($l['amount'])?></span></div><?php endforeach;?>
 <hr><div class="row tot"><span>Amount paid</span><span><?=money($p['amount'])?></span></div>
 <div class="row m"><span>Course fee</span><span><?=money($a['total_fee'])?></span></div>
 <?php if((float)$a['scholarship']>0):?><div class="row m"><span>Scholarship</span><span>− <?=money($a['scholarship'])?></span></div><?php endif;?>
 <div class="row m"><span>Total paid to date</span><span><?=money($sum['paid'])?></span></div>
 <div class="row m"><span>Balance due</span><span><?=money($sum['balance'])?></span></div>
 <hr><div class="m">Received by: <?=e($p['rby']??'—')?><?=$p['note']?' · '.e($p['note']):''?></div>
 <div class="m" style="margin-top:6px">Thank you. This is a computer-generated receipt.</div>
</div>
<?php if(isset($_GET['new'])):?><script>window.addEventListener('load',()=>setTimeout(()=>window.print(),400));</script><?php endif;?>
</body></html>
