<?php
require_once __DIR__.'/includes/bootstrap.php';
if (($_SESSION['user']['role']??'')==='student') { header('Location: my_home.php'); exit; }
$partial=isset($_GET['partial']);
if ($partial) { require_menu('dashboard'); refresh_batches(); }
else { $pageKey='dashboard'; require __DIR__.'/includes/header.php'; }
// NOTE: no revenue / expense / overdue figures here by design; those live in Reports.
$s=branch_scope(); $bf=$s; if($s===null && gint('branch')) $bf=gint('branch');
$menu=allowed_menu($_SESSION['user']['role']); $has=fn($k)=>in_array($k,$menu,true); $me=(int)$_SESSION['user']['id'];
$bB=($bf!==null?' AND b.branch_id='.(int)$bf:'').tfrag('b');
$sS=($bf!==null?' AND s.branch_id='.(int)$bf:'').(is_teacher()?" AND s.id IN (SELECT e.student_id FROM enrolments e JOIN batches tb ON tb.id=e.batch_id WHERE (tb.teacher_id=$me OR EXISTS (SELECT 1 FROM substitutions sbx WHERE sbx.batch_id=tb.id AND sbx.substitute_id=$me AND sbx.class_date>=DATE_SUB(CURDATE(),INTERVAL 1 DAY))))":'');
function lq(string $sql): array { return db()->query($sql)->fetchAll(); }
function qv(string $sql) { return db()->query($sql)->fetchColumn(); }
$today=date('Y-m-d'); $wd=date('D'); $nowT=date('H:i:s');

