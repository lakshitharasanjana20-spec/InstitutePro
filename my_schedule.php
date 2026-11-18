<?php
require_once __DIR__.'/includes/bootstrap.php';
require_once __DIR__.'/includes/student_lib.php';
require_once __DIR__.'/includes/learn_lib.php';
stu_guard('sschedule'); refresh_batches(); $pageKey='sschedule'; require __DIR__.'/includes/header.php';
$ids=stu_ids(); $ref=$_GET['week']??date('Y-m-d'); if(!is_string($ref)||!strtotime($ref)) $ref=date('Y-m-d');
$monday=date('Y-m-d',strtotime('monday this week',strtotime($ref))); $sunday=date('Y-m-d',strtotime($monday.' +6 days')); $today=date('Y-m-d');
function rows(string $sql,array $p=[]): array { $q=db()->prepare($sql); $q->execute($p); return $q->fetchAll(); }
$bat=rows("SELECT b.id,b.name,b.batch_year,b.start_date,b.end_date,b.start_time,b.end_time,b.class_days,c.name cname,u.full_name tch FROM batches b JOIN courses c ON c.id=b.course_id LEFT JOIN users u ON u.id=b.teacher_id WHERE b.id IN ($ids) AND b.start_date<=? AND b.end_date>=? ORDER BY b.start_time",[$sunday,$monday]);
$subs=[]; foreach(rows("SELECT sb.batch_id,sb.class_date,su.full_name n FROM substitutions sb JOIN users su ON su.id=sb.substitute_id WHERE sb.batch_id IN ($ids) AND sb.class_date BETWEEN ? AND ?",[$monday,$sunday]) as $r) $subs[$r['batch_id']][$r['class_date']]=$r['n'];
$exams=rows("SELECT x.id,x.title,x.exam_date,x.start_time,b.name bname,c.name cname FROM exams x JOIN batches b ON b.id=x.batch_id JOIN courses c ON c.id=b.course_id WHERE x.batch_id IN ($ids) AND x.exam_date BETWEEN ? AND ? ORDER BY x.start_time",[$monday,$sunday]);
$live=rows("SELECT m.id,m.title,m.url,m.starts_at,m.duration_min,b.name bname,c.name cname FROM materials m JOIN batches b ON b.id=m.batch_id JOIN courses c ON c.id=b.course_id WHERE m.batch_id IN ($ids) AND m.kind='live' AND m.publish=1 AND DATE(m.starts_at) BETWEEN ? AND ? ORDER BY m.starts_at",[$monday,$sunday]);
?>
<div class="card-ui mb-3 d-flex align-items-center gap-2">
 <a class="icon-btn text-decoration-none text-reset" href="my_schedule.php?week=<?=date('Y-m-d',strtotime("$monday -7 days"))?>"><i class="bi bi-chevron-left"></i></a>
 <div class="flex-grow-1 text-center fw-bold"><?=date('j M',strtotime($monday))?> – <?=date('j M Y',strtotime($sunday))?></div>
 <a class="icon-btn text-decoration-none text-reset" href="my_schedule.php?week=<?=date('Y-m-d',strtotime("$monday +7 days"))?>"><i class="bi bi-chevron-right"></i></a>
 <a class="btn btn-light rounded-pill" href="my_schedule.php">Today</a></div>
<div style="display:grid;grid-template-columns:repeat(auto-fit,minmax(230px,1fr));gap:12px">
<?php for($i=0;$i<7;$i++): $d=date('Y-m-d',strtotime("$monday +$i days")); $dn=date('D',strtotime($d)); $has=false;?>
<div class="card-ui" style="<?=$d===$today?'outline:2px solid var(--blue)':''?>"><div class="d-flex justify-content-between mb-2"><b><?=$dn?> <?=date('j M',strtotime($d))?></b><?php if($d===$today):?><span class="badge rounded-pill text-bg-primary">Today</span><?php endif;?></div>
<?php foreach($exams as $x) if($x['exam_date']===$d){ $has=true;?><div class="border-top py-2"><span class="badge rounded-pill text-bg-warning">Exam</span> <?=$x['start_time']?e(substr($x['start_time'],0,5)):''?><div><?=e($x['title'])?></div><div class="lbl"><?=e($x['cname'])?> · <?=e($x['bname'])?></div></div><?php }?>
<?php foreach($live as $m) if(date('Y-m-d',strtotime($m['starts_at']))===$d){ $has=true; $ls=live_state($m['starts_at'],$m['duration_min']);?><div class="border-top py-2"><span class="badge rounded-pill text-bg-<?=$ls[1]?>">Live</span> <b style="color:var(--blue)"><?=e(date('g:i A',strtotime($m['starts_at'])))?></b><div><?=e($m['title'])?></div><div class="lbl"><?=e($m['cname'])?> · <?=e($m['bname'])?></div><?php if($ls[2]&&$ls[1]!=='dark'):?><a class="btn btn-sm btn-danger rounded-pill mt-1" href="<?=e($m['url'])?>" target="_blank" rel="noopener noreferrer">Join</a><?php endif;?></div><?php }?>
<?php foreach($bat as $b){ if($b['start_date']>$d||$b['end_date']<$d) continue; if($b['class_days']!==''&&$b['class_days']!==null&&!in_array($dn,explode(',',$b['class_days']),true)) continue; $has=true; $sub=$subs[$b['id']][$d]??null;?>
<a href="my_class.php?id=<?=$b['id']?>" class="d-block text-decoration-none text-reset border-top py-2"><div class="fw-bold" style="color:var(--blue)"><?=e(substr($b['start_time'],0,5))?>–<?=e(substr($b['end_time'],0,5))?><?php if($sub):?> <span class="badge rounded-pill text-bg-info">Substitute</span><?php endif;?></div><div><?=e($b['cname'])?> · <?=e($b['name'])?></div><div class="lbl"><?=e($sub??$b['tch']??'Teacher to be announced')?></div></a><?php }?>
<?php if(!$has):?><div class="lbl">No classes</div><?php endif;?></div>
<?php endfor;?></div>
<?php require __DIR__.'/includes/footer.php';
