<?php
require_once __DIR__.'/includes/bootstrap.php';
require_menu('students'); refresh_batches();
$s = branch_scope(); $errs = []; $old = []; $openModal = false;
const NIC_RE = '/^(\d{9}[VvXx]|\d{12})$/';

if ($_SERVER['REQUEST_METHOD']==='POST' && ($_POST['act']??'')==='register') {
  csrf_check();
  if (!can('register_student')) { http_response_code(403); exit('Access denied.'); }
  $old = $_POST; $openModal = true;
  $first=trim($_POST['first_name']??''); $last=trim($_POST['last_name']??'');
  $full=trim($_POST['full_name']??'') ?: trim("$first $last");
  $dob=$_POST['dob']??''; $addr=trim($_POST['address']??''); $phone=trim($_POST['phone']??''); $email=trim($_POST['email']??'');
  $nic=strtoupper(trim($_POST['nic']??'')); $gn=trim($_POST['g_name']??''); $gr=$_POST['g_rel']??''; $gp=trim($_POST['g_phone']??'');
  $fp=trim($_POST['fingerprint_id']??''); $batchId=(int)($_POST['batch_id']??0);
  $branchId = is_owner() ? (int)($_POST['branch_id']??0) : (int)$s;
  $age=null;
  if ($first===''||$last==='') $errs[]='First name and last name are required.';
  if ($dob==='' || !strtotime($dob)) $errs[]='A valid date of birth is required.';
  else { $age=(new DateTime($dob))->diff(new DateTime())->y; if (new DateTime($dob) > new DateTime()) $errs[]='Date of birth cannot be in the future.'; }
  if ($phone==='') $errs[]='Contact number is required.';
  if ($email!=='' && !filter_var($email,FILTER_VALIDATE_EMAIL)) $errs[]='Email address is not valid.';
  if ($age!==null) {
    if ($age>=18) { if (!preg_match(NIC_RE,$nic)) $errs[]='NIC number is required for students aged 18 or over (9 digits + V/X, or 12 digits).'; }
    else { if ($gn===''||$gp===''||!in_array($gr,['Mother','Father','Guardian'],true)) $errs[]='Guardian name, relationship and contact number are required for students under 18.'; }
  }
  $b=null;
  if (!$errs) {
    $st=db()->prepare("SELECT b.*,c.code ccode,t.code tcode FROM batches b JOIN courses c ON c.id=b.course_id JOIN course_types t ON t.id=c.type_id WHERE b.id=? AND b.branch_id=? AND b.status='active'");
    $st->execute([$batchId,$branchId]); $b=$st->fetch();
    if (!$b) $errs[]='Please choose a valid active course/class and batch for this branch.';
    elseif ($b['max_students']) {
      $c=db()->prepare('SELECT COUNT(*) FROM enrolments WHERE batch_id=?'); $c->execute([$batchId]);
      if ($c->fetchColumn() >= $b['max_students']) $errs[]='This class is full (maximum '.$b['max_students'].' students).';
    }
  }
  if (!$errs) {
    $pdo=db();
    try {
      $pdo->beginTransaction();
      $br=$pdo->prepare('SELECT code FROM branches WHERE id=?'); $br->execute([$branchId]); $bcode=$br->fetchColumn();
      $prefix=$bcode.'-'.$b['tcode'].'-'.$b['ccode'].'-'.$b['batch_year'];
      $q=$pdo->prepare('SELECT COALESCE(MAX(seq_no),0)+1 FROM students WHERE id_prefix=? FOR UPDATE'); $q->execute([$prefix]);
      $seq=(int)$q->fetchColumn(); $code=$prefix.'-'.str_pad($seq,3,'0',STR_PAD_LEFT);
      $ins=$pdo->prepare('INSERT INTO students (student_code,id_prefix,seq_no,branch_id,batch_id,first_name,last_name,full_name,date_of_birth,address,phone,email,nic,guardian_name,guardian_relation,guardian_phone,fingerprint_id,registered_by) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)');
      $minor = $age<18;
      $ins->execute([$code,$prefix,$seq,$branchId,$batchId,$first,$last,$full,$dob,$addr,$phone,$email?:null,
        $minor?null:$nic, $minor?$gn:null, $minor?$gr:null, $minor?$gp:null, $fp?:null, $_SESSION['user']['id']]);
      $sid=$pdo->lastInsertId();
      $pdo->prepare('INSERT INTO enrolments (student_id,batch_id,enrolled_on) VALUES (?,?,CURDATE())')->execute([$sid,$batchId]);
      $pdo->commit();
      require_once __DIR__.'/includes/payments_lib.php'; ensure_account_for((int)$sid,(int)$batchId);
      log_activity('student_register',"$code $full");
      $cred=null;
      if (!empty($_POST['create_login']) && $email!=='') { require_once __DIR__.'/includes/student_lib.php'; $cr=create_student_login(['id'=>$sid,'branch_id'=>$branchId,'full_name'=>$full,'phone'=>$phone],$email); $cred=$cr[0]?['ok',$email,$cr[1]]:['err',$cr[1]]; }
      flash_set('reg',[$code,$full,$cred]); header('Location: students.php'); exit;
    } catch (PDOException $x) {
      if ($pdo->inTransaction()) $pdo->rollBack();
      $errs[] = $x->getCode()==='23000' ? 'That fingerprint ID is already used in this branch.' : 'Could not save the student. Please try again.';
    }
  }
}

