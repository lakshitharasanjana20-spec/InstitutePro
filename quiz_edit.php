<?php
require_once __DIR__.'/includes/bootstrap.php';
require_once __DIR__.'/includes/learn_lib.php';
require_menu('materials');
$s=branch_scope(); $me=(int)$_SESSION['user']['id']; $canUp=can('upload_material'); $id=gint('id');
$q=db()->prepare("SELECT m.*,b.name bname,b.batch_year,b.branch_id bbranch,c.name cname FROM materials m JOIN batches b ON b.id=m.batch_id JOIN courses c ON c.id=b.course_id WHERE m.id=? AND m.kind='quiz'"); $q->execute([$id]); $m=$q->fetch();
if (!$m || ($s!==null && (int)$m['bbranch']!==$s) || (is_teacher() && !teacher_has_batch((int)$m['batch_id']))) { http_response_code(404); exit('Quiz not found.'); }
$back='quiz_edit.php?id='.$id;
if ($_SERVER['REQUEST_METHOD']==='POST') {
  csrf_check(); if(!$canUp){ http_response_code(403); exit('Access denied.'); }
  $act=$_POST['act']??'';
  if ($act==='addq') {
    $qt=trim($_POST['question']??''); $o=[trim($_POST['a']??''),trim($_POST['b']??''),trim($_POST['c']??''),trim($_POST['d']??'')]; $cor=$_POST['correct']??''; $ex=mb_substr(trim($_POST['explanation']??''),0,255);
    $n=(int)db()->query('SELECT COUNT(*) FROM quiz_questions WHERE material_id='.(int)$id)->fetchColumn();
    if ($qt===''||in_array('',$o,true)||!in_array($cor,['A','B','C','D'],true)) flash_set('msg','Enter the question, all four options, and choose the correct answer.');
    elseif (mb_strlen($qt)>1000||max(array_map('mb_strlen',$o))>255) flash_set('msg','Question or an option is too long.');
    elseif ($n>=100) flash_set('msg','A quiz can have up to 100 questions.');
    else { db()->prepare('INSERT INTO quiz_questions (material_id,position,question,opt_a,opt_b,opt_c,opt_d,correct,explanation) VALUES (?,?,?,?,?,?,?,?,?)')->execute([$id,$n+1,$qt,$o[0],$o[1],$o[2],$o[3],$cor,$ex?:null]); log_activity('quiz_question','quiz '.$id); flash_set('msg','Question added.'); }
  } elseif ($act==='delq') {
    db()->prepare('DELETE FROM quiz_questions WHERE id=? AND material_id=?')->execute([(int)($_POST['qid']??0),$id]);
    db()->exec('SET @p=0'); db()->prepare('UPDATE quiz_questions SET position=(@p:=@p+1) WHERE material_id=? ORDER BY position,id')->execute([$id]); flash_set('msg','Question deleted.');
  } elseif ($act==='settings') {
    $title=trim($_POST['title']??''); $tl=(int)($_POST['time_limit_min']??0); $att=(int)($_POST['max_attempts']??1); $op=dt_local($_POST['opens_at']??''); $cl=dt_local($_POST['closes_at']??'');
    if ($title===''||$tl<0||$tl>300||$att<1||$att>10||($op&&$cl&&$cl<=$op)) flash_set('msg','Check the title, time limit (0–300), attempts (1–10) and the opening/closing times.');
    else { db()->prepare('UPDATE materials SET title=?,description=?,time_limit_min=?,max_attempts=?,opens_at=?,closes_at=? WHERE id=?')->execute([mb_substr($title,0,150),trim($_POST['description']??'')?:null,$tl?:null,$att,$op,$cl,$id]); log_activity('quiz_settings',$title); flash_set('msg','Quiz settings saved.'); }
  }
  header('Location: '.$back.(isset($_POST['tab'])?'&tab='.urlencode($_POST['tab']):'')); exit;
}
$pageKey='materials'; require __DIR__.'/includes/header.php'; $msg=flash_get('msg');
$tab=($_GET['tab']??'')==='results'?'results':'questions';
$qs=db()->prepare('SELECT * FROM quiz_questions WHERE material_id=? ORDER BY position,id'); $qs->execute([$id]); $questions=$qs->fetchAll();
$res=db()->prepare("SELECT s.student_code,s.full_name,COUNT(a.id) attempts,MAX(a.score) best,MAX(a.total) total FROM enrolments e JOIN students s ON s.id=e.student_id LEFT JOIN quiz_attempts a ON a.student_id=s.id AND a.material_id=? AND a.submitted_at IS NOT NULL WHERE e.batch_id=? GROUP BY s.id,s.student_code,s.full_name ORDER BY s.full_name");
$res->execute([$id,$m['batch_id']]); $results=$res->fetchAll();
$done=array_filter($results,fn($r)=>$r['attempts']>0); $avg=$done?array_sum(array_map(fn($r)=>$r['total']?$r['best']/$r['total']*100:0,$done))/count($done):0;
?>
<?php if($msg):?><div class="alert alert-info rounded-4"><?=e($msg)?></div><?php endif;?>
<a href="learning.php?batch=<?=$m['batch_id']?>&type=quiz" class="lbl text-decoration-none">← Back to learning hub</a>
<div class="card-ui my-3"><div class="stat" style="font-size:1.3rem"><?=e($m['title'])?></div><div class="lbl"><?=e($m['cname'])?> · <?=e($m['bname'])?> (<?=$m['batch_year']?>) · <?=count($questions)?> questions · <?=$m['publish']?'Visible to students':'Hidden'?></div>
<?php if(!$questions):?><div class="alert alert-warning rounded-4 mt-2 mb-0 py-2">Add at least one question. Quizzes with no questions don't appear to students.</div><?php endif;?></div>
<ul class="nav nav-pills mb-3"><li class="nav-item"><a class="nav-link <?=$tab==='questions'?'active':''?>" href="<?=$back?>">Questions &amp; settings</a></li><li class="nav-item"><a class="nav-link <?=$tab==='results'?'active':''?>" href="<?=$back?>&tab=results">Results</a></li></ul>
<?php if($tab==='results'):?>
<div class="row g-2 text-center mb-3"><div class="col-4"><div class="card-ui"><div class="stat" style="font-size:1.2rem"><?=count($done)?>/<?=count($results)?></div><div class="lbl">Attempted</div></div></div>
<div class="col-4"><div class="card-ui"><div class="stat" style="font-size:1.2rem"><?=round($avg)?>%</div><div class="lbl">Average (best)</div></div></div></div>
<input class="form-control mb-2" id="qrFilter" placeholder="Search student">
<div class="card-ui table-wrap"><table class="table align-middle"><thead><tr><th>Student</th><th>Attempts</th><th>Best score</th></tr></thead><tbody data-pager="10" data-filter="#qrFilter">
<?php foreach($results as $r):?><tr><td><?=e($r['full_name'])?><div class="lbl"><?=e($r['student_code'])?></div></td><td><?=$r['attempts']?></td><td><?=$r['attempts']?e($r['best'].' / '.$r['total']).' ('.round($r['total']?$r['best']/$r['total']*100:0).'%)':'—'?></td></tr><?php endforeach;?></tbody></table></div>
<?php else:?>
<?php if($canUp):?>
<form method="post" class="card-ui mb-3 row g-2"><input type="hidden" name="csrf" value="<?=csrf()?>"><input type="hidden" name="act" value="settings">
 <div class="fw-bold">Settings</div>
 <div class="col-12"><input class="form-control" name="title" maxlength="150" value="<?=e($m['title'])?>" required></div>
 <div class="col-12"><textarea class="form-control" name="description" rows="2" placeholder="Instructions"><?=e($m['description'])?></textarea></div>
 <div class="col-6 col-md-3"><label class="lbl">Time limit (min, 0 = none)</label><input class="form-control" type="number" min="0" max="300" name="time_limit_min" value="<?=(int)$m['time_limit_min']?>"></div>
 <div class="col-6 col-md-3"><label class="lbl">Attempts</label><input class="form-control" type="number" min="1" max="10" name="max_attempts" value="<?=(int)$m['max_attempts']?>"></div>
 <div class="col-6 col-md-3"><label class="lbl">Opens</label><input class="form-control" type="datetime-local" name="opens_at" value="<?=e(dt_input($m['opens_at']))?>"></div>
 <div class="col-6 col-md-3"><label class="lbl">Closes</label><input class="form-control" type="datetime-local" name="closes_at" value="<?=e(dt_input($m['closes_at']))?>"></div>
 <div class="col-12"><button class="btn-ui">Save settings</button></div></form>
