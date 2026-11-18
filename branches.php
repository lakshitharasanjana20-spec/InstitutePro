<?php
require_once __DIR__.'/includes/bootstrap.php';
require_menu('branches');
if (!is_owner()) { http_response_code(403); exit('Access denied.'); }
if ($_SERVER['REQUEST_METHOD']==='POST') {
  csrf_check(); $act=$_POST['act']??''; $name=trim($_POST['name']??''); $addr=trim($_POST['address']??''); $ph=trim($_POST['phone']??'');
  if ($act==='add') {
    $code=strtoupper(preg_replace('/[^A-Za-z0-9]/','',$_POST['code']??''));
    if ($name===''||strlen($code)<2||strlen($code)>6) flash_set('msg','Name and a branch code of 2–6 letters/numbers are required.');
    else { try { db()->prepare('INSERT INTO branches (code,name,address,phone) VALUES (?,?,?,?)')->execute([$code,$name,$addr,$ph]); log_activity('branch_create',"$code $name"); flash_set('msg','Branch added.'); }
      catch (PDOException $x) { flash_set('msg','That branch code already exists.'); } }
  } elseif ($act==='edit') {
    $id=(int)$_POST['id']; $status=($_POST['status']??'')==='inactive'?'inactive':'active';
    if ($name!=='') { db()->prepare('UPDATE branches SET name=?,address=?,phone=?,status=? WHERE id=?')->execute([$name,$addr,$ph,$status,$id]); log_activity('branch_update',"$name ($status)"); flash_set('msg','Branch updated.'); }
  }
  header('Location: branches.php'); exit;
}
$pageKey='branches'; require __DIR__.'/includes/header.php';
$msg=flash_get('msg');
$rows=db()->query("SELECT b.*,(SELECT COUNT(*) FROM students s WHERE s.branch_id=b.id) sc,(SELECT COUNT(*) FROM users u WHERE u.branch_id=b.id) uc,(SELECT COUNT(*) FROM batches x WHERE x.branch_id=b.id AND x.status='active') bc FROM branches b ORDER BY b.name")->fetchAll();
?>
<?php if($msg):?><div class="alert alert-info rounded-4"><?=e($msg)?></div><?php endif;?>
<div class="mb-3 text-end"><button class="btn-ui" data-bs-toggle="modal" data-bs-target="#addB"><i class="bi bi-plus-lg"></i> Add branch</button></div>
<div class="row g-3">
<?php foreach($rows as $r):?><div class="col-md-6 col-xl-4"><div class="card-ui h-100">
 <div class="d-flex justify-content-between"><div class="fw-bold"><?=e($r['name'])?></div><span class="badge rounded-pill text-bg-<?=$r['status']==='active'?'success':'secondary'?>"><?=e($r['status'])?></span></div>
 <div class="lbl">Code: <?=e($r['code'])?></div><div class="mt-2"><?=e($r['address']?:'—')?></div><div class="lbl"><?=e($r['phone'])?></div>
 <div class="d-flex gap-3 mt-3 lbl"><span><?=$r['sc']?> students</span><span><?=$r['uc']?> staff</span><span><?=$r['bc']?> active batches</span></div>
 <button class="btn btn-sm btn-light rounded-pill mt-3" data-bs-toggle="modal" data-bs-target="#editB" onclick='editB(<?=json_encode(["id"=>$r["id"],"name"=>$r["name"],"address"=>$r["address"],"phone"=>$r["phone"],"status"=>$r["status"]],JSON_HEX_APOS|JSON_HEX_QUOT)?>)'>Edit</button>
</div></div><?php endforeach;?></div>
<div class="modal fade" id="addB" tabindex="-1"><div class="modal-dialog modal-dialog-centered"><div class="modal-content" style="border-radius:24px"><form method="post">
<input type="hidden" name="csrf" value="<?=csrf()?>"><input type="hidden" name="act" value="add">
<div class="modal-header"><h5 class="modal-title">Add branch</h5><button type="button" class="btn-close" data-bs-dismiss="modal"></button></div>
<div class="modal-body d-grid gap-2"><input class="form-control" name="name" placeholder="Branch name *" required>
<input class="form-control" name="code" placeholder="Branch code * (2–6 letters, used in student IDs, cannot change later)" maxlength="6" required>
<input class="form-control" name="address" placeholder="Address"><input class="form-control" name="phone" placeholder="Phone"></div>
<div class="modal-footer"><button class="btn-ui">Add</button></div></form></div></div></div>
<div class="modal fade" id="editB" tabindex="-1"><div class="modal-dialog modal-dialog-centered"><div class="modal-content" style="border-radius:24px"><form method="post">
<input type="hidden" name="csrf" value="<?=csrf()?>"><input type="hidden" name="act" value="edit"><input type="hidden" name="id" id="eId">
<div class="modal-header"><h5 class="modal-title">Edit branch</h5><button type="button" class="btn-close" data-bs-dismiss="modal"></button></div>
<div class="modal-body d-grid gap-2"><input class="form-control" name="name" id="eName" required><input class="form-control" name="address" id="eAddr" placeholder="Address"><input class="form-control" name="phone" id="ePhone" placeholder="Phone">
<select class="form-select" name="status" id="eStatus"><option value="active">Active</option><option value="inactive">Inactive</option></select></div>
<div class="modal-footer"><button class="btn-ui">Save</button></div></form></div></div></div>
<?php ob_start();?><script>function editB(b){eId.value=b.id;eName.value=b.name;eAddr.value=b.address||'';ePhone.value=b.phone||'';eStatus.value=b.status;}</script>
<?php $pageScript=ob_get_clean(); require __DIR__.'/includes/footer.php';
