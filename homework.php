<?php
require_once __DIR__.'/includes/bootstrap.php';
require_menu('materials');
$s=branch_scope(); $me=(int)$_SESSION['user']['id']; $id=(int)($_GET['id']??0); $canEdit=can('upload_material');
$q=db()->prepare("SELECT m.*,b.name bname,b.batch_year,b.branch_id bbranch,c.name cname FROM materials m JOIN batches b ON b.id=m.batch_id JOIN courses c ON c.id=b.course_id WHERE m.id=? AND m.mtype='assignment'");
$q->execute([$id]); $m=$q->fetch();
if (!$m || ($s!==null && (int)$m['bbranch']!==$s) || (is_teacher() && !teacher_has_batch((int)$m['batch_id']))) { http_response_code(404); exit('Homework not found.'); }
$max=$m['max_marks']!==null?(float)$m['max_marks']:null;
if ($_SERVER['REQUEST_METHOD']==='POST') {
  csrf_check(); if(!$canEdit){ http_response_code(403); exit('Access denied.'); }
  $e1=db()->prepare('SELECT student_id FROM enrolments WHERE batch_id=?'); $e1->execute([$m['batch_id']]); $n=0; $bad=0;
  $up=db()->prepare('INSERT INTO homework_records (material_id,student_id,status,marks,remarks,recorded_by) VALUES (?,?,?,?,?,?) ON DUPLICATE KEY UPDATE status=VALUES(status),marks=VALUES(marks),remarks=VALUES(remarks),recorded_by=VALUES(recorded_by)');
  $del=db()->prepare('DELETE FROM homework_records WHERE material_id=? AND student_id=?');
  foreach ($e1->fetchAll(PDO::FETCH_COLUMN) as $sid) {
    $st=$_POST['status'][$sid]??''; $mk=trim($_POST['marks'][$sid]??''); $rm=mb_substr(trim($_POST['remarks'][$sid]??''),0,255);
    if (!in_array($st,['submitted','late','missing'],true)) { $del->execute([$id,$sid]); continue; }
    $val=null; if ($mk!==''){ if(!is_numeric($mk)||$mk<0||($max!==null&&$mk>$max)){ $bad++; continue; } $val=round((float)$mk,2); }
    $up->execute([$id,$sid,$st,$val,$rm?:null,$me]); $n++;
  }
  log_activity('homework_track',$m['title'].' ('.$n.')'); flash_set('msg',"Saved $n records.".($bad?" $bad row(s) skipped: marks out of range.":'')); header("Location: homework.php?id=$id"); exit;
}
$pageKey='materials'; require __DIR__.'/includes/header.php'; $msg=flash_get('msg');
$r=db()->prepare('SELECT s.id,s.student_code,s.full_name,h.status,h.marks,h.remarks,sb.id subid,sb.original_name subname,sb.note subnote,sb.submitted_at subat FROM enrolments e JOIN students s ON s.id=e.student_id LEFT JOIN homework_records h ON h.student_id=s.id AND h.material_id=? LEFT JOIN assignment_submissions sb ON sb.student_id=s.id AND sb.material_id=? WHERE e.batch_id=? ORDER BY s.full_name');
$r->execute([$id,$id,$m['batch_id']]); $rows=$r->fetchAll();
$cnt=['submitted'=>0,'late'=>0,'missing'=>0,'none'=>0]; foreach($rows as $z) $cnt[$z['status']?:'none']++;
?>
<?php if($msg):?><div class="alert alert-info rounded-4"><?=e($msg)?></div><?php endif;?>
<a href="learning.php?batch=<?=$m['batch_id']?>&type=homework" class="lbl text-decoration-none">← Back to learning hub</a>
<div class="card-ui my-3"><div class="stat" style="font-size:1.3rem"><?=e($m['title'])?></div><div class="lbl"><?=e($m['cname'])?> · <?=e($m['bname'])?> (<?=$m['batch_year']?>)<?=$m['due_date']?' · due '.e($m['due_date']):''?><?=$max!==null?' · max '.rtrim(rtrim(number_format($max,2,'.',''),'0'),'.'):''?></div>
<?php if($m['description']):?><p class="mt-2 mb-0"><?=nl2br(e($m['description']))?></p><?php endif;?>
<div class="row g-2 text-center mt-2"><?php foreach(['submitted'=>'Submitted','late'=>'Late','missing'=>'Missing','none'=>'Not recorded'] as $k=>$l):?><div class="col-3"><div class="stat" style="font-size:1.2rem"><?=$cnt[$k]?></div><div class="lbl"><?=$l?></div></div><?php endforeach;?></div></div>
<input class="form-control mb-2" id="hwFilter" placeholder="Search student name or ID">
<form method="post"><input type="hidden" name="csrf" value="<?=csrf()?>"><div class="card-ui" data-pager="10" data-filter="#hwFilter">
<?php foreach($rows as $z):?><div class="row g-2 align-items-center py-2 border-top"><div class="col-12 col-md-4"><?=e($z['full_name'])?><div class="lbl"><?=e($z['student_code'])?></div>
 <?php if($z['subid']):?><div class="lbl"><i class="bi bi-paperclip"></i> <?php if($z['subname']):?><a href="submission.php?id=<?=$z['subid']?>"><?=e($z['subname'])?></a><?php else:?>Text answer<?php endif;?> · <?=e(date('j M, g:i A',strtotime($z['subat'])))?><?=$z['subnote']?'<br>'.e($z['subnote']):''?></div><?php endif;?></div>
 <div class="col-6 col-md-3"><select class="form-select" name="status[<?=$z['id']?>]" <?=$canEdit?'':'disabled'?>><option value="">Not recorded</option><?php foreach(['submitted'=>'Submitted','late'=>'Late','missing'=>'Missing'] as $k=>$l):?><option value="<?=$k?>" <?=$z['status']===$k?'selected':''?>><?=$l?></option><?php endforeach;?></select></div>
 <div class="col-6 col-md-2"><input class="form-control" type="number" step="0.01" min="0" <?=$max!==null?'max="'.$max.'"':''?> name="marks[<?=$z['id']?>]" value="<?=$z['marks']!==null?e(rtrim(rtrim(number_format((float)$z['marks'],2,'.',''),'0'),'.')):''?>" placeholder="Marks" <?=$canEdit?'':'disabled'?>></div>
 <div class="col-12 col-md-3"><input class="form-control" name="remarks[<?=$z['id']?>]" value="<?=e($z['remarks'])?>" placeholder="Remarks" <?=$canEdit?'':'disabled'?>></div></div>
<?php endforeach; if(!$rows):?><p class="lbl mb-0" data-pg-skip>No students enrolled in this class.</p><?php endif;?>
<?php if($canEdit&&$rows):?><button class="btn-ui w-100 mt-3" data-pg-skip>Save tracking</button><?php endif;?></div></form>
<?php require __DIR__.'/includes/footer.php';
