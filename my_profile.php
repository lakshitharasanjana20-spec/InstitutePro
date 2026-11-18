<?php
require_once __DIR__.'/includes/bootstrap.php';
require_once __DIR__.'/includes/student_lib.php';
require_once __DIR__.'/includes/people_lib.php';
stu_guard('sprofile'); $sid=stu_id(); $msg='';
if ($_SERVER['REQUEST_METHOD']==='POST' && ($_POST['act']??'')==='photo') {
  csrf_check(); $cur=stu_row(); [$np,$er]=save_photo($_FILES['photo']??null,$cur['photo']??null);
  if($np){ db()->prepare('UPDATE students SET photo=? WHERE id=?')->execute([$np,$sid]); $msg='Photo updated.'; } else $msg=$er;
  $_SERVER['REQUEST_METHOD']='GET';
}
if ($_SERVER['REQUEST_METHOD']==='POST') {
  csrf_check(); $ph=trim($_POST['phone']??''); $ad=mb_substr(trim($_POST['address']??''),0,255);
  if ($ph===''||strlen(preg_replace('/\D/','',$ph))<9) $msg='Enter a valid contact number.';
  else { db()->prepare('UPDATE students SET phone=?,address=? WHERE id=?')->execute([$ph,$ad,$sid]); db()->prepare('UPDATE users SET phone=? WHERE id=?')->execute([$ph,$_SESSION['user']['id']]); log_activity('student_profile_update','Contact details updated'); $msg='Your details were saved.'; }
}
$pageKey='sprofile'; require __DIR__.'/includes/header.php';
$st=stu_row(); $bs=stu_batches(); $age=(new DateTime($st['date_of_birth']))->diff(new DateTime())->y;
?>
<?php if($msg):?><div class="alert alert-info rounded-4"><?=e($msg)?></div><?php endif;?>
<div class="card-ui mb-3 d-flex gap-3 align-items-center"><?=avatar('s',$sid,$st['photo']??null,$st['full_name'],72)?>
<div><div class="stat" style="font-size:1.3rem"><?=e($st['full_name'])?></div><div class="lbl"><?=e($st['student_code'])?> · <?=e($st['brname'])?></div><span class="badge rounded-pill text-bg-<?=$st['status']==='active'?'success':'secondary'?>"><?=e($st['status'])?></span></div></div>
<form method="post" enctype="multipart/form-data" class="card-ui mb-3 d-flex flex-wrap gap-2 align-items-center"><input type="hidden" name="csrf" value="<?=csrf()?>"><input type="hidden" name="act" value="photo"><b>Profile photo</b><input class="form-control" style="max-width:300px" type="file" name="photo" accept="image/jpeg,image/png,image/webp" required><button class="btn btn-light rounded-pill">Upload</button></form>
<div class="card-ui mb-3"><div class="fw-bold mb-2">Personal details</div><div class="row g-3">
<?php foreach(['Date of birth'=>$st['date_of_birth'].' (age '.$age.')','Login email'=>$_SESSION['user']['email'],'NIC'=>$st['nic'],'Guardian'=>$st['guardian_name']?$st['guardian_name'].' ('.$st['guardian_relation'].')':null,'Guardian contact'=>$st['guardian_phone']] as $l=>$v): if(!$v) continue;?><div class="col-md-6"><div class="lbl"><?=e($l)?></div><div><?=e($v)?></div></div><?php endforeach;?></div>
<div class="lbl mt-2">To change your name, date of birth or email, please ask the institute office.</div></div>
<form method="post" class="card-ui mb-3 row g-2"><input type="hidden" name="csrf" value="<?=csrf()?>"><div class="fw-bold">Contact details</div>
<div class="col-md-5"><label class="lbl">Contact number</label><input class="form-control" name="phone" value="<?=e($st['phone'])?>" inputmode="tel" required></div><div class="col-md-7"><label class="lbl">Address</label><input class="form-control" name="address" maxlength="255" value="<?=e($st['address'])?>"></div><div class="col-12"><button class="btn-ui">Save</button></div></form>
<div class="card-ui mb-3"><div class="fw-bold mb-2">My classes</div><?php foreach($bs as $b):?><a href="my_class.php?id=<?=$b['id']?>" class="d-block text-decoration-none text-reset border-top py-2"><?=e($b['cname'])?> · <?=e($b['name'])?> (<?=$b['batch_year']?>) <span class="badge rounded-pill text-bg-<?=$b['status']==='active'?'success':'secondary'?>"><?=e($b['status'])?></span></a><?php endforeach;?></div>
<div class="card-ui d-flex flex-wrap gap-2 justify-content-between align-items-center"><div><b>Need help?</b><div class="lbl">Contact <?=e($st['brname'])?><?=$st['brphone']?' on '.e($st['brphone']):''?>.</div></div><div class="d-flex gap-2"><a class="btn btn-light rounded-pill" href="change_password.php">Change password</a><a class="btn btn-light rounded-pill" href="logout.php">Sign out</a></div></div>
<?php require __DIR__.'/includes/footer.php';
