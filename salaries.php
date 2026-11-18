<?php
require_once __DIR__.'/includes/bootstrap.php';
require_once __DIR__.'/includes/payments_lib.php';
require_menu('salaries'); if(!can('manage_payroll')){ http_response_code(403); exit('Access denied.'); }
$s=branch_scope(); $manage=can('manage_payroll');
function payroll_target_ok(array $u): bool {
  if ((int)$u['id']===(int)$_SESSION['user']['id']) return false;
  $s=branch_scope(); if ($s!==null && (int)$u['branch_id']!==$s) return false;
  if (!is_owner() && !in_array($u['role'],['administrator','receptionist','teacher','finance','other'],true)) return false;
  return true;
}
$period=$_GET['period']??($_POST['period']??date('Y-m'));
if (!preg_match('/^\d{4}-(0[1-9]|1[0-2])$/',$period) || $period>date('Y-m')) $period=date('Y-m');
$back="salaries.php?period=$period";
function get_user(int $id): ?array { $q=db()->prepare('SELECT id,role,branch_id,full_name FROM users WHERE id=?'); $q->execute([$id]); return $q->fetch()?:null; }

if ($_SERVER['REQUEST_METHOD']==='POST') {
  csrf_check(); if(!$manage){ http_response_code(403); exit('Access denied.'); }
  $act=$_POST['act']??'';
  if ($act==='setup') {
    $u=get_user((int)($_POST['user_id']??0)); $basic=round((float)($_POST['basic']??0),2); $al=round((float)($_POST['allowance']??0),2);
    if (!$u || !payroll_target_ok($u)) flash_set('msg','You cannot set pay for that person.');
    elseif ($basic<0||$al<0) flash_set('msg','Amounts cannot be negative.');
    else {
      db()->prepare('INSERT INTO salary_structures (user_id,basic,allowance,epf) VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE basic=VALUES(basic),allowance=VALUES(allowance),epf=VALUES(epf)')->execute([$u['id'],$basic,$al,empty($_POST['epf'])?0:1]);
      log_activity('salary_setup',$u['full_name']); flash_set('msg','Salary setup saved for '.$u['full_name'].'.');
    }
    header("Location: salaries.php?tab=setup&period=$period"); exit;
  }
  if ($act==='generate') {
    $q=db()->prepare('SELECT u.id,u.role,u.branch_id,ss.basic,ss.allowance,ss.epf FROM users u JOIN salary_structures ss ON ss.user_id=u.id WHERE u.status=\'active\' AND ss.basic>0'.($s!==null?' AND u.branch_id='.(int)$s:''));
    $q->execute(); $n=0;
    $ins=db()->prepare('INSERT IGNORE INTO payroll (user_id,branch_id,period,basic,allowance,epf,net,created_by) VALUES (?,?,?,?,?,?,?,?)');
    foreach($q->fetchAll() as $u){ if(!payroll_target_ok($u)) continue;
      $epf=$u['epf']?round($u['basic']*0.08,2):0; $net=round($u['basic']+$u['allowance']-$epf,2);
      $ins->execute([$u['id'],$u['branch_id'],$period,$u['basic'],$u['allowance'],$epf,$net,$_SESSION['user']['id']]); $n+=$ins->rowCount(); }
    log_activity('payroll_generate',"$period ($n rows)"); flash_set('msg',$n?"Payroll created for $n staff.":'Nothing new to create. Set up salaries first, or payroll already exists for this month.');
    header("Location: $back"); exit;
  }
  if ($act==='update') {
    $pdo=db(); $id=(int)($_POST['id']??0);
    $q=$pdo->prepare('SELECT p.*,u.role,u.full_name FROM payroll p JOIN users u ON u.id=p.user_id WHERE p.id=?'); $q->execute([$id]); $r=$q->fetch();
    $f=fn($k)=>round(max(0,(float)($_POST[$k]??0)),2);
    if (!$r || !payroll_target_ok(['id'=>$r['user_id'],'role'=>$r['role'],'branch_id'=>$r['branch_id']])) flash_set('msg','You cannot change that payroll.');
    elseif ($r['status']==='paid') flash_set('msg','Paid payroll is locked.');
    else {
      $bonus=$f('bonus');$ot=$f('overtime');$od=$f('other_deduction');$adv=$f('advance');
      $net=round($r['basic']+$r['allowance']+$bonus+$ot-$r['epf']-$od-$adv,2);
      if ($net<0) flash_set('msg','Deductions are larger than the pay.');
      else {
        $pay=($_POST['do']??'')==='pay'; $date=$_POST['paid_on']??date('Y-m-d'); $method=$_POST['method']??'cash';
        if ($pay && (!strtotime($date)||$date>date('Y-m-d'))) { flash_set('msg','Payment date is not valid.'); header("Location: $back"); exit; }
        if (!in_array($method,['cash','bank_transfer','cheque','other'],true)) $method='cash';
        try {
          $pdo->beginTransaction();
          $pdo->prepare('UPDATE payroll SET bonus=?,overtime=?,other_deduction=?,advance=?,net=?,note=? WHERE id=? AND status=\'draft\'')->execute([$bonus,$ot,$od,$adv,$net,trim($_POST['note']??'')?:null,$id]);
          if ($pay) {
            $prefix='PS-'.str_replace('-','',$r['period']);
            $m=$pdo->prepare('SELECT COALESCE(MAX(seq_no),0)+1 FROM payroll WHERE slip_prefix=? FOR UPDATE'); $m->execute([$prefix]); $seq=(int)$m->fetchColumn();
            $pdo->prepare("UPDATE payroll SET status='paid',paid_on=?,method=?,slip_prefix=?,seq_no=?,slip_no=? WHERE id=? AND status='draft'")->execute([$date,$method,$prefix,$seq,$prefix.'-'.str_pad($seq,4,'0',STR_PAD_LEFT),$id]);
            log_activity('payroll_paid',$r['full_name'].' '.$r['period']);
          } else log_activity('payroll_edit',$r['full_name'].' '.$r['period']);
          $pdo->commit(); flash_set('msg',$pay?'Marked as paid. Payslip is ready.':'Payroll saved.');
        } catch (PDOException $x) { if($pdo->inTransaction()) $pdo->rollBack(); flash_set('msg','Could not save. Please try again.'); }
      }
    }
    header("Location: $back"); exit;
  }
}
$pageKey='salaries'; require __DIR__.'/includes/header.php';
$msg=flash_get('msg'); $tab=($_GET['tab']??'')==='setup'?'setup':'payroll';
$sc=$s!==null?' AND u.branch_id='.(int)$s:'';
$fmt=fn($v)=>number_format((float)$v,2);
?>
<?php if($msg):?><div class="alert alert-info rounded-4"><?=e($msg)?></div><?php endif;?>
<div class="d-flex gap-2 mb-3 align-items-center flex-wrap">
 <ul class="nav nav-pills me-auto"><li class="nav-item"><a class="nav-link <?=$tab==='payroll'?'active':''?>" href="salaries.php?period=<?=$period?>">Monthly payroll</a></li>
 <?php if($manage):?><li class="nav-item"><a class="nav-link <?=$tab==='setup'?'active':''?>" href="salaries.php?tab=setup&period=<?=$period?>">Salary setup</a></li><?php endif;?></ul>
 <?php if($tab==='payroll'):?><form method="get" class="d-flex gap-2"><input class="form-control" type="month" name="period" value="<?=e($period)?>" max="<?=date('Y-m')?>"><button class="btn-ui">Go</button></form>
 <?php if($manage):?><form method="post"><input type="hidden" name="csrf" value="<?=csrf()?>"><input type="hidden" name="act" value="generate"><input type="hidden" name="period" value="<?=e($period)?>"><button class="btn-ui text-nowrap">Create payroll</button></form><?php endif; endif;?>
