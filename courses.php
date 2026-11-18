<?php
require_once __DIR__.'/includes/bootstrap.php';
require_menu('courses'); require_once __DIR__.'/includes/schedule_lib.php'; refresh_batches();
$s=branch_scope(); $errs=[]; $old=[]; $open=false;

if ($_SERVER['REQUEST_METHOD']==='POST') {
  csrf_check();
  if (!can('manage_batches')) { http_response_code(403); exit('Access denied.'); }
  $act=$_POST['act']??'';
  if ($act==='new_course') {
    $tid=(int)$_POST['type_id']; $code=strtoupper(preg_replace('/[^A-Za-z0-9]/','',$_POST['code']??'')); $name=trim($_POST['name']??'');
    if ($tid && $code!=='' && $name!=='' && strlen($code)<=10) {
      try { db()->prepare('INSERT INTO courses (type_id,code,name) VALUES (?,?,?)')->execute([$tid,$code,$name]); log_activity('course_create',"$code $name"); flash_set('msg','Course added.'); }
      catch (PDOException $x) { flash_set('msg','That course code already exists for this type.'); }
    } else flash_set('msg','Type, short code (max 10 letters/numbers) and name are required.');
    header('Location: courses.php?tab=catalog'); exit;
  }
  if ($act==='new_batch') {
    $old=$_POST; $open=true;
    $bid = is_owner() ? (int)($_POST['branch_id']??0) : (int)$s;
    $cid=(int)($_POST['course_id']??0); $year=(int)($_POST['batch_year']??0); $name=trim($_POST['name']??'');
    $start=$_POST['start_date']??''; $dur=(int)($_POST['duration']??0);
    $st=$_POST['start_time']??''; $et=$_POST['end_time']??''; $days=implode(',',array_intersect($_POST['days']??[],['Mon','Tue','Wed','Thu','Fri','Sat','Sun']));
    $fee=(float)($_POST['fee']??0); $plan=$_POST['fee_plan']??'monthly'; $max=($_POST['max_students']??'')!=='' ? (int)$_POST['max_students'] : null;
    if (!$bid) $errs[]='Choose a branch.';
    if (!$cid) $errs[]='Choose a course or grade.';
    if ($year<2000||$year>2100) $errs[]='Enter a valid batch year.';
    if ($name==='') $errs[]='Batch / class name is required.';
    if (!$start||!strtotime($start)) $errs[]='Start date is required.';
    if ($dur<1||$dur>60) $errs[]='Duration must be 1–60 months.';
    if (!$st||!$et||$et<=$st) $errs[]='Enter a start time and a later end time.';
    if ($fee<0) $errs[]='Fee cannot be negative.';
    if (!in_array($plan,['installments_3','monthly','custom'],true)) $plan='monthly';
    if (!$errs) {
      $r=db()->prepare('SELECT DATE_ADD(?, INTERVAL ? MONTH)'); $r->execute([$start,$dur]); $end=$r->fetchColumn();
      $tid=(int)($_POST['teacher_id']??0)?:null;
      if ($tid) { $tq=db()->prepare("SELECT 1 FROM users WHERE id=? AND role='teacher' AND status='active' AND branch_id=?"); $tq->execute([$tid,$bid]);
        if (!$tq->fetch()) $errs[]='Choose a teacher from this branch.';
        elseif ($cl=teacher_clash($tid,$start,$end,$st,$et,$days,null)) $errs[]='This teacher already teaches '.$cl.' at that time.'; }
      if (!$errs) {
      db()->prepare('INSERT INTO batches (branch_id,course_id,batch_year,name,start_date,duration_months,end_date,start_time,end_time,class_days,fee,fee_plan,max_students,created_by,teacher_id) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)')
        ->execute([$bid,$cid,$year,$name,$start,$dur,$end,$st,$et,$days,$fee,$plan,$max,$_SESSION['user']['id'],$tid]);
      log_activity('batch_create',"$name ($year)"); flash_set('msg','Batch created. End date: '.$end); header('Location: courses.php'); exit; }
    }
  }
}
$pageKey='courses'; require __DIR__.'/includes/header.php';
$tab=in_array($_GET['tab']??'',['completed','catalog'],true)?$_GET['tab']:'active';
$msg=flash_get('msg');
$bw=$s!==null?' AND b.branch_id='.(int)$s:''; $bw.=tfrag('b');
function batch_rows(string $status,string $bw,array $p,string $lim): array {
  $q=db()->prepare("SELECT b.*,u.full_name tchname,c.name cname,t.name tname,br.name brname,(SELECT COUNT(*) FROM enrolments e WHERE e.batch_id=b.id) cnt
   FROM batches b JOIN courses c ON c.id=b.course_id JOIN course_types t ON t.id=c.type_id JOIN branches br ON br.id=b.branch_id LEFT JOIN users u ON u.id=b.teacher_id
   WHERE b.status='$status'$bw ORDER BY b.start_date DESC $lim");
  $q->execute($p); return $q->fetchAll();
}
$types=db()->query('SELECT id,name FROM course_types ORDER BY id')->fetchAll();
$courses=db()->query('SELECT c.id,c.type_id,c.code,c.name,t.name tname FROM courses c JOIN course_types t ON t.id=c.type_id ORDER BY c.type_id,c.id')->fetchAll();
$teachers=db()->query("SELECT id,full_name,branch_id FROM users WHERE role='teacher' AND status='active'".($s!==null?' AND branch_id='.(int)$s:'')." ORDER BY full_name")->fetchAll();
$branches=db()->query("SELECT id,name FROM branches WHERE status='active' ORDER BY name")->fetchAll();
$o=fn($k)=>e($old[$k]??'');
function batch_table(array $rows): void { ?>
<div class="card-ui table-wrap"><table class="table align-middle"><thead><tr><th>Batch</th><th>Type / course</th><th>Branch</th><th>Dates</th><th>Time</th><th>Teacher</th><th>Students</th><th>Fee</th></tr></thead><tbody>
<?php foreach($rows as $r):?><tr>
<td><a href="batch_view.php?id=<?=$r['id']?>"><?=e($r['name'])?> (<?=$r['batch_year']?>)</a></td>
<td><?=e($r['tname'])?><div class="lbl"><?=e($r['cname'])?></div></td><td><?=e($r['brname'])?></td>
<td><?=e($r['start_date'])?> → <?=e($r['end_date'])?><div class="lbl"><?=$r['duration_months']?> months</div></td>
<td><?=e(substr($r['start_time'],0,5))?>–<?=e(substr($r['end_time'],0,5))?><div class="lbl"><?=e($r['class_days'])?></div></td>
<td><?=e($r['tchname']??'—')?></td>
<td><?=$r['cnt']?><?=$r['max_students']?' / '.$r['max_students']:''?></td><td><?=number_format((float)$r['fee'],2)?></td></tr>
<?php endforeach; if(!$rows):?><tr><td colspan="8" class="text-center lbl py-4">Nothing here yet.</td></tr><?php endif;?></tbody></table></div>
<?php }
?>
<?php if($msg):?><div class="alert alert-info rounded-4"><?=e($msg)?></div><?php endif;?>
<div class="d-flex gap-2 mb-3 align-items-center">
 <ul class="nav nav-pills flex-nowrap overflow-auto me-auto">
  <li class="nav-item"><a class="nav-link <?=$tab==='active'?'active':''?>" href="courses.php">Active</a></li>
  <li class="nav-item"><a class="nav-link <?=$tab==='completed'?'active':''?>" href="courses.php?tab=completed">Completed</a></li>
  <?php if(can('manage_batches')):?><li class="nav-item"><a class="nav-link <?=$tab==='catalog'?'active':''?>" href="courses.php?tab=catalog">Course list</a></li><?php endif;?>
 </ul>
 <?php if(can('manage_batches')):?><button class="btn-ui text-nowrap" data-bs-toggle="modal" data-bs-target="#batchModal"><i class="bi bi-plus-lg"></i> <span class="d-none d-md-inline">New batch / class</span></button><?php endif;?>
</div>
<?php if($tab==='catalog'):
 $cq1=gstr('q'); $cty=gint('type');
 $cl=array_values(array_filter($courses,fn($c)=>(!$cty||(int)$c['type_id']===$cty)&&($cq1===''||stripos($c['name'].' '.$c['code'],$cq1)!==false)));
 $total=count($cl); $per=pg_per(); [$pg,$pages,$off]=pg_page($total,$per); $cslice=array_slice($cl,$off,$per);?>
<div class="card-ui mb-3"><form method="post" class="row g-2"><input type="hidden" name="csrf" value="<?=csrf()?>"><input type="hidden" name="act" value="new_course">
 <div class="col-md-3"><select class="form-select" name="type_id" required><?php foreach($types as $t):?><option value="<?=$t['id']?>"><?=e($t['name'])?></option><?php endforeach;?></select></div>
 <div class="col-md-2"><input class="form-control" name="code" placeholder="Short code" maxlength="10" required></div>
 <div class="col-md-5"><input class="form-control" name="name" placeholder="Course / grade name" required></div>
 <div class="col-md-2"><button class="btn-ui w-100">Add</button></div></form></div>
<?php f_open(['tab']); f_input('q','Search course or code'); f_select('type',array_column($types,'name','id'),'Any type'); f_close(['tab']);?>
<div class="card-ui table-wrap"><table class="table"><thead><tr><th>Type</th><th>Code</th><th>Name</th></tr></thead><tbody>
<?php foreach($cslice as $c):?><tr><td><?=e($c['tname'])?></td><td><?=e($c['code'])?></td><td><?=e($c['name'])?></td></tr><?php endforeach; if(!$cslice):?><tr><td colspan="3" class="lbl text-center py-3">No courses found.</td></tr><?php endif;?></tbody></table></div>
<?php pg_render($total,$per);?>
<?php else:
 $st1=$tab==='completed'?'completed':'active';
 $fq=gstr('q'); $fty=gint('type'); $fco=gint('course'); $ftc=gint('teacher'); $fbr=$s===null?gint('branch'):0; $d1=gdate('from'); $d2=gdate('to'); if($d1&&$d2&&$d1>$d2)[$d1,$d2]=[$d2,$d1];
 $bx=$bw; $bp=[];
 if($fq!==''){$bx.=' AND (b.name LIKE ? OR c.name LIKE ?)';array_push($bp,"%$fq%","%$fq%");}
 if($fty){$bx.=' AND c.type_id=?';$bp[]=$fty;} if($fco){$bx.=' AND b.course_id=?';$bp[]=$fco;} if($ftc){$bx.=' AND b.teacher_id=?';$bp[]=$ftc;}
 if($fbr){$bx.=' AND b.branch_id=?';$bp[]=$fbr;} if($d1){$bx.=' AND b.start_date>=?';$bp[]=$d1;} if($d2){$bx.=' AND b.start_date<=?';$bp[]=$d2;}
 $cq=db()->prepare("SELECT COUNT(*) FROM batches b JOIN courses c ON c.id=b.course_id WHERE b.status='$st1'$bx"); $cq->execute($bp); $total=(int)$cq->fetchColumn();
 $per=pg_per(); [$pg,$pages,$off]=pg_page($total,$per);
 f_open(['tab']); f_input('q','Search batch or course','text','col-12 col-md-4');
 f_select('type',array_column($types,'name','id'),'Any type','col-6 col-md-2'); f_select('course',array_column($courses,'name','id'),'Any course','col-6 col-md-3');
 if(!is_teacher()) f_select('teacher',array_column($teachers,'full_name','id'),'Any teacher','col-6 col-md-3');
 if($s===null) f_select('branch',array_column($branches,'name','id'),'All branches','col-6 col-md-3');
 f_input('from','Starts from','date','col-6 col-md-3'); f_input('to','Starts to','date','col-6 col-md-3'); f_close(['tab']);
 batch_table(batch_rows($st1,$bx,$bp,"LIMIT $per OFFSET $off")); pg_render($total,$per);
endif;?>

<?php if(can('manage_batches')):?>
<div class="modal fade" id="batchModal" tabindex="-1"><div class="modal-dialog modal-lg modal-fullscreen-md-down modal-dialog-scrollable"><div class="modal-content" style="border-radius:24px">
<form method="post"><input type="hidden" name="csrf" value="<?=csrf()?>"><input type="hidden" name="act" value="new_batch">
<div class="modal-header"><h5 class="modal-title">New batch / class</h5><button type="button" class="btn-close" data-bs-dismiss="modal"></button></div>
<div class="modal-body"><?php foreach($errs as $m):?><div class="alert alert-danger rounded-4 py-2"><?=e($m)?></div><?php endforeach;?>
<div class="row g-2">
 <?php if(is_owner()):?><div class="col-md-6"><select class="form-select" name="branch_id" id="bBranch" required><option value="">Branch</option><?php foreach($branches as $b):?><option value="<?=$b['id']?>" <?=($old['branch_id']??'')==$b['id']?'selected':''?>><?=e($b['name'])?></option><?php endforeach;?></select></div><?php endif;?>
 <div class="col-md-6"><select class="form-select" id="bType"><option value="">Course / class type</option><?php foreach($types as $t):?><option value="<?=$t['id']?>"><?=e($t['name'])?></option><?php endforeach;?></select></div>
 <div class="col-md-6"><select class="form-select" name="course_id" id="bCourse" required><option value="">Course / grade</option></select></div>
 <div class="col-md-3"><input class="form-control" type="number" name="batch_year" placeholder="Batch year" value="<?=$old['batch_year']??date('Y')?>" required></div>
 <div class="col-md-3"><input class="form-control" name="name" placeholder="Batch / class name" value="<?=$o('name')?>" required></div>
 <div class="col-md-4"><label class="lbl">Start date</label><input class="form-control" type="date" name="start_date" id="sd" value="<?=$o('start_date')?>" required></div>
 <div class="col-md-4"><label class="lbl">Duration (months)</label><input class="form-control" type="number" min="1" max="60" name="duration" id="du" value="<?=$o('duration')?>" required></div>
 <div class="col-md-4"><label class="lbl">End date (automatic)</label><input class="form-control" id="ed" readonly></div>
 <div class="col-md-6"><label class="lbl">Start time</label><input class="form-control" type="time" name="start_time" value="<?=$o('start_time')?>" required></div>
 <div class="col-md-6"><label class="lbl">End time</label><input class="form-control" type="time" name="end_time" value="<?=$o('end_time')?>" required></div>
 <div class="col-md-6"><select class="form-select" name="teacher_id" id="bTeacher"><option value="">Teacher (optional)</option></select></div>
 <div class="col-12"><label class="lbl d-block">Class days</label>
  <?php foreach(['Mon','Tue','Wed','Thu','Fri','Sat','Sun'] as $d):?><label class="me-3"><input type="checkbox" name="days[]" value="<?=$d?>" <?=in_array($d,$old['days']??[])?'checked':''?>> <?=$d?></label><?php endforeach;?></div>
 <div class="col-md-4"><label class="lbl">Course / class fee</label><input class="form-control" type="number" step="0.01" min="0" name="fee" value="<?=$o('fee')?>" required></div>
 <div class="col-md-4"><label class="lbl">Payment plan</label><select class="form-select" name="fee_plan"><option value="installments_3">3 installments</option><option value="monthly">Monthly</option><option value="custom">Custom</option></select></div>
 <div class="col-md-4"><label class="lbl">Max students (optional)</label><input class="form-control" type="number" min="1" name="max_students" value="<?=$o('max_students')?>"></div>
</div></div>
<div class="modal-footer"><button class="btn-ui">Create</button></div></form></div></div></div>
<?php endif;?>
<?php ob_start(); ?>
<script>
const COURSES=<?=json_encode($courses)?>,TEACHERS=<?=json_encode($teachers)?>,OWN=<?=json_encode($s)?>;const $=id=>document.getElementById(id);
if($('bType')){
 function rc(){const t=$('bType').value,s=$('bCourse');const v=s.value;s.innerHTML='<option value="">Course / grade</option>';
  COURSES.filter(c=>!t||c.type_id==t).forEach(c=>{const o=document.createElement('option');o.value=c.id;o.textContent=(t?'':c.tname+' – ')+c.name;s.appendChild(o);});s.value=v;}
 $('bType').onchange=rc;rc();
 function rt(){const b=$('bBranch')?$('bBranch').value:OWN,s=$('bTeacher'),v=s.value;s.innerHTML='<option value="">Teacher (optional)</option>';
  TEACHERS.filter(t=>!b||t.branch_id==b).forEach(t=>{const o=document.createElement('option');o.value=t.id;o.textContent=t.full_name;s.appendChild(o);});s.value=v;}
 if($('bBranch'))$('bBranch').onchange=rt;rt();
 function ed(){const s=$('sd').value,m=parseInt($('du').value);if(!s||!m){$('ed').value='';return;}
  const d=new Date(s+'T00:00:00'),day=d.getDate();d.setMonth(d.getMonth()+m);if(d.getDate()<day)d.setDate(0);
  $('ed').value=d.getFullYear()+'-'+String(d.getMonth()+1).padStart(2,'0')+'-'+String(d.getDate()).padStart(2,'0');}
 $('sd').onchange=ed;$('du').oninput=ed;ed();
 <?php if($open):?>new bootstrap.Modal($('batchModal')).show();<?php endif;?>
}
</script>
<?php $pageScript=ob_get_clean(); require __DIR__.'/includes/footer.php';
