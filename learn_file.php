<?php
require_once __DIR__.'/includes/bootstrap.php';
require_once __DIR__.'/includes/learn_lib.php';
$id=gint('id'); $q=db()->prepare('SELECT * FROM materials WHERE id=? AND publish=1 AND stored_name IS NOT NULL'); $q->execute([$id]); $m=$q->fetch();
if (!$m || !portal_student((int)$m['batch_id'])) { http_response_code(404); exit('File not found.'); }
send_stored_file($m);
