<?php
require_once __DIR__.'/includes/bootstrap.php';
require_once __DIR__.'/includes/people_lib.php';
require_once __DIR__.'/includes/student_lib.php';
require_once __DIR__.'/includes/payments_lib.php';
require_once __DIR__.'/includes/users_admin.php';
require_login(); $me=(int)$_SESSION['user']['id']; $id=gint('id'); $s=branch_scope();
$menu=allowed_menu($_SESSION['user']['role']);
if (!in_array('staff',$menu,true)&&!in_array('teachers',$menu,true)) { http_response_code(403); exit('Access denied.'); }
$q=db()->prepare("SELECT u.*,b.name brname FROM users u LEFT JOIN branches b ON b.id=u.branch_id WHERE u.id=? AND u.role<>'student'"); $q->execute([$id]); $t=$q->fetch();
if (!$t || ($s!==null && (int)$t['branch_id']!==$s && $id!==$me)) { http_response_code(404); exit('Person not found.'); }
$self=$id===$me;
$roles=array_values(array_unique(array_merge(assignable_roles('staff'),assignable_roles('teachers'))));
$mgr=can('manage_users')&&!$self&&in_array($t['role'],$roles,true); $full=$mgr||$self; $pay=can('manage_payroll')&&!$self;
$isT=$t['role']==='teacher'; $ownerRole=in_array($t['role'],['super_admin','admin'],true);
const EMP=['full_time'=>'Full time','part_time'=>'Part time','contract'=>'Contract','visiting'=>'Visiting'];
$errs=[]; $old=[]; $openEdit=false; $back='staff_view.php?id='.$id;
if ($_SERVER['REQUEST_METHOD']==='POST') {
  csrf_check(); if(!$mgr){ http_response_code(403); exit('Access denied.'); }
  $act=$_POST['act']??'';
  if ($act==='edit') {
    $old=$_POST; $openEdit=true;
    $f=['full_name'=>mb_substr(trim($_POST['full_name']??''),0,150),'email'=>trim($_POST['email']??''),'phone'=>trim($_POST['phone']??''),'nic'=>strtoupper(trim($_POST['nic']??'')),'date_of_birth'=>$_POST['dob']??'','address'=>mb_substr(trim($_POST['address']??''),0,255),
        'job_title'=>mb_substr(trim($_POST['job_title']??''),0,100),'employment_type'=>$_POST['employment_type']??'','qualifications'=>mb_substr(trim($_POST['qualifications']??''),0,255),'subjects'=>mb_substr(trim($_POST['subjects']??''),0,255),
        'emergency_name'=>mb_substr(trim($_POST['emergency_name']??''),0,120),'emergency_phone'=>trim($_POST['emergency_phone']??''),'joined_on'=>$_POST['joined_on']??'','fingerprint_id'=>mb_substr(trim($_POST['fingerprint_id']??''),0,20),'notes'=>mb_substr(trim($_POST['notes']??''),0,2000)];
    $role=$_POST['role']??$t['role']; if(!in_array($role,$roles,true)) $role=$t['role'];
    if($f['full_name']==='') $errs[]='Full name is required.';
    if(!filter_var($f['email'],FILTER_VALIDATE_EMAIL)) $errs[]='A valid email is required (it is the login).';
    else { $c=db()->prepare('SELECT 1 FROM users WHERE email=? AND id<>?'); $c->execute([$f['email'],$id]); if($c->fetch()) $errs[]='That email is already used by another account.'; }
    if($f['phone']!==''&&strlen(preg_replace('/\D/','',$f['phone']))<9) $errs[]='Enter a valid phone number.';
    if($f['emergency_phone']!==''&&strlen(preg_replace('/\D/','',$f['emergency_phone']))<9) $errs[]='Enter a valid emergency phone number.';
    if($f['nic']!==''&&!preg_match(NIC_PATTERN,$f['nic'])) $errs[]='NIC must be 9 digits + V/X, or 12 digits.';
    if($f['date_of_birth']!==''&&(!strtotime($f['date_of_birth'])||strtotime($f['date_of_birth'])>strtotime('-14 years'))) $errs[]='Date of birth is not valid.';
    if($f['joined_on']!==''&&!strtotime($f['joined_on'])) $errs[]='Joined date is not valid.';
    if($f['employment_type']!==''&&!isset(EMP[$f['employment_type']])) $errs[]='Choose a valid employment type.';
    $bid=$t['branch_id'];
    if(is_owner()){ $bid=in_array($role,['super_admin','admin'],true)?null:((int)($_POST['branch_id']??0)?:null);
      if($bid===null&&!in_array($role,['super_admin','admin'],true)) $errs[]='Choose a branch.';
      elseif($bid){ $c=db()->prepare('SELECT 1 FROM branches WHERE id=?'); $c->execute([$bid]); if(!$c->fetch()) $errs[]='Choose a valid branch.'; } }
    if($t['role']==='super_admin'&&$role!=='super_admin'&&(int)db()->query("SELECT COUNT(*) FROM users WHERE role='super_admin' AND id<>".(int)$id)->fetchColumn()===0) $errs[]='There must be at least one Super Admin.';
    if(!$errs){
      try {
        db()->prepare('UPDATE users SET full_name=?,email=?,role=?,branch_id=?,phone=?,nic=?,date_of_birth=?,address=?,job_title=?,employment_type=?,qualifications=?,subjects=?,emergency_name=?,emergency_phone=?,joined_on=?,fingerprint_id=?,notes=? WHERE id=?')
          ->execute([$f['full_name'],$f['email'],$role,$bid,$f['phone']?:null,$f['nic']?:null,$f['date_of_birth']?:null,$f['address']?:null,$f['job_title']?:null,$f['employment_type']?:null,$f['qualifications']?:null,$f['subjects']?:null,$f['emergency_name']?:null,$f['emergency_phone']?:null,$f['joined_on']?:null,$f['fingerprint_id']?:null,$f['notes']?:null,$id]);
        $chg=[]; foreach($f as $k=>$v) if((string)($t[$k]??'')!==(string)$v) $chg[]=$k; if($role!==$t['role']) $chg[]='role'; if((int)$bid!==(int)$t['branch_id']) $chg[]='branch';
        log_activity('staff_edit',$f['full_name'].($chg?' changed: '.implode(', ',$chg):'')); flash_set('msg','Details saved.'); header('Location: '.$back); exit;
      } catch (PDOException $x) { $errs[]='That fingerprint ID is already used in this branch.'; }
    }
  } elseif ($act==='photo') {
    [$ph,$er]=save_photo($_FILES['photo']??null,$t['photo']);
    if($ph){ db()->prepare('UPDATE users SET photo=? WHERE id=?')->execute([$ph,$id]); log_activity('staff_photo',$t['full_name']); flash_set('msg','Photo updated.'); } else flash_set('msg',$er);
    header('Location: '.$back); exit;
  } elseif ($act==='toggle') {
    db()->prepare("UPDATE users SET status=IF(status='active','inactive','active') WHERE id=?")->execute([$id]); log_activity('user_status',$t['email']); flash_set('msg','Status changed.'); header('Location: '.$back); exit;
  } elseif ($act==='reset') {
    $pw=gen_password(); db()->prepare('UPDATE users SET password_hash=?,must_change_pw=1,failed_logins=0,locked_until=NULL WHERE id=?')->execute([password_hash($pw,PASSWORD_DEFAULT),$id]);
    db()->prepare('UPDATE password_resets SET used_at=NOW() WHERE user_id=? AND used_at IS NULL')->execute([$id]); log_activity('user_pw_reset',$t['email']); flash_set('cred',[$t['email'],$pw]); header('Location: '.$back); exit;
  } elseif ($act==='delete') {
    $paid=(int)db()->query("SELECT COUNT(*) FROM payroll WHERE status='paid' AND user_id=".(int)$id)->fetchColumn();
    $lastSA=$t['role']==='super_admin'&&(int)db()->query("SELECT COUNT(*) FROM users WHERE role='super_admin' AND id<>".(int)$id)->fetchColumn()===0;
    if(!is_owner()) flash_set('msg','Only the Super Admin or an Admin can delete accounts.');
    elseif(trim($_POST['confirm']??'')!=='DELETE') flash_set('msg','Type DELETE to confirm.');
    elseif($paid>0) flash_set('msg','This person has paid payroll records, so the account cannot be deleted. Deactivate it instead.');
    elseif($lastSA) flash_set('msg','The last Super Admin cannot be deleted.');
    else { if($t['photo']) @unlink(__DIR__.'/uploads/'.basename($t['photo'])); db()->prepare('DELETE FROM users WHERE id=?')->execute([$id]); log_activity('staff_delete',$t['full_name'].' ('.$t['email'].')'); flash_set('msg','Account deleted.'); header('Location: '.($isT?'teachers.php':'staff.php')); exit; }
    header('Location: '.$back); exit;
  }
}
$pageKey=$isT?'teachers':'staff'; if(!in_array($pageKey,$menu,true)) $pageKey=in_array('staff',$menu,true)?'staff':'teachers';
require __DIR__.'/includes/header.php'; $msg=flash_get('msg'); $cred=flash_get('cred');
$branches=db()->query("SELECT id,name FROM branches WHERE status='active' ORDER BY name")->fetchAll();
$o=fn($k,$d='')=>e($old[$k]??($t[$k]??$d)); $dobv=$old['dob']??$t['date_of_birth'];
$tabs=['overview'=>'Overview']; if($isT) $tabs['classes']='Classes'; if($full){ $tabs['attendance']='Attendance'; $tabs['leave']='Leave'; } if($pay) $tabs['salary']='Salary'; if($mgr) $tabs['activity']='Activity';
$tenure=$t['joined_on']?(new DateTime($t['joined_on']))->diff(new DateTime()):null;
?>
<a href="<?=$isT?'teachers.php':'staff.php'?>" class="lbl text-decoration-none">← Back to <?=$isT?'teachers':'staff'?></a>
<?php if($msg):?><div class="alert alert-info rounded-4 mt-2"><?=e($msg)?></div><?php endif;?>
<?php if($cred):?><div class="alert alert-success rounded-4 mt-2"><b>New temporary password</b> (shown once)<div>Email: <?=e($cred[0])?></div><div>Password: <b><?=e($cred[1])?></b></div><div class="lbl">They must choose a new password at the next sign-in.</div></div><?php endif;?>
<div class="card-ui my-3 d-flex flex-wrap gap-3 align-items-center justify-content-between"><div class="d-flex gap-3 align-items-center"><?=avatar('u',$id,$t['photo']??null,$t['full_name'],72)?>
 <div><div class="stat" style="font-size:1.3rem"><?=e($t['full_name'])?></div><div class="lbl"><?=e(ROLE_LABELS[$t['role']]??$t['role'])?><?=$t['job_title']?' · '.e($t['job_title']):''?> · <?=e($t['brname']??'All branches')?></div>
 <span class="badge rounded-pill text-bg-<?=$t['status']==='active'?'success':'secondary'?>"><?=e($t['status'])?></span> <?php if($t['employment_type']):?><span class="badge rounded-pill text-bg-light"><?=e(EMP[$t['employment_type']])?></span><?php endif;?></div></div>
 <?php if($mgr):?><div class="d-flex flex-wrap gap-2">
  <button class="btn btn-light rounded-pill btn-sm" data-bs-toggle="modal" data-bs-target="#editStaff"><i class="bi bi-pencil-fill"></i> Edit</button>
  <button class="btn btn-light rounded-pill btn-sm" data-bs-toggle="modal" data-bs-target="#photoStaff"><i class="bi bi-camera-fill"></i> Photo</button>
  <form method="post" onsubmit="return confirm('Create a new temporary password?')"><input type="hidden" name="csrf" value="<?=csrf()?>"><input type="hidden" name="act" value="reset"><button class="btn btn-light rounded-pill btn-sm"><i class="bi bi-key-fill"></i> Reset password</button></form>
  <form method="post"><input type="hidden" name="csrf" value="<?=csrf()?>"><input type="hidden" name="act" value="toggle"><button class="btn btn-light rounded-pill btn-sm"><?=$t['status']==='active'?'Deactivate':'Activate'?></button></form>
  <?php if(is_owner()):?><button class="btn btn-light rounded-pill btn-sm text-danger" data-bs-toggle="modal" data-bs-target="#delStaff"><i class="bi bi-trash-fill"></i> Delete</button><?php endif;?></div><?php endif;?></div>
