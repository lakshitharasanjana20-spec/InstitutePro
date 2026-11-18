<?php
require_once __DIR__.'/includes/bootstrap.php';
require_menu('students'); require_once __DIR__.'/includes/student_lib.php'; require_once __DIR__.'/includes/people_lib.php'; refresh_batches();
$s=branch_scope(); $id=(int)($_GET['id']??0);
$st=db()->prepare('SELECT s.*,br.name brname FROM students s JOIN branches br ON br.id=s.branch_id WHERE s.id=?'); $st->execute([$id]); $p=$st->fetch();
if (!$p || ($s!==null && (int)$p['branch_id']!==$s)) { http_response_code(404); exit('Student not found.'); }
if (is_teacher()) { $tq=db()->prepare('SELECT 1 FROM enrolments e JOIN batches b ON b.id=e.batch_id WHERE e.student_id=? AND (b.teacher_id=? OR EXISTS (SELECT 1 FROM substitutions sbx WHERE sbx.batch_id=b.id AND sbx.substitute_id=? AND sbx.class_date>=DATE_SUB(CURDATE(),INTERVAL 1 DAY)))'); $tq->execute([$id,$_SESSION['user']['id'],$_SESSION['user']['id']]); if(!$tq->fetch()){ http_response_code(404); exit('Student not found.'); } }
$editErrs=[]; $oldIn=[]; $openEdit=false;
if ($_SERVER['REQUEST_METHOD']==='POST' && in_array($_POST['act']??'',['student_edit','student_photo','student_delete'],true)) {
  csrf_check(); $a=$_POST['act'];
  if ($a==='student_delete') {
    if(!can('delete_student')){ http_response_code(403); exit('Access denied.'); }
    $pc=(int)db()->query('SELECT COUNT(*) FROM payments WHERE student_id='.(int)$id)->fetchColumn();
    if (trim($_POST['confirm']??'')!==$p['student_code']) flash_set('msg','Type the student ID exactly to confirm the deletion.');
    elseif ($pc>0) flash_set('msg','This student has payment records, so the profile cannot be deleted. Set the status to Inactive instead.');
    else {
      $fl=db()->prepare('SELECT stored_name FROM assignment_submissions WHERE student_id=? AND stored_name IS NOT NULL'); $fl->execute([$id]); foreach($fl->fetchAll(PDO::FETCH_COLUMN) as $fn) @unlink(__DIR__.'/uploads/'.basename($fn));
      if ($p['photo']) @unlink(__DIR__.'/uploads/'.basename($p['photo']));
      db()->prepare('DELETE FROM users WHERE student_id=?')->execute([$id]); db()->prepare('DELETE FROM students WHERE id=?')->execute([$id]);
      log_activity('student_delete',$p['student_code'].' '.$p['full_name']); flash_set('msg','Student deleted.'); header('Location: students.php'); exit;
    }
    header('Location: student_view.php?id='.$id); exit;
  }
  if(!can('edit_student')){ http_response_code(403); exit('Access denied.'); }
  if ($a==='student_photo') {
    [$ph,$er]=save_photo($_FILES['photo']??null,$p['photo']??null);
    if($ph){ db()->prepare('UPDATE students SET photo=? WHERE id=?')->execute([$ph,$id]); log_activity('student_photo',$p['student_code']); flash_set('msg','Photo updated.'); } else flash_set('msg',$er);
    header('Location: student_view.php?id='.$id); exit;
  }
  $oldIn=$_POST; $openEdit=true; [$editErrs,$c]=validate_student($_POST);
  $status=in_array($_POST['status']??'',['active','inactive'],true)?$_POST['status']:$p['status'];
  if (!$editErrs) {
    try {
      db()->prepare('UPDATE students SET first_name=?,last_name=?,full_name=?,date_of_birth=?,address=?,phone=?,email=?,nic=?,guardian_name=?,guardian_relation=?,guardian_phone=?,fingerprint_id=?,status=? WHERE id=?')
        ->execute([$c['first_name'],$c['last_name'],$c['full_name'],$c['date_of_birth'],$c['address']?:null,$c['phone'],$c['email'],$c['nic'],$c['guardian_name'],$c['guardian_relation'],$c['guardian_phone'],$c['fingerprint_id'],$status,$id]);
      db()->prepare('UPDATE users SET full_name=?,phone=? WHERE student_id=?')->execute([$c['full_name'],$c['phone'],$id]);
      $chg=[]; foreach($c as $k=>$v) if((string)($p[$k]??'')!==(string)$v) $chg[]=$k; if($status!==$p['status']) $chg[]='status';
      log_activity('student_edit',$p['student_code'].($chg?' changed: '.implode(', ',$chg):'')); flash_set('msg','Student details saved.'); header('Location: student_view.php?id='.$id); exit;
    } catch (PDOException $x) { $editErrs[]='That fingerprint ID is already used in this branch.'; }
  }
}
if ($_SERVER['REQUEST_METHOD']==='POST' && in_array($_POST['act']??'',['login_create','login_reset','login_toggle'],true)) {
  csrf_check(); if(!can('manage_student_accounts')){ http_response_code(403); exit('Access denied.'); }
  $a=$_POST['act'];
  if ($a==='login_create') { $r=create_student_login(['id'=>$id,'branch_id'=>$p['branch_id'],'full_name'=>$p['full_name'],'phone'=>$p['phone']],trim($_POST['email']??'')); if($r[0]){ log_activity('student_login_create',$p['student_code']); flash_set('cred',[trim($_POST['email']),$r[1]]); } else flash_set('msg',$r[1]); }
  elseif ($a==='login_reset') { $pw=reset_student_login($id); if($pw){ log_activity('student_login_reset',$p['student_code']); $em=db()->prepare('SELECT email FROM users WHERE student_id=?'); $em->execute([$id]); flash_set('cred',[$em->fetchColumn(),$pw]); } }
  elseif ($a==='login_toggle') { db()->prepare("UPDATE users SET status=IF(status='active','inactive','active') WHERE student_id=?")->execute([$id]); log_activity('student_login_toggle',$p['student_code']); flash_set('msg','Login status changed.'); }
  header('Location: student_view.php?id='.$id); exit;
}
if ($_SERVER['REQUEST_METHOD']==='POST' && ($_POST['act']??'')==='comment') {
  csrf_check();
  if (!can('comment')) { http_response_code(403); exit('Access denied.'); }
  $c=trim($_POST['comment']??'');
  if ($c!=='') { db()->prepare('INSERT INTO student_comments (student_id,user_id,comment) VALUES (?,?,?)')->execute([$id,$_SESSION['user']['id'],$c]); log_activity('student_comment',$p['student_code']); }
  header('Location: student_view.php?id='.$id.'#comments'); exit;
}
$pageKey='students'; require __DIR__.'/includes/header.php';
$age=(new DateTime($p['date_of_birth']))->diff(new DateTime())->y;
$en=db()->prepare("SELECT b.id,b.name,b.batch_year,b.status,c.name cname,t.name tname,e.enrolled_on FROM enrolments e JOIN batches b ON b.id=e.batch_id JOIN courses c ON c.id=b.course_id JOIN course_types t ON t.id=c.type_id WHERE e.student_id=? ORDER BY e.enrolled_on DESC");
$en->execute([$id]); $enr=$en->fetchAll();
$cm=db()->prepare('SELECT m.*,u.full_name FROM student_comments m LEFT JOIN users u ON u.id=m.user_id WHERE m.student_id=? ORDER BY m.id DESC'); $cm->execute([$id]); $comments=$cm->fetchAll();
$tabs=['personal'=>'Personal','academic'=>'Academic','attendance'=>'Attendance','payments'=>'Payments','exams'=>'Exams & Results','comments'=>'Comments'];
$ar=db()->prepare('SELECT a.*,b.name bname FROM attendance a JOIN batches b ON b.id=a.batch_id WHERE a.student_id=? ORDER BY a.att_date DESC LIMIT 90'); $ar->execute([$id]); $att=$ar->fetchAll();
$ph=[];
?>
<a href="students.php" class="lbl text-decoration-none">← Back to students</a>
<div class="card-ui my-3 d-flex flex-wrap gap-3 align-items-center justify-content-between"><div class="d-flex gap-3 align-items-center"><?=avatar('s',$id,$p['photo']??null,$p['full_name'],72)?>
 <div><div class="stat" style="font-size:1.3rem"><?=e($p['full_name'])?></div>
 <div class="lbl"><?=e($p['student_code'])?> · <?=e($p['brname'])?> · <span class="badge rounded-pill text-bg-<?=$p['status']==='active'?'success':'secondary'?>"><?=e($p['status'])?></span></div></div></div>
 <?php if(can('edit_student')||can('delete_student')):?><div class="d-flex flex-wrap gap-2">
  <?php if(can('edit_student')):?><button class="btn btn-light rounded-pill btn-sm" data-bs-toggle="modal" data-bs-target="#editStu"><i class="bi bi-pencil-fill"></i> Edit details</button>
  <button class="btn btn-light rounded-pill btn-sm" data-bs-toggle="modal" data-bs-target="#photoStu"><i class="bi bi-camera-fill"></i> Photo</button><?php endif;?>
  <?php if(can('delete_student')):?><button class="btn btn-light rounded-pill btn-sm text-danger" data-bs-toggle="modal" data-bs-target="#delStu"><i class="bi bi-trash-fill"></i> Delete</button><?php endif;?></div><?php endif;?></div>
