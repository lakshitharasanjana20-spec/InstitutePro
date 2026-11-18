<?php
// Run once in the browser: install.php?key=<INSTALL_KEY>, then DELETE this file.
require_once __DIR__.'/config.php';
if (($_GET['key']??'')!==INSTALL_KEY || INSTALL_KEY==='change-me-before-install') exit('Set a custom INSTALL_KEY in config.php, then open install.php?key=YOUR_KEY');
$root = new PDO('mysql:host='.DB_HOST.';charset=utf8mb4',DB_USER,DB_PASS,[PDO::ATTR_ERRMODE=>PDO::ERRMODE_EXCEPTION]);
$root->exec('CREATE DATABASE IF NOT EXISTS `'.DB_NAME.'` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci');
$root->exec('USE `'.DB_NAME.'`'); $root->exec(file_get_contents(__DIR__.'/schema.sql'));
if ($_SERVER['REQUEST_METHOD']==='POST') {
  $root->prepare("INSERT IGNORE INTO branches (code,name) VALUES ('MN','Main Branch')")->execute();
  $root->prepare("INSERT INTO users (role,full_name,email,password_hash) VALUES ('super_admin',?,?,?)")
    ->execute([$_POST['name'],$_POST['email'],password_hash($_POST['password'],PASSWORD_DEFAULT)]);
  exit('Installed. DELETE install.php now, then <a href="login.php">sign in</a>.');
}
?><form method="post" style="max-width:360px;margin:40px auto;font-family:sans-serif">
<h3>Create Super Admin</h3><p><input name="name" placeholder="Full name" required style="width:100%;padding:8px"></p>
<p><input name="email" type="email" placeholder="Email" required style="width:100%;padding:8px"></p>
<p><input name="password" type="password" minlength="8" placeholder="Password (8+)" required style="width:100%;padding:8px"></p>
<button style="padding:10px 20px">Install</button></form>