$pageKey='students'; require __DIR__.'/includes/header.php';
$q=gstr('q'); $w=[]; $p=[];
if ($s!==null) { $w[]='s.branch_id=?'; $p[]=$s; }
if (is_teacher()) { $tid=(int)$_SESSION['user']['id']; $w[]='s.id IN (SELECT e.student_id FROM enrolments e JOIN batches tb ON tb.id=e.batch_id WHERE (tb.teacher_id='.$tid.' OR EXISTS (SELECT 1 FROM substitutions sbx WHERE sbx.batch_id=tb.id AND sbx.substitute_id='.$tid.' AND sbx.class_date>=DATE_SUB(CURDATE(),INTERVAL 1 DAY))))'; }
if ($q!=='') { $w[]='(s.full_name LIKE ? OR s.student_code LIKE ? OR s.phone LIKE ?)'; $l="%$q%"; array_push($p,$l,$l,$l); }
$fs=genum('status',['active','inactive']); $ft=gint('type'); $fbt=gint('batch'); $fbr=$s===null?gint('branch'):0; $d1=gdate('from'); $d2=gdate('to'); if($d1&&$d2&&$d1>$d2)[$d1,$d2]=[$d2,$d1];
if($fs){$w[]='s.status=?';$p[]=$fs;} if($ft){$w[]='c.type_id=?';$p[]=$ft;} if($fbt){$w[]='s.batch_id=?';$p[]=$fbt;} if($fbr){$w[]='s.branch_id=?';$p[]=$fbr;}
if($d1){$w[]='DATE(s.registered_at)>=?';$p[]=$d1;} if($d2){$w[]='DATE(s.registered_at)<=?';$p[]=$d2;}
$fromSql=' FROM students s JOIN batches b ON b.id=s.batch_id JOIN courses c ON c.id=b.course_id'.($w?' WHERE '.implode(' AND ',$w):'');
$cq=db()->prepare('SELECT COUNT(*)'.$fromSql); $cq->execute($p); $total=(int)$cq->fetchColumn();
$per=pg_per(); [$pg,$pages,$off]=pg_page($total,$per);
$st=db()->prepare('SELECT s.*,c.name cname,b.name bname'.$fromSql.' ORDER BY s.id DESC LIMIT '.$per.' OFFSET '.$off); $st->execute($p); $rows=$st->fetchAll();
$types=db()->query('SELECT id,name FROM course_types ORDER BY id')->fetchAll();
$courses=db()->query('SELECT id,type_id,name FROM courses ORDER BY type_id,id')->fetchAll();
$bw = $s!==null ? ' AND b.branch_id='.(int)$s : '';
$batches=db()->query("SELECT b.id,b.branch_id,b.course_id,c.type_id,CONCAT(b.name,' (',b.batch_year,')') label FROM batches b JOIN courses c ON c.id=b.course_id WHERE b.status='active'$bw ORDER BY b.batch_year DESC,b.name")->fetchAll();
$branches=db()->query("SELECT id,name FROM branches WHERE status='active' ORDER BY name")->fetchAll();
$fbatches=db()->query("SELECT b.id,CONCAT(b.name,' (',b.batch_year,')') label FROM batches b WHERE 1=1$bw ORDER BY b.batch_year DESC,b.name LIMIT 300")->fetchAll();
$reg=flash_get('reg'); $o=fn($k)=>e($old[$k]??'');
?>
<div class="d-flex justify-content-between align-items-center mb-2"><div class="lbl"><?=$total?> student<?=$total===1?'':'s'?></div>
<?php if(can('register_student')):?><button type="button" class="btn-ui text-nowrap" data-bs-toggle="modal" data-bs-target="#regModal"><i class="bi bi-person-plus-fill"></i> Register</button><?php endif;?></div>
<?php f_open(); f_input('q','Search name, ID or phone','text','col-12 col-md-4');
 f_select('status',['active'=>'Active','inactive'=>'Inactive'],'Any status','col-6 col-md-2');
 f_select('type',array_column($types,'name','id'),'Any type','col-6 col-md-3');
 f_select('batch',array_column($fbatches,'label','id'),'Any class / batch','col-6 col-md-3');
 if($s===null) f_select('branch',array_column($branches,'name','id'),'All branches','col-6 col-md-3');
 f_input('from','Registered from','date','col-6 col-md-3'); f_input('to','Registered to','date','col-6 col-md-3'); f_close();?>
