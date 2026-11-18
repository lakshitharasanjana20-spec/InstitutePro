<?php
require_once __DIR__.'/includes/bootstrap.php';
require_once __DIR__.'/includes/student_lib.php';
require_login(); $t=$_GET['t']??''; $id=gint('id'); $s=branch_scope(); $photo=null;
if ($t==='u') {
  $q=db()->prepare("SELECT photo,branch_id FROM users WHERE id=? AND role<>'student'"); $q->execute([$id]); $r=$q->fetch();
  if ($r && !is_student_user() && ($s===null||(int)$r['branch_id']===$s||$id===(int)$_SESSION['user']['id'])) $photo=$r['photo'];
} elseif ($t==='s') {
  $q=db()->prepare('SELECT photo,branch_id FROM students WHERE id=?'); $q->execute([$id]); $r=$q->fetch();
  if ($r) { if (is_student_user()) { if (stu_id()===$id) $photo=$r['photo']; } elseif (in_array('students',allowed_menu($_SESSION['user']['role']),true)&&($s===null||(int)$r['branch_id']===$s)) $photo=$r['photo']; }
}
$path=$photo?dirname(__FILE__).'/uploads/'.basename($photo):'';
if (!$photo||!is_file($path)) { http_response_code(404); exit; }
$ext=strtolower(pathinfo($path,PATHINFO_EXTENSION)); $types=['jpg'=>'image/jpeg','png'=>'image/png','webp'=>'image/webp'];
header('Content-Type: '.($types[$ext]??'application/octet-stream')); header('X-Content-Type-Options: nosniff'); header('Cache-Control: private, max-age=86400'); header('Content-Length: '.filesize($path)); readfile($path);