<ul class="nav nav-pills mb-3 flex-nowrap overflow-auto"><?php foreach($tabs as $k=>$l):?><li class="nav-item"><button class="nav-link <?=$k==='overview'?'active':''?>" data-bs-toggle="pill" data-bs-target="#t-<?=$k?>"><?=$l?></button></li><?php endforeach;?></ul>
<div class="tab-content card-ui">
<div class="tab-pane fade show active" id="t-overview"><div class="row g-3">
<?php $rows=['Role'=>ROLE_LABELS[$t['role']]??$t['role'],'Branch'=>$t['brname']??'All branches','Job title'=>$t['job_title'],'Employment'=>$t['employment_type']?EMP[$t['employment_type']]:null,
 'Joined'=>$t['joined_on']?$t['joined_on'].($tenure?' ('.($tenure->y?$tenure->y.' yr ':'').$tenure->m.' mo)':''):null,'Email'=>$t['email'],'Phone'=>$t['phone'],'Subjects'=>$t['subjects'],'Qualifications'=>$t['qualifications']];
 if($full||$mgr) $rows+=['Fingerprint ID'=>$t['fingerprint_id']];
 if($full) $rows+=['NIC'=>$t['nic'],'Date of birth'=>$t['date_of_birth']?$t['date_of_birth'].' (age '.age_of($t['date_of_birth']).')':null,'Address'=>$t['address'],'Emergency contact'=>$t['emergency_name']?$t['emergency_name'].($t['emergency_phone']?' · '.$t['emergency_phone']:''):null,'Last sign-in'=>$t['last_login']];
 foreach($rows as $l=>$v):?><div class="col-md-6"><div class="lbl"><?=e($l)?></div><div><?=e($v?:'—')?></div></div><?php endforeach;?>
