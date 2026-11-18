<?php
const ROLE_LABELS=['super_admin'=>'Super Admin','admin'=>'Admin','branch_manager'=>'Branch Manager','administrator'=>'Administrator','receptionist'=>'Receptionist','teacher'=>'Teacher','finance'=>'Finance','other'=>'Other'];
function assignable_roles(string $mode): array {
  $r=$_SESSION['user']['role']; $all=array_keys(ROLE_LABELS);
  if ($r==='super_admin') $x=$all;
  elseif ($r==='admin') $x=array_diff($all,['super_admin']);
  elseif ($r==='branch_manager') $x=['administrator','receptionist','teacher','finance','other'];
  else $x=[];
  return array_values($mode==='teachers' ? array_intersect($x,['teacher']) : array_diff($x,['teacher']));
}
function users_handle(string $mode): void {
  if ($_SERVER['REQUEST_METHOD']!=='POST') return;
  csrf_check();
  if (!can('manage_users')) { http_response_code(403); exit('Access denied.'); }
  $s=branch_scope(); $roles=assignable_roles($mode); $act=$_POST['act']??''; $back=$mode==='teachers'?'teachers.php':'staff.php';
  if ($act==='add') {
    $role=$_POST['role']??''; $name=trim($_POST['full_name']??''); $email=trim($_POST['email']??''); $pw=$_POST['password']??'';
    $bid=is_owner()?(int)($_POST['branch_id']??0):(int)$s; $ownerRole=in_array($role,['super_admin','admin'],true);
    if (!in_array($role,$roles,true)) $m='You cannot create that role.';
    elseif ($name===''||!filter_var($email,FILTER_VALIDATE_EMAIL)) $m='Name and a valid email are required.';
    elseif (strlen($pw)<8) $m='Password must be at least 8 characters.';
    elseif (!$ownerRole && !$bid) $m='Choose a branch for this person.';
    else {
      try {
        db()->prepare('INSERT INTO users (branch_id,role,full_name,email,password_hash,phone,nic,job_title,joined_on,fingerprint_id,must_change_pw) VALUES (?,?,?,?,?,?,?,?,?,?,1)')
          ->execute([$ownerRole?null:$bid,$role,$name,$email,password_hash($pw,PASSWORD_DEFAULT),trim($_POST['phone']??'')?:null,strtoupper(trim($_POST['nic']??''))?:null,trim($_POST['job_title']??'')?:null,($_POST['joined_on']??'')?:null,trim($_POST['fingerprint_id']??'')?:null]);
        log_activity('user_create',"$name ($role)"); $m='Account created for '.$name.'.';
      } catch (PDOException $x) { $m=$x->getCode()==='23000'?'That email or fingerprint ID is already in use.':'Could not save.'; }
    }
    flash_set('msg',$m); header("Location: $back"); exit;
  }
  if ($act==='setfp') {
    $id=(int)($_POST['id']??0); $q=db()->prepare('SELECT * FROM users WHERE id=?'); $q->execute([$id]); $t=$q->fetch();
    $ok=$t && in_array($t['role'],$roles,true) && ($s===null || (int)$t['branch_id']===$s);
    if (!$ok) flash_set('msg','You cannot change that account.');
    else { try { db()->prepare('UPDATE users SET fingerprint_id=? WHERE id=?')->execute([trim($_POST['fingerprint_id']??'')?:null,$id]); log_activity('user_fp',$t['email']); flash_set('msg','Fingerprint ID saved.'); }
      catch (PDOException $x) { flash_set('msg','That fingerprint ID is already used in this branch.'); } }
    header("Location: $back"); exit;
  }
  if ($act==='toggle' || $act==='reset') {
    $id=(int)($_POST['id']??0); $q=db()->prepare('SELECT * FROM users WHERE id=?'); $q->execute([$id]); $t=$q->fetch();
    $okTarget=$t && $id!==(int)$_SESSION['user']['id'] && in_array($t['role'],$roles,true) && ($s===null || (int)$t['branch_id']===$s);
    if (!$okTarget) { flash_set('msg','You cannot change that account.'); }
    elseif ($act==='toggle') {
      $n=$t['status']==='active'?'inactive':'active';
      db()->prepare('UPDATE users SET status=? WHERE id=?')->execute([$n,$id]); log_activity('user_status',$t['email'].' → '.$n); flash_set('msg',$t['full_name'].' is now '.$n.'.');
    } else {
      $pw=$_POST['password']??'';
      if (strlen($pw)<8) flash_set('msg','Password must be at least 8 characters.');
      else { db()->prepare('UPDATE users SET password_hash=?,failed_logins=0,locked_until=NULL,must_change_pw=1 WHERE id=?')->execute([password_hash($pw,PASSWORD_DEFAULT),$id]); log_activity('user_pw_reset',$t['email']); db()->prepare('UPDATE password_resets SET used_at=NOW() WHERE user_id=? AND used_at IS NULL')->execute([$id]); flash_set('msg','Password reset for '.$t['full_name'].'.'); }
    }
    header("Location: $back"); exit;
  }
}
function users_render(string $mode): void {
  $s=branch_scope(); $roles=assignable_roles($mode); $msg=flash_get('msg'); $manage=can('manage_users');
  $w=[$mode==='teachers'?"u.role='teacher'":"u.role NOT IN ('teacher','student')"]; $p=[];
  if ($s!==null) { $w[]='u.branch_id=?'; $p[]=$s; }
  $w2=$w; $p2=$p; $fq=gstr('q'); $fr=genum('role',array_keys(ROLE_LABELS)); $fs=genum('status',['active','inactive']); $fbr=$s===null?gint('branch'):0;
  if($fq!==''){ $w2[]='(u.full_name LIKE ? OR u.email LIKE ? OR u.phone LIKE ?)'; array_push($p2,"%$fq%","%$fq%","%$fq%"); }
  if($fr){ $w2[]='u.role=?'; $p2[]=$fr; } if($fs){ $w2[]='u.status=?'; $p2[]=$fs; } if($fbr){ $w2[]='u.branch_id=?'; $p2[]=$fbr; }
  $cq=db()->prepare('SELECT COUNT(*) FROM users u WHERE '.implode(' AND ',$w2)); $cq->execute($p2); $total=(int)$cq->fetchColumn(); $per=pg_per(); [$pg,$pages,$off]=pg_page($total,$per);
  $st=db()->prepare('SELECT u.*,b.name brname FROM users u LEFT JOIN branches b ON b.id=u.branch_id WHERE '.implode(' AND ',$w2).' ORDER BY u.full_name LIMIT '.$per.' OFFSET '.$off); $st->execute($p2); $rows=$st->fetchAll();
  $pr=db()->prepare('SELECT u.full_name,MAX(r.requested_at) at FROM password_resets r JOIN users u ON u.id=r.user_id WHERE r.used_at IS NULL AND r.requested_at>(NOW() - INTERVAL 1 DAY) AND '.implode(' AND ',$w).' GROUP BY u.id,u.full_name'); $pr->execute($p); $pending=$manage?$pr->fetchAll():[];
  $branches=db()->query("SELECT id,name FROM branches WHERE status='active' ORDER BY name")->fetchAll();
  $label=$mode==='teachers'?'teacher':'staff member'; ?>
<?php if($msg):?><div class="alert alert-info rounded-4"><?=e($msg)?></div><?php endif;?>
<?php if($pending):?><div class="alert alert-warning rounded-4"><b>Password reset requested:</b> <?=e(implode(', ',array_column($pending,'full_name')))?>. Use “Reset password” below to give them a new temporary password.</div><?php endif;?>
<?php if($manage && $roles):?><div class="mb-3 text-end"><button class="btn-ui" data-bs-toggle="modal" data-bs-target="#addModal"><i class="bi bi-plus-lg"></i> Add <?=$label?></button></div><?php endif;?>
<?php f_open(); f_input('q','Search name, email or phone','text','col-12 col-md-4');
 if($mode==='staff') f_select('role',array_diff_key(ROLE_LABELS,['teacher'=>1]),'Any role','col-6 col-md-3');
 f_select('status',['active'=>'Active','inactive'=>'Inactive'],'Any status','col-6 col-md-2');
 if($s===null) f_select('branch',array_column($branches,'name','id'),'All branches','col-6 col-md-3'); f_close();?>
<div class="card-ui table-wrap"><table class="table align-middle"><thead><tr><th>Name</th><th>Role</th><th>Branch</th><th>Contact</th><th>Status</th><?php if($manage):?><th></th><?php endif;?></tr></thead><tbody>
<?php foreach($rows as $r): $self=(int)$r['id']===(int)$_SESSION['user']['id']; $can=$manage && !$self && in_array($r['role'],$roles,true);?>
<tr><td><a href="staff_view.php?id=<?=$r['id']?>" class="fw-bold text-decoration-none"><?=e($r['full_name'])?></a><div class="lbl"><?=e($r['job_title'])?></div></td><td><?=e(ROLE_LABELS[$r['role']])?></td><td><?=e($r['brname']??'All')?></td>
<td><?=e($r['email'])?><div class="lbl"><?=e($r['phone'])?></div></td>
<td><span class="badge rounded-pill text-bg-<?=$r['status']==='active'?'success':'secondary'?>"><?=e($r['status'])?></span></td>
<?php if($manage):?><td class="text-nowrap"><?php if($r['role']==='teacher'):?><a class="btn btn-sm btn-light rounded-pill" href="schedule.php?teacher=<?=$r['id']?>">Timetable</a> <?php endif; if($can):?>
 <form method="post" class="d-inline"><input type="hidden" name="csrf" value="<?=csrf()?>"><input type="hidden" name="act" value="toggle"><input type="hidden" name="id" value="<?=$r['id']?>"><button class="btn btn-sm btn-light rounded-pill"><?=$r['status']==='active'?'Deactivate':'Activate'?></button></form>
 <button class="btn btn-sm btn-light rounded-pill" onclick="setReset(<?=$r['id']?>,'<?=e(addslashes($r['full_name']))?>')" data-bs-toggle="modal" data-bs-target="#resetModal">Reset password</button>
 <button class="btn btn-sm btn-light rounded-pill" onclick="setFp(<?=$r['id']?>,'<?=e(addslashes($r['full_name']))?>','<?=e($r['fingerprint_id']??'')?>')" data-bs-toggle="modal" data-bs-target="#fpModal">Fingerprint ID</button>
<?php endif;?></td><?php endif;?></tr>
<?php endforeach; if(!$rows):?><tr><td colspan="6" class="text-center lbl py-4">No one found.</td></tr><?php endif;?></tbody></table></div>
<?php pg_render($total,$per);?>
<?php if($manage && $roles):?>
<div class="modal fade" id="addModal" tabindex="-1"><div class="modal-dialog modal-lg modal-fullscreen-md-down modal-dialog-scrollable"><div class="modal-content" style="border-radius:24px">
<form method="post"><input type="hidden" name="csrf" value="<?=csrf()?>"><input type="hidden" name="act" value="add">
<div class="modal-header"><h5 class="modal-title">Add <?=$label?></h5><button type="button" class="btn-close" data-bs-dismiss="modal"></button></div>
<div class="modal-body"><div class="row g-2">
 <div class="col-md-6"><input class="form-control" name="full_name" placeholder="Full name *" required></div>
 <div class="col-md-6"><input class="form-control" type="email" name="email" placeholder="Email (login) *" required></div>
 <div class="col-md-6"><select class="form-select" name="role" required><?php foreach($roles as $r):?><option value="<?=$r?>"><?=e(ROLE_LABELS[$r])?></option><?php endforeach;?></select></div>
 <?php if(is_owner()):?><div class="col-md-6"><select class="form-select" name="branch_id"><option value="">Branch (not needed for Super Admin / Admin)</option><?php foreach($branches as $b):?><option value="<?=$b['id']?>"><?=e($b['name'])?></option><?php endforeach;?></select></div><?php endif;?>
 <div class="col-md-6"><input class="form-control" name="phone" placeholder="Phone"></div>
 <div class="col-md-6"><input class="form-control" name="nic" placeholder="NIC"></div>
 <div class="col-md-6"><input class="form-control" name="job_title" placeholder="Job title"></div>
 <div class="col-md-6"><input class="form-control" name="fingerprint_id" placeholder="Fingerprint machine ID"></div>
 <div class="col-md-6"><label class="lbl">Joined on</label><input class="form-control" type="date" name="joined_on"></div>
 <div class="col-12"><input class="form-control" type="text" name="password" placeholder="Temporary password (8+ characters) *" minlength="8" required autocomplete="off"></div>
</div></div><div class="modal-footer"><button class="btn-ui">Create account</button></div></form></div></div></div>
<?php endif; if($manage):?>
<div class="modal fade" id="resetModal" tabindex="-1"><div class="modal-dialog modal-dialog-centered"><div class="modal-content" style="border-radius:24px"><form method="post">
<input type="hidden" name="csrf" value="<?=csrf()?>"><input type="hidden" name="act" value="reset"><input type="hidden" name="id" id="resetId">
<div class="modal-header"><h5 class="modal-title">Reset password</h5><button type="button" class="btn-close" data-bs-dismiss="modal"></button></div>
<div class="modal-body"><div class="lbl mb-2" id="resetName"></div><input class="form-control" type="text" name="password" placeholder="New password (8+ characters)" minlength="8" required autocomplete="off"></div>
<div class="modal-footer"><button class="btn-ui">Save</button></div></form></div></div></div>
<div class="modal fade" id="fpModal" tabindex="-1"><div class="modal-dialog modal-dialog-centered"><div class="modal-content" style="border-radius:24px"><form method="post">
<input type="hidden" name="csrf" value="<?=csrf()?>"><input type="hidden" name="act" value="setfp"><input type="hidden" name="id" id="fpId">
<div class="modal-header"><h5 class="modal-title">Fingerprint ID</h5><button type="button" class="btn-close" data-bs-dismiss="modal"></button></div>
<div class="modal-body"><div class="lbl mb-2" id="fpName"></div><input class="form-control" name="fingerprint_id" id="fpVal" placeholder="ID on the fingerprint machine (blank to clear)"></div>
<div class="modal-footer"><button class="btn-ui">Save</button></div></form></div></div></div>
<?php ob_start();?><script>function setReset(i,n){document.getElementById('resetId').value=i;document.getElementById('resetName').textContent=n;}
function setFp(i,n,f){document.getElementById('fpId').value=i;document.getElementById('fpName').textContent=n;document.getElementById('fpVal').value=f;}</script>
<?php $GLOBALS['pageScript']=ob_get_clean(); endif;
}
