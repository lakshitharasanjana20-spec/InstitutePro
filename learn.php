<?php
require_once __DIR__.'/includes/bootstrap.php';
require_once __DIR__.'/includes/learn_lib.php';
$tok=preg_replace('/[^a-f0-9]/','',strtolower((string)($_GET['t']??'')));
$q=db()->prepare('SELECT b.*,c.name cname,t.name tname FROM batches b JOIN courses c ON c.id=b.course_id JOIN course_types t ON t.id=c.type_id WHERE b.access_token=? AND b.portal_on=1');
$q->execute([$tok]); $b=$tok!==''?$q->fetch():false;
$inst=setting('institute_name',APP_NAME);
function page_head(string $title,string $inst): void { ?><!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title><?=e($title)?></title>
<link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css" rel="stylesheet"><link href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.3/font/bootstrap-icons.css" rel="stylesheet"><link href="assets/css/oneui.css" rel="stylesheet"></head><body>
<div style="max-width:760px;margin:0 auto;padding:16px"><div class="brand"><i class="bi bi-mortarboard-fill"></i><span><?=e($inst)?></span></div><?php }
function page_foot(): void { ?></div><script>function copyText(b){var t=b.getAttribute('data-copy');var d=function(){var o=b.textContent;b.textContent='Copied';setTimeout(function(){b.textContent=o;},1500);};if(navigator.clipboard&&window.isSecureContext){navigator.clipboard.writeText(t).then(d);}else{var a=document.createElement('textarea');a.value=t;document.body.appendChild(a);a.select();try{document.execCommand('copy');d();}catch(e){}document.body.removeChild(a);}}</script></body></html><?php }
if (!$b) { http_response_code(404); page_head('Link not active',$inst); echo '<div class="card-ui text-center"><i class="bi bi-link-45deg" style="font-size:2.5rem"></i><h5>This link is not active</h5><p class="lbl mb-0">Please ask your institute for the current class link.</p></div>'; page_foot(); exit; }
$bid=(int)$b['id'];
if (isset($_GET['out'])) { unset($_SESSION['learn']); header('Location: learn.php?t='.$tok); exit; }
$err='';
if ($_SERVER['REQUEST_METHOD']==='POST') {
  csrf_check(); $ip=$_SERVER['REMOTE_ADDR']??'';
  $c=db()->prepare('SELECT COUNT(*) FROM portal_fails WHERE ip=? AND created_at>(NOW() - INTERVAL 15 MINUTE)'); $c->execute([$ip]);
  if ((int)$c->fetchColumn()>=10) $err='Too many attempts. Please wait 15 minutes and try again.';
  else {
    $code=strtoupper(trim($_POST['student_code']??'')); $ph=preg_replace('/\D/','',$_POST['phone']??'');
    $st=db()->prepare('SELECT s.* FROM students s JOIN enrolments e ON e.student_id=s.id WHERE s.student_code=? AND e.batch_id=?'); $st->execute([$code,$bid]); $stu=$st->fetch();
    $okPhone=$stu && strlen($ph)>=9 && substr(preg_replace('/\D/','',$stu['phone']),-9)===substr($ph,-9);
    if ($okPhone) {
      session_regenerate_id(true); $_SESSION['learn']=['sid'=>(int)$stu['id'],'bid'=>$bid,'t'=>time()];
      db()->prepare('INSERT INTO activity_logs (branch_id,action,details,ip_address) VALUES (?,?,?,?)')->execute([$b['branch_id'],'portal_login',$stu['student_code'],$ip]);
      header('Location: learn.php?t='.$tok); exit;
    }
    db()->prepare('INSERT INTO portal_fails (ip) VALUES (?)')->execute([$ip]); usleep(600000); $err='We could not confirm those details. Check your Student ID and the contact number you registered with.';
  }
}
$stu=portal_student($bid);
if (!$stu) { page_head('Class access',$inst); ?>
<div class="card-ui"><h5 class="mb-1"><?=e($b['cname'])?> · <?=e($b['name'])?> (<?=$b['batch_year']?>)</h5><div class="lbl mb-3">Confirm who you are to open your class materials.</div>
<?php if($err):?><div class="alert alert-danger rounded-4"><?=e($err)?></div><?php endif;?>
<form method="post" autocomplete="off"><input type="hidden" name="csrf" value="<?=csrf()?>">
<label class="lbl mb-1">Student ID</label><input class="form-control mb-3" name="student_code" placeholder="e.g. MN-AI-OL-2026-001" required autofocus>
<label class="lbl mb-1">Contact number you registered with</label><input class="form-control mb-3" name="phone" inputmode="tel" required>
<button class="btn-ui w-100">Open my class</button></form></div><?php page_foot(); exit; }
$now=date('Y-m-d H:i:s');
$items=db()->prepare('SELECT m.*,(SELECT COUNT(*) FROM quiz_questions z WHERE z.material_id=m.id) nq,(SELECT COUNT(*) FROM quiz_attempts a WHERE a.material_id=m.id AND a.student_id=? AND a.submitted_at IS NOT NULL) used FROM materials m WHERE m.batch_id=? AND m.publish=1 ORDER BY m.id DESC');
$items->execute([$stu['id'],$bid]); $all=$items->fetchAll(); $by=[]; foreach($all as $m) $by[$m['kind']][]=$m;
$live=$by['live']??[]; usort($live,fn($x,$y)=>strcmp($x['starts_at'],$y['starts_at']));
$live=array_values(array_filter($live,fn($m)=>strtotime($m['starts_at'])+max(5,(int)$m['duration_min'])*60>time()-86400));
$hw=db()->prepare('SELECT material_id,status,marks,remarks FROM homework_records WHERE student_id=?'); $hw->execute([$stu['id']]); $rec=[]; foreach($hw->fetchAll() as $r) $rec[$r['material_id']]=$r;
$kb=fn($n)=>$n>=1048576?round($n/1048576,1).' MB':max(1,round($n/1024)).' KB';
page_head($b['name'].' · '.$inst,$inst); ?>
<div class="card-ui mb-3 d-flex justify-content-between align-items-center gap-2"><div><div class="fw-bold"><?=e($b['cname'])?> · <?=e($b['name'])?> (<?=$b['batch_year']?>)</div><div class="lbl">Hello, <?=e($stu['first_name'])?> · <?=e($stu['student_code'])?></div></div><a class="btn btn-light rounded-pill btn-sm" href="learn.php?t=<?=e($tok)?>&out=1">Sign out</a></div>
<?php if($live):?><div class="fw-bold mb-2"><i class="bi bi-camera-video-fill"></i> Live classes</div>
<?php foreach($live as $m): $ls=live_state($m['starts_at'],$m['duration_min']);?><div class="card-ui mb-2" <?=$ls[1]==='danger'?'style="outline:2px solid #d9534f"':''?>><div class="d-flex justify-content-between gap-2 flex-wrap"><div><b><?=e($m['title'])?></b> <span class="badge rounded-pill text-bg-<?=$ls[1]?>"><?=e($ls[0])?></span>
 <div class="lbl"><?=e(date('D j M Y, g:i A',strtotime($m['starts_at'])))?> · <?=(int)$m['duration_min']?> min</div><?php if($m['description']):?><div class="lbl"><?=nl2br(e($m['description']))?></div><?php endif;?>
 <?php if($m['passcode']):?><div class="lbl">Passcode: <b><?=e($m['passcode'])?></b></div><?php endif;?></div>
 <div class="d-flex gap-1 align-items-start"><?php if($ls[2]&&$ls[1]!=='dark'):?><a class="btn-ui text-decoration-none" href="<?=e($m['url'])?>" target="_blank" rel="noopener noreferrer">Join class</a><?php else:?><span class="lbl">Joining opens 10 minutes before</span><?php endif;?><button type="button" class="btn btn-light rounded-pill btn-sm" data-copy="<?=e($m['url'])?>" onclick="copyText(this)">Copy link</button></div></div></div><?php endforeach; endif;?>