<?php if($mgr&&$t['notes']):?><div class="col-12"><div class="lbl">Management notes</div><div><?=nl2br(e($t['notes']))?></div></div><?php endif;?></div></div>
<?php if($isT): $cl=db()->prepare('SELECT b.*,c.name cname,(SELECT COUNT(*) FROM enrolments e WHERE e.batch_id=b.id) cnt FROM batches b JOIN courses c ON c.id=b.course_id WHERE b.teacher_id=? ORDER BY (b.status=\'active\') DESC,b.start_date DESC'); $cl->execute([$id]); $cls=$cl->fetchAll();
 $sb=db()->prepare('SELECT COUNT(*) FROM substitutions WHERE substitute_id=? AND class_date>=CURDATE()'); $sb->execute([$id]);?>
<div class="tab-pane fade" id="t-classes"><div class="d-flex flex-wrap justify-content-between gap-2 mb-2"><div class="lbl"><?=count($cls)?> class(es) · <?=(int)$sb->fetchColumn()?> upcoming substitute cover(s)</div><a class="btn btn-light rounded-pill btn-sm" href="schedule.php?teacher=<?=$id?>"><i class="bi bi-calendar3"></i> Timetable</a></div>
<div class="table-wrap"><table class="table align-middle"><thead><tr><th>Class</th><th>Time</th><th>Students</th><th>Status</th></tr></thead><tbody data-pager="10">
<?php foreach($cls as $c):?><tr><td><?php if(is_teacher()):?><?=e($c['cname'])?><?php else:?><a href="batch_view.php?id=<?=$c['id']?>"><?=e($c['cname'])?></a><?php endif;?><div class="lbl"><?=e($c['name'])?> (<?=$c['batch_year']?>)</div></td><td><?=e(substr($c['start_time'],0,5))?>–<?=e(substr($c['end_time'],0,5))?><div class="lbl"><?=e($c['class_days'])?></div></td><td><?=$c['cnt']?></td><td><span class="badge rounded-pill text-bg-<?=$c['status']==='active'?'success':'secondary'?>"><?=e($c['status'])?></span></td></tr>
<?php endforeach; if(!$cls):?><tr><td colspan="4" class="lbl text-center py-3">No classes assigned.</td></tr><?php endif;?></tbody></table></div></div><?php endif;?>
<?php if($full): $aq=db()->prepare('SELECT att_date,status,check_in,check_out FROM staff_attendance WHERE user_id=? ORDER BY att_date DESC LIMIT 90'); $aq->execute([$id]); $ar=$aq->fetchAll();
 $sq=db()->prepare("SELECT status,COUNT(*) c FROM staff_attendance WHERE user_id=? AND att_date>=DATE_FORMAT(CURDATE(),'%Y-%m-01') GROUP BY status"); $sq->execute([$id]); $sm=$sq->fetchAll(PDO::FETCH_KEY_PAIR);
 $lv=db()->prepare('SELECT * FROM leave_requests WHERE user_id=? ORDER BY id DESC LIMIT 40'); $lv->execute([$id]); $lvs=$lv->fetchAll();?>
