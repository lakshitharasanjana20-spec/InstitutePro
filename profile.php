<?php
require_once __DIR__.'/includes/bootstrap.php';
require_once __DIR__.'/includes/people_lib.php';
$pageKey='profile'; require __DIR__.'/includes/header.php';
$st=db()->prepare('SELECT u.*,b.name branch FROM users u LEFT JOIN branches b ON b.id=u.branch_id WHERE u.id=?');
$st->execute([$u['id']]); $p=$st->fetch();
$msg='';
if ($_SERVER['REQUEST_METHOD']==='POST') { csrf_check();
  if (($_POST['act']??'')==='photo') { [$ph,$er]=save_photo($_FILES['photo']??null,$p['photo']??null); if($ph){ db()->prepare('UPDATE users SET photo=? WHERE id=?')->execute([$ph,$p['id']]); $msg='Photo updated.'; } else $msg=$er; }
  elseif (($_POST['act']??'')==='pw') {
    if (password_verify($_POST['old']??'',$p['password_hash']) && strlen($_POST['new']??'')>=8) {
      db()->prepare('UPDATE users SET password_hash=?,must_change_pw=0 WHERE id=?')->execute([password_hash($_POST['new'],PASSWORD_DEFAULT),$p['id']]);
      log_activity('password_change','Own password changed'); $msg='Password updated.';
    } else $msg='Current password wrong or new password under 8 characters.';
  } else {
    db()->prepare('UPDATE users SET phone=?,address=?,emergency_name=?,emergency_phone=?,qualifications=? WHERE id=?')->execute([trim($_POST['phone']),mb_substr(trim($_POST['address']),0,255),mb_substr(trim($_POST['emergency_name']??''),0,120)?:null,trim($_POST['emergency_phone']??'')?:null,mb_substr(trim($_POST['qualifications']??''),0,255)?:null,$p['id']]);
    log_activity('profile_update','Contact details updated'); $msg='Profile saved.';
  }
  $st->execute([$u['id']]); $p=$st->fetch();
}
?>
<?php if($msg):?><div class="alert alert-info rounded-4"><?=e($msg)?></div><?php endif;?>
<div class="card-ui mb-3 d-flex gap-3 align-items-center">
 <?=avatar('u',(int)$p['id'],$p['photo']??null,$p['full_name'],72)?>
 <div><div class="stat" style="font-size:1.3rem"><?=e($p['full_name'])?></div>
 <div class="lbl"><?=e(ucwords(str_replace('_',' ',$p['role'])))?> · <?=e($p['branch']??'All branches')?></div></div></div>
<ul class="nav nav-pills mb-3 flex-nowrap overflow-auto">
 <?php foreach(['personal'=>'Personal','contact'=>'Contact','attendance'=>'Attendance','salary'=>'Salary Payroll','security'=>'Security'] as $id=>$t):?>
 <li class="nav-item"><button class="nav-link <?=$id==='personal'?'active':''?>" data-bs-toggle="pill" data-bs-target="#t-<?=$id?>"><?=$t?></button></li><?php endforeach;?>
</ul>
<div class="tab-content card-ui">
 <div class="tab-pane fade show active" id="t-personal"><form method="post" enctype="multipart/form-data" class="d-flex flex-wrap gap-2 mb-3"><input type="hidden" name="csrf" value="<?=csrf()?>"><input type="hidden" name="act" value="photo"><input class="form-control" style="max-width:320px" type="file" name="photo" accept="image/jpeg,image/png,image/webp" required><button class="btn btn-light rounded-pill">Change photo</button></form><div class="row g-3">
  <?php foreach(['Full name'=>$p['full_name'],'Email'=>$p['email'],'NIC'=>$p['nic'],'Date of birth'=>$p['date_of_birth'],'Job title'=>$p['job_title'],'Employment'=>$p['employment_type']?ucwords(str_replace('_',' ',$p['employment_type'])):null,'Qualifications'=>$p['qualifications'],'Subjects'=>$p['subjects'],'Joined'=>$p['joined_on'],'Last login'=>$p['last_login']] as $l=>$v):?>
  <div class="col-md-6"><div class="lbl"><?=$l?></div><div><?=e($v?:'—')?></div></div><?php endforeach;?></div></div>
 <div class="tab-pane fade" id="t-contact"><form method="post" class="row g-3"><input type="hidden" name="csrf" value="<?=csrf()?>">
  <div class="col-md-6"><label class="lbl">Phone</label><input class="form-control" name="phone" value="<?=e($p['phone'])?>"></div>
  <div class="col-md-6"><label class="lbl">Address</label><input class="form-control" name="address" value="<?=e($p['address'])?>"></div>
  <div class="col-md-6"><label class="lbl">Emergency contact name</label><input class="form-control" name="emergency_name" value="<?=e($p['emergency_name'])?>"></div>
  <div class="col-md-6"><label class="lbl">Emergency contact phone</label><input class="form-control" name="emergency_phone" value="<?=e($p['emergency_phone'])?>"></div>
  <div class="col-12"><label class="lbl">Qualifications</label><input class="form-control" name="qualifications" value="<?=e($p['qualifications'])?>"></div>
  <div class="col-12"><button class="btn-ui">Save</button></div></form></div>
 <div class="tab-pane fade" id="t-attendance"><?php $aq=db()->prepare('SELECT att_date,status,check_in,check_out,source FROM staff_attendance WHERE user_id=? ORDER BY att_date DESC LIMIT 60'); $aq->execute([$p['id']]); $arows=$aq->fetchAll();