$stuBy=array_column(lq("SELECT s.status,COUNT(*) c FROM students s WHERE 1=1$sS GROUP BY s.status"),'c','status');
$active=(int)($stuBy['active']??0); $inactive=(int)($stuBy['inactive']??0);
$newMonth=(int)qv("SELECT COUNT(*) FROM students s WHERE s.registered_at>=DATE_FORMAT(CURDATE(),'%Y-%m-01')$sS");
$batchesActive=(int)qv("SELECT COUNT(*) FROM batches b WHERE b.status='active'$bB");
$cls=array_values(array_filter(lq("SELECT b.id,b.name,b.batch_year,b.start_time,b.end_time,b.class_days,c.name cname,u.full_name tch,
  (SELECT COUNT(*) FROM enrolments e WHERE e.batch_id=b.id) cnt,(SELECT COUNT(*) FROM attendance a WHERE a.batch_id=b.id AND a.att_date=CURDATE()) marked
  FROM batches b JOIN courses c ON c.id=b.course_id LEFT JOIN users u ON u.id=b.teacher_id
  WHERE b.status='active' AND b.start_date<=CURDATE() AND b.end_date>=CURDATE()$bB ORDER BY b.start_time"),
  fn($b)=>$b['class_days']===''||$b['class_days']===null||in_array($wd,explode(',',$b['class_days']),true)));
$attToday=array_column(lq("SELECT a.status,COUNT(*) c FROM attendance a JOIN batches b ON b.id=a.batch_id WHERE a.att_date=CURDATE()$bB GROUP BY a.status"),'c','status');
$present=(int)($attToday['present']??0); $absent=(int)($attToday['absent']??0);
$exams=lq("SELECT x.id,x.title,x.exam_type,x.exam_date,x.start_time,b.name bname,c.name cname FROM exams x JOIN batches b ON b.id=x.batch_id JOIN courses c ON c.id=b.course_id WHERE x.exam_date BETWEEN CURDATE() AND DATE_ADD(CURDATE(),INTERVAL 7 DAY)$bB ORDER BY x.exam_date,x.start_time LIMIT 6");
$pending=lq("SELECT x.id,x.title,x.exam_date,b.name bname,
  (SELECT COUNT(*) FROM exam_marks m WHERE m.exam_id=x.id AND (m.marks IS NOT NULL OR m.absent=1)) done,(SELECT COUNT(*) FROM enrolments e WHERE e.batch_id=x.batch_id) total
  FROM exams x JOIN batches b ON b.id=x.batch_id WHERE x.exam_date<=CURDATE()$bB
  HAVING total>0 AND done<total ORDER BY x.exam_date DESC LIMIT 5");
$trend=[]; for($i=6;$i>=0;$i--) $trend[date('Y-m-d',strtotime("-$i days"))]=[0,0];
foreach(lq("SELECT a.att_date d,a.status st,COUNT(*) c FROM attendance a JOIN batches b ON b.id=a.batch_id WHERE a.att_date>=DATE_SUB(CURDATE(),INTERVAL 6 DAY)$bB GROUP BY a.att_date,a.status") as $r)
  if(isset($trend[$r['d']])) $trend[$r['d']][$r['st']==='present'?0:1]=(int)$r['c'];
$trMax=max(1,max(array_map(fn($v)=>$v[0]+$v[1],$trend)));
$inact=lq("SELECT s.id,s.full_name,s.student_code,c.name cname FROM students s JOIN batches b ON b.id=s.batch_id JOIN courses c ON c.id=b.course_id WHERE s.status='inactive'$sS ORDER BY s.id DESC LIMIT 5");
$recent=lq("SELECT s.id,s.full_name,s.student_code,s.registered_at,c.name cname FROM students s JOIN batches b ON b.id=s.batch_id JOIN courses c ON c.id=b.course_id WHERE 1=1$sS ORDER BY s.id DESC LIMIT 5");
$ending=lq("SELECT b.id,b.name,b.batch_year,b.end_date,c.name cname FROM batches b JOIN courses c ON c.id=b.course_id WHERE b.status='active' AND b.end_date BETWEEN CURDATE() AND DATE_ADD(CURDATE(),INTERVAL 30 DAY)$bB ORDER BY b.end_date LIMIT 5");
$staffAtt=[]; $resets=0;
if (can('mark_staff_attendance')) $staffAtt=array_column(lq("SELECT sa.status,COUNT(*) c FROM staff_attendance sa JOIN users u ON u.id=sa.user_id WHERE sa.att_date=CURDATE()".($bf!==null?' AND u.branch_id='.(int)$bf:'')." GROUP BY sa.status"),'c','status');
if (can('manage_users')) $resets=(int)qv("SELECT COUNT(DISTINCT r.user_id) FROM password_resets r JOIN users u ON u.id=r.user_id WHERE r.used_at IS NULL AND r.requested_at>(NOW() - INTERVAL 1 DAY)".($bf!==null?' AND u.branch_id='.(int)$bf:''));
function card(string $label,$val,string $icon,string $href,string $color='var(--blue)'): void {
  echo '<a class="card-ui quick" href="'.e($href).'"><span class="ic" style="color:'.$color.'"><i class="bi '.$icon.'"></i></span><span><span class="stat" style="font-size:1.4rem;display:block">'.e((string)$val).'</span><span class="lbl">'.e($label).'</span></span></a>';
}
$hr=(int)date('G'); $greet=$hr<12?'Good morning':($hr<17?'Good afternoon':'Good evening');
$branches=$s===null?db()->query("SELECT id,name FROM branches WHERE status='active' ORDER BY name")->fetchAll():[];
if (!$partial): ?>
<div class="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3">
 <div><div class="lbl"><?=e(date('l, j F Y'))?></div><div class="stat" style="font-size:1.4rem"><?=$greet?>, <?=e(explode(' ',$_SESSION['user']['full_name'])[0])?></div></div>
 <?php if($branches):?><form method="get"><select class="form-select" name="branch" onchange="this.form.submit()"><option value="">All branches</option><?php foreach($branches as $b):?><option value="<?=$b['id']?>" <?=$bf===(int)$b['id']?'selected':''?>><?=e($b['name'])?></option><?php endforeach;?></select></form><?php endif;?>
</div>
<div id="live">
<?php endif; ?>
<?php if($resets):?><a href="staff.php" class="alert alert-warning rounded-4 d-block text-decoration-none"><i class="bi bi-key-fill"></i> <?=$resets?> password reset request<?=$resets>1?'s':''?> waiting. Open Staff to reset.</a><?php endif;?>
<div class="kpi-grid mb-3">
<?php
if($has('students')){ card('Active students',$active,'bi-people-fill','students.php?status=active'); card('Inactive students',$inactive,'bi-person-dash-fill','students.php?status=inactive',$inactive?'#d9534f':'var(--blue)');  }
if($has('schedule')) card('Classes today',count($cls),'bi-calendar-check-fill','schedule.php');
if($has('attendance')) card('Present today',$present.($absent?' · '.$absent.' absent':''),'bi-check2-square','attendance.php','#1e9e5a');
if($has('students')) card('New this month',$newMonth,'bi-person-plus-fill','students.php');
if($has('exams')) card('Exams in 7 days',count($exams),'bi-pencil-square','exams.php','#e08a00');
if($has('courses')) card('Active batches',$batchesActive,'bi-journal-bookmark-fill','courses.php');
if($staffAtt||can('mark_staff_attendance')) card('Staff present today',(int)($staffAtt['present']??0)+(int)($staffAtt['half_day']??0),'bi-person-badge-fill','staff_attendance.php','#1e9e5a');
?>
</div>
<div class="row g-3">
<?php if($has('schedule')):?><div class="col-lg-6"><div class="card-ui h-100"><div class="fw-bold mb-2">Today's classes</div>
 <?php foreach($cls as $c): $st=$nowT>=$c['start_time']&&$nowT<=$c['end_time']?['Now','primary']:($nowT<$c['start_time']?['Upcoming','secondary']:['Done','success']);?>
 <a href="batch_view.php?id=<?=$c['id']?>" class="d-block text-decoration-none text-reset border-top py-2">
  <div class="d-flex justify-content-between"><b style="color:var(--blue)"><?=e(substr($c['start_time'],0,5))?>–<?=e(substr($c['end_time'],0,5))?></b><span class="badge rounded-pill text-bg-<?=$st[1]?>"><?=$st[0]?></span></div>
  <div><?=e($c['cname'])?> · <?=e($c['name'])?> (<?=$c['batch_year']?>)</div>
  <div class="lbl"><?=e($c['tch']??'No teacher')?> · <?=$c['cnt']?> students<?=$has('attendance')?($c['marked']?' · attendance marked':' · attendance not marked'):''?></div></a>
 <?php endforeach; if(!$cls):?><div class="lbl">No classes today.</div><?php endif;?></div></div><?php endif;?>
<?php if($has('attendance')):?><div class="col-lg-6"><div class="card-ui h-100"><div class="fw-bold mb-2">Attendance, last 7 days</div>
 <div class="d-flex align-items-end gap-2" style="height:110px">
 <?php foreach($trend as $d=>$v):?><div class="text-center" style="flex:1 1 0;min-width:0;padding:0 4px"><div class="d-flex flex-column justify-content-end" style="height:90px" title="<?=e($d)?>: <?=$v[0]?> present, <?=$v[1]?> absent">
  <div style="height:<?=round($v[1]/$trMax*90)?>px;background:#d9534f;border-radius:6px 6px 0 0"></div><div style="height:<?=round($v[0]/$trMax*90)?>px;background:#1e9e5a;<?=$v[1]?'':'border-radius:6px 6px 0 0'?>"></div></div><div class="lbl" style="font-size:.7rem"><?=e(date('D',strtotime($d)))?></div></div><?php endforeach;?></div>
 <div class="lbl mt-2"><span style="color:#1e9e5a">■</span> Present &nbsp; <span style="color:#d9534f">■</span> Absent</div></div></div><?php endif;?>
<?php if($has('exams')):?><div class="col-lg-6"><div class="card-ui h-100"><div class="fw-bold mb-2">Upcoming exams</div>
 <?php foreach($exams as $x):?><a href="exam_marks.php?id=<?=$x['id']?>" class="d-block text-decoration-none text-reset border-top py-2"><div class="d-flex justify-content-between"><span><?=e($x['title'])?></span><span class="lbl"><?=e(date('D j M',strtotime($x['exam_date'])))?></span></div><div class="lbl"><?=e($x['cname'])?> · <?=e($x['bname'])?></div></a><?php endforeach;
 if(!$exams):?><div class="lbl">No exams in the next 7 days.</div><?php endif;?></div></div>
<div class="col-lg-6"><div class="card-ui h-100"><div class="fw-bold mb-2">Marks still to enter</div>
 <?php foreach($pending as $x):?><a href="exam_marks.php?id=<?=$x['id']?>" class="d-block text-decoration-none text-reset border-top py-2"><div class="d-flex justify-content-between"><span><?=e($x['title'])?></span><span class="badge rounded-pill text-bg-warning"><?=$x['done']?>/<?=$x['total']?></span></div><div class="lbl"><?=e($x['bname'])?> · <?=e($x['exam_date'])?></div></a><?php endforeach;
 if(!$pending):?><div class="lbl">All marks are up to date.</div><?php endif;?></div></div><?php endif;?>
<?php if($has('students')):?><div class="col-lg-6"><div class="card-ui h-100"><div class="fw-bold mb-2">Inactive students</div>
 <?php foreach($inact as $r):?><a href="student_view.php?id=<?=$r['id']?>" class="d-block text-decoration-none text-reset border-top py-2"><?=e($r['full_name'])?><div class="lbl"><?=e($r['student_code'])?> · <?=e($r['cname'])?></div></a><?php endforeach;
 if(!$inact):?><div class="lbl">No inactive students.</div><?php endif;?></div></div>
<div class="col-lg-6"><div class="card-ui h-100"><div class="fw-bold mb-2">Recently registered</div>
 <?php foreach($recent as $r):?><a href="student_view.php?id=<?=$r['id']?>" class="d-block text-decoration-none text-reset border-top py-2"><div class="d-flex justify-content-between"><span><?=e($r['full_name'])?></span><span class="lbl"><?=e(substr($r['registered_at'],0,10))?></span></div><div class="lbl"><?=e($r['student_code'])?> · <?=e($r['cname'])?></div></a><?php endforeach;
 if(!$recent):?><div class="lbl">No students yet.</div><?php endif;?></div></div><?php endif;?>
<?php if($has('courses')):?><div class="col-lg-6"><div class="card-ui h-100"><div class="fw-bold mb-2">Batches ending within 30 days</div>
 <?php foreach($ending as $b):?><a href="batch_view.php?id=<?=$b['id']?>" class="d-block text-decoration-none text-reset border-top py-2"><div class="d-flex justify-content-between"><span><?=e($b['cname'])?> · <?=e($b['name'])?> (<?=$b['batch_year']?>)</span><span class="lbl"><?=e($b['end_date'])?></span></div></a><?php endforeach;
 if(!$ending):?><div class="lbl">None.</div><?php endif;?></div></div><?php endif;?>
</div>
<div class="lbl text-end mt-3">Updated <?=date('H:i')?> · refreshes every minute</div>
<?php if(!$partial): ?>
</div>
<?php ob_start();?><script>
(function(){var box=document.getElementById('live');if(!box)return;
 var url='dashboard.php?partial=1'+location.search.replace('?','&');
 setInterval(function(){if(document.hidden)return;fetch(url,{credentials:'same-origin'}).then(function(r){return r.ok?r.text():null;}).then(function(h){if(h)box.innerHTML=h;}).catch(function(){});},60000);})();
</script>
<?php $pageScript=ob_get_clean(); require __DIR__.'/includes/footer.php'; endif;
