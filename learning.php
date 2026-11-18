<?php
require_once __DIR__.'/includes/bootstrap.php';
require_once __DIR__.'/includes/learn_lib.php';
require_menu('materials'); refresh_batches();
$s=branch_scope(); $me=(int)$_SESSION['user']['id']; $canUp=can('upload_material'); $mgr=in_array($_SESSION['user']['role'],['super_admin','admin','branch_manager','administrator'],true);
$bid=gint('batch');
$q=db()->prepare('SELECT b.*,c.name cname,t.name tname,br.name brname,u.full_name tch FROM batches b JOIN courses c ON c.id=b.course_id JOIN course_types t ON t.id=c.type_id JOIN branches br ON br.id=b.branch_id LEFT JOIN users u ON u.id=b.teacher_id WHERE b.id=?');
$q->execute([$bid]); $b=$q->fetch();
if (!$b || ($s!==null && (int)$b['branch_id']!==$s) || (is_teacher() && !teacher_has_batch($bid))) { http_response_code(404); exit('Class not found.'); }
$type=genum('type',array_keys(LEARN_TYPES))?:'lecture'; $errs=[]; $old=[]; $open=false;
if ($_SERVER['REQUEST_METHOD']==='POST') {
  csrf_check(); if(!$canUp){ http_response_code(403); exit('Access denied.'); }
  $act=$_POST['act']??''; $back='learning.php?batch='.$bid;
  if ($act==='add') {
    $old=$_POST; $open=true; $tp=$_POST['type']??''; if(!isset(LEARN_TYPES[$tp])) $tp='lecture'; $type=$tp;
    $title=trim($_POST['title']??''); $desc=trim($_POST['description']??''); $pub=empty($_POST['publish'])?0:1;
    $url=null; $pass=null; $starts=null; $dur=null; $due=null; $max=null; $tl=null; $op=null; $cl=null; $att=1; $stored=$orig=$size=null;
    if($title===''||mb_strlen($title)>150) $errs[]='A title (up to 150 characters) is required.';
    $link=trim($_POST['url']??'');
    if ($tp==='notice') { if($desc==='') $errs[]='Write the announcement text.'; if($link!==''){ [$url,$er]=clean_url($link); if($er) $errs[]=$er; } }
    if ($tp==='lecture') { [$url,$er]=clean_url($link); if($er) $errs[]=$er; }
    if ($tp==='live') {
      [$url,$er]=clean_url($link,true); if($er) $errs[]=$er;
      $starts=dt_local($_POST['starts_at']??''); if(!$starts) $errs[]='Choose the date and time of the live class.';
      $dur=(int)($_POST['duration_min']??60); if($dur<5||$dur>600) $errs[]='Duration must be 5 to 600 minutes.';
      $pass=mb_substr(trim($_POST['passcode']??''),0,60)?:null;
    }
    if ($tp==='material'||$tp==='reading'||$tp==='homework') {
      [$stored,$orig,$size,$er]=save_upload($_FILES['file']??null); if($er) $errs[]=$er;
      if ($tp!=='homework') {
        if ($link!=='') { [$url,$er2]=clean_url($link); if($er2) $errs[]=$er2; }
        if (!$er && !$stored && !$url && !$errs) $errs[]='Add a file or a link.';
      }
    }
    if ($tp==='homework') {
      $d=$_POST['due_date']??''; if($d!==''){ if(strtotime($d)) $due=$d; else $errs[]='Due date is not valid.'; }
      if(($_POST['max_marks']??'')!==''){ $max=(float)$_POST['max_marks']; if($max<=0||$max>1000) $errs[]='Maximum marks must be between 1 and 1000.'; }
    }
    if ($tp==='quiz') {
      $tl=(int)($_POST['time_limit_min']??0); if($tl<0||$tl>300) $errs[]='Time limit must be 0 (none) to 300 minutes.'; if($tl===0) $tl=null;
      $op=dt_local($_POST['opens_at']??''); $cl=dt_local($_POST['closes_at']??''); if($op&&$cl&&$cl<=$op) $errs[]='The closing time must be after the opening time.';
      $att=(int)($_POST['max_attempts']??1); if($att<1||$att>10) $errs[]='Attempts must be 1 to 10.';
    }
    if ($errs && $stored) { @unlink(dirname(__FILE__).'/uploads/'.$stored); }
    if (!$errs) {
      db()->prepare('INSERT INTO materials (batch_id,branch_id,kind,title,description,url,passcode,starts_at,duration_min,due_date,max_marks,time_limit_min,opens_at,closes_at,max_attempts,publish,stored_name,original_name,size,uploaded_by) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)')
        ->execute([$bid,$b['branch_id'],$tp,$title,$desc?:null,$url,$pass,$starts,$dur,$due,$max,$tl,$op,$cl,$att,$pub,$stored,$orig,$size,$me]);
      $nid=(int)db()->lastInsertId(); log_activity('learning_add',LEARN_TYPES[$tp][2].': '.$title); flash_set('msg',LEARN_TYPES[$tp][2].' added.');
      header('Location: '.($tp==='quiz'?'quiz_edit.php?id='.$nid.'&new=1':$back.'&type='.$tp)); exit;
    }
  } elseif ($act==='toggle' || $act==='delete') {
    $id=(int)($_POST['id']??0); $x=db()->prepare('SELECT * FROM materials WHERE id=? AND batch_id=?'); $x->execute([$id,$bid]); $m=$x->fetch();
    if (!$m) flash_set('msg','Item not found.');
    elseif ($act==='toggle') { db()->prepare('UPDATE materials SET publish=1-publish WHERE id=?')->execute([$id]); flash_set('msg',$m['publish']?'Hidden from students.':'Now visible to students.'); }
    elseif ($mgr || (int)$m['uploaded_by']===$me) {
      if ($m['stored_name']) @unlink(__DIR__.'/uploads/'.basename($m['stored_name']));
      db()->prepare('DELETE FROM materials WHERE id=?')->execute([$id]); log_activity('learning_delete',$m['title']); flash_set('msg','Deleted.');
    } else flash_set('msg','You cannot delete that item.');
    header('Location: '.$back.'&type='.$m['kind']); exit;
  } elseif ($act==='portal') {
    $mode=$_POST['mode']??'';
    if ($mode==='on') { $tok=$b['access_token']?:bin2hex(random_bytes(16)); db()->prepare('UPDATE batches SET portal_on=1,access_token=? WHERE id=?')->execute([$tok,$bid]); flash_set('msg','Student access is ON.'); }
    elseif ($mode==='off') { db()->prepare('UPDATE batches SET portal_on=0 WHERE id=?')->execute([$bid]); flash_set('msg','Student access is OFF.'); }
    elseif ($mode==='regen') { db()->prepare('UPDATE batches SET access_token=?,portal_on=1 WHERE id=?')->execute([bin2hex(random_bytes(16)),$bid]); flash_set('msg','New student link created. The old link no longer works.'); }
    log_activity('portal_'.$mode,"batch $bid"); header('Location: '.$back.'&type='.$type); exit;
  }
}
$pageKey='materials'; require __DIR__.'/includes/header.php'; $msg=flash_get('msg');
$cn=array_column(db()->query("SELECT kind,COUNT(*) c FROM materials WHERE batch_id=".(int)$bid." GROUP BY kind")->fetchAll(),'c','kind');
$fq=gstr('q'); $w=''; $p=[$bid,$type]; if($fq!==''){ $w=' AND (m.title LIKE ? OR m.description LIKE ?)'; array_push($p,"%$fq%","%$fq%"); }
$cq=db()->prepare("SELECT COUNT(*) FROM materials m WHERE m.batch_id=? AND m.kind=?$w"); $cq->execute($p); $total=(int)$cq->fetchColumn(); $per=pg_per(); [$pg,$pages,$off]=pg_page($total,$per);
$order=$type==='live'?'m.starts_at DESC':'m.id DESC';
$lq=db()->prepare("SELECT m.*,(SELECT full_name FROM users WHERE id=m.uploaded_by) up,(SELECT COUNT(*) FROM quiz_questions z WHERE z.material_id=m.id) nq FROM materials m WHERE m.batch_id=? AND m.kind=?$w ORDER BY $order LIMIT $per OFFSET $off"); $lq->execute($p); $rows=$lq->fetchAll();
$o=fn($k)=>e($old[$k]??''); $kb=fn($n)=>$n>=1048576?round($n/1048576,1).' MB':max(1,round($n/1024)).' KB';
$T=LEARN_TYPES[$type]; $pLink=$b['access_token']?portal_url($b['access_token']):'';
?>
<?php if($msg):?><div class="alert alert-info rounded-4"><?=e($msg)?></div><?php endif;?>
<a href="batch_view.php?id=<?=$bid?>" class="lbl text-decoration-none">← Back to class profile</a>
<div class="card-ui my-3"><div class="stat" style="font-size:1.3rem">Learning hub</div>
 <div class="lbl"><?=e($b['tname'])?> · <?=e($b['cname'])?> · <?=e($b['name'])?> (<?=$b['batch_year']?>) · <?=e($b['brname'])?><?=$b['tch']?' · '.e($b['tch']):''?></div>
 <div class="lbl mt-1">Everything here belongs to this class only, and only its students can see it.</div></div>