<?php foreach(['notice','lecture','material','homework','quiz','reading'] as $k): $list=$by[$k]??[]; if($k==='quiz') $list=array_values(array_filter($list,fn($m)=>$m['nq']>0)); if(!$list) continue; $T=LEARN_TYPES[$k];?>
<div class="fw-bold mt-3 mb-2"><i class="bi <?=$T[1]?>"></i> <?=e($T[0])?></div>
<?php foreach($list as $m): $r=$rec[$m['id']]??null; ?><div class="card-ui mb-2"><div class="d-flex justify-content-between gap-2 flex-wrap"><div style="min-width:0"><b><?=e($m['title'])?></b>
 <?php if($m['description']):?><div class="lbl"><?=nl2br(e($m['description']))?></div><?php endif;?>
 <?php if($m['url']):?><div class="lbl"><i class="bi bi-link-45deg"></i> <?=e(url_label($m['url']))?></div><?php endif;?>
 <?php if($m['original_name']):?><div class="lbl"><i class="bi bi-paperclip"></i> <?=e($m['original_name'])?> · <?=$kb((int)$m['size'])?></div><?php endif;?>
 <?php if($k==='homework'):?><div class="lbl <?=($m['due_date']&&$m['due_date']<date('Y-m-d'))?'text-danger':''?>"><?=$m['due_date']?'Due '.e($m['due_date']):'No due date'?><?=$m['max_marks']!==null?' · max '.rtrim(rtrim(number_format((float)$m['max_marks'],2,'.',''),'0'),'.'):''?></div>
  <?php if($r):?><div class="mt-1"><span class="badge rounded-pill text-bg-<?=['submitted'=>'success','late'=>'warning','missing'=>'danger'][$r['status']]?>"><?=e(ucfirst($r['status']))?></span><?=$r['marks']!==null?' <b>'.rtrim(rtrim(number_format((float)$r['marks'],2,'.',''),'0'),'.').'</b>'.($m['max_marks']!==null?' / '.rtrim(rtrim(number_format((float)$m['max_marks'],2,'.',''),'0'),'.'):''):''?><?=$r['remarks']?' <span class="lbl">'.e($r['remarks']).'</span>':''?></div><?php endif;?><?php endif;?>
 <?php if($k==='quiz'):?><div class="lbl"><?=(int)$m['nq']?> questions · <?=$m['time_limit_min']?(int)$m['time_limit_min'].' min':'no time limit'?> · attempts used <?=(int)$m['used']?>/<?=(int)$m['max_attempts']?><?=$m['closes_at']?' · closes '.e(date('j M, g:i A',strtotime($m['closes_at']))):''?></div><?php endif;?></div>
 <div class="d-flex gap-1 align-items-start flex-wrap"><?php if($m['url']):?><a class="btn-ui text-decoration-none" style="padding:8px 18px" href="<?=e($m['url'])?>" target="_blank" rel="noopener noreferrer"><?=$k==='lecture'?'Watch':'Open'?></a><button type="button" class="btn btn-light rounded-pill btn-sm" data-copy="<?=e($m['url'])?>" onclick="copyText(this)">Copy link</button><?php endif;?>
 <?php if($m['stored_name']):?><a class="btn btn-light rounded-pill btn-sm" href="learn_file.php?id=<?=$m['id']?>"><i class="bi bi-download"></i> Download</a><?php endif;?>
 <?php if($k==='quiz'):?><a class="btn-ui text-decoration-none" style="padding:8px 18px" href="learn_quiz.php?id=<?=$m['id']?>"><?=($m['used']>0)?'Open':'Start'?></a><?php endif;?></div></div></div>
<?php endforeach; endforeach;
if(!$all): ?><div class="card-ui text-center lbl">Nothing has been shared with your class yet.</div><?php endif;
page_foot();
