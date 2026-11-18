<?php
require_once __DIR__.'/includes/bootstrap.php';
$token=$_GET['token']??($_POST['token']??''); $err=''; $row=null;
if (preg_match('/^[a-f0-9]{64}$/',$token)) {
  $q=db()->prepare("SELECT r.id,r.user_id FROM password_resets r JOIN users u ON u.id=r.user_id WHERE r.token_hash=? AND r.used_at IS NULL AND r.expires_at>NOW() AND u.status='active'"); $q->execute([hash('sha256',$token)]); $row=$q->fetch();
}
if ($row && $_SERVER['REQUEST_METHOD']==='POST') {
  csrf_check(); $pw=$_POST['password']??'';
  if (strlen($pw)<8) $err='Password must be at least 8 characters.';
  elseif ($pw!==($_POST['confirm']??'')) $err='The two passwords do not match.';
  else {
    db()->prepare('UPDATE users SET password_hash=?,failed_logins=0,locked_until=NULL WHERE id=?')->execute([password_hash($pw,PASSWORD_DEFAULT),$row['user_id']]);
    db()->prepare('UPDATE password_resets SET used_at=NOW() WHERE user_id=? AND used_at IS NULL')->execute([$row['user_id']]);
    db()->prepare('INSERT INTO activity_logs (user_id,action,details,ip_address) VALUES (?,?,?,?)')->execute([$row['user_id'],'password_reset','Reset via email link',$_SERVER['REMOTE_ADDR']??'']);
    header('Location: login.php?reset=1'); exit;
  }
}
?><!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Reset password · <?=APP_NAME?></title>
<link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css" rel="stylesheet"><link href="assets/css/oneui.css" rel="stylesheet"></head><body>
<div class="login-wrap"><div class="login-card card-ui p-4"><h5 class="mb-3">Choose a new password</h5>
<?php if(!$row):?><div class="alert alert-danger rounded-4">This reset link is invalid or has expired. Please request a new one.</div><a class="btn-ui d-block text-center text-decoration-none" href="forgot.php">Request again</a>
<?php else:?><?php if($err):?><div class="alert alert-danger rounded-4"><?=e($err)?></div><?php endif;?>
<form method="post"><input type="hidden" name="csrf" value="<?=csrf()?>"><input type="hidden" name="token" value="<?=e($token)?>">
<input class="form-control mb-2" type="password" name="password" placeholder="New password (8+ characters)" minlength="8" required autofocus>
<input class="form-control mb-3" type="password" name="confirm" placeholder="Confirm new password" required><button class="btn-ui w-100">Save password</button></form><?php endif;?>
</div></div></body></html>