$sq=db()->prepare("SELECT status,COUNT(*) c FROM staff_attendance WHERE user_id=? AND att_date>=DATE_FORMAT(CURDATE(),'%Y-%m-01') GROUP BY status"); $sq->execute([$p['id']]); $sm=$sq->fetchAll(PDO::FETCH_KEY_PAIR);?>
<div class="row g-2 text-center mb-3"><?php foreach(['present'=>'Present','half_day'=>'Half day','leave'=>'Leave','absent'=>'Absent'] as $k=>$l):?><div class="col-3"><div class="stat" style="font-size:1.3rem"><?=(int)($sm[$k]??0)?></div><div class="lbl"><?=$l?></div></div><?php endforeach;?></div><div class="lbl mb-2">This month</div>
<div class="table-wrap"><table class="table table-sm"><thead><tr><th>Date</th><th>Status</th><th>In</th><th>Out</th></tr></thead><tbody data-pager="10">
<?php foreach($arows as $z):?><tr><td><?=e($z['att_date'])?></td><td><?=e(ucwords(str_replace('_',' ',$z['status'])))?></td><td><?=e($z['check_in']?substr($z['check_in'],0,5):'—')?></td><td><?=e($z['check_out']?substr($z['check_out'],0,5):'—')?></td></tr>
<?php endforeach; if(!$arows):?><tr><td colspan="4" class="lbl text-center py-3">No attendance recorded yet.</td></tr><?php endif;?></tbody></table></div></div>
 <div class="tab-pane fade" id="t-salary"><?php require_once __DIR__.'/includes/payments_lib.php'; $pq=db()->prepare("SELECT id,period,net,paid_on,slip_no FROM payroll WHERE user_id=? AND status='paid' ORDER BY period DESC LIMIT 36"); $pq->execute([$p['id']]); $slips=$pq->fetchAll();?>
<div class="table-wrap"><table class="table align-middle"><thead><tr><th>Month</th><th>Net pay</th><th>Paid on</th><th></th></tr></thead><tbody data-pager="10">
<?php foreach($slips as $z):?><tr><td><?=e(date('F Y',strtotime($z['period'].'-01')))?></td><td><?=money($z['net'])?></td><td><?=e($z['paid_on'])?></td><td><a class="btn btn-sm btn-light rounded-pill" href="payslip.php?id=<?=$z['id']?>" target="_blank">View / Print</a></td></tr>
<?php endforeach; if(!$slips):?><tr><td colspan="4" class="lbl text-center py-3">No payslips yet.</td></tr><?php endif;?></tbody></table></div></div>
 <div class="tab-pane fade" id="t-security"><form method="post" class="row g-3"><input type="hidden" name="csrf" value="<?=csrf()?>"><input type="hidden" name="act" value="pw">
  <div class="col-md-6"><label class="lbl">Current password</label><input type="password" class="form-control" name="old" required></div>
  <div class="col-md-6"><label class="lbl">New password (8+ chars)</label><input type="password" class="form-control" name="new" required minlength="8"></div>
  <div class="col-12"><button class="btn-ui">Change password</button></div></form></div>
</div>
<?php require __DIR__.'/includes/footer.php';