<div class="tab-pane fade" id="t-attendance"><div class="lbl mb-2">This month</div><div class="row g-2 text-center mb-3"><?php foreach(['present'=>'Present','half_day'=>'Half day','leave'=>'Leave','absent'=>'Absent'] as $k=>$l):?><div class="col-3"><div class="stat" style="font-size:1.3rem"><?=(int)($sm[$k]??0)?></div><div class="lbl"><?=$l?></div></div><?php endforeach;?></div>
<div class="table-wrap"><table class="table table-sm"><thead><tr><th>Date</th><th>Status</th><th>In</th><th>Out</th></tr></thead><tbody data-pager="10">
<?php foreach($ar as $z):?><tr><td><?=e($z['att_date'])?></td><td><?=e(ucwords(str_replace('_',' ',$z['status'])))?></td><td><?=e($z['check_in']?substr($z['check_in'],0,5):'—')?></td><td><?=e($z['check_out']?substr($z['check_out'],0,5):'—')?></td></tr>
<?php endforeach; if(!$ar):?><tr><td colspan="4" class="lbl text-center py-3">No attendance recorded.</td></tr><?php endif;?></tbody></table></div></div>
<div class="tab-pane fade" id="t-leave"><div class="table-wrap"><table class="table table-sm align-middle"><thead><tr><th>Dates</th><th>Type</th><th>Status</th></tr></thead><tbody data-pager="10">
<?php foreach($lvs as $z):?><tr><td><?=e($z['from_date'])?> → <?=e($z['to_date'])?><div class="lbl"><?=e($z['reason'])?></div></td><td><?=e(ucfirst($z['leave_type']))?></td><td><span class="badge rounded-pill text-bg-<?=['pending'=>'warning','approved'=>'success','rejected'=>'danger','cancelled'=>'secondary'][$z['status']]?>"><?=e($z['status'])?></span></td></tr>
<?php endforeach; if(!$lvs):?><tr><td colspan="3" class="lbl text-center py-3">No leave requests.</td></tr><?php endif;?></tbody></table></div></div><?php endif;?>
<?php if($pay): $ss=db()->prepare('SELECT * FROM salary_structures WHERE user_id=?'); $ss->execute([$id]); $struct=$ss->fetch();
 $pq=db()->prepare('SELECT id,period,net,status,paid_on,slip_no FROM payroll WHERE user_id=? ORDER BY period DESC LIMIT 24'); $pq->execute([$id]); $pr=$pq->fetchAll();?>
