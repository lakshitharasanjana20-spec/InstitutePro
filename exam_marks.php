<?php
require_once __DIR__.'/includes/bootstrap.php';
require_once __DIR__.'/includes/exams_lib.php';
require_menu('exams'); $s=branch_scope(); $id=(int)($_GET['id']??0);
$q=db()->prepare('SELECT x.*,b.teacher_id,b.name bname,b.batch_year,c.name cname,t.code tcode,t.name tname FROM exams x JOIN batches b ON b.id=x.batch_id JOIN courses c ON c.id=b.course_id JOIN course_types t ON t.id=c.type_id WHERE x.id=?');
$q->execute([$id]); $x=$q->fetch();
if(!$x || ($s!==null && (int)$x['branch_id']!==$s) || (is_teacher() && !teacher_has_batch((int)$x['batch_id']))){ http_response_code(404); exit('Exam not found.'); }
$canEnter=can('enter_marks'); $started=$x['exam_date']<=date('Y-m-d'); $max=(float)$x['max_marks'];
if ($_SERVER['REQUEST_METHOD']==='POST') {
  csrf_check(); if(!$canEnter){ http_response_code(403); exit('Access denied.'); }
  if(!$started){ flash_set('msg','Marks can only be entered on or after the exam date.'); header("Location: exam_marks.php?id=$id"); exit; }
  $m=db()->prepare('SELECT student_id FROM enrolments WHERE batch_id=?'); $m->execute([$x['batch_id']]); $valid=$m->fetchAll(PDO::FETCH_COLUMN);
  $up=db()->prepare('INSERT INTO exam_marks (exam_id,student_id,marks,absent,remarks,entered_by) VALUES (?,?,?,?,?,?) ON DUPLICATE KEY UPDATE marks=VALUES(marks),absent=VALUES(absent),remarks=VALUES(remarks),entered_by=VALUES(entered_by)');
  $n=0; $bad=[];
  foreach($valid as $sid){
    $raw=trim($_POST['marks'][$sid]??''); $abs=!empty($_POST['absent'][$sid])?1:0; $rem=trim($_POST['remarks'][$sid]??'');
    if($raw===''&&!$abs&&$rem==='') continue;
    $val=null; if(!$abs && $raw!==''){ if(!is_numeric($raw)||$raw<0||$raw>$max){ $bad[]=$sid; continue; } $val=round((float)$raw,2); }
    $up->execute([$id,$sid,$val,$abs,$rem?:null,$_SESSION['user']['id']]); $n++;
  }
  log_activity('exam_marks',"exam $id ($n students)");
  flash_set('msg',"Saved marks for $n students.".($bad?' '.count($bad).' row(s) skipped: marks must be between 0 and '.$max.'.':'')); header("Location: exam_marks.php?id=$id"); exit;
}
$pageKey='exams'; require __DIR__.'/includes/header.php'; $msg=flash_get('msg');
$r=db()->prepare('SELECT s.id,s.student_code,s.full_name,m.marks,m.absent,m.remarks FROM enrolments e JOIN students s ON s.id=e.student_id LEFT JOIN exam_marks m ON m.student_id=s.id AND m.exam_id=? WHERE e.batch_id=? ORDER BY s.full_name');
$r->execute([$id,$x['batch_id']]); $rows=$r->fetchAll();
$vals=[]; foreach($rows as $z) if($z['marks']!==null) $vals[]=(float)$z['marks'];
$pass=0; foreach($vals as $v) if(is_pass($v/$max*100)) $pass++;
$fmt=fn($v)=>rtrim(rtrim(number_format((float)$v,2,'.',''),'0'),'.');
?>
<?php if($msg):?><div class="alert alert-info rounded-4"><?=e($msg)?></div><?php endif;?>
<a href="exams.php" class="lbl text-decoration-none">← Back to exams</a>
<div class="card-ui my-3"><div class="stat" style="font-size:1.3rem"><?=e($x['title'])?></div>
 <div class="lbl"><?=e($x['tname'])?> · <?=e($x['cname'])?> · <?=e($x['bname'])?> (<?=$x['batch_year']?>)</div>
 <div class="row g-2 mt-1"><div class="col-6 col-md-3"><div class="lbl">Date</div><?=e($x['exam_date'])?></div><div class="col-6 col-md-3"><div class="lbl">Max marks</div><?=$fmt($max)?></div>
 <?php if($vals):?><div class="col-6 col-md-3"><div class="lbl">Average</div><?=$fmt(array_sum($vals)/count($vals))?></div><div class="col-6 col-md-3"><div class="lbl">Highest · C or better</div><?=$fmt(max($vals))?> · <?=$pass?>/<?=count($vals)?></div><?php endif;?></div>
 <?php if($x['exam_type']==='final' && $x['tcode']==='AI'):?><div class="lbl mt-2">Final exam rule: a grade of C (50%) or better completes the course; otherwise the student must repeat.</div><?php endif;?>
 <?php if($x['exam_type']==='final' && $x['tcode']==='GR'):?><div class="lbl mt-2">Grade class: there is no minimum mark to move up to the next grade.</div><?php endif;?></div>
<?php if($canEnter && !$started):?><div class="alert alert-warning rounded-4">Marks can be entered from the exam date (<?=e($x['exam_date'])?>).</div><?php endif;?>
<input class="form-control mb-2" id="mkFilter" placeholder="Search student name or ID">
<form method="post"><input type="hidden" name="csrf" value="<?=csrf()?>"><div class="card-ui" data-pager="10" data-filter="#mkFilter">
<?php foreach($rows as $z): $pct=$z['marks']!==null?(float)$z['marks']/$max*100:null; ?>
<div class="row g-2 align-items-center py-2 border-top">
 <div class="col-12 col-md-4"><?=e($z['full_name'])?><div class="lbl"><?=e($z['student_code'])?></div></div>
 <div class="col-4 col-md-2"><input class="form-control" type="number" step="0.01" min="0" max="<?=$max?>" name="marks[<?=$z['id']?>]" value="<?=$z['marks']!==null?e($fmt($z['marks'])):''?>" placeholder="Marks" <?=($canEnter&&$started)?'':'disabled'?>></div>
 <div class="col-3 col-md-1"><label class="lbl"><input type="checkbox" name="absent[<?=$z['id']?>]" value="1" <?=$z['absent']?'checked':''?> <?=($canEnter&&$started)?'':'disabled'?>> Abs.</label></div>
 <div class="col-5 col-md-2"><?php if($pct!==null):?><span class="badge rounded-pill text-bg-<?=is_pass($pct)?'success':'warning'?>"><?=grade_for($pct)?></span> <span class="lbl"><?=exam_outcome($x['exam_type'],$x['tcode'],$pct)?></span><?php endif;?></div>
 <div class="col-12 col-md-3"><input class="form-control" name="remarks[<?=$z['id']?>]" value="<?=e($z['remarks'])?>" placeholder="Remarks" <?=($canEnter&&$started)?'':'disabled'?>></div>
</div>
<?php endforeach; if(!$rows):?><p class="lbl mb-0" data-pg-skip>No students enrolled in this class.</p><?php endif;?>
<?php if($canEnter && $started && $rows):?><button class="btn-ui w-100 mt-3" data-pg-skip>Save marks</button><?php endif;?></div></form>
<?php require __DIR__.'/includes/footer.php';
