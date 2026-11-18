<?php
require_once __DIR__.'/includes/bootstrap.php';
require_login();
$u=$_SESSION['user']; $forced=!empty($u['must_change_pw']); $err=''; $home=($u['role']==='student')?'my_home.php':'dashboard.php';
if ($_SERVER['REQUEST_METHOD']==='POST') {
  csrf_check(); $cur=$_POST['current']??''; $new=$_POST['new']??'';
  $q=db()->prepare('SELECT password_hash FROM users WHERE id=?'); $q->execute([$u['id']]); $hash=$q->fetchColumn();
  if (!$hash||!password_verify($cur,$hash)) $err='Your current password is not correct.';
  elseif (strlen($new)<8) $err='The new password must be at least 8 characters.';
  elseif ($new!==($_POST['confirm']??'')) $err='The two new passwords do not match.';
  elseif ($new===$cur) $err='Choose a password different from the current one.';
  else {
    db()->prepare('UPDATE users SET password_hash=?,must_change_pw=0 WHERE id=?')->execute([password_hash($new,PASSWORD_DEFAULT),$u['id']]);
    $_SESSION['user']['must_change_pw']=0; log_activity('password_change','Own password changed'); header('Location: '.$home.'?pw=1'); exit;
  }
}
?><!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Change password · <?=APP_NAME?></title>
<link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css" rel="stylesheet"><link href="assets/css/oneui.css" rel="stylesheet"></head><body>
<div class="login-wrap"><div class="login-card card-ui p-4"><h5 class="mb-1"><?=$forced?'Choose your own password':'Change password'?></h5>
<?php if($forced):?><p class="lbl">You signed in with a temporary password. Please set a new one to continue.</p><?php endif;?>
<?php if($err):?><div class="alert alert-danger rounded-4"><?=e($err)?></div><?php endif;?>
<form method="post"><input type="hidden" name="csrf" value="<?=csrf()?>">
<input class="form-control mb-2" type="password" name="current" placeholder="<?=$forced?'Temporary password':'Current password'?>" required autofocus>
<input class="form-control mb-2" type="password" name="new" placeholder="New password (8+ characters)" minlength="8" required>
<input class="form-control mb-3" type="password" name="confirm" placeholder="Confirm new password" required>
<button class="btn-ui w-100">Save password</button></form>
<div class="text-center mt-3"><?php if($forced):?><a href="logout.php">Sign out</a><?php else:?><a href="<?=$home?>">Cancel</a><?php endif;?></div></div></div></body></html>