<div class="card-ui mb-3"><div class="d-flex flex-wrap justify-content-between align-items-center gap-2"><div><b>Student access</b> <span class="badge rounded-pill text-bg-<?=$b['portal_on']?'success':'secondary'?>"><?=$b['portal_on']?'ON':'OFF'?></span>
 <div class="lbl">Students open a private link and confirm with their Student ID and contact number to see published items.</div></div>
 <?php if($canUp):?><form method="post" class="d-flex gap-2 flex-wrap"><input type="hidden" name="csrf" value="<?=csrf()?>"><input type="hidden" name="act" value="portal">
  <?php if($b['portal_on']):?><button class="btn btn-sm btn-light rounded-pill" name="mode" value="off">Turn off</button><?php else:?><button class="btn-ui btn-sm" name="mode" value="on">Turn on</button><?php endif;?>
  <?php if($b['access_token']):?><button class="btn btn-sm btn-light rounded-pill" name="mode" value="regen" onclick="return confirm('Create a new link? The old link will stop working.')">New link</button><?php endif;?></form><?php endif;?></div>
 <?php if($b['portal_on']&&$pLink):?><div class="input-group mt-2"><input class="form-control" readonly value="<?=e($pLink)?>" onclick="this.select()"><button type="button" class="btn btn-light" data-copy="<?=e($pLink)?>" onclick="copyText(this)">Copy link</button><a class="btn btn-light" href="<?=e($pLink)?>" target="_blank" rel="noopener noreferrer">Open</a></div>
 <div class="lbl mt-1">Share it only with this class's students. Hidden items never appear to students.</div><?php endif;?></div>