<div class="card-ui table-wrap"><table class="table align-middle">
<thead><tr><th>Student ID</th><th>Name</th><th>Course / Class</th><th>Batch</th><th>Phone</th><th>Status</th></tr></thead><tbody>
<?php foreach($rows as $r):?>
<tr><td><a href="student_view.php?id=<?=$r['id']?>"><?=e($r['student_code'])?></a></td><td><?=e($r['full_name'])?></td><td><?=e($r['cname'])?></td><td><?=e($r['bname'])?></td><td><?=e($r['phone'])?></td>
<td><span class="badge rounded-pill text-bg-<?=$r['status']==='active'?'success':'secondary'?>"><?=e($r['status'])?></span></td></tr>
<?php endforeach; if(!$rows):?><tr><td colspan="6" class="text-center lbl py-4">No students found.</td></tr><?php endif;?>
</tbody></table></div>
<?php pg_render($total,$per);?>

<?php if(can('register_student')):?>
<div class="modal fade" id="regModal" tabindex="-1"><div class="modal-dialog modal-lg modal-fullscreen-md-down modal-dialog-scrollable"><div class="modal-content" style="border-radius:24px">
<form method="post" id="regForm"><input type="hidden" name="csrf" value="<?=csrf()?>"><input type="hidden" name="act" value="register">
<div class="modal-header"><h5 class="modal-title">Register student</h5><button type="button" class="btn-close" data-bs-dismiss="modal"></button></div>
<div class="modal-body">
<?php foreach($errs as $m):?><div class="alert alert-danger rounded-4 py-2"><?=e($m)?></div><?php endforeach;?>
<div class="lbl fw-bold mb-2">Course / class</div>
<div class="row g-2 mb-3">
 <?php if(is_owner()):?><div class="col-md-6"><select class="form-select" name="branch_id" id="fBranch" required><option value="">Branch</option>
  <?php foreach($branches as $b):?><option value="<?=$b['id']?>" <?=($old['branch_id']??'')==$b['id']?'selected':''?>><?=e($b['name'])?></option><?php endforeach;?></select></div><?php endif;?>
 <div class="col-md-6"><select class="form-select" id="fType" required><option value="">Course / class type</option>
  <?php foreach($types as $t):?><option value="<?=$t['id']?>"><?=e($t['name'])?></option><?php endforeach;?></select></div>
 <div class="col-md-6"><select class="form-select" id="fCourse" required><option value="">Course / grade</option></select></div>
 <div class="col-md-6"><select class="form-select" name="batch_id" id="fBatch" required><option value="">Batch / class</option></select></div>
</div>
<div class="lbl fw-bold mb-2">Personal details</div>
<div class="row g-2 mb-3">
 <div class="col-md-6"><input class="form-control" name="first_name" placeholder="First name *" value="<?=$o('first_name')?>" required></div>
 <div class="col-md-6"><input class="form-control" name="last_name" placeholder="Last name *" value="<?=$o('last_name')?>" required></div>
 <div class="col-12"><input class="form-control" name="full_name" placeholder="Full name (optional)" value="<?=$o('full_name')?>"></div>
 <div class="col-md-6"><label class="lbl">Date of birth *</label><input class="form-control" type="date" name="dob" id="dob" value="<?=$o('dob')?>" required></div>
 <div class="col-md-6"><label class="lbl">Contact no *</label><input class="form-control" name="phone" inputmode="tel" value="<?=$o('phone')?>" required></div>
 <div class="col-md-6"><input class="form-control" type="email" name="email" placeholder="Email" value="<?=$o('email')?>"></div>
 <div class="col-md-6"><input class="form-control" name="fingerprint_id" placeholder="Fingerprint machine ID" value="<?=$o('fingerprint_id')?>"></div>
 <div class="col-12"><input class="form-control" name="address" placeholder="Address" value="<?=$o('address')?>"></div>
 <div class="col-12"><label><input type="checkbox" name="create_login" value="1" <?=!empty($old['create_login'])?'checked':''?>> Create a student portal login (uses the email above)</label></div>
