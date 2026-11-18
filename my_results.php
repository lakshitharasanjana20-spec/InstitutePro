<?php
require_once __DIR__.'/includes/bootstrap.php';
require_once __DIR__.'/includes/student_lib.php';
require_once __DIR__.'/includes/exams_lib.php';
stu_guard('sresults'); $pageKey='sresults'; require __DIR__.'/includes/header.php';
$sid=stu_id(); $bs=stu_batches(); $tab=($_GET['tab']??'')==='quizzes'?'quizzes':'exams';
$fb=gint('batch'); if($fb&&!in_array($fb,array_map('intval',array_column($bs,'id')),true)) $fb=0; $fmt=fn($v)=>rtrim(rtrim(number_format((float)$v,2,'.',''),'0'),'.');
?>
<ul class="nav nav-pills mb-3"><li class="nav-item"><a class="nav-link <?=$tab==='exams'?'active':''?>" href="my_results.php">Exams</a></li><li class="nav-item"><a class="nav-link <?=$tab==='quizzes'?'active':''?>" href="my_results.php?tab=quizzes">Quizzes</a></li></ul>
<?php f_open(['tab']); f_select('batch',array_column($bs,'name','id'),'All classes','col-8 col-md-4'); f_close(['tab']);
if($tab==='exams'):
 $w=$fb?' AND x.batch_id=?':''; $p=[$sid]; if($fb) $p[]=$fb;
 $base="FROM exam_marks m JOIN exams x ON x.id=m.exam_id JOIN batches b ON b.id=x.batch_id JOIN courses c ON c.id=b.course_id JOIN course_types t ON t.id=c.type_id WHERE m.student_id=? AND (m.marks IS NOT NULL OR m.absent=1)$w";
 $cq=db()->prepare("SELECT COUNT(*),AVG(IF(m.marks IS NULL,NULL,m.marks/x.max_marks*100)) $base"); $cq->execute($p); [$total,$avg]=$cq->fetch(PDO::FETCH_NUM); $total=(int)$total; $per=pg_per(); [$pg,$pages,$off]=pg_page($total,$per);
 $q=db()->prepare("SELECT x.title,x.exam_type,x.exam_date,x.max_marks,m.marks,m.absent,m.remarks,b.name bname,c.name cname,t.code tcode $base ORDER BY x.exam_date DESC LIMIT $per OFFSET $off"); $q->execute($p); $rows=$q->fetchAll();?>
<?php if($avg!==null):?><div class="card-ui mb-3 text-center"><div class="lbl">Average score</div><div class="stat" style="color:<?=pct_color((float)$avg)?>"><?=round($avg)?>%</div></div><?php endif;?>
<?php foreach($rows as $r): $pc=$r['marks']!==null&&$r['max_marks']>0?$r['marks']/$r['max_marks']*100:null;?>
<div class="card-ui mb-2"><div class="d-flex justify-content-between gap-2"><div><b><?=e($r['title'])?></b><div class="lbl"><?=e($r['cname'])?> · <?=e($r['bname'])?> · <?=e(date('j M Y',strtotime($r['exam_date'])))?></div>
<?php if($pc!==null):?><div class="lbl"><?=e(exam_outcome($r['exam_type'],$r['tcode'],$pc))?></div><?php endif;?><?php if($r['remarks']):?><div class="lbl">Teacher: <?=e($r['remarks'])?></div><?php endif;?></div>
<div class="text-end"><?php if($r['absent']):?><span class="badge rounded-pill text-bg-secondary">Absent</span><?php else:?><div class="stat" style="font-size:1.3rem"><?=$fmt($r['marks'])?> <span class="lbl">/ <?=$fmt($r['max_marks'])?></span></div><span class="badge rounded-pill" style="background:<?=pct_color($pc)?>">Grade <?=grade_for($pc)?> · <?=round($pc)?>%</span><?php endif;?></div></div>
<?php if($pc!==null):?><div class="progress mt-2" style="height:6px"><div class="progress-bar" style="width:<?=min(100,round($pc))?>%;background:<?=pct_color($pc)?>"></div></div><?php endif;?></div>
<?php endforeach; if(!$rows):?><div class="card-ui lbl">No exam results yet.</div><?php endif; pg_render($total,$per);
else:
 $w=$fb?' AND m.batch_id=?':''; $p=[$sid]; if($fb) $p[]=$fb;
 $base="FROM quiz_attempts a JOIN materials m ON m.id=a.material_id JOIN batches b ON b.id=m.batch_id JOIN courses c ON c.id=b.course_id WHERE a.student_id=? AND a.submitted_at IS NOT NULL$w";
 $cq=db()->prepare("SELECT COUNT(*) $base"); $cq->execute($p); $total=(int)$cq->fetchColumn(); $per=pg_per(); [$pg,$pages,$off]=pg_page($total,$per);
 $q=db()->prepare("SELECT a.id,a.material_id,a.score,a.total,a.submitted_at,a.overtime,m.title,b.name bname,c.name cname $base ORDER BY a.id DESC LIMIT $per OFFSET $off"); $q->execute($p); $rows=$q->fetchAll();
 foreach($rows as $r): $pc=$r['total']?$r['score']/$r['total']*100:0;?>
<a href="learn_quiz.php?id=<?=$r['material_id']?>&result=<?=$r['id']?>" class="card-ui d-block mb-2 text-decoration-none text-reset"><div class="d-flex justify-content-between gap-2"><div><b><?=e($r['title'])?></b><div class="lbl"><?=e($r['cname'])?> · <?=e($r['bname'])?> · <?=e(date('j M Y, g:i A',strtotime($r['submitted_at'])))?></div><?php if($r['overtime']):?><div class="lbl text-danger">Time limit passed</div><?php endif;?></div>
<div class="text-end"><div class="stat" style="font-size:1.3rem"><?=(int)$r['score']?> <span class="lbl">/ <?=(int)$r['total']?></span></div><span class="badge rounded-pill" style="background:<?=pct_color($pc)?>"><?=round($pc)?>%</span></div></div></a>
<?php endforeach; if(!$rows):?><div class="card-ui lbl">No quiz attempts yet.</div><?php endif; pg_render($total,$per); endif;
require __DIR__.'/includes/footer.php';
