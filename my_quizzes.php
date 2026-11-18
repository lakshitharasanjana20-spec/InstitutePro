<?php
require_once __DIR__.'/includes/bootstrap.php';
require_once __DIR__.'/includes/student_lib.php';
stu_guard('squiz'); $pageKey='squiz'; require __DIR__.'/includes/header.php';
$sid=stu_id(); $ids=stu_ids(); $bs=stu_batches(); $fq=gstr('q'); $fb=gint('batch');
$w=''; $p=[$sid,$sid,$sid]; if($fq!==''){$w.=' AND (m.title LIKE ? OR m.description LIKE ?)';array_push($p,"%$fq%","%$fq%");} if($fb&&in_array($fb,array_map('intval',array_column($bs,'id')),true)){$w.=' AND m.batch_id=?';$p[]=$fb;}
$base="FROM materials m JOIN batches b ON b.id=m.batch_id JOIN courses c ON c.id=b.course_id WHERE m.batch_id IN ($ids) AND m.kind='quiz' AND m.publish=1 AND EXISTS (SELECT 1 FROM quiz_questions z WHERE z.material_id=m.id)$w";
$cp=array_slice($p,3); $cq=db()->prepare("SELECT COUNT(*) $base"); $cq->execute($cp); $total=(int)$cq->fetchColumn(); $per=pg_per(); [$pg,$pages,$off]=pg_page($total,$per);
$q=db()->prepare("SELECT m.id,m.title,m.time_limit_min,m.max_attempts,m.opens_at,m.closes_at,c.name cname,b.name bname,(SELECT COUNT(*) FROM quiz_questions z WHERE z.material_id=m.id) nq,
 (SELECT COUNT(*) FROM quiz_attempts a WHERE a.material_id=m.id AND a.student_id=? AND a.submitted_at IS NOT NULL) used,(SELECT MAX(a.score) FROM quiz_attempts a WHERE a.material_id=m.id AND a.student_id=? AND a.submitted_at IS NOT NULL) best,
 (SELECT MAX(a.total) FROM quiz_attempts a WHERE a.material_id=m.id AND a.student_id=? AND a.submitted_at IS NOT NULL) tot $base ORDER BY m.id DESC LIMIT $per OFFSET $off"); $q->execute($p); $rows=$q->fetchAll();
f_open(); f_input('q','Search quizzes','text','col-12 col-md-5'); f_select('batch',array_column($bs,'name','id'),'All classes','col-6 col-md-4'); f_close();
?>
<?php foreach($rows as $r): $nowT=time(); $closed=$r['closes_at']&&$nowT>strtotime($r['closes_at']); $notOpen=$r['opens_at']&&$nowT<strtotime($r['opens_at']); $left=(int)$r['max_attempts']-(int)$r['used'];
 $state=$notOpen?['Opens '.date('j M, g:i A',strtotime($r['opens_at'])),'secondary']:($closed?['Closed','dark']:($left<=0?['No attempts left','secondary']:['Open','success']));?>
<div class="card-ui mb-2"><div class="d-flex flex-wrap justify-content-between gap-2"><div><b><?=e($r['title'])?></b> <span class="badge rounded-pill text-bg-<?=$state[1]?>"><?=e($state[0])?></span><div class="lbl"><?=e($r['cname'])?> · <?=e($r['bname'])?></div>
<div class="lbl"><?=(int)$r['nq']?> questions · <?=$r['time_limit_min']?(int)$r['time_limit_min'].' min':'no time limit'?> · attempts <?=(int)$r['used']?>/<?=(int)$r['max_attempts']?><?=$r['closes_at']?' · closes '.e(date('j M, g:i A',strtotime($r['closes_at']))):''?></div>
<?php if($r['best']!==null):?><div class="mt-1">Best score: <b><?=(int)$r['best']?> / <?=(int)$r['tot']?></b> (<?=round($r['tot']?$r['best']/$r['tot']*100:0)?>%)</div><?php endif;?></div>
<div class="align-self-start"><a class="btn-ui text-decoration-none" style="padding:8px 18px" href="learn_quiz.php?id=<?=$r['id']?>"><?=($state[0]==='Open')?($r['used']?'Try again':'Start'):'View'?></a></div></div></div>
<?php endforeach; if(!$rows):?><div class="card-ui lbl">No quizzes yet.</div><?php endif;?>
<?php pg_render($total,$per); require __DIR__.'/includes/footer.php';