<?php if(can('edit_student')):
 $v=fn($k,$d='')=>e($oldIn[$k]??$d); $isMinorForm=true;?>
<div class="modal fade" id="editStu" tabindex="-1"><div class="modal-dialog modal-lg modal-fullscreen-md-down modal-dialog-scrollable"><div class="modal-content" style="border-radius:24px"><form method="post">
<input type="hidden" name="csrf" value="<?=csrf()?>"><input type="hidden" name="act" value="student_edit">
<div class="modal-header"><h5 class="modal-title">Edit student details</h5><button type="button" class="btn-close" data-bs-dismiss="modal"></button></div>
<div class="modal-body"><?php foreach($editErrs as $m):?><div class="alert alert-danger rounded-4 py-2"><?=e($m)?></div><?php endforeach;?><div class="row g-2">
 <div class="col-md-6"><label class="lbl">First name *</label><input class="form-control" name="first_name" value="<?=$v('first_name',$p['first_name'])?>" required></div>
 <div class="col-md-6"><label class="lbl">Last name *</label><input class="form-control" name="last_name" value="<?=$v('last_name',$p['last_name'])?>" required></div>
 <div class="col-12"><label class="lbl">Full name</label><input class="form-control" name="full_name" value="<?=$v('full_name',$p['full_name'])?>"></div>
 <div class="col-md-6"><label class="lbl">Date of birth *</label><input class="form-control" type="date" id="eDob" name="dob" value="<?=$v('dob',$p['date_of_birth'])?>" required></div>
 <div class="col-md-6"><label class="lbl">Contact no *</label><input class="form-control" name="phone" value="<?=$v('phone',$p['phone'])?>" required></div>
 <div class="col-md-6"><label class="lbl">Email</label><input class="form-control" type="email" name="email" value="<?=$v('email',$p['email'])?>"></div>
 <div class="col-md-6"><label class="lbl">Fingerprint machine ID</label><input class="form-control" name="fingerprint_id" value="<?=$v('fingerprint_id',$p['fingerprint_id'])?>"></div>
 <div class="col-12"><label class="lbl">Address</label><input class="form-control" name="address" value="<?=$v('address',$p['address'])?>"></div>
 <div class="col-12" id="eAdult" style="display:none"><label class="lbl">NIC number (age 18 or over) *</label><input class="form-control" name="nic" value="<?=$v('nic',$p['nic'])?>"></div>
 <div class="col-12" id="eMinor" style="display:none"><div class="row g-2"><div class="col-md-4"><label class="lbl">Guardian name *</label><input class="form-control" name="g_name" value="<?=$v('g_name',$p['guardian_name'])?>"></div>
  <div class="col-md-4"><label class="lbl">Relationship *</label><select class="form-select" name="g_rel"><option value="">Choose</option><?php foreach(['Mother','Father','Guardian'] as $r):?><option <?=(($oldIn['g_rel']??$p['guardian_relation'])===$r)?'selected':''?>><?=$r?></option><?php endforeach;?></select></div>
  <div class="col-md-4"><label class="lbl">Guardian contact *</label><input class="form-control" name="g_phone" value="<?=$v('g_phone',$p['guardian_phone'])?>"></div></div></div>
 <div class="col-md-6"><label class="lbl">Status</label><select class="form-select" name="status"><option value="active" <?=(($oldIn['status']??$p['status'])==='active')?'selected':''?>>Active</option><option value="inactive" <?=(($oldIn['status']??$p['status'])==='inactive')?'selected':''?>>Inactive</option></select></div>
