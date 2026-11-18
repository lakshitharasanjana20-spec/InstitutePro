<?php
require_once __DIR__.'/includes/bootstrap.php';
require_once __DIR__.'/includes/learn_lib.php';
$id=gint('id'); $q=db()->prepare("SELECT m.*,b.access_token,b.portal_on,b.name bname FROM materials m JOIN batches b ON b.id=m.batch_id WHERE m.id=? AND m.kind='quiz' AND m.publish=1"); $q->execute([$id]); $m=$q->fetch();
if (!$m || (!$m['portal_on'] && !is_student_user())) { http_response_code(404); exit('Quiz not found.'); }
$stu=portal_student((int)$m['batch_id']); $home=is_student_user()?'my_class.php?id='.(int)$m['batch_id'].'&tab=quiz':'learn.php?t='.$m['access_token'];
if (!$stu) { header('Location: '.$home); exit; }
$sid=(int)$stu['id']; $inst=setting('institute_name',APP_NAME);
$qs=db()->prepare('SELECT * FROM quiz_questions WHERE material_id=? ORDER BY position,id'); $qs->execute([$id]); $questions=$qs->fetchAll();
if (!$questions) { header('Location: '.$home); exit; }
$limit=$m['time_limit_min']?(int)$m['time_limit_min']*60:0;
function head(string $t,string $inst): void { ?><!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title><?=e($t)?></title>
<link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css" rel="stylesheet"><link href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.3/font/bootstrap-icons.css" rel="stylesheet"><link href="assets/css/oneui.css" rel="stylesheet"></head><body>
<div style="max-width:760px;margin:0 auto;padding:16px"><div class="brand"><i class="bi bi-mortarboard-fill"></i><span><?=e($inst)?></span></div><?php }
function foot(): void { echo '</div></body></html>'; }
// finish any attempt left open past its time (counts as an attempt)
$un=db()->prepare('SELECT * FROM quiz_attempts WHERE material_id=? AND student_id=? AND submitted_at IS NULL ORDER BY id DESC LIMIT 1'); $un->execute([$id,$sid]); $open=$un->fetch();
if ($open && $limit && time()>strtotime($open['started_at'])+$limit+60) {
  db()->prepare('UPDATE quiz_attempts SET submitted_at=NOW(),score=0,total=?,answers=?,overtime=1 WHERE id=?')->execute([count($questions),'{}',$open['id']]); $open=null;
}
function grade(array $questions,array $post): array { $score=0; $ans=[]; foreach($questions as $z){ $a=strtoupper((string)($post[$z['id']]??'')); if(!in_array($a,['A','B','C','D'],true)) $a=''; $ans[$z['id']]=$a; if($a!==''&&$a===$z['correct']) $score++; } return [$score,$ans]; }
if ($_SERVER['REQUEST_METHOD']==='POST') {
  csrf_check(); $aid=(int)($_POST['attempt']??0);
  $a=db()->prepare('SELECT * FROM quiz_attempts WHERE id=? AND material_id=? AND student_id=? AND submitted_at IS NULL'); $a->execute([$aid,$id,$sid]); $att=$a->fetch();
  if ($att) {
    [$score,$ans]=grade($questions,$_POST['a']??[]); $over=($limit&&time()>strtotime($att['started_at'])+$limit+60)?1:0;
    db()->prepare('UPDATE quiz_attempts SET submitted_at=NOW(),score=?,total=?,answers=?,overtime=? WHERE id=?')->execute([$over?0:$score,count($questions),json_encode($ans),$over,$aid]);
    db()->prepare('INSERT INTO activity_logs (branch_id,action,details,ip_address) VALUES (?,?,?,?)')->execute([$m['branch_id'],'quiz_submit',$stu['student_code'].' quiz '.$id,$_SERVER['REMOTE_ADDR']??'']);
    header('Location: learn_quiz.php?id='.$id.'&result='.$aid); exit;
  }
  header('Location: learn_quiz.php?id='.$id); exit;
}
$resId=gint('result');
if ($resId) {
  $r=db()->prepare('SELECT * FROM quiz_attempts WHERE id=? AND material_id=? AND student_id=? AND submitted_at IS NOT NULL'); $r->execute([$resId,$id,$sid]); $att=$r->fetch();
  if (!$att) { header('Location: learn_quiz.php?id='.$id); exit; }
  $ans=json_decode((string)$att['answers'],true)?:[]; $pct=$att['total']?round($att['score']/$att['total']*100):0;
  head($m['title'],$inst); ?>
<div class="card-ui text-center mb-3"><div class="lbl"><?=e($m['title'])?></div><div class="stat" style="font-size:2.4rem"><?=(int)$att['score']?> / <?=(int)$att['total']?></div><div class="fw-bold" style="color:<?=$pct>=50?'#1e9e5a':'#d9534f'?>"><?=$pct?>%</div>
<?php if($att['overtime']):?><div class="alert alert-warning rounded-4 mt-2 mb-0 py-2">The time limit had passed, so this attempt was not scored.</div><?php endif;?></div>
<?php if(!$att['overtime']) foreach($questions as $i=>$z): $mine=$ans[$z['id']]??''; $ok=$mine===$z['correct'];?>
<div class="card-ui mb-2"><div><b><?=$i+1?>.</b> <?=nl2br(e($z['question']))?></div>
<?php foreach(['A'=>'opt_a','B'=>'opt_b','C'=>'opt_c','D'=>'opt_d'] as $l=>$col): $cls=$l===$z['correct']?'text-success fw-bold':($l===$mine?'text-danger':'lbl');?><div class="<?=$cls?>"><?=$l?>. <?=e($z[$col])?><?=$l===$z['correct']?' ✓':($l===$mine?' ✗ (your answer)':'')?></div><?php endforeach;?>
<?php if($mine===''):?><div class="lbl">Not answered</div><?php endif;?><?php if($z['explanation']):?><div class="lbl mt-1">Explanation: <?=e($z['explanation'])?></div><?php endif;?></div><?php endforeach;?>
<a class="btn-ui d-block text-center text-decoration-none mt-3" href="<?=e($home)?>">Back to my class</a><?php foot(); exit;
}
$ua=db()->prepare('SELECT COUNT(*) FROM quiz_attempts WHERE material_id=? AND student_id=? AND submitted_at IS NOT NULL'); $ua->execute([$id,$sid]); $used=(int)$ua->fetchColumn();
$nowT=time(); $notOpen=$m['opens_at']&&$nowT<strtotime($m['opens_at']); $closed=$m['closes_at']&&$nowT>strtotime($m['closes_at']);
if (!$open && ($notOpen||$closed||$used>=(int)$m['max_attempts'])) {
  $past=db()->prepare('SELECT id,score,total,submitted_at FROM quiz_attempts WHERE material_id=? AND student_id=? AND submitted_at IS NOT NULL ORDER BY id DESC'); $past->execute([$id,$sid]); $ps=$past->fetchAll();
  head($m['title'],$inst); ?>
<div class="card-ui mb-3"><h5><?=e($m['title'])?></h5><div class="alert alert-info rounded-4 mb-0"><?=$notOpen?'This quiz opens on '.e(date('j M Y, g:i A',strtotime($m['opens_at']))).'.':($closed?'This quiz is closed.':'You have used all '.(int)$m['max_attempts'].' attempt(s).')?></div></div>
<?php foreach($ps as $p):?><a class="card-ui d-block mb-2 text-decoration-none text-reset" href="learn_quiz.php?id=<?=$id?>&result=<?=$p['id']?>"><b><?=(int)$p['score']?> / <?=(int)$p['total']?></b> <span class="lbl"><?=e(date('j M Y, g:i A',strtotime($p['submitted_at'])))?> · view answers</span></a><?php endforeach;?>
<a class="btn-ui d-block text-center text-decoration-none mt-3" href="<?=e($home)?>">Back to my class</a><?php foot(); exit; }
if (!$open) { db()->prepare('INSERT INTO quiz_attempts (material_id,student_id,started_at) VALUES (?,?,NOW())')->execute([$id,$sid]); $aid=(int)db()->lastInsertId(); $started=time(); }
else { $aid=(int)$open['id']; $started=strtotime($open['started_at']); }
$remain=$limit?max(1,$started+$limit-time()):0;
head($m['title'],$inst); ?>
<div class="card-ui mb-3 position-sticky" style="top:8px;z-index:5"><div class="d-flex justify-content-between align-items-center"><div><b><?=e($m['title'])?></b><div class="lbl"><?=count($questions)?> questions · attempt <?=$used+1?> of <?=(int)$m['max_attempts']?></div></div><?php if($limit):?><div class="stat" style="font-size:1.3rem" id="clock">--:--</div><?php endif;?></div></div>
<?php if($m['description']):?><div class="card-ui mb-3"><?=nl2br(e($m['description']))?></div><?php endif;?>
<form method="post" id="qf"><input type="hidden" name="csrf" value="<?=csrf()?>"><input type="hidden" name="attempt" value="<?=$aid?>">
<?php foreach($questions as $i=>$z):?><div class="card-ui mb-2"><div class="mb-2"><b><?=$i+1?>.</b> <?=nl2br(e($z['question']))?></div>
<?php foreach(['A'=>'opt_a','B'=>'opt_b','C'=>'opt_c','D'=>'opt_d'] as $l=>$col):?><label class="d-block py-1"><input type="radio" class="form-check-input me-2" name="a[<?=$z['id']?>]" value="<?=$l?>"> <?=$l?>. <?=e($z[$col])?></label><?php endforeach;?></div><?php endforeach;?>
<button class="btn-ui w-100 mb-4" onclick="return confirm('Submit your answers? You cannot change them afterwards.')">Submit answers</button></form>
<?php if($limit):?><script>var left=<?=(int)$remain?>,c=document.getElementById('clock'),f=document.getElementById('qf');
function tick(){var m=Math.floor(left/60),s=left%60;c.textContent=(m<10?'0':'')+m+':'+(s<10?'0':'')+s;if(left<=60)c.style.color='#d9534f';if(left<=0){f.submit();return;}left--;setTimeout(tick,1000);}tick();</script><?php endif;?>
<?php foot();
