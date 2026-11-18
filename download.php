<?php
require_once __DIR__.'/includes/bootstrap.php';
require_menu('materials');
$id=(int)($_GET['id']??0); $s=branch_scope();
$q=db()->prepare('SELECT m.*,b.branch_id bbranch FROM materials m JOIN batches b ON b.id=m.batch_id WHERE m.id=?'); $q->execute([$id]); $m=$q->fetch();
if (!$m || !$m['stored_name'] || ($s!==null && (int)$m['bbranch']!==$s) || (is_teacher() && !teacher_has_batch((int)$m['batch_id']))) { http_response_code(404); exit('File not found.'); }
$path=__DIR__.'/uploads/'.basename($m['stored_name']);
if (!is_file($path)) { http_response_code(404); exit('File is missing on the server.'); }
$name=$m['original_name']?:basename($path);
header('Content-Type: application/octet-stream'); header('X-Content-Type-Options: nosniff');
header('Content-Disposition: attachment; filename="'.str_replace(['"','\\'],'_',$name).'"; filename*=UTF-8\'\''.rawurlencode($name));
header('Content-Length: '.filesize($path)); header('Cache-Control: private, no-store');
readfile($path); log_activity('material_download',$m['title']);