<div class="tab-pane fade" id="t-salary"><div class="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-2"><div><?php if($struct):?>Basic <b><?=money($struct['basic'])?></b> · Allowance <b><?=money($struct['allowance'])?></b> · EPF <?=$struct['epf']?'8%':'none'?><?php else:?><span class="lbl">No salary set up yet.</span><?php endif;?></div><a class="btn btn-light rounded-pill btn-sm" href="salaries.php?tab=setup">Salary setup</a></div>
<div class="table-wrap"><table class="table table-sm align-middle"><thead><tr><th>Month</th><th>Net pay</th><th>Status</th><th></th></tr></thead><tbody data-pager="10">
<?php foreach($pr as $z):?><tr><td><?=e(date('F Y',strtotime($z['period'].'-01')))?></td><td><?=money($z['net'])?></td><td><span class="badge rounded-pill text-bg-<?=$z['status']==='paid'?'success':'secondary'?>"><?=e($z['status'])?></span><?=$z['paid_on']?' <span class="lbl">'.e($z['paid_on']).'</span>':''?></td><td><?php if($z['status']==='paid'):?><a class="btn btn-sm btn-light rounded-pill" target="_blank" href="payslip.php?id=<?=$z['id']?>">Slip</a><?php endif;?></td></tr>
<?php endforeach; if(!$pr):?><tr><td colspan="4" class="lbl text-center py-3">No payroll yet.</td></tr><?php endif;?></tbody></table></div></div><?php endif;?>
<?php if($mgr): $lg=db()->prepare('SELECT created_at,action,details FROM activity_logs WHERE user_id=? ORDER BY id DESC LIMIT 60'); $lg->execute([$id]); $logs=$lg->fetchAll();?>
<div class="tab-pane fade" id="t-activity"><div class="table-wrap"><table class="table table-sm"><thead><tr><th>When</th><th>Action</th><th>Details</th></tr></thead><tbody data-pager="10">
<?php foreach($logs as $z):?><tr><td class="text-nowrap"><?=e($z['created_at'])?></td><td><?=e($z['action'])?></td><td><?=e($z['details'])?></td></tr>
<?php endforeach; if(!$logs):?><tr><td colspan="3" class="lbl text-center py-3">No activity yet.</td></tr><?php endif;?></tbody></table></div></div><?php endif;?>
</div>
<?php if($mgr):?>
<div class="modal fade" id="editStaff" tabindex="-1"><div class="modal-dialog modal-lg modal-fullscreen-md-down modal-dialog-scrollable"><div class="modal-content" style="border-radius:24px"><form method="post">
<input type="hidden" name="csrf" value="<?=csrf()?>"><input type="hidden" name="act" value="edit">
<div class="modal-header"><h5 class="modal-title">Edit <?=e($t['full_name'])?></h5><button type="button" class="btn-close" data-bs-dismiss="modal"></button></div>
<div class="modal-body"><?php foreach($errs as $m):?><div class="alert alert-danger rounded-4 py-2"><?=e($m)?></div><?php endforeach;?><div class="row g-2">
 <div class="col-md-6"><label class="lbl">Full name *</label><input class="form-control" name="full_name" value="<?=$o('full_name')?>" required></div>
 <div class="col-md-6"><label class="lbl">Email (login) *</label><input class="form-control" type="email" name="email" value="<?=$o('email')?>" required></div>
 <div class="col-md-6"><label class="lbl">Role</label><select class="form-select" name="role"><?php foreach($roles as $r):?><option value="<?=$r?>" <?=(($old['role']??$t['role'])===$r)?'selected':''?>><?=e(ROLE_LABELS[$r])?></option><?php endforeach;?></select></div>
 <?php if(is_owner()):?><div class="col-md-6"><label class="lbl">Branch</label><select class="form-select" name="branch_id"><option value="">None (Super Admin / Admin)</option><?php foreach($branches as $b):?><option value="<?=$b['id']?>" <?=((int)($old['branch_id']??$t['branch_id'])===(int)$b['id'])?'selected':''?>><?=e($b['name'])?></option><?php endforeach;?></select></div><?php endif;?>
 <div class="col-md-6"><label class="lbl">Phone</label><input class="form-control" name="phone" value="<?=$o('phone')?>"></div>
 <div class="col-md-6"><label class="lbl">NIC</label><input class="form-control" name="nic" value="<?=$o('nic')?>"></div>
 <div class="col-md-6"><label class="lbl">Date of birth</label><input class="form-control" type="date" name="dob" value="<?=e($dobv)?>"></div>
 <div class="col-md-6"><label class="lbl">Joined on</label><input class="form-control" type="date" name="joined_on" value="<?=$o('joined_on')?>"></div>
 <div class="col-12"><label class="lbl">Address</label><input class="form-control" name="address" value="<?=$o('address')?>"></div>
 <div class="col-md-6"><label class="lbl">Job title</label><input class="form-control" name="job_title" value="<?=$o('job_title')?>"></div>
 <div class="col-md-6"><label class="lbl">Employment type</label><select class="form-select" name="employment_type"><option value="">—</option><?php foreach(EMP as $k=>$l):?><option value="<?=$k?>" <?=(($old['employment_type']??$t['employment_type'])===$k)?'selected':''?>><?=$l?></option><?php endforeach;?></select></div>
 <div class="col-md-6"><label class="lbl">Qualifications</label><input class="form-control" name="qualifications" value="<?=$o('qualifications')?>"></div>
 <div class="col-md-6"><label class="lbl">Subjects taught</label><input class="form-control" name="subjects" value="<?=$o('subjects')?>"></div>
 <div class="col-md-6"><label class="lbl">Emergency contact name</label><input class="form-control" name="emergency_name" value="<?=$o('emergency_name')?>"></div>
 <div class="col-md-6"><label class="lbl">Emergency contact phone</label><input class="form-control" name="emergency_phone" value="<?=$o('emergency_phone')?>"></div>
 <div class="col-md-6"><label class="lbl">Fingerprint machine ID</label><input class="form-control" name="fingerprint_id" value="<?=$o('fingerprint_id')?>"></div>
 <div class="col-12"><label class="lbl">Management notes (private)</label><textarea class="form-control" name="notes" rows="2"><?=$o('notes')?></textarea></div>