</div><div class="lbl mt-2">The student ID, class and branch cannot be changed here. Changes are recorded in the Activity Log.</div></div>
<div class="modal-footer"><button class="btn-ui">Save changes</button></div></form></div></div></div>
<div class="modal fade" id="photoStu" tabindex="-1"><div class="modal-dialog modal-dialog-centered"><div class="modal-content" style="border-radius:24px"><form method="post" enctype="multipart/form-data"><input type="hidden" name="csrf" value="<?=csrf()?>"><input type="hidden" name="act" value="student_photo">
<div class="modal-header"><h5 class="modal-title">Student photo</h5><button type="button" class="btn-close" data-bs-dismiss="modal"></button></div><div class="modal-body"><input class="form-control" type="file" name="photo" accept="image/jpeg,image/png,image/webp" required><div class="lbl mt-1">JPG, PNG or WebP, up to 2 MB.</div></div><div class="modal-footer"><button class="btn-ui">Upload</button></div></form></div></div></div>
<?php endif;?>
<?php if(can('delete_student')):?><div class="modal fade" id="delStu" tabindex="-1"><div class="modal-dialog modal-dialog-centered"><div class="modal-content" style="border-radius:24px"><form method="post"><input type="hidden" name="csrf" value="<?=csrf()?>"><input type="hidden" name="act" value="student_delete">
<div class="modal-header"><h5 class="modal-title text-danger">Delete student</h5><button type="button" class="btn-close" data-bs-dismiss="modal"></button></div><div class="modal-body"><p>This permanently removes <b><?=e($p['full_name'])?></b> with their enrolments, attendance, exam marks, comments and portal login. It cannot be undone. Students with payment records cannot be deleted; set them to Inactive instead.</p>
<input class="form-control" name="confirm" placeholder="Type <?=e($p['student_code'])?> to confirm" autocomplete="off" required></div><div class="modal-footer"><button class="btn btn-danger rounded-pill">Delete permanently</button></div></form></div></div></div><?php endif;?>
<?php $cred=flash_get('cred'); $fm=flash_get('msg'); $lg=db()->prepare('SELECT id,email,status,last_login FROM users WHERE student_id=?'); $lg->execute([$id]); $login=$lg->fetch();?>
<?php if($fm):?><div class="alert alert-info rounded-4"><?=e($fm)?></div><?php endif;?>
<?php if($cred):?><div class="alert alert-success rounded-4"><b>Portal login ready</b> (shown once)<div>Email: <?=e($cred[0])?></div><div>Temporary password: <b><?=e($cred[1])?></b></div><div class="lbl">The student must choose a new password at first sign-in.</div></div><?php endif;?>
<?php if(can('manage_student_accounts')):?><div class="card-ui mb-3"><div class="fw-bold mb-2"><i class="bi bi-person-lock"></i> Student portal login</div>
<?php if($login):?><div class="d-flex flex-wrap justify-content-between align-items-center gap-2"><div><?=e($login['email'])?> <span class="badge rounded-pill text-bg-<?=$login['status']==='active'?'success':'secondary'?>"><?=e($login['status'])?></span><div class="lbl">Last sign-in: <?=e($login['last_login']?:'never')?></div></div>
<div class="d-flex gap-1"><form method="post" onsubmit="return confirm('Create a new temporary password?')"><input type="hidden" name="csrf" value="<?=csrf()?>"><input type="hidden" name="act" value="login_reset"><button class="btn btn-sm btn-light rounded-pill">Reset password</button></form>
<form method="post"><input type="hidden" name="csrf" value="<?=csrf()?>"><input type="hidden" name="act" value="login_toggle"><button class="btn btn-sm btn-light rounded-pill"><?=$login['status']==='active'?'Disable login':'Enable login'?></button></form></div></div>
<?php else:?><form method="post" class="row g-2"><input type="hidden" name="csrf" value="<?=csrf()?>"><input type="hidden" name="act" value="login_create"><div class="col-12 col-md-8"><input class="form-control" type="email" name="email" value="<?=e($p['email'])?>" placeholder="Student's email (used to sign in)" required></div><div class="col-12 col-md-4"><button class="btn-ui w-100">Create login</button></div></form><?php endif;?></div><?php endif;?>
<ul class="nav nav-pills mb-3 flex-nowrap overflow-auto">
<?php foreach($tabs as $k=>$t):?><li class="nav-item"><button class="nav-link <?=$k==='personal'?'active':''?>" data-bs-toggle="pill" data-bs-target="#t-<?=$k?>"><?=$t?></button></li><?php endforeach;?></ul>
<div class="tab-content card-ui">
<div class="tab-pane fade show active" id="t-personal"><div class="row g-3">
<?php $f=['First name'=>$p['first_name'],'Last name'=>$p['last_name'],'Full name'=>$p['full_name'],'Date of birth'=>$p['date_of_birth'].' (age '.$age.')','Contact no'=>$p['phone'],'Email'=>$p['email'],'Address'=>$p['address'],'Fingerprint ID'=>$p['fingerprint_id']];
if($p['nic']) $f['NIC']=$p['nic']; if($p['guardian_name']){ $f['Guardian']=$p['guardian_name'].' ('.$p['guardian_relation'].')'; $f['Guardian contact']=$p['guardian_phone']; }
$f['Registered']=$p['registered_at'];
foreach($f as $l=>$v):?><div class="col-md-6"><div class="lbl"><?=e($l)?></div><div><?=e($v?:'—')?></div></div><?php endforeach;?></div></div>
<div class="tab-pane fade" id="t-academic"><div class="table-wrap"><table class="table"><thead><tr><th>Class</th><th>Type / course</th><th>Joined</th><th>Status</th></tr></thead><tbody data-pager="10">
<?php foreach($enr as $r):?><tr><td><a href="batch_view.php?id=<?=$r['id']?>"><?=e($r['name'])?> (<?=$r['batch_year']?>)</a></td><td><?=e($r['tname'])?> · <?=e($r['cname'])?></td><td><?=e($r['enrolled_on'])?></td><td><?=e($r['status'])?></td></tr><?php endforeach;?></tbody></table></div></div>
<div class="tab-pane fade" id="t-attendance"><div class="table-wrap"><table class="table"><thead><tr><th>Date</th><th>Class</th><th>Status</th><th>In</th><th>Out</th><th>Source</th></tr></thead><tbody data-pager="10">
<?php foreach($att as $a):?><tr><td><?=e($a['att_date'])?></td><td><?=e($a['bname'])?></td><td><span class="badge rounded-pill text-bg-<?=$a['status']==='present'?'success':'danger'?>"><?=e($a['status'])?></span></td><td><?=e($a['check_in']?substr($a['check_in'],0,5):'—')?></td><td><?=e($a['check_out']?substr($a['check_out'],0,5):'—')?></td><td><?=e($a['source'])?></td></tr><?php endforeach; if(!$att):?><tr><td colspan="6" class="lbl text-center py-3">No attendance recorded yet.</td></tr><?php endif;?></tbody></table></div></div>
<div class="tab-pane fade" id="t-payments">
<?php if((in_array('payments',$menu,true) && can('take_payment'))): $pq=db()->prepare('SELECT p.id,p.voided_at,p.receipt_no,p.paid_on,p.amount,p.method,b.name bname FROM payments p JOIN batches b ON b.id=p.batch_id WHERE p.student_id=? ORDER BY p.id DESC LIMIT 50'); $pq->execute([$id]); $pays=$pq->fetchAll();?>
<div class="mb-2"><a class="btn-ui d-inline-block text-decoration-none" href="payments.php?verify=<?=$id?>">Take payment</a></div>
<div class="table-wrap"><table class="table"><thead><tr><th>Receipt</th><th>Date</th><th>Class</th><th>Amount</th><th>Method</th></tr></thead><tbody data-pager="10">
<?php foreach($pays as $y):?><tr><td><a href="receipt.php?id=<?=$y['id']?>" target="_blank"><?=e($y['receipt_no'])?></a></td><td><?=e($y['paid_on'])?></td><td><?=e($y['bname'])?></td><td><?php if($y['voided_at']):?><s class="lbl"><?=number_format((float)$y['amount'],2)?></s> <span class="badge rounded-pill text-bg-danger">void</span><?php else:?><?=number_format((float)$y['amount'],2)?><?php endif;?></td><td><?=e($y['method'])?></td></tr><?php endforeach; if(!$pays):?><tr><td colspan="5" class="lbl text-center py-3">No payments yet.</td></tr><?php endif;?></tbody></table></div>
<?php else:?><p class="lbl mb-0">You do not have access to payment details.</p><?php endif;?></div>
<div class="tab-pane fade" id="t-exams">
<?php $xq=db()->prepare("SELECT x.title,x.exam_type,x.exam_date,x.max_marks,b.name bname,t.code tcode,m.marks,m.absent,m.remarks FROM exam_marks m JOIN exams x ON x.id=m.exam_id JOIN batches b ON b.id=x.batch_id JOIN courses c ON c.id=b.course_id JOIN course_types t ON t.id=c.type_id WHERE m.student_id=? ORDER BY x.exam_date DESC"); $xq->execute([$id]); $xr=$xq->fetchAll(); require_once __DIR__.'/includes/exams_lib.php';?>
<div class="table-wrap"><table class="table"><thead><tr><th>Exam</th><th>Class</th><th>Date</th><th>Marks</th><th>Grade</th><th>Result</th></tr></thead><tbody data-pager="10">
<?php foreach($xr as $x): $pct=$x['marks']!==null?$x['marks']/$x['max_marks']*100:null; ?>
<tr><td><?=e($x['title'])?></td><td><?=e($x['bname'])?></td><td><?=e($x['exam_date'])?></td>
<td><?=$x['absent']?'Absent':($x['marks']!==null?e(rtrim(rtrim(number_format((float)$x['marks'],2,'.',''),'0'),'.')).' / '.(int)$x['max_marks']:'—')?></td>
<td><?=$pct!==null?grade_for($pct):'—'?></td><td><?=e($pct!==null?exam_outcome($x['exam_type'],$x['tcode'],$pct):'')?></td></tr>
<?php endforeach; if(!$xr):?><tr><td colspan="6" class="lbl text-center py-3">No exam results yet.</td></tr><?php endif;?></tbody></table></div></div>
<?php foreach($ph as $k=>$t):?><div class="tab-pane fade" id="t-<?=$k?>"><p class="lbl mb-0"><?=e($t)?></p></div><?php endforeach;?>
<div class="tab-pane fade" id="t-comments">
<?php if(can('comment')):?><form method="post" class="mb-3"><input type="hidden" name="csrf" value="<?=csrf()?>"><input type="hidden" name="act" value="comment">
<textarea class="form-control mb-2" name="comment" rows="3" placeholder="Write a comment about this student" required></textarea><button class="btn-ui">Add comment</button></form><?php endif;?>
<div data-pager="10"><?php foreach($comments as $c):?><div class="border-top py-2"><div class="lbl"><?=e($c['full_name']??'Unknown')?> · <?=e($c['created_at'])?></div><div><?=nl2br(e($c['comment']))?></div></div><?php endforeach;?></div>
<?php if(!$comments):?><p class="lbl mb-0">No comments yet.</p><?php endif;?></div>
</div>
<?php ob_start(); ?><script>if(location.hash==='#comments'){const b=document.querySelector('[data-bs-target="#t-comments"]');b&&new bootstrap.Tab(b).show();}</script>
<script>(function(){var d=document.getElementById('eDob');if(!d)return;function ck(){var v=d.value,a=null;if(v){var b=new Date(v),n=new Date();a=n.getFullYear()-b.getFullYear();if(n<new Date(n.getFullYear(),b.getMonth(),b.getDate()))a--;}
document.getElementById('eAdult').style.display=(a!==null&&a>=18)?'':'none';document.getElementById('eMinor').style.display=(a!==null&&a<18)?'':'none';}d.addEventListener('change',ck);ck();})();
<?php if($openEdit):?>new bootstrap.Modal(document.getElementById('editStu')).show();<?php endif;?></script>
<?php $pageScript=ob_get_clean(); require __DIR__.'/includes/footer.php';
