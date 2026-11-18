<?php
require_once __DIR__.'/includes/bootstrap.php';
require_menu('exams'); refresh_batches();
$s=branch_scope(); $canSched=can('schedule_exam'); $errs=[]; $old=[]; $open=false;
$bw=$s!==null?' AND b.branch_id='.(int)$s:''; $bw.=tfrag('b');
if ($_SERVER['REQUEST_METHOD']==='POST' && ($_POST['act']??'')==='schedule') {
  csrf_check(); if(!$canSched){ http_response_code(403); exit('Access denied.'); }
  $old=$_POST; $open=true;
  $bid=(int)($_POST['batch_id']??0); $title=trim($_POST['title']??''); $type=$_POST['exam_type']??'term'; $date=$_POST['exam_date']??''; $time=($_POST['start_time']??'')?:null; $max=(float)($_POST['max_marks']??100);
  $q=db()->prepare("SELECT b.id,b.branch_id FROM batches b WHERE b.id=?$bw"); $q->execute([$bid]); $b=$q->fetch();
  if(!$b) $errs[]='Choose a class / batch.';
  if($title==='') $errs[]='Exam title is required.';
  if(!in_array($type,['term','project','final','other'],true)) $errs[]='Choose an exam type.';
  if(!$date||!strtotime($date)) $errs[]='Exam date is required.';
  if($max<=0||$max>1000) $errs[]='Maximum marks must be between 1 and 1000.';
  if(!$errs){
    db()->prepare('INSERT INTO exams (branch_id,batch_id,title,exam_type,exam_date,start_time,max_marks,created_by) VALUES (?,?,?,?,?,?,?,?)')->execute([$b['branch_id'],$bid,$title,$type,$date,$time,$max,$_SESSION['user']['id']]);
    log_activity('exam_schedule',"$title $date"); flash_set('msg','Exam scheduled.'); header('Location: exams.php'); exit;
  }
}
$pageKey='exams'; require __DIR__.'/includes/header.php';
$msg=flash_get('msg'); $tab=($_GET['tab']??'')==='past'?'past':'upcoming';
$sw=($s!==null?' AND x.branch_id='.(int)$s:'').tfrag('b');
$fq=gstr('q'); $fty=genum('etype',['term','project','final','other']); $fct=gint('ctype'); $fbr=$s===null?gint('branch'):0; $d1=gdate('from'); $d2=gdate('to'); if($d1&&$d2&&$d1>$d2)[$d1,$d2]=[$d2,$d1];
$ex=''; $xp=[];
if($fq!==''){$ex.=' AND (x.title LIKE ? OR c.name LIKE ?)';array_push($xp,"%$fq%","%$fq%");}
if($fty){$ex.=' AND x.exam_type=?';$xp[]=$fty;} if($fct){$ex.=' AND c.type_id=?';$xp[]=$fct;} if($fbr){$ex.=' AND x.branch_id=?';$xp[]=$fbr;}
if($d1){$ex.=' AND x.exam_date>=?';$xp[]=$d1;} if($d2){$ex.=' AND x.exam_date<=?';$xp[]=$d2;}
$fromSql=" FROM exams x JOIN batches b ON b.id=x.batch_id JOIN courses c ON c.id=b.course_id JOIN course_types t ON t.id=c.type_id JOIN branches br ON br.id=x.branch_id WHERE x.exam_date ".($tab==='past'?'<':'>=')." CURDATE()$sw$ex";
$cq=db()->prepare('SELECT COUNT(*)'.$fromSql); $cq->execute($xp); $total=(int)$cq->fetchColumn(); $per=pg_per(); [$pg,$pages,$off]=pg_page($total,$per);
$q2=db()->prepare('SELECT x.*,b.name bname,b.batch_year,c.name cname,t.name tname,br.name brname,
 (SELECT COUNT(*) FROM exam_marks m WHERE m.exam_id=x.id AND (m.marks IS NOT NULL OR m.absent=1)) done,
 (SELECT COUNT(*) FROM enrolments e WHERE e.batch_id=x.batch_id) total'.$fromSql.' ORDER BY x.exam_date '.($tab==='past'?'DESC':'ASC').' LIMIT '.$per.' OFFSET '.$off);
$q2->execute($xp); $rows=$q2->fetchAll();
$ctypes=db()->query('SELECT id,name FROM course_types ORDER BY id')->fetchAll();
$brs=db()->query('SELECT id,name FROM branches ORDER BY name')->fetchAll();
$batches=db()->query("SELECT b.id,b.name,b.batch_year,c.name cname,br.name brname FROM batches b JOIN courses c ON c.id=b.course_id JOIN branches br ON br.id=b.branch_id WHERE b.status='active'$bw ORDER BY br.name,c.name,b.name")->fetchAll();
$o=fn($k)=>e($old[$k]??'');
?>
<?php if($msg):?><div class="alert alert-info rounded-4"><?=e($msg)?></div><?php endif;?>
<div class="d-flex gap-2 mb-3 align-items-center">
 <ul class="nav nav-pills me-auto"><li class="nav-item"><a class="nav-link <?=$tab==='upcoming'?'active':''?>" href="exams.php">Upcoming</a></li><li class="nav-item"><a class="nav-link <?=$tab==='past'?'active':''?>" href="exams.php?tab=past">Past</a></li></ul>
 <?php if($canSched):?><button class="btn-ui text-nowrap" data-bs-toggle="modal" data-bs-target="#exModal"><i class="bi bi-plus-lg"></i> <span class="d-none d-md-inline">Schedule exam</span></button><?php endif;?>
</div>
<?php f_open(['tab']); f_input('q','Search title or course','text','col-12 col-md-4');
 f_select('etype',['term'=>'Term / unit test','project'=>'Project','final'=>'Final exam','other'=>'Other'],'Any exam type','col-6 col-md-2');
 f_select('ctype',array_column($ctypes,'name','id'),'Any course type','col-6 col-md-3');
 if($s===null) f_select('branch',array_column($brs,'name','id'),'All branches','col-6 col-md-3');
 f_input('from','Date from','date','col-6 col-md-3'); f_input('to','Date to','date','col-6 col-md-3'); f_close(['tab']);?>
<div class="card-ui table-wrap"><table class="table align-middle"><thead><tr><th>Exam</th><th>Class</th><th>Date</th><th>Marks entered</th></tr></thead><tbody>
<?php foreach($rows as $r):?><tr><td><a href="exam_marks.php?id=<?=$r['id']?>"><?=e($r['title'])?></a><div class="lbl"><?=e(ucfirst($r['exam_type']))?> · max <?=rtrim(rtrim(number_format((float)$r['max_marks'],2,'.',''),'0'),'.')?></div></td>
<td><?=e($r['tname'])?> · <?=e($r['cname'])?><div class="lbl"><?=e($r['bname'])?> (<?=$r['batch_year']?>)<?=$s===null?' · '.e($r['brname']):''?></div></td>
<td><?=e($r['exam_date'])?><?=$r['start_time']?'<div class="lbl">'.e(substr($r['start_time'],0,5)).'</div>':''?></td><td><?=$r['done']?> / <?=$r['total']?></td></tr>
<?php endforeach; if(!$rows):?><tr><td colspan="4" class="lbl text-center py-4">No exams found.</td></tr><?php endif;?></tbody></table></div>
<?php pg_render($total,$per);?>
<?php if($canSched):?>
<div class="modal fade" id="exModal" tabindex="-1"><div class="modal-dialog modal-dialog-centered modal-fullscreen-sm-down"><div class="modal-content" style="border-radius:24px"><form method="post">
<input type="hidden" name="csrf" value="<?=csrf()?>"><input type="hidden" name="act" value="schedule">
<div class="modal-header"><h5 class="modal-title">Schedule exam</h5><button type="button" class="btn-close" data-bs-dismiss="modal"></button></div>
<div class="modal-body d-grid gap-2"><?php foreach($errs as $m):?><div class="alert alert-danger rounded-4 py-2 mb-0"><?=e($m)?></div><?php endforeach;?>
<select class="form-select" name="batch_id" required><option value="">Course type · course · batch</option>
<?php foreach($batches as $b):?><option value="<?=$b['id']?>" <?=($old['batch_id']??'')==$b['id']?'selected':''?>><?=e(($s===null?$b['brname'].' · ':'').$b['cname'].' · '.$b['name'].' ('.$b['batch_year'].')')?></option><?php endforeach;?></select>
<input class="form-control" name="title" placeholder="Exam title *" value="<?=$o('title')?>" required>
<select class="form-select" name="exam_type"><?php foreach(['term'=>'Term / unit test','project'=>'Project','final'=>'Final exam','other'=>'Other'] as $k=>$l):?><option value="<?=$k?>" <?=($old['exam_type']??'')===$k?'selected':''?>><?=$l?></option><?php endforeach;?></select>
<div class="row g-2"><div class="col-6"><label class="lbl">Exam date</label><input class="form-control" type="date" name="exam_date" value="<?=$o('exam_date')?>" required></div>
<div class="col-6"><label class="lbl">Start time</label><input class="form-control" type="time" name="start_time" value="<?=$o('start_time')?>"></div></div>
<div><label class="lbl">Maximum marks</label><input class="form-control" type="number" step="0.01" min="1" name="max_marks" value="<?=$old['max_marks']??100?>" required></div></div>
<div class="modal-footer"><button class="btn-ui">Schedule</button></div></form></div></div></div>
<?php if($open):?><?php ob_start();?><script>new bootstrap.Modal(document.getElementById('exModal')).show();</script><?php $pageScript=ob_get_clean(); endif; endif;?>
<?php require __DIR__.'/includes/footer.php';