<ul class="nav nav-pills mb-3 flex-nowrap overflow-auto"><?php foreach(LEARN_TYPES as $k=>$t):?><li class="nav-item"><a class="nav-link <?=$k===$type?'active':''?>" href="learning.php?batch=<?=$bid?>&type=<?=$k?>"><i class="bi <?=$t[1]?>"></i> <?=e($t[0])?> <span class="badge rounded-pill text-bg-light"><?=(int)($cn[$k]??0)?></span></a></li><?php endforeach;?></ul>
<div class="d-flex gap-2 mb-3"><form method="get" class="d-flex gap-2 flex-grow-1"><input type="hidden" name="batch" value="<?=$bid?>"><input type="hidden" name="type" value="<?=e($type)?>"><input class="form-control" name="q" value="<?=e($fq)?>" placeholder="Search <?=e(strtolower($T[0]))?>"><button class="btn-ui"><i class="bi bi-search"></i></button></form>
 <?php if($canUp):?><button class="btn-ui text-nowrap" data-bs-toggle="modal" data-bs-target="#addModal"><i class="bi bi-plus-lg"></i> Add</button><?php endif;?></div>
<div class="card-ui">
<?php foreach($rows as $r): $ls=$type==='live'?live_state($r['starts_at'],$r['duration_min']):null; $over=$type==='homework'&&$r['due_date']&&$r['due_date']<date('Y-m-d');?>
 <div class="border-top py-3"><div class="d-flex flex-wrap justify-content-between gap-2">
  <div style="min-width:0"><b><?=e($r['title'])?></b> <span class="badge rounded-pill text-bg-<?=$r['publish']?'success':'secondary'?>"><?=$r['publish']?'Visible':'Hidden'?></span>
   <?php if($ls):?><span class="badge rounded-pill text-bg-<?=$ls[1]?>"><?=e($ls[0])?></span><?php endif;?>
   <?php if($r['description']):?><div class="lbl"><?=nl2br(e($r['description']))?></div><?php endif;?>
   <?php if($type==='live'):?><div class="lbl"><i class="bi bi-clock"></i> <?=e(date('D j M Y, g:i A',strtotime($r['starts_at'])))?> · <?=(int)$r['duration_min']?> min<?=$r['passcode']?' · Passcode: '.e($r['passcode']):''?></div><?php endif;?>
   <?php if($r['url']):?><div class="lbl"><i class="bi bi-link-45deg"></i> <?=e(url_label($r['url']))?></div><?php endif;?>
   <?php if($r['original_name']):?><div class="lbl"><i class="bi bi-paperclip"></i> <?=e($r['original_name'])?> · <?=$kb((int)$r['size'])?></div><?php endif;?>
   <?php if($type==='homework'&&($r['due_date']||$r['max_marks']!==null)):?><div class="lbl <?=$over?'text-danger':''?>"><?=$r['due_date']?'Due '.e($r['due_date']).($over?' (past)':''):''?><?=$r['max_marks']!==null?' · max '.rtrim(rtrim(number_format((float)$r['max_marks'],2,'.',''),'0'),'.'):''?></div><?php endif;?>
   <?php if($type==='quiz'):?><div class="lbl"><?=(int)$r['nq']?> question<?=$r['nq']==1?'':'s'?> · <?=$r['time_limit_min']?(int)$r['time_limit_min'].' min':'no time limit'?> · <?=(int)$r['max_attempts']?> attempt<?=$r['max_attempts']>1?'s':''?><?=$r['opens_at']?' · opens '.e(date('j M, g:i A',strtotime($r['opens_at']))):''?><?=$r['closes_at']?' · closes '.e(date('j M, g:i A',strtotime($r['closes_at']))):''?></div><?php endif;?>
   <div class="lbl">Added <?=e(substr($r['created_at'],0,10))?> by <?=e($r['up']??'—')?></div></div>
  <div class="d-flex flex-wrap gap-1 align-items-start">
   <?php if($r['url']):?><a class="btn btn-sm btn-<?=($ls&&$ls[2]&&$ls[1]!=='dark')?'danger':'light'?> rounded-pill" href="<?=e($r['url'])?>" target="_blank" rel="noopener noreferrer"><i class="bi bi-box-arrow-up-right"></i> <?=$type==='live'?'Join':($type==='lecture'?'Watch':'Open')?></a>
    <button type="button" class="btn btn-sm btn-light rounded-pill" data-copy="<?=e($r['url'])?>" onclick="copyText(this)">Copy link</button><?php endif;?>
   <?php if($r['stored_name']):?><a class="btn btn-sm btn-light rounded-pill" href="download.php?id=<?=$r['id']?>"><i class="bi bi-download"></i> Download</a><?php endif;?>
   <?php if($type==='homework'):?><a class="btn btn-sm btn-light rounded-pill" href="homework.php?id=<?=$r['id']?>">Tracking</a><?php endif;?>
   <?php if($type==='quiz'):?><a class="btn btn-sm btn-light rounded-pill" href="quiz_edit.php?id=<?=$r['id']?>">Questions &amp; results</a><?php endif;?>
   <?php if($canUp):?><form method="post"><input type="hidden" name="csrf" value="<?=csrf()?>"><input type="hidden" name="act" value="toggle"><input type="hidden" name="id" value="<?=$r['id']?>"><button class="btn btn-sm btn-light rounded-pill"><?=$r['publish']?'Hide':'Publish'?></button></form>
   <?php if($mgr||(int)$r['uploaded_by']===$me):?><form method="post" onsubmit="return confirm('Delete this item?')"><input type="hidden" name="csrf" value="<?=csrf()?>"><input type="hidden" name="act" value="delete"><input type="hidden" name="id" value="<?=$r['id']?>"><button class="btn btn-sm btn-light rounded-pill">Delete</button></form><?php endif; endif;?></div></div></div>