</div></div><div class="modal-footer"><button class="btn-ui">Save changes</button></div></form></div></div></div>
<div class="modal fade" id="photoStaff" tabindex="-1"><div class="modal-dialog modal-dialog-centered"><div class="modal-content" style="border-radius:24px"><form method="post" enctype="multipart/form-data"><input type="hidden" name="csrf" value="<?=csrf()?>"><input type="hidden" name="act" value="photo">
<div class="modal-header"><h5 class="modal-title">Profile photo</h5><button type="button" class="btn-close" data-bs-dismiss="modal"></button></div><div class="modal-body"><input class="form-control" type="file" name="photo" accept="image/jpeg,image/png,image/webp" required><div class="lbl mt-1">JPG, PNG or WebP, up to 2 MB.</div></div><div class="modal-footer"><button class="btn-ui">Upload</button></div></form></div></div></div>
<?php if(is_owner()):?><div class="modal fade" id="delStaff" tabindex="-1"><div class="modal-dialog modal-dialog-centered"><div class="modal-content" style="border-radius:24px"><form method="post"><input type="hidden" name="csrf" value="<?=csrf()?>"><input type="hidden" name="act" value="delete">
<div class="modal-header"><h5 class="modal-title text-danger">Delete account</h5><button type="button" class="btn-close" data-bs-dismiss="modal"></button></div><div class="modal-body"><p>This permanently removes <b><?=e($t['full_name'])?></b>, their attendance and leave records<?=$isT?', and unassigns their classes':''?>. It cannot be undone. If they have been paid through payroll, deactivate the account instead.</p><input class="form-control" name="confirm" placeholder="Type DELETE to confirm" autocomplete="off" required></div><div class="modal-footer"><button class="btn btn-danger rounded-pill">Delete permanently</button></div></form></div></div></div><?php endif;?>
<?php if($openEdit):?><?php ob_start();?><script>new bootstrap.Modal(document.getElementById('editStaff')).show();</script><?php $pageScript=ob_get_clean(); endif; endif;?>
<?php require __DIR__.'/includes/footer.php';
