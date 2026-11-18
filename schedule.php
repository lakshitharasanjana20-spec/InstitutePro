<?php
require_once __DIR__.'/includes/bootstrap.php';
require_menu('schedule'); refresh_batches();
$s=branch_scope();
$ref=$_GET['week']??date('Y-m-d'); if(!is_string($ref)||!strtotime($ref)) $ref=date('Y-m-d');
$monday=date('Y-m-d',strtotime('monday this week',strtotime($ref))); $sunday=date('Y-m-d',strtotime($monday.' +6 days'));
$bf=$s; if($s===null && gint('branch')) $bf=gint('branch');
$tf=is_teacher()?(int)$_SESSION['user']['id']:gint('teacher');
$bcB=$bf!==null?' AND b.branch_id='.(int)$bf:'';
$pageKey='schedule'; require __DIR__.'/includes/header.php';
$q=db()->prepare("SELECT b.id,b.name,b.batch_year,b.start_date,b.end_date,b.start_time,b.end_time,b.class_days,b.teacher_id,c.name cname,br.name brname,u.full_name tch
 FROM batches b JOIN courses c ON c.id=b.course_id JOIN branches br ON br.id=b.branch_id LEFT JOIN users u ON u.id=b.teacher_id
 WHERE b.start_date<=? AND b.end_date>=?$bcB ORDER BY b.start_time");
$q->execute([$sunday,$monday]); $batches=$q->fetchAll();
$subs=[]; $sq=db()->prepare("SELECT sb.batch_id,sb.class_date,sb.substitute_id,su.full_name sname FROM substitutions sb JOIN users su ON su.id=sb.substitute_id JOIN batches b ON b.id=sb.batch_id WHERE sb.class_date BETWEEN ? AND ?$bcB");
$sq->execute([$monday,$sunday]); foreach($sq->fetchAll() as $r) $subs[$r['batch_id']][$r['class_date']]=$r;
$leave=[]; $lq=db()->prepare("SELECT lr.user_id,lr.from_date,lr.to_date,u.full_name FROM leave_requests lr JOIN users u ON u.id=lr.user_id WHERE lr.status='approved' AND lr.from_date<=? AND lr.to_date>=?".($bf!==null?' AND u.branch_id='.(int)$bf:''));
$lq->execute([$sunday,$monday]); $leaves=$lq->fetchAll();
$eq=db()->prepare("SELECT x.id,x.title,x.exam_type,x.exam_date,x.start_time,b.name bname,c.name cname FROM exams x JOIN batches b ON b.id=x.batch_id JOIN courses c ON c.id=b.course_id WHERE x.exam_date BETWEEN ? AND ?$bcB".($tf?' AND b.teacher_id='.(int)$tf:'')." ORDER BY x.start_time");
$eq->execute([$monday,$sunday]); $exams=$eq->fetchAll();
$days=[];
for($i=0;$i<7;$i++){
  $d=date('Y-m-d',strtotime("$monday +$i days")); $dn=date('D',strtotime($d)); $items=[];
  $onLeave=[]; foreach($leaves as $l) if($l['from_date']<=$d&&$l['to_date']>=$d) $onLeave[$l['user_id']]=$l['full_name'];
  foreach($batches as $b){
    if($b['start_date']>$d||$b['end_date']<$d) continue;
    if($b['class_days']!==''&&$b['class_days']!==null&&!in_array($dn,explode(',',$b['class_days']),true)) continue;
    $sub=$subs[$b['id']][$d]??null; $effId=$sub?(int)$sub['substitute_id']:(int)$b['teacher_id']; $effName=$sub?$sub['sname']:$b['tch'];
    $covered=false;
    if($tf){ if($effId!==$tf){ if($sub&&(int)$b['teacher_id']===$tf) $covered=true; else continue; } }
    $items[]=['b'=>$b,'sub'=>$sub,'eff'=>$effId,'name'=>$effName,'covered'=>$covered,'noCover'=>(!$sub&&$effId&&isset($onLeave[$effId])),'clash'=>false];
  }
  foreach($items as $i1=>$a) foreach($items as $i2=>$c){
    if($i1>=$i2||!$a['eff']||$a['eff']!==$c['eff']||$a['covered']||$c['covered']) continue;
    if($a['b']['start_time']<$c['b']['end_time']&&$c['b']['start_time']<$a['b']['end_time']){ $items[$i1]['clash']=true; $items[$i2]['clash']=true; }
  }
  $days[$d]=['dn'=>$dn,'items'=>$items,'exams'=>array_values(array_filter($exams,fn($x)=>$x['exam_date']===$d)),'leave'=>$onLeave];
}
$branches=db()->query("SELECT id,name FROM branches WHERE status='active' ORDER BY name")->fetchAll();
$teachers=is_teacher()?[]:db()->query("SELECT id,full_name FROM users WHERE role='teacher' AND status='active'".($bf!==null?' AND branch_id='.(int)$bf:'')." ORDER BY full_name")->fetchAll();
$tname=''; foreach($teachers as $t) if((int)$t['id']===$tf) $tname=$t['full_name'];
$qs=fn($w)=>http_build_query(array_filter(['week'=>$w,'branch'=>$s===null?($bf?:''):'','teacher'=>is_teacher()?'':($tf?:'')]));
$today=date('Y-m-d');
?>
<div class="card-ui mb-3">
 <div class="d-flex align-items-center gap-2 mb-2">
  <a class="icon-btn text-decoration-none text-reset" href="schedule.php?<?=$qs(date('Y-m-d',strtotime("$monday -7 days")))?>"><i class="bi bi-chevron-left"></i></a>
  <div class="flex-grow-1 text-center fw-bold"><?=date('j M',strtotime($monday))?> – <?=date('j M Y',strtotime($sunday))?><?=$tname?'<div class="lbl">'.e($tname).' timetable</div>':''?></div>
  <a class="icon-btn text-decoration-none text-reset" href="schedule.php?<?=$qs(date('Y-m-d',strtotime("$monday +7 days")))?>"><i class="bi bi-chevron-right"></i></a>
  <a class="btn btn-light rounded-pill" href="schedule.php?<?=$qs('')?>">Today</a></div>
 <?php if(!is_teacher()):?><form method="get" class="row g-2"><input type="hidden" name="week" value="<?=e($monday)?>">
  <?php if($s===null):?><div class="col-6"><select class="form-select" name="branch" onchange="this.form.submit()"><option value="">All branches</option><?php foreach($branches as $b):?><option value="<?=$b['id']?>" <?=$bf===(int)$b['id']?'selected':''?>><?=e($b['name'])?></option><?php endforeach;?></select></div><?php endif;?>
  <div class="col-<?=$s===null?'6':'12'?>"><select class="form-select" name="teacher" onchange="this.form.submit()"><option value="">All teachers</option><?php foreach($teachers as $t):?><option value="<?=$t['id']?>" <?=$tf===(int)$t['id']?'selected':''?>><?=e($t['full_name'])?></option><?php endforeach;?></select></div></form><?php endif;?>
 <?php if(can('manage_substitutes')):?><div class="mt-2"><a class="btn btn-light rounded-pill btn-sm" href="substitutes.php"><i class="bi bi-arrow-left-right"></i> Substitute classes</a></div><?php endif;?>
</div>
<div style="display:grid;grid-template-columns:repeat(auto-fit,minmax(230px,1fr));gap:12px">
<?php foreach($days as $d=>$day): $isToday=$d===$today; $myLeave=$tf&&isset($day['leave'][$tf]);?>
 <div class="card-ui" style="<?=$isToday?'outline:2px solid var(--blue)':''?>">
  <div class="d-flex justify-content-between mb-2"><b><?=e($day['dn'])?> <?=date('j M',strtotime($d))?></b><?php if($isToday):?><span class="badge rounded-pill text-bg-primary">Today</span><?php endif;?></div>
  <?php if($myLeave):?><div class="mb-2"><span class="badge rounded-pill text-bg-warning">On leave</span></div><?php endif;?>
  <?php foreach($day['exams'] as $x):?><a href="exam_marks.php?id=<?=$x['id']?>" class="d-block text-decoration-none text-reset border-top py-2"><span class="badge rounded-pill text-bg-warning">Exam</span> <?=$x['start_time']?e(substr($x['start_time'],0,5)):''?>
   <div><?=e($x['title'])?></div><div class="lbl"><?=e($x['cname'])?> · <?=e($x['bname'])?></div></a><?php endforeach;?>
  <?php foreach($day['items'] as $it): $b=$it['b'];?>
  <a href="batch_view.php?id=<?=$b['id']?>" class="d-block text-decoration-none text-reset border-top py-2" style="<?=$it['covered']?'opacity:.55':''?>">
   <div class="fw-bold" style="color:var(--blue)"><?=e(substr($b['start_time'],0,5))?>–<?=e(substr($b['end_time'],0,5))?>
    <?php if($it['clash']):?><i class="bi bi-exclamation-triangle-fill text-danger" title="Teacher has two classes at the same time"></i><?php endif;?>
    <?php if($it['sub']&&!$it['covered']):?><span class="badge rounded-pill text-bg-info">Substitute</span><?php endif;?>
    <?php if($it['covered']):?><span class="badge rounded-pill text-bg-secondary">Covered</span><?php endif;?>
    <?php if($it['noCover']):?><span class="badge rounded-pill text-bg-danger">On leave – no cover</span><?php endif;?></div>
   <div><?=e($b['cname'])?> · <?=e($b['name'])?> (<?=$b['batch_year']?>)</div>
   <div class="lbl"><?=e($it['name']??'No teacher')?><?=$it['covered']?' covers this class':''?><?=($s===null&&$bf===null)?' · '.e($b['brname']):''?></div></a>
  <?php endforeach; if(!$day['items']&&!$day['exams']):?><div class="lbl">No classes</div><?php endif;?>
 </div>
<?php endforeach;?></div>
<?php require __DIR__.'/includes/footer.php';