</div>
<div id="adultBox" class="d-none"><div class="lbl fw-bold mb-2">Age 18 or over</div>
 <input class="form-control mb-3" name="nic" placeholder="NIC number *" value="<?=$o('nic')?>"></div>
<div id="minorBox" class="d-none"><div class="lbl fw-bold mb-2">Guardian details (under 18)</div><div class="row g-2">
 <div class="col-md-4"><input class="form-control" name="g_name" placeholder="Guardian name *" value="<?=$o('g_name')?>"></div>
 <div class="col-md-4"><select class="form-select" name="g_rel"><option value="">Relationship *</option>
  <?php foreach(['Mother','Father','Guardian'] as $r):?><option <?=($old['g_rel']??'')===$r?'selected':''?>><?=$r?></option><?php endforeach;?></select></div>
 <div class="col-md-4"><input class="form-control" name="g_phone" placeholder="Guardian contact *" value="<?=$o('g_phone')?>"></div></div></div>
<div class="lbl mt-2" id="ageNote"></div>
</div>
<div class="modal-footer"><button class="btn-ui">Register student</button></div>
</form></div></div></div>
<?php endif;?>

<?php if($reg):?>
<div class="modal fade" id="okModal" tabindex="-1"><div class="modal-dialog modal-dialog-centered"><div class="modal-content text-center p-3" style="border-radius:24px">
<div class="modal-body"><i class="bi bi-check-circle-fill text-success" style="font-size:3.5rem"></i>
<h5 class="mt-2">Registered successfully</h5>
<div class="lbl">Student ID</div><div class="stat" style="font-size:1.4rem"><?=e($reg[0])?></div>
<div class="lbl mt-2">Student name</div><div class="fw-bold"><?=e($reg[1])?></div>
<?php if(!empty($reg[2])):?><?php if($reg[2][0]==='ok'):?><div class="alert alert-success rounded-4 mt-3 mb-0 text-start"><b>Portal login created</b><div>Email: <?=e($reg[2][1])?></div><div>Temporary password: <b><?=e($reg[2][2])?></b></div><div class="lbl">Shown once only. The student must change it at first sign-in.</div></div>
<?php else:?><div class="alert alert-warning rounded-4 mt-3 mb-0 text-start">Login not created: <?=e($reg[2][1])?></div><?php endif;?><?php endif;?>
<button class="btn-ui mt-3" data-bs-dismiss="modal">OK</button></div></div></div></div>
<?php endif;?>
<?php ob_start(); ?>
<script>
const COURSES=<?=json_encode($courses)?>, BATCHES=<?=json_encode($batches)?>, OWN_BRANCH=<?=json_encode($s)?>;
const $=id=>document.getElementById(id);
function fill(sel,items,ph,lab,keep){const v=keep?sel.value:'';sel.innerHTML='';const o=document.createElement('option');o.value='';o.textContent=ph;sel.appendChild(o);
 items.forEach(i=>{const x=document.createElement('option');x.value=i.id;x.textContent=lab(i);sel.appendChild(x);});sel.value=v;}
function refCourses(){const t=$('fType').value;fill($('fCourse'),COURSES.filter(c=>!t||c.type_id==t),'Course / grade',c=>c.name,true);refBatches();}
function refBatches(){const t=$('fType').value,c=$('fCourse').value,b=$('fBranch')?$('fBranch').value:OWN_BRANCH;
 fill($('fBatch'),BATCHES.filter(x=>(!t||x.type_id==t)&&(!c||x.course_id==c)&&(!b||x.branch_id==b)),'Batch / class',x=>x.label,true);}
if($('fType')){$('fType').onchange=refCourses;$('fCourse').onchange=refBatches;if($('fBranch'))$('fBranch').onchange=refBatches;refCourses();
 const keep=<?=json_encode($old['batch_id']??'')?>;if(keep)$('fBatch').value=keep;}
function ageCheck(){const v=$('dob').value;if(!v){$('adultBox').classList.add('d-none');$('minorBox').classList.add('d-none');$('ageNote').textContent='';return;}
 const d=new Date(v),n=new Date();let a=n.getFullYear()-d.getFullYear();if(n<new Date(n.getFullYear(),d.getMonth(),d.getDate()))a--;
 $('adultBox').classList.toggle('d-none',a<18);$('minorBox').classList.toggle('d-none',a>=18);$('ageNote').textContent='Age: '+a;}
if($('dob')){$('dob').onchange=ageCheck;ageCheck();}
<?php if($openModal):?>new bootstrap.Modal($('regModal')).show();<?php endif;?>
<?php if($reg):?>new bootstrap.Modal($('okModal')).show();<?php endif;?>
</script>
<?php $pageScript=ob_get_clean(); require __DIR__.'/includes/footer.php';
