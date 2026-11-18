<?php
require_once __DIR__.'/includes/bootstrap.php';
require_once __DIR__.'/includes/schedule_lib.php';
require_menu('schedule'); refresh_batches();
if (!can('manage_substitutes')) { http_response_code(403); exit('Access denied.'); }
$s=branch_scope(); $me=(int)$_SESSION['user']['id'];
$from=gdate('from')?:date('Y-m-d'); $to=gdate('to')?:date('Y-m-d',strtotime($from.' +14 days'));
if ($to<$from) [$from,$to]=[$to,$from]; if ((strtotime($to)-strtotime($from))/86400>60) $to=date('Y-m-d',strtotime($from.' +60 days'));
$bsc=$s!==null?' AND b.branch_id='.(int)$s:'';
if ($_SERVER['REQUEST_METHOD']==='POST') {
  csrf_check(); $act=$_POST['act']??'';
  if ($act==='assign') {
    $bid=(int)($_POST['batch_id']??0); $date=$_POST['class_date']??''; $sid=(int)($_POST['substitute_id']??0); $lid=(int)($_POST['leave_id']??0)?:null; $note=mb_substr(trim($_POST['note']??''),0,255);
    $q=db()->prepare("SELECT b.* FROM batches b WHERE b.id=? AND b.status='active'$bsc"); $q->execute([$bid]); $b=$q->fetch();
    $m=null;
    if (!$b) $m='Class not found.';
    elseif (!strtotime($date)||$date<date('Y-m-d')||$date<$b['start_date']||$date>$b['end_date']) $m='Choose a valid future date within the class dates.';
    elseif ($b['class_days']!==''&&$b['class_days']!==null&&!in_array(date('D',strtotime($date)),explode(',',$b['class_days']),true)) $m='This class does not meet on that day.';
    else {
      $t=db()->prepare("SELECT 1 FROM users WHERE id=? AND role='teacher' AND status='active' AND branch_id=?"); $t->execute([$sid,$b['branch_id']]);
      if (!$t->fetch()) $m='Choose a teacher from this branch.';
      elseif ((int)$b['teacher_id']===$sid) $m='That is already the class teacher.';
      elseif ($why=teacher_busy($sid,$date,$b['start_time'],$b['end_time'],$bid)) $m='That teacher is not free: '.$why.'.';
      else {
        try { db()->prepare('INSERT INTO substitutions (batch_id,class_date,original_teacher_id,substitute_id,leave_id,note,created_by) VALUES (?,?,?,?,?,?,?)')->execute([$bid,$date,$b['teacher_id'],$sid,$lid,$note?:null,$me]); log_activity('substitute_assign',"batch $bid $date → user $sid"); $m='Substitute assigned.'; }
        catch (PDOException $x) { $m='This class already has a substitute on that date.'; }
      }
    }
    flash_set('msg',$m);
  } elseif ($act==='remove') {
    $id=(int)($_POST['id']??0);
    $d=db()->prepare("DELETE sb FROM substitutions sb JOIN batches b ON b.id=sb.batch_id WHERE sb.id=?$bsc"); $d->execute([$id]);
    if ($d->rowCount()) { log_activity('substitute_remove','#'.$id); flash_set('msg','Substitute removed.'); }
  }
  header('Location: substitutes.php?from='.$from.'&to='.$to); exit;
}
$pageKey='schedule'; require __DIR__.'/includes/header.php'; $msg=flash_get('msg');
// Classes needing cover: approved teacher leaves in the range
$lq=db()->prepare("SELECT lr.id,lr.user_id,lr.from_date,lr.to_date,u.full_name,u.branch_id FROM leave_requests lr JOIN users u ON u.id=lr.user_id WHERE lr.status='approved' AND u.role='teacher' AND lr.from_date<=? AND lr.to_date>=?".($s!==null?' AND u.branch_id='.(int)$s:''));
$lq->execute([$to,$from]); $needs=[]; $teachersByBranch=[];
$tq=db()->query("SELECT id,full_name,branch_id FROM users WHERE role='teacher' AND status='active' ORDER BY full_name"); foreach($tq->fetchAll() as $t) $teachersByBranch[$t['branch_id']][]=$t;
foreach ($lq->fetchAll() as $l) {
  $bq=db()->prepare("SELECT b.*,c.name cname FROM batches b JOIN courses c ON c.id=b.course_id WHERE b.teacher_id=? AND b.status='active' AND b.start_date<=? AND b.end_date>=?"); $bq->execute([$l['user_id'],$to,$from]);
  foreach ($bq->fetchAll() as $b) {
    $d0=max($from,$l['from_date'],$b['start_date'],date('Y-m-d')); $d1=min($to,$l['to_date'],$b['end_date']);
    for ($d=$d0; $d<=$d1; $d=date('Y-m-d',strtotime("$d +1 day"))) {
      if ($b['class_days']!==''&&$b['class_days']!==null&&!in_array(date('D',strtotime($d)),explode(',',$b['class_days']),true)) continue;
      $c=db()->prepare('SELECT 1 FROM substitutions WHERE batch_id=? AND class_date=?'); $c->execute([$b['id'],$d]); if ($c->fetch()) continue;
      $needs[]=['date'=>$d,'b'=>$b,'leave'=>$l];
    }
  }
}
usort($needs,fn($a,$b)=>[$a['date'],$a['b']['start_time']]<=>[$b['date'],$b['b']['start_time']]);
$total=count($needs); $per=pg_per(); [$pg,$pages,$off]=pg_page($total,$per); $slice=array_slice($needs,$off,$per);
// Existing substitutions
$sq=db()->prepare("SELECT sb.id,sb.class_date,sb.note,b.name bname,b.batch_year,b.start_time,b.end_time,c.name cname,ou.full_name oname,su.full_name sname FROM substitutions sb JOIN batches b ON b.id=sb.batch_id JOIN courses c ON c.id=b.course_id LEFT JOIN users ou ON ou.id=sb.original_teacher_id JOIN users su ON su.id=sb.substitute_id WHERE sb.class_date BETWEEN ? AND ?$bsc ORDER BY sb.class_date,b.start_time");
$sq->execute([$from,$to]); $existing=$sq->fetchAll();
$mb=db()->query("SELECT b.id,b.name,b.batch_year,c.name cname FROM batches b JOIN courses c ON c.id=b.course_id WHERE b.status='active'$bsc ORDER BY c.name,b.name")->fetchAll();
$allT=db()->query("SELECT id,full_name FROM users WHERE role='teacher' AND status='active'".($s!==null?' AND branch_id='.(int)$s:'')." ORDER BY full_name")->fetchAll();
?>
<?php if($msg):?><div class="alert alert-info rounded-4"><?=e($msg)?></div><?php endif;?>
<a href="schedule.php" class="lbl text-decoration-none">← Back to schedule</a>
<form method="get" class="card-ui my-3 row g-2 align-items-end"><div class="col-6 col-md-3"><label class="lbl">From</label><input class="form-control" type="date" name="from" value="<?=e($from)?>"></div>
<div class="col-6 col-md-3"><label class="lbl">To</label><input class="form-control" type="date" name="to" value="<?=e($to)?>"></div><div class="col-12 col-md-2"><button class="btn-ui w-100">Show</button></div></form>
<div class="card-ui mb-3"><div class="fw-bold mb-2">Classes that need cover (<?=$total?>)</div><div class="lbl mb-2">Teacher is on approved leave and no substitute is assigned yet.</div>
<?php foreach($slice as $n): $b=$n['b'];?>
<form method="post" class="border-top py-2 row g-2 align-items-center"><input type="hidden" name="csrf" value="<?=csrf()?>"><input type="hidden" name="act" value="assign"><input type="hidden" name="batch_id" value="<?=$b['id']?>"><input type="hidden" name="class_date" value="<?=e($n['date'])?>"><input type="hidden" name="leave_id" value="<?=$n['leave']['id']?>">
 <div class="col-12 col-md-5"><b><?=e(date('D j M',strtotime($n['date'])))?></b> · <?=e(substr($b['start_time'],0,5))?>–<?=e(substr($b['end_time'],0,5))?><div><?=e($b['cname'])?> · <?=e($b['name'])?> (<?=$b['batch_year']?>)</div><div class="lbl"><?=e($n['leave']['full_name'])?> is on leave</div></div>
 <div class="col-8 col-md-4"><select class="form-select" name="substitute_id" required><option value="">Choose teacher</option>
 <?php foreach(($teachersByBranch[$b['branch_id']]??[]) as $t){ if((int)$t['id']===(int)$b['teacher_id']) continue; $busy=teacher_busy((int)$t['id'],$n['date'],$b['start_time'],$b['end_time'],(int)$b['id']);?>
  <option value="<?=$t['id']?>" <?=$busy?'disabled':''?>><?=e($t['full_name'])?><?=$busy?' – '.e($busy):''?></option><?php }?></select></div>
 <div class="col-4 col-md-3"><button class="btn-ui w-100">Assign</button></div></form>
