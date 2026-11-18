<?php
require_once __DIR__.'/includes/bootstrap.php';
require_once __DIR__.'/includes/student_lib.php';
require_once __DIR__.'/includes/learn_lib.php';
stu_guard('sclasses'); $bid=gint('id'); $sid=stu_id();
if (!$bid || !stu_in_batch($bid)) { http_response_code(404); exit('Class not found.'); }
$pageKey='sclasses'; require __DIR__.'/includes/header.php';
$q=db()->prepare('SELECT b.*,c.name cname,t.name tname,u.full_name tch FROM batches b JOIN courses c ON c.id=b.course_id JOIN course_types t ON t.id=c.type_id LEFT JOIN users u ON u.id=b.teacher_id WHERE b.id=?'); $q->execute([$bid]); $b=$q->fetch();
$t=gstr('type')?:gstr('tab'); $type=isset(LEARN_TYPES[$t])?$t:'notice';
$cn=array_column(db()->query("SELECT kind,COUNT(*) c FROM materials WHERE batch_id=".(int)$bid." AND publish=1 AND (kind<>'quiz' OR EXISTS (SELECT 1 FROM quiz_questions z WHERE z.material_id=materials.id)) GROUP BY kind")->fetchAll(),'c','kind');
if(!isset($_GET['type'])&&!isset($_GET['tab'])){ foreach(['notice','live','lecture','material','homework','quiz','reading'] as $k) if(!empty($cn[$k])){ $type=$k; break; } }
$qq=gstr('q'); $w=''; $p=[$sid,$sid,$sid,$bid,$type]; if($qq!==''){ $w=' AND (m.title LIKE ? OR m.description LIKE ?)'; array_push($p,"%$qq%","%$qq%"); }
$quizOk=$type==='quiz'?' AND EXISTS (SELECT 1 FROM quiz_questions z WHERE z.material_id=m.id)':'';
$cp=$p; array_shift($cp); array_shift($cp); array_shift($cp);
$cq=db()->prepare("SELECT COUNT(*) FROM materials m WHERE m.batch_id=? AND m.kind=? AND m.publish=1$w$quizOk"); $cq->execute($cp); $total=(int)$cq->fetchColumn(); $per=pg_per(); [$pg,$pages,$off]=pg_page($total,$per);
$order=$type==='live'?'m.starts_at DESC':'m.id DESC';
$lq=db()->prepare("SELECT m.*,(SELECT COUNT(*) FROM quiz_questions z WHERE z.material_id=m.id) nq,(SELECT COUNT(*) FROM quiz_attempts a WHERE a.material_id=m.id AND a.student_id=? AND a.submitted_at IS NOT NULL) used,(SELECT status FROM homework_records h WHERE h.material_id=m.id AND h.student_id=?) hstat,(SELECT id FROM assignment_submissions sb WHERE sb.material_id=m.id AND sb.student_id=?) subid FROM materials m WHERE m.batch_id=? AND m.kind=? AND m.publish=1$w$quizOk ORDER BY $order LIMIT $per OFFSET $off"); $lq->execute($p); $rows=$lq->fetchAll();
$kb=fn($n)=>$n>=1048576?round($n/1048576,1).' MB':max(1,round($n/1024)).' KB';
?>
<a href="my_classes.php" class="lbl text-decoration-none">← My classes</a>
<div class="card-ui my-3"><div class="stat" style="font-size:1.25rem"><?=e($b['cname'])?> · <?=e($b['name'])?> (<?=$b['batch_year']?>)</div>
<div class="lbl"><?=e($b['tname'])?> · <?=e($b['tch']??'Teacher to be announced')?> · <?=e(substr($b['start_time'],0,5))?>–<?=e(substr($b['end_time'],0,5))?><?=$b['class_days']?' · '.e($b['class_days']):''?></div></div>
<ul class="nav nav-pills mb-3 flex-nowrap overflow-auto"><?php foreach(LEARN_TYPES as $k=>$T):?><li class="nav-item"><a class="nav-link <?=$k===$type?'active':''?>" href="my_class.php?id=<?=$bid?>&type=<?=$k?>"><i class="bi <?=$T[1]?>"></i> <?=e($T[0])?> <span class="badge rounded-pill text-bg-light"><?=(int)($cn[$k]??0)?></span></a></li><?php endforeach;?></ul>
<form method="get" class="d-flex gap-2 mb-3"><input type="hidden" name="id" value="<?=$bid?>"><input type="hidden" name="type" value="<?=e($type)?>"><input class="form-control" name="q" value="<?=e($qq)?>" placeholder="Search <?=e(strtolower(LEARN_TYPES[$type][0]))?>"><button class="btn-ui"><i class="bi bi-search"></i></button></form>
<?php foreach($rows as $r): $ls=$type==='live'?live_state($r['starts_at'],$r['duration_min']):null; $over=$type==='homework'&&$r['due_date']&&$r['due_date']<date('Y-m-d');?>
<div class="card-ui mb-2"><div class="d-flex flex-wrap justify-content-between gap-2"><div style="min-width:0"><b><?=e($r['title'])?></b> <?php if($ls):?><span class="badge rounded-pill text-bg-<?=$ls[1]?>"><?=e($ls[0])?></span><?php endif;?>
 <?php if($r['description']):?><div class="lbl"><?=nl2br(e($r['description']))?></div><?php endif;?>
 <?php if($type==='live'):?><div class="lbl"><i class="bi bi-clock"></i> <?=e(date('D j M Y, g:i A',strtotime($r['starts_at'])))?> · <?=(int)$r['duration_min']?> min<?=$r['passcode']?' · Passcode: <b>'.e($r['passcode']).'</b>':''?></div><?php endif;?>
 <?php if($r['url']):?><div class="lbl"><i class="bi bi-link-45deg"></i> <?=e(url_label($r['url']))?></div><?php endif;?>
 <?php if($r['original_name']):?><div class="lbl"><i class="bi bi-paperclip"></i> <?=e($r['original_name'])?> · <?=$kb((int)$r['size'])?></div><?php endif;?>
 <?php if($type==='homework'):?><div class="lbl <?=$over?'text-danger':''?>"><?=$r['due_date']?'Due '.e(date('j M Y',strtotime($r['due_date']))).($over?' (past)':''):'No due date'?></div>
  <div class="mt-1"><?php $s1=$r['hstat']?:($r['subid']?'submitted':'pending'); ?><span class="badge rounded-pill text-bg-<?=['submitted'=>'success','late'=>'warning','missing'=>'danger','pending'=>'secondary'][$s1]?>"><?=e(ucfirst($s1))?></span></div><?php endif;?>
 <?php if($type==='quiz'):?><div class="lbl"><?=(int)$r['nq']?> questions · <?=$r['time_limit_min']?(int)$r['time_limit_min'].' min':'no time limit'?> · attempts <?=(int)$r['used']?>/<?=(int)$r['max_attempts']?><?=$r['closes_at']?' · closes '.e(date('j M, g:i A',strtotime($r['closes_at']))):''?></div><?php endif;?>
 <div class="lbl"><?=e(date('j M Y',strtotime($r['created_at'])))?></div></div>
 <div class="d-flex flex-wrap gap-1 align-items-start">
  <?php if($r['url']):?><a class="btn-ui text-decoration-none" style="padding:8px 18px" href="<?=e($r['url'])?>" target="_blank" rel="noopener noreferrer"><?=$type==='live'?'Join class':($type==='lecture'?'Watch':'Open')?></a><button type="button" class="btn btn-light rounded-pill btn-sm" data-copy="<?=e($r['url'])?>" onclick="copyText(this)">Copy link</button><?php endif;?>
  <?php if($r['stored_name']):?><a class="btn btn-light rounded-pill btn-sm" href="learn_file.php?id=<?=$r['id']?>"><i class="bi bi-download"></i> Download</a><?php endif;?>
  <?php if($type==='homework'):?><a class="btn-ui text-decoration-none" style="padding:8px 18px" href="my_assignment.php?id=<?=$r['id']?>">Open</a><?php endif;?>
  <?php if($type==='quiz'):?><a class="btn-ui text-decoration-none" style="padding:8px 18px" href="learn_quiz.php?id=<?=$r['id']?>"><?=$r['used']>0?'Open':'Start'?></a><?php endif;?></div></div></div>
<?php endforeach; if(!$rows):?><div class="card-ui lbl">Nothing here yet.</div><?php endif;?>
<?php pg_render($total,$per); require __DIR__.'/includes/footer.php';