<?php endforeach; if(!$rows):?><div class="lbl">Nothing added yet.</div><?php endif;?></div>
<?php pg_render($total,$per);?>
<?php if($canUp):?>
<div class="modal fade" id="addModal" tabindex="-1"><div class="modal-dialog modal-dialog-centered modal-fullscreen-sm-down modal-dialog-scrollable"><div class="modal-content" style="border-radius:24px"><form method="post" enctype="multipart/form-data">
<input type="hidden" name="csrf" value="<?=csrf()?>"><input type="hidden" name="act" value="add"><input type="hidden" name="type" value="<?=e($type)?>">
<div class="modal-header"><h5 class="modal-title">Add: <?=e($T[2])?></h5><button type="button" class="btn-close" data-bs-dismiss="modal"></button></div>
<div class="modal-body d-grid gap-2"><?php foreach($errs as $m):?><div class="alert alert-danger rounded-4 py-2 mb-0"><?=e($m)?></div><?php endforeach;?>
<input class="form-control" name="title" maxlength="150" placeholder="Title *" value="<?=$o('title')?>" required>
<textarea class="form-control" name="description" rows="2" placeholder="Notes / instructions (optional)"><?=$o('description')?></textarea>
<?php if($type==='lecture'):?><input class="form-control" name="url" placeholder="Recording link * (YouTube, Vimeo, Google Drive, Zoom recording…)" value="<?=$o('url')?>" required>
<?php elseif($type==='live'):?><input class="form-control" name="url" placeholder="Zoom / Google Meet / Teams link *" value="<?=$o('url')?>" required>
 <div class="row g-2"><div class="col-12 col-md-7"><label class="lbl">Date and time</label><input class="form-control" type="datetime-local" name="starts_at" value="<?=$o('starts_at')?>" required></div>
 <div class="col-6 col-md-5"><label class="lbl">Minutes</label><input class="form-control" type="number" min="5" max="600" name="duration_min" value="<?=$old['duration_min']??60?>"></div></div>
 <input class="form-control" name="passcode" maxlength="60" placeholder="Passcode (optional)" value="<?=$o('passcode')?>">
