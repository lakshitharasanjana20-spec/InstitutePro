<?php
const LEARN_TYPES=[
  'lecture'=>['Recorded lectures','bi-play-btn-fill','Recorded lecture'],
  'live'=>['Live classes','bi-camera-video-fill','Live class'],
  'material'=>['Course materials','bi-file-earmark-text-fill','Course material'],
  'homework'=>['Assignments','bi-journal-check','Assignment'],
  'quiz'=>['Quizzes & MCQ','bi-ui-checks','Quiz'],
  'reading'=>['Extra readings','bi-book-fill','Extra reading'],
  'notice'=>['Announcements','bi-megaphone-fill','Announcement'],
];
const UP_EXT=['pdf','doc','docx','ppt','pptx','xls','xlsx','txt','jpg','jpeg','png','mp3','mp4','zip'];
const UP_MAX=10*1024*1024;
function live_host_ok(string $h): bool { foreach(['zoom.us','zoom.com','zoomgov.com','meet.google.com','teams.microsoft.com','teams.live.com'] as $d) if($h===$d||str_ends_with($h,'.'.$d)) return true; return false; }
/** Cleans and validates a web link. Returns [url, error]. Adds https:// when missing; only http/https are accepted. */
function clean_url(string $u,bool $liveOnly=false): array {
  $u=trim($u); if($u==='') return [null,'A link is required.'];
  if(preg_match('/[\s<>"\'\x00-\x1f]/',$u)) return [null,'The link contains invalid characters.'];
  if(!preg_match('~^[a-z][a-z0-9+.-]*://~i',$u)) $u='https://'.$u;
  if(strlen($u)>500) return [null,'The link is too long.'];
  $p=parse_url($u); $scheme=strtolower($p['scheme']??''); $host=strtolower($p['host']??'');
  if(!in_array($scheme,['http','https'],true)||$host==='') return [null,'Enter a valid web link starting with http:// or https://'];
  if(!preg_match('/^[a-z0-9.-]+\.[a-z]{2,}$/',$host)) return [null,'The link address does not look valid.'];
  if($liveOnly&&!live_host_ok($host)) return [null,'Use a Zoom, Google Meet or Microsoft Teams meeting link.'];
  return [preg_replace('~^http://~i','https://',$u),null];
}
function url_label(string $u): string {
  $h=strtolower((string)parse_url($u,PHP_URL_HOST)); $h=preg_replace('/^www\./','',$h);
  $map=['youtube.com'=>'YouTube','youtu.be'=>'YouTube','vimeo.com'=>'Vimeo','drive.google.com'=>'Google Drive','docs.google.com'=>'Google Docs','onedrive.live.com'=>'OneDrive','1drv.ms'=>'OneDrive','sharepoint.com'=>'SharePoint','dropbox.com'=>'Dropbox','zoom.us'=>'Zoom','meet.google.com'=>'Google Meet','teams.microsoft.com'=>'Microsoft Teams','facebook.com'=>'Facebook','fb.watch'=>'Facebook'];
  foreach($map as $d=>$n) if($h===$d||str_ends_with($h,'.'.$d)) return $n;
  return $h?:'Link';
}
/** [label, bootstrap colour, can-join] for a live class. Joining opens 10 minutes before the start. */
function live_state(?string $startsAt,?int $dur): array {
  if(!$startsAt) return ['No time set','secondary',true];
  $st=strtotime($startsAt); $en=$st+max(5,(int)($dur?:60))*60; $now=time();
  if($now<$st-600){ $d=$st-$now; $t=$d>=86400?floor($d/86400).' day'.(floor($d/86400)>1?'s':''):($d>=3600?floor($d/3600).' h '.floor(($d%3600)/60).' min':ceil($d/60).' min'); return ['Starts in '.$t,'secondary',false]; }
  if($now<=$en) return ['Live now','danger',true];
  return ['Ended','dark',false];
}
function dt_local(?string $v): ?string { return ($v&&preg_match('/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/',$v)&&strtotime($v))?str_replace('T',' ',$v).':00':null; }
function dt_input(?string $v): string { return $v?date('Y-m-d\TH:i',strtotime($v)):''; }
/** Saves an uploaded file under uploads/ with a random name. Returns [stored, original, size, error]. */
function save_upload(?array $f): array {
  if(!$f||$f['error']===UPLOAD_ERR_NO_FILE) return [null,null,null,null];
  if($f['error']!==UPLOAD_ERR_OK) return [null,null,null,'Upload failed. The file may be larger than the server allows (limit 10 MB).'];
  if($f['size']>UP_MAX) return [null,null,null,'File is larger than 10 MB.'];
  $orig=mb_substr(preg_replace('/[^\p{L}\p{N}\s._()-]/u','_',basename($f['name'])),0,190); $ext=strtolower(pathinfo($orig,PATHINFO_EXTENSION));
  if(!in_array($ext,UP_EXT,true)) return [null,null,null,'This file type is not allowed. Allowed: '.implode(', ',UP_EXT).'.'];
  $dir=dirname(__DIR__).'/uploads'; if(!is_dir($dir)) @mkdir($dir,0755,true);
  $stored=bin2hex(random_bytes(16)).'.'.$ext;
  if(!move_uploaded_file($f['tmp_name'],$dir.'/'.$stored)) return [null,null,null,'Could not save the file on the server.'];
  return [$stored,$orig,(int)$f['size'],null];
}
function send_stored_file(array $m): void {
  $path=dirname(__DIR__).'/uploads/'.basename((string)$m['stored_name']);
  if(!is_file($path)){ http_response_code(404); exit('File is missing on the server.'); }
  $name=$m['original_name']?:basename($path);
  header('Content-Type: application/octet-stream'); header('X-Content-Type-Options: nosniff');
  header('Content-Disposition: attachment; filename="'.str_replace(['"','\\'],'_',$name).'"; filename*=UTF-8\'\''.rawurlencode($name));
  header('Content-Length: '.filesize($path)); header('Cache-Control: private, no-store'); readfile($path); exit;
}
function portal_student(int $batchId): ?array {
  if (($_SESSION['user']['role']??'')==='student') {
    $q=db()->prepare('SELECT s.* FROM students s JOIN enrolments e ON e.student_id=s.id WHERE s.id=? AND e.batch_id=?'); $q->execute([(int)($_SESSION['user']['student_id']??0),$batchId]); return $q->fetch()?:null;
  }
  $L=$_SESSION['learn']??null; if(!$L||(int)$L['bid']!==$batchId||time()-(int)$L['t']>10800) return null;
  $q=db()->prepare('SELECT s.* FROM students s JOIN enrolments e ON e.student_id=s.id WHERE s.id=? AND e.batch_id=?'); $q->execute([(int)$L['sid'],$batchId]); return $q->fetch()?:null;
}
function portal_url(string $token): string {
  $base=(defined('APP_URL')&&APP_URL!=='')?rtrim(APP_URL,'/'):((!empty($_SERVER['HTTPS'])&&$_SERVER['HTTPS']!=='off'?'https':'http').'://'.($_SERVER['HTTP_HOST']??'localhost').rtrim(dirname($_SERVER['SCRIPT_NAME']),'/\\'));
  return $base.'/learn.php?t='.$token;
}