<?php endforeach; if(!$slice):?><div class="lbl">Nothing needs cover in this period.</div><?php endif;?>
<?php pg_render($total,$per);?></div>
<div class="card-ui mb-3"><div class="fw-bold mb-2">Assigned substitutes</div>
<div class="table-wrap"><table class="table align-middle"><thead><tr><th>Date</th><th>Class</th><th>Teacher → Substitute</th><th></th></tr></thead><tbody data-pager="10">
<?php foreach($existing as $x):?><tr><td><?=e(date('D j M',strtotime($x['class_date'])))?><div class="lbl"><?=e(substr($x['start_time'],0,5))?>–<?=e(substr($x['end_time'],0,5))?></div></td><td><?=e($x['cname'])?> · <?=e($x['bname'])?> (<?=$x['batch_year']?>)</td>
<td><?=e($x['oname']??'—')?> → <b><?=e($x['sname'])?></b><?=$x['note']?'<div class="lbl">'.e($x['note']).'</div>':''?></td>
<td><?php if($x['class_date']>=date('Y-m-d')):?><form method="post" onsubmit="return confirm('Remove this substitute?')"><input type="hidden" name="csrf" value="<?=csrf()?>"><input type="hidden" name="act" value="remove"><input type="hidden" name="id" value="<?=$x['id']?>"><button class="btn btn-sm btn-light rounded-pill">Remove</button></form><?php endif;?></td></tr>
<?php endforeach; if(!$existing):?><tr><td colspan="4" class="lbl text-center py-3">No substitutes in this period.</td></tr><?php endif;?></tbody></table></div></div>
<form method="post" class="card-ui row g-2"><input type="hidden" name="csrf" value="<?=csrf()?>"><input type="hidden" name="act" value="assign">
 <div class="fw-bold">Add a substitute manually</div><div class="lbl">For unplanned absences with no leave request.</div>
 <div class="col-12 col-md-4"><select class="form-select" name="batch_id" required><option value="">Class</option><?php foreach($mb as $b):?><option value="<?=$b['id']?>"><?=e($b['cname'].' · '.$b['name'].' ('.$b['batch_year'].')')?></option><?php endforeach;?></select></div>
 <div class="col-6 col-md-2"><input class="form-control" type="date" name="class_date" min="<?=date('Y-m-d')?>" required></div>
 <div class="col-6 col-md-3"><select class="form-select" name="substitute_id" required><option value="">Substitute teacher</option><?php foreach($allT as $t):?><option value="<?=$t['id']?>"><?=e($t['full_name'])?></option><?php endforeach;?></select></div>
 <div class="col-12 col-md-3"><input class="form-control" name="note" maxlength="255" placeholder="Note (optional)"></div>
 <div class="col-12"><button class="btn-ui">Assign substitute</button></div></form>
<?php require __DIR__.'/includes/footer.php';
