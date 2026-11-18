<?php
require_once __DIR__.'/includes/bootstrap.php';
$err='';
if ($_SERVER['REQUEST_METHOD']==='POST') {
  csrf_check();
  $st = db()->prepare('SELECT * FROM users WHERE email=?'); $st->execute([trim($_POST['email']??'')]);
  $u = $st->fetch();
  if ($u && $u['locked_until'] && strtotime($u['locked_until'])>time()) $err='Account temporarily locked. Try again in a few minutes.';
  elseif ($u && $u['status']==='active' && password_verify($_POST['password']??'', $u['password_hash'])) {
    session_regenerate_id(true);
    db()->prepare('UPDATE users SET failed_logins=0,locked_until=NULL,last_login=NOW() WHERE id=?')->execute([$u['id']]);
    unset($u['password_hash']); $_SESSION['user']=$u; $_SESSION['last']=time();
    log_activity('login','Signed in'); header('Location: dashboard.php'); exit;
  } else {
    if ($u) { $n=$u['failed_logins']+1; $lock=$n>=5?date('Y-m-d H:i:s',time()+600):null;
      db()->prepare('UPDATE users SET failed_logins=?,locked_until=? WHERE id=?')->execute([$lock?0:$n,$lock,$u['id']]); }
    $err='Invalid email or password.';
  }
}
?><!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Sign in · <?=APP_NAME?></title>
<link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css" rel="stylesheet">
<link href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.3/font/bootstrap-icons.css" rel="stylesheet">
<link href="assets/css/oneui.css" rel="stylesheet"></head><body>
<div class="login-wrap"><div class="login-card card-ui p-4">
<div class="text-center mb-4"><div class="brand justify-content-center"><i class="bi bi-mortarboard-fill"></i><?=APP_NAME?></div>
<div class="lbl">English Language Institute · Office use only</div></div>
<?php if($err):?><div class="alert alert-danger rounded-4"><?=e($err)?></div><?php endif;?>
<?php if(isset($_GET['reset'])):?><div class="alert alert-success rounded-4">Password updated. Please sign in.</div><?php endif;?>
<?php if(isset($_GET['timeout'])):?><div class="alert alert-warning rounded-4">Session expired. Please sign in again.</div><?php endif;?>
<form method="post" autocomplete="off">
<input type="hidden" name="csrf" value="<?=csrf()?>">
<label class="lbl mb-1">Email</label><input class="form-control mb-3" type="email" name="email" required autofocus>
<label class="lbl mb-1">Password</label><input class="form-control mb-4" type="password" name="password" required>
<button class="btn-ui w-100">Sign in</button></form>
<div class="text-center mt-3"><a href="forgot.php">Forgot password?</a></div></div></div></body></html>