</div>
<?php if($tab==='setup' && $manage):
  $st=db()->prepare("SELECT u.id,u.full_name,u.role,u.branch_id,u.job_title,ss.basic,ss.allowance,ss.epf FROM users u LEFT JOIN salary_structures ss ON ss.user_id=u.id WHERE u.status='active' AND u.role<>'student'$sc ORDER BY u.full_name"); $st->execute(); $people=$st->fetchAll(); ?>
<div class="card-ui table-wrap"><input class="form-control mb-2" id="suFilter" placeholder="Search staff"><table class="table align-middle"><thead><tr><th>Name</th><th>Basic</th><th>Allowance</th><th>EPF 8%</th><th></th></tr></thead><tbody data-pager="10" data-filter="#suFilter">
<?php foreach($people as $u): $ok=payroll_target_ok($u);?><tr><td><?=e($u['full_name'])?><div class="lbl"><?=e(ucwords(str_replace('_',' ',$u['role'])))?><?=$u['job_title']?' · '.e($u['job_title']):''?></div></td>
<td><?=$u['basic']!==null?$fmt($u['basic']):'—'?></td><td><?=$u['allowance']!==null?$fmt($u['allowance']):'—'?></td><td><?=$u['epf']?'Yes':'No'?></td>
<td><?php if($ok):?><button class="btn btn-sm btn-light rounded-pill" data-bs-toggle="modal" data-bs-target="#setupModal" onclick='setupU(<?=json_encode(["id"=>$u["id"],"name"=>$u["full_name"],"basic"=>$u["basic"]??0,"allowance"=>$u["allowance"]??0,"epf"=>(int)$u["epf"]],JSON_HEX_APOS|JSON_HEX_QUOT)?>)'>Set pay</button><?php endif;?></td></tr><?php endforeach;?></tbody></table></div>
<div class="modal fade" id="setupModal" tabindex="-1"><div class="modal-dialog modal-dialog-centered"><div class="modal-content" style="border-radius:24px"><form method="post">
<input type="hidden" name="csrf" value="<?=csrf()?>"><input type="hidden" name="act" value="setup"><input type="hidden" name="period" value="<?=e($period)?>"><input type="hidden" name="user_id" id="suId">
<div class="modal-header"><h5 class="modal-title" id="suName">Salary setup</h5><button type="button" class="btn-close" data-bs-dismiss="modal"></button></div>
<div class="modal-body d-grid gap-2"><label class="lbl">Basic salary (monthly)</label><input class="form-control" type="number" step="0.01" min="0" name="basic" id="suBasic" required>
<label class="lbl">Fixed allowance</label><input class="form-control" type="number" step="0.01" min="0" name="allowance" id="suAl">
<label><input type="checkbox" name="epf" value="1" id="suEpf"> Deduct EPF (8% of basic)</label></div>
<div class="modal-footer"><button class="btn-ui">Save</button></div></form></div></div></div>
<?php ob_start();?><script>function setupU(u){suId.value=u.id;suName.textContent=u.name;suBasic.value=u.basic;suAl.value=u.allowance;suEpf.checked=!!u.epf;}</script>
<?php $pageScript=ob_get_clean();
else:
  $q=db()->prepare("SELECT p.*,u.full_name,u.role,u.branch_id ubranch,u.job_title FROM payroll p JOIN users u ON u.id=p.user_id WHERE p.period=?".($s!==null?' AND p.branch_id='.(int)$s:'')." ORDER BY u.full_name"); $q->execute([$period]); $rows=$q->fetchAll();
  $total=0; foreach($rows as $r) $total+=$r['net'];?>
