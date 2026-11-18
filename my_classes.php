<?php
require_once __DIR__.'/includes/bootstrap.php';
require_once __DIR__.'/includes/student_lib.php';
stu_guard('sclasses'); $pageKey='sclasses'; require __DIR__.'/includes/header.php';
$bs=stu_batches(); $sid=stu_id();
$q=db()->prepare('SELECT batch_id,SUM(status=\'present\') p,COUNT(*) n FROM attendance WHERE student_id=? GROUP BY batch_id'); $q->execute([$sid]); $att=[]; foreach($q->fetchAll() as $r) $att[$r['batch_id']]=$r;
?>
<?php if(!$bs):?><div class="card-ui text-center lbl">You are not enrolled in any class yet.</div><?php endif;?>
<div class="row g-3"><?php foreach($bs as $b): $a=$att[$b['id']]??null; $pc=$a&&$a['n']?round($a['p']/$a['n']*100):null;?>
<div class="col-md-6"><a href="my_class.php?id=<?=$b['id']?>" class="card-ui d-block h-100 text-decoration-none text-reset">
 <div class="d-flex justify-content-between gap-2"><div><div class="lbl"><?=e($b['tname'])?></div><div class="fw-bold" style="font-size:1.1rem"><?=e($b['cname'])?></div><div><?=e($b['name'])?> (<?=$b['batch_year']?>)</div></div><span class="badge rounded-pill align-self-start text-bg-<?=$b['status']==='active'?'success':'secondary'?>"><?=e($b['status'])?></span></div>
 <div class="lbl mt-2"><i class="bi bi-person"></i> <?=e($b['tch']??'Teacher to be announced')?></div>
 <div class="lbl"><i class="bi bi-clock"></i> <?=e(substr($b['start_time'],0,5))?>–<?=e(substr($b['end_time'],0,5))?><?=$b['class_days']?' · '.e($b['class_days']):''?></div>
 <div class="lbl"><i class="bi bi-calendar-range"></i> <?=e(date('j M Y',strtotime($b['start_date'])))?> – <?=e(date('j M Y',strtotime($b['end_date'])))?></div>
 <?php if($pc!==null):?><div class="mt-2"><div class="d-flex justify-content-between lbl"><span>Attendance</span><b style="color:<?=pct_color($pc)?>"><?=$pc?>%</b></div><div class="progress" style="height:6px"><div class="progress-bar" style="width:<?=$pc?>%;background:<?=pct_color($pc)?>"></div></div></div><?php endif;?></a></div>
<?php endforeach;?></div>
<?php require __DIR__.'/includes/footer.php';
