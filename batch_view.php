<?php
require_once __DIR__.'/includes/bootstrap.php';
require_menu('courses'); require_once __DIR__.'/includes/schedule_lib.php'; require_once __DIR__.'/includes/student_lib.php'; refresh_batches();
$s=branch_scope(); $id=(int)($_GET['id']??0);
$st=db()->prepare("SELECT b.*,(SELECT full_name FROM users WHERE id=b.teacher_id) tchname,c.name cname,t.name tname,br.name brname FROM batches b JOIN courses c ON c.id=b.course_id JOIN course_types t ON t.id=c.type_id JOIN branches br ON br.id=b.branch_id WHERE b.id=?");
$st->execute([$id]); $b=$st->fetch();
if (!$b || ($s!==null && (int)$b['branch_id']!==$s) || (is_teacher() && !teacher_has_batch($id))) { http_response_code(404); exit('Batch not found.'); }
if ($_SERVER['REQUEST_METHOD']==='POST' && ($_POST['act']??'')==='bulk_logins') {
  csrf_check(); if (!can('manage_student_accounts')) { http_response_code(403); exit('Access denied.'); }
  $sq=db()->prepare('SELECT s.* FROM enrolments e JOIN students s ON s.id=e.student_id WHERE e.batch_id=? AND NOT EXISTS (SELECT 1 FROM users u WHERE u.student_id=s.id) ORDER BY s.full_name'); $sq->execute([$id]);
  $made=[]; $skip=0; foreach($sq->fetchAll() as $st1){ if(!$st1['email']){ $skip++; continue; } $r=create_student_login($st1,$st1['email']); if($r[0]) $made[]=[$st1['full_name'],$st1['student_code'],$st1['email'],$r[1]]; else $skip++; }
  log_activity('student_login_bulk',"batch $id: ".count($made).' created'); flash_set('newlogins',$made);
  flash_set('msg',count($made).' login(s) created.'.($skip?" $skip student(s) skipped (no email, or the email is already used).":'')); header('Location: batch_view.php?id='.$id); exit;
}
if ($_SERVER['REQUEST_METHOD']==='POST' && ($_POST['act']??'')==='assign') {
  csrf_check(); if (!can('manage_batches')) { http_response_code(403); exit('Access denied.'); }
  $tid=(int)($_POST['teacher_id']??0)?:null;
  if ($tid) {
    $tq=db()->prepare("SELECT 1 FROM users WHERE id=? AND role='teacher' AND status='active' AND branch_id=?"); $tq->execute([$tid,$b['branch_id']]);
    if (!$tq->fetch()) flash_set('msg','Choose a teacher from this branch.');
    elseif ($cl=teacher_clash($tid,$b['start_date'],$b['end_date'],$b['start_time'],$b['end_time'],(string)$b['class_days'],$id)) flash_set('msg','This teacher already teaches '.$cl.' at that time.');
    else { db()->prepare('UPDATE batches SET teacher_id=? WHERE id=?')->execute([$tid,$id]); log_activity('teacher_assign',"batch $id → user $tid"); flash_set('msg','Teacher assigned.'); }
  } else { db()->prepare('UPDATE batches SET teacher_id=NULL WHERE id=?')->execute([$id]); log_activity('teacher_unassign',"batch $id"); flash_set('msg','Teacher removed.'); }
  header('Location: batch_view.php?id='.$id); exit;
}
if ($_SERVER['REQUEST_METHOD']==='POST' && ($_POST['act']??'')==='enrol') {
  csrf_check();
  if (!can('register_student') && !can('manage_batches')) { http_response_code(403); exit('Access denied.'); }
  $code=trim($_POST['student_code']??'');
  $q=db()->prepare('SELECT id,full_name FROM students WHERE student_code=? AND branch_id=?'); $q->execute([$code,$b['branch_id']]); $stu=$q->fetch();
  $cnt=db()->prepare('SELECT COUNT(*) FROM enrolments WHERE batch_id=?'); $cnt->execute([$id]); $n=(int)$cnt->fetchColumn();
  if (!$stu) flash_set('msg','No student with that ID in this branch.');
  elseif ($b['status']!=='active') flash_set('msg','This batch is completed.');
  elseif ($b['max_students'] && $n>=$b['max_students']) flash_set('msg','Class is full (max '.$b['max_students'].').');
  else {
    $i=db()->prepare('INSERT IGNORE INTO enrolments (student_id,batch_id,enrolled_on) VALUES (?,?,CURDATE())'); $i->execute([$stu['id'],$id]);
    if ($i->rowCount()) { require_once __DIR__.'/includes/payments_lib.php'; ensure_account_for((int)$stu['id'],$id); log_activity('enrol',$code.' → batch '.$id); flash_set('msg',$stu['full_name'].' added to the class.'); } else flash_set('msg','Student is already in this class.');
  }
  header('Location: batch_view.php?id='.$id); exit;
}
$pageKey='courses'; require __DIR__.'/includes/header.php';
$msg=flash_get('msg');
$m=db()->prepare('SELECT s.id,s.student_code,s.full_name,s.phone,s.status,e.enrolled_on FROM enrolments e JOIN students s ON s.id=e.student_id WHERE e.batch_id=? ORDER BY s.full_name');
$m->execute([$id]); $members=$m->fetchAll();
?>
<?php if($msg):?><div class="alert alert-info rounded-4"><?=e($msg)?></div><?php endif;?>
<a href="courses.php" class="lbl text-decoration-none">← Back to courses</a>
<div class="card-ui my-3"><div class="stat" style="font-size:1.4rem"><?=e($b['name'])?> (<?=$b['batch_year']?>)</div>
 <div class="lbl"><?=e($b['tname'])?> · <?=e($b['cname'])?> · <?=e($b['brname'])?></div>
 <div class="row g-3 mt-1">
  <div class="col-6 col-md-3"><div class="lbl">Start</div><?=e($b['start_date'])?></div>
  <div class="col-6 col-md-3"><div class="lbl">End</div><?=e($b['end_date'])?></div>
  <div class="col-6 col-md-3"><div class="lbl">Time</div><?=e(substr($b['start_time'],0,5).'–'.substr($b['end_time'],0,5))?></div>
  <div class="col-6 col-md-3"><div class="lbl">Status</div><span class="badge rounded-pill text-bg-<?=$b['status']==='active'?'success':'secondary'?>"><?=e($b['status'])?></span></div></div></div>