<?php elseif($type==='material'||$type==='reading'):?><input class="form-control" name="url" placeholder="Link (optional if you upload a file)" value="<?=$o('url')?>">
 <div><label class="lbl">File (max 10 MB: <?=e(implode(', ',UP_EXT))?>)</label><input class="form-control" type="file" name="file" accept=".<?=e(implode(',.',UP_EXT))?>"></div>
<?php elseif($type==='homework'):?><div class="row g-2"><div class="col-6"><label class="lbl">Due date</label><input class="form-control" type="date" name="due_date" value="<?=$o('due_date')?>"></div><div class="col-6"><label class="lbl">Max marks</label><input class="form-control" type="number" step="0.01" min="1" name="max_marks" value="<?=$o('max_marks')?>"></div></div>
 <div><label class="lbl">Attachment (optional, max 10 MB)</label><input class="form-control" type="file" name="file" accept=".<?=e(implode(',.',UP_EXT))?>"></div>
<?php elseif($type==='notice'):?><input class="form-control" name="url" placeholder="Link (optional)" value="<?=$o('url')?>">
<?php elseif($type==='quiz'):?><div class="row g-2"><div class="col-6"><label class="lbl">Time limit (min, 0 = none)</label><input class="form-control" type="number" min="0" max="300" name="time_limit_min" value="<?=$old['time_limit_min']??30?>"></div><div class="col-6"><label class="lbl">Attempts allowed</label><input class="form-control" type="number" min="1" max="10" name="max_attempts" value="<?=$old['max_attempts']??1?>"></div>
 <div class="col-12 col-md-6"><label class="lbl">Opens (optional)</label><input class="form-control" type="datetime-local" name="opens_at" value="<?=$o('opens_at')?>"></div><div class="col-12 col-md-6"><label class="lbl">Closes (optional)</label><input class="form-control" type="datetime-local" name="closes_at" value="<?=$o('closes_at')?>"></div></div>
 <div class="lbl">You add the questions on the next screen.</div><?php endif;?>
<label><input type="checkbox" name="publish" value="1" <?=($_SERVER['REQUEST_METHOD']!=='POST'||!empty($old['publish']))?'checked':''?>> Visible to students now</label></div>
<div class="modal-footer"><button class="btn-ui">Save</button></div></form></div></div></div>
<?php if($open):?><?php ob_start();?><script>new bootstrap.Modal(document.getElementById('addModal')).show();</script><?php $pageScript=ob_get_clean(); endif; endif;?>
<?php require __DIR__.'/includes/footer.php';
