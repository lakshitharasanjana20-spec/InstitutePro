<?php
require_once __DIR__.'/includes/bootstrap.php';
$done=false;
if ($_SERVER['REQUEST_METHOD']==='POST') {
  csrf_check(); $email=trim($_POST['email']??'');
  $q=db()->prepare("SELECT id,full_name,email FROM users WHERE email=? AND status='active'"); $q->execute([$email]); $u=$q->fetch();
  if ($u) {
    $c=db()->prepare('SELECT COUNT(*) FROM password_resets WHERE user_id=? AND requested_at>(NOW() - INTERVAL 1 HOUR)'); $c->execute([$u['id']]);
    if ((int)$c->fetchColumn()<3) {
      $token=bin2hex(random_bytes(32));
      db()->prepare('INSERT INTO password_resets (user_id,token_hash,expires_at,ip_address) VALUES (?,?,DATE_ADD(NOW(),INTERVAL 1 HOUR),?)')->execute([$u['id'],hash('sha256',$token),$_SERVER['REMOTE_ADDR']??'']);
      db()->prepare('INSERT INTO activity_logs (user_id,action,details,ip_address) VALUES (?,?,?,?)')->execute([$u['id'],'password_reset_request',$u['email'],$_SERVER['REMOTE_ADDR']??'']);
      if (defined('APP_URL') && APP_URL!=='') {   // optional: only if you set APP_URL in config.php
        $link=rtrim(APP_URL,'/').'/reset.php?token='.$token;
        @mail($u['email'],'Reset your '.APP_NAME.' password',"Hello {$u['full_name']},\n\nUse this link within 1 hour to choose a new password:\n$link\n\nIf you did not ask for this, ignore this email.",'From: no-reply@'.(parse_url(APP_URL,PHP_URL_HOST)?:'localhost'));
      }
    }
  }
  $done=true;
}
?><!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Forgot password · <?=APP_NAME?></title>
<link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css" rel="stylesheet"><link href="assets/css/oneui.css" rel="stylesheet"></head><body>
<div class="login-wrap"><div class="login-card card-ui p-4"><h5 class="mb-3">Forgot password</h5>
<?php if($done):?><div class="alert alert-info rounded-4">If that email belongs to an account, a reset link has been emailed (when email is set up on this server) and the managers have been notified. If you don't receive an email, ask your Branch Manager or an Admin to reset your password.</div>
<?php else:?><form method="post"><input type="hidden" name="csrf" value="<?=csrf()?>"><label class="lbl mb-1">Your login email</label><input class="form-control mb-3" type="email" name="email" required autofocus><button class="btn-ui w-100">Request reset</button></form><?php endif;?>
<div class="text-center mt-3"><a href="login.php">Back to sign in</a></div></div></div></body></html>