<?php $tl=db()->prepare("SELECT id,full_name FROM users WHERE role='teacher' AND status='active' AND branch_id=? ORDER BY full_name"); $tl->execute([$b['branch_id']]); $tlist=$tl->fetchAll();?>
<div class="mb-3"><a class="btn-ui d-inline-block text-decoration-none" href="learning.php?batch=<?=$id?>"><i class="bi bi-folder-fill"></i> Learning hub: lectures, live class, materials, assignments, quizzes</a> <?php if(can('manage_student_accounts')):?><form method="post" class="d-inline" onsubmit="return confirm('Create portal logins for every student in this class who has an email and no login yet?')"><input type="hidden" name="csrf" value="<?=csrf()?>"><input type="hidden" name="act" value="bulk_logins"><button class="btn btn-light rounded-pill"><i class="bi bi-person-lock"></i> Create student logins</button></form><?php endif;?></div>
<?php $nl=flash_get('newlogins'); if($nl):?><div class="card-ui mb-3"><div class="d-flex justify-content-between align-items-center mb-2"><b>New logins (shown once. Print or copy now.)</b><button class="btn btn-sm btn-light rounded-pill" onclick="window.print()">Print</button></div><div class="table-wrap"><table class="table table-sm"><thead><tr><th>Student</th><th>ID</th><th>Email</th><th>Temporary password</th></tr></thead><tbody>
<?php foreach($nl as $x):?><tr><td><?=e($x[0])?></td><td><?=e($x[1])?></td><td><?=e($x[2])?></td><td><b><?=e($x[3])?></b></td></tr><?php endforeach;?></tbody></table></div></div><?php endif;?>
<div class="card-ui mb-3"><div class="lbl">Teacher</div><div class="fw-bold mb-2"><?=e($b['tchname']??'Not assigned')?></div>
<?php if(can('manage_batches')):?><form method="post" class="d-flex gap-2"><input type="hidden" name="csrf" value="<?=csrf()?>"><input type="hidden" name="act" value="assign">
<select class="form-select" name="teacher_id"><option value="">No teacher</option><?php foreach($tlist as $t):?><option value="<?=$t['id']?>" <?=(int)$b['teacher_id']===(int)$t['id']?'selected':''?>><?=e($t['full_name'])?></option><?php endforeach;?></select><button class="btn-ui text-nowrap">Save</button></form><?php endif;?></div>
<?php if($b['status']==='active' && (can('register_student')||can('manage_batches'))):?>
<form method="post" class="card-ui mb-3 d-flex gap-2"><input type="hidden" name="csrf" value="<?=csrf()?>"><input type="hidden" name="act" value="enrol">
 <input class="form-control" name="student_code" placeholder="Enter student ID to add to this class" required><button class="btn-ui text-nowrap">Add</button></form>
<?php endif;?>
<div class="card-ui table-wrap"><div class="fw-bold mb-2">Students (<?=count($members)?><?=$b['max_students']?' / '.$b['max_students']:''?>)</div>
<input class="form-control mb-2" id="mFilter" placeholder="Search students">
<table class="table"><thead><tr><th>ID</th><th>Name</th><th>Phone</th><th>Joined</th></tr></thead><tbody data-pager="10" data-filter="#mFilter">
<?php foreach($members as $r):?><tr><td><a href="student_view.php?id=<?=$r['id']?>"><?=e($r['student_code'])?></a></td><td><?=e($r['full_name'])?></td><td><?=e($r['phone'])?></td><td><?=e($r['enrolled_on'])?></td></tr><?php endforeach;?>
</tbody></table></div>
<?php require __DIR__.'/includes/footer.php';