<div class="card-ui table-wrap"><div class="d-flex justify-content-between mb-2"><div class="fw-bold"><?=e(date('F Y',strtotime($period.'-01')))?></div><div>Total net: <b><?=money($total)?></b></div></div>
<input class="form-control mb-2" id="plFilter" placeholder="Search staff">
<table class="table align-middle"><thead><tr><th>Staff</th><th>Earnings</th><th>Deductions</th><th>Net pay</th><th>Status</th><th></th></tr></thead><tbody data-pager="10" data-filter="#plFilter">
<?php foreach($rows as $r): $earn=$r['basic']+$r['allowance']+$r['bonus']+$r['overtime']; $ded=$r['epf']+$r['other_deduction']+$r['advance']; $ok=$manage&&$r['status']==='draft'&&payroll_target_ok(['id'=>$r['user_id'],'role'=>$r['role'],'branch_id'=>$r['branch_id']]);?>
<tr><td><?=e($r['full_name'])?><div class="lbl"><?=e($r['job_title']?:ucwords(str_replace('_',' ',$r['role'])))?></div></td><td><?=$fmt($earn)?></td><td><?=$fmt($ded)?></td><td class="fw-bold"><?=$fmt($r['net'])?></td>
<td><span class="badge rounded-pill text-bg-<?=$r['status']==='paid'?'success':'secondary'?>"><?=e($r['status'])?></span></td>
<td class="text-nowrap"><?php if($ok):?><button class="btn btn-sm btn-light rounded-pill" data-bs-toggle="modal" data-bs-target="#editModal" onclick='editP(<?=json_encode(["id"=>$r["id"],"name"=>$r["full_name"],"basic"=>$r["basic"],"allowance"=>$r["allowance"],"epf"=>$r["epf"],"bonus"=>$r["bonus"],"overtime"=>$r["overtime"],"other"=>$r["other_deduction"],"advance"=>$r["advance"],"note"=>$r["note"]],JSON_HEX_APOS|JSON_HEX_QUOT)?>)'>Edit / Pay</button><?php endif;?>
<?php if($r['status']==='paid'):?><a class="btn btn-sm btn-light rounded-pill" target="_blank" href="payslip.php?id=<?=$r['id']?>">Slip</a><?php endif;?></td></tr>
<?php endforeach; if(!$rows):?><tr><td colspan="6" class="lbl text-center py-4">No payroll for this month yet.<?=$manage?' Set up salaries, then press Create payroll.':''?></td></tr><?php endif;?></tbody></table></div>
<?php if($manage):?>
<div class="modal fade" id="editModal" tabindex="-1"><div class="modal-dialog modal-dialog-centered modal-fullscreen-sm-down"><div class="modal-content" style="border-radius:24px"><form method="post">
<input type="hidden" name="csrf" value="<?=csrf()?>"><input type="hidden" name="act" value="update"><input type="hidden" name="period" value="<?=e($period)?>"><input type="hidden" name="id" id="pId">
<div class="modal-header"><h5 class="modal-title" id="pName">Payroll</h5><button type="button" class="btn-close" data-bs-dismiss="modal"></button></div>
<div class="modal-body"><div class="lbl mb-2" id="pBase"></div><div class="row g-2">
<div class="col-6"><label class="lbl">Bonus</label><input class="form-control" type="number" step="0.01" min="0" name="bonus" id="pBonus"></div>
<div class="col-6"><label class="lbl">Overtime</label><input class="form-control" type="number" step="0.01" min="0" name="overtime" id="pOt"></div>
<div class="col-6"><label class="lbl">Other deductions</label><input class="form-control" type="number" step="0.01" min="0" name="other_deduction" id="pOd"></div>
<div class="col-6"><label class="lbl">Salary advance</label><input class="form-control" type="number" step="0.01" min="0" name="advance" id="pAdv"></div>
<div class="col-12"><input class="form-control" name="note" id="pNote" placeholder="Note (optional)"></div>
<div class="col-6"><label class="lbl">Paid on</label><input class="form-control" type="date" name="paid_on" value="<?=date('Y-m-d')?>" max="<?=date('Y-m-d')?>"></div>
<div class="col-6"><label class="lbl">Method</label><select class="form-select" name="method"><option value="cash">Cash</option><option value="bank_transfer">Bank transfer</option><option value="cheque">Cheque</option><option value="other">Other</option></select></div>
</div></div>
<div class="modal-footer"><button class="btn btn-light rounded-pill" name="do" value="save">Save draft</button><button class="btn-ui" name="do" value="pay" onclick="return confirm('Mark as paid? Paid payroll cannot be edited.')">Save &amp; mark paid</button></div></form></div></div></div>
<?php ob_start();?><script>function editP(r){pId.value=r.id;pName.textContent=r.name;pBase.textContent='Basic '+r.basic+' · Allowance '+r.allowance+' · EPF −'+r.epf;pBonus.value=r.bonus;pOt.value=r.overtime;pOd.value=r.other;pAdv.value=r.advance;pNote.value=r.note||'';}</script>
<?php $pageScript=ob_get_clean(); endif; endif;?>
<?php require __DIR__.'/includes/footer.php';
