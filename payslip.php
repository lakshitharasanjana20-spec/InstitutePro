<?php
require_once __DIR__.'/includes/bootstrap.php';
require_once __DIR__.'/includes/payments_lib.php';
require_login(); $me=$_SESSION['user']; $s=branch_scope(); $id=(int)($_GET['id']??0);
$q=db()->prepare('SELECT p.*,u.full_name,u.role,u.nic,u.job_title,br.name brname FROM payroll p JOIN users u ON u.id=p.user_id LEFT JOIN branches br ON br.id=p.branch_id WHERE p.id=?');
$q->execute([$id]); $p=$q->fetch();
$own=$p && (int)$p['user_id']===(int)$me['id'] && $p['status']==='paid';
$admin=$p && $p['status']==='paid' && in_array('salaries',allowed_menu($me['role']),true) && ($s===null || (int)$p['branch_id']===$s);
if (!$p || (!$own && !$admin)) { http_response_code(404); exit('Payslip not found.'); }
$inst=db()->query("SELECT v FROM settings WHERE k='institute_name'")->fetchColumn() ?: APP_NAME;
$earn=[['Basic salary',$p['basic']],['Allowance',$p['allowance']],['Bonus',$p['bonus']],['Overtime',$p['overtime']]];
$ded=[['EPF (8%)',$p['epf']],['Other deductions',$p['other_deduction']],['Salary advance',$p['advance']]];
?><!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Payslip <?=e($p['slip_no'])?></title>
<style>
body{font-family:"Segoe UI",Roboto,Arial,sans-serif;background:#f2f3f7;margin:0;padding:16px;color:#1a1c20}
.r{max-width:600px;margin:0 auto;background:#fff;border-radius:16px;padding:28px}
h1{margin:0;font-size:1.3rem}.m{color:#6b7280;font-size:.85rem}.row{display:flex;justify-content:space-between;gap:12px;padding:5px 0}
hr{border:0;border-top:1px solid #e5e7eb;margin:14px 0}.tot{font-size:1.25rem;font-weight:700}h3{margin:6px 0;font-size:.95rem}
.btns{max-width:600px;margin:0 auto 12px;display:flex;gap:8px}.btns a,.btns button{border:0;border-radius:50px;padding:10px 20px;background:#3e91ff;color:#fff;font-weight:600;text-decoration:none;cursor:pointer;font-size:.95rem}
.btns a.g{background:#fff;color:#1a1c20}
@media print{body{background:#fff;padding:0}.r{border-radius:0;max-width:none}.btns{display:none}}
</style></head><body>
<div class="btns"><button onclick="window.print()">Print / Save as PDF</button><a class="g" href="javascript:history.back()">Back</a></div>
<div class="r"><h1><?=e($inst)?></h1><div class="m"><?=e($p['brname']??'Head office')?></div>
<hr><div class="row"><b>PAYSLIP · <?=e(date('F Y',strtotime($p['period'].'-01')))?></b><span><?=e($p['slip_no'])?></span></div>
<div class="row"><span class="m">Employee</span><span><?=e($p['full_name'])?></span></div>
<div class="row"><span class="m">Position</span><span><?=e($p['job_title']?:ucwords(str_replace('_',' ',$p['role'])))?></span></div>
<?php if($p['nic']):?><div class="row"><span class="m">NIC</span><span><?=e($p['nic'])?></span></div><?php endif;?>
<hr><h3>Earnings</h3><?php foreach($earn as $x): if((float)$x[1]==0 && $x[0]!=='Basic salary') continue;?><div class="row"><span><?=e($x[0])?></span><span><?=money($x[1])?></span></div><?php endforeach;?>
<h3 style="margin-top:12px">Deductions</h3><?php $any=false; foreach($ded as $x){ if((float)$x[1]==0) continue; $any=true;?><div class="row"><span><?=e($x[0])?></span><span>− <?=money($x[1])?></span></div><?php } if(!$any):?><div class="row m"><span>None</span><span></span></div><?php endif;?>
<hr><div class="row tot"><span>Net pay</span><span><?=money($p['net'])?></span></div>
<div class="row m"><span>Paid on <?=e($p['paid_on'])?></span><span><?=e(ucwords(str_replace('_',' ',$p['method'])))?></span></div>
<?php if($p['note']):?><div class="m"><?=e($p['note'])?></div><?php endif;?>
<hr><div class="m">This is a computer-generated payslip.</div></div></body></html>