<form method="post" class="card-ui mb-3 row g-2"><input type="hidden" name="csrf" value="<?=csrf()?>"><input type="hidden" name="act" value="addq">
 <div class="fw-bold">Add a question</div>
 <div class="col-12"><textarea class="form-control" name="question" rows="2" maxlength="1000" placeholder="Question *" required></textarea></div>
 <?php foreach(['a'=>'A','b'=>'B','c'=>'C','d'=>'D'] as $k=>$l):?><div class="col-12 col-md-6"><div class="input-group"><span class="input-group-text"><?=$l?></span><input class="form-control" name="<?=$k?>" maxlength="255" placeholder="Option <?=$l?> *" required></div></div><?php endforeach;?>
 <div class="col-6 col-md-3"><label class="lbl">Correct answer</label><select class="form-select" name="correct" required><option value="">Choose</option><?php foreach(['A','B','C','D'] as $l):?><option><?=$l?></option><?php endforeach;?></select></div>
 <div class="col-12 col-md-9"><label class="lbl">Explanation shown after the quiz (optional)</label><input class="form-control" name="explanation" maxlength="255"></div>
 <div class="col-12"><button class="btn-ui">Add question</button></div></form>
<?php endif;?>
<div class="card-ui" data-pager="10"><?php foreach($questions as $i=>$z):?>
 <div class="border-top py-3"><div class="d-flex justify-content-between gap-2"><div><b><?=$z['position']?>.</b> <?=nl2br(e($z['question']))?>
  <?php foreach(['A'=>'opt_a','B'=>'opt_b','C'=>'opt_c','D'=>'opt_d'] as $l=>$col):?><div class="<?=$z['correct']===$l?'text-success fw-bold':'lbl'?>"><?=$l?>. <?=e($z[$col])?><?=$z['correct']===$l?' ✓':''?></div><?php endforeach;?>
  <?php if($z['explanation']):?><div class="lbl">Explanation: <?=e($z['explanation'])?></div><?php endif;?></div>
  <?php if($canUp):?><form method="post" onsubmit="return confirm('Delete this question?')"><input type="hidden" name="csrf" value="<?=csrf()?>"><input type="hidden" name="act" value="delq"><input type="hidden" name="qid" value="<?=$z['id']?>"><button class="btn btn-sm btn-light rounded-pill">Delete</button></form><?php endif;?></div></div>
<?php endforeach; if(!$questions):?><div class="lbl" data-pg-skip>No questions yet.</div><?php endif;?></div>
<?php endif;?>
<?php require __DIR__.'/includes/footer.php';
