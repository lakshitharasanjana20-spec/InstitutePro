<?php
require_once __DIR__.'/includes/bootstrap.php';
require_once __DIR__.'/includes/learn_lib.php';
require_once __DIR__.'/includes/student_lib.php';
require_login(); $id=gint('id');
$q=db()->prepare('SELECT sb.*,m.batch_id,b.branch_id FROM assignment_submissions sb JOIN materials m ON m.id=sb.material_id JOIN batches b ON b.id=m.batch_id WHERE sb.id=?'); $q->execute([$id]); $sb=$q->fetch();
$ok=false;
if ($sb) {
  if (is_student_user()) $ok=((int)$sb['student_id']===stu_id());
  else { $s=branch_scope(); $ok=in_array('materials',allowed_menu($_SESSION['user']['role']),true)&&($s===null||(int)$sb['branch_id']===$s)&&(!is_teacher()||teacher_has_batch((int)$sb['batch_id'])); }
}
if (!$ok || !$sb['stored_name']) { http_response_code(404); exit('File not found.'); }
send_stored_file($sb);
