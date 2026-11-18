<?php
require_once __DIR__.'/includes/bootstrap.php';
require_once __DIR__.'/includes/student_lib.php';
require_once __DIR__.'/includes/learn_lib.php';
stu_guard('sassign'); $id=gint('id'); $sid=stu_id();
$q=db()->prepare("SELECT m.*,b.name bname,b.batch_year,c.name cname FROM materials m JOIN batches b ON b.id=m.batch_id JOIN courses c ON c.id=b.course_id WHERE m.id=? AND m.kind='homework' AND m.publish=1"); $q->execute([$id]); $m=$q->fetch();
if (!$m || !stu_in_batch((int)$m['batch_id'])) { http_response_code(404); exit('Assignment not found.'); }
$h=db()->prepare('SELECT * FROM homework_records WHERE material_id=? AND student_id=?'); $h->execute([$id,$sid]); $rec=$h->fetch();
$sq=db()->prepare('SELECT * FROM assignment_submissions WHERE material_id=? AND student_id=?'); $sq->execute([$id,$sid]); $sub=$sq->fetch();
$locked=$rec && $rec['marks']!==null; $err='';
if ($_SERVER['REQUEST_METHOD']==='POST') {
  csrf_check();
  if ($locked) $err='This assignment has been marked, so it can no longer be changed.';
  else {
    $note=mb_substr(trim($_POST['note']??''),0,500); [$stored,$orig,$size,$er]=save_upload($_FILES['file']??null);
    if ($er) $err=$er; elseif (!$stored && mb_strlen($note)<5 && !($sub&&$sub['stored_name'])) $err='Attach a file or write your answer in the box.';
    if (!$err) {
      if ($stored && $sub && $sub['stored_name']) @unlink(__DIR__.'/uploads/'.basename($sub['stored_name']));
      $keep=!$stored&&$sub?[$sub['stored_name'],$sub['original_name'],$sub['size']]:[$stored,$orig,$size];
      db()->prepare('INSERT INTO assignment_submissions (material_id,student_id,stored_name,original_name,size,note,submitted_at) VALUES (?,?,?,?,?,?,NOW()) ON DUPLICATE KEY UPDATE stored_name=VALUES(stored_name),original_name=VALUES(original_name),size=VALUES(size),note=VALUES(note),submitted_at=NOW()')->execute([$id,$sid,$keep[0],$keep[1],$keep[2],$note?:null]);
      $late=$m['due_date']&&date('Y-m-d')>$m['due_date'];
      db()->prepare('INSERT INTO homework_records (material_id,student_id,status) VALUES (?,?,?) ON DUPLICATE KEY UPDATE status=VALUES(status)')->execute([$id,$sid,$late?'late':'submitted']);
      db()->prepare('INSERT INTO activity_logs (user_id,branch_id,action,details,ip_address) VALUES (?,?,?,?,?)')->execute([$_SESSION['user']['id'],$_SESSION['user']['branch_id'],'assignment_submit',$m['title'],$_SERVER['REMOTE_ADDR']??'']);
      header('Location: my_assignment.php?id='.$id.'&ok=1'); exit;
    }
    if ($stored) @unlink(__DIR__.'/uploads/'.$stored);
  }
}
$pageKey='sassign'; require __DIR__.'/includes/header.php';
$over=$m['due_date']&&$m['due_date']<date('Y-m-d'); $fmt=fn($v)=>rtrim(rtrim(number_format((float)$v,2,'.',''),'0'),'.');
?>
<a href="my_assignments.php" class="lbl text-decoration-none">← Assignments</a>
<?php if(isset($_GET['ok'])):?><div class="alert alert-success rounded-4 mt-2">Your work was submitted.</div><?php endif;?>
<div class="card-ui my-3"><div class="stat" style="font-size:1.25rem"><?=e($m['title'])?></div><div class="lbl"><?=e($m['cname'])?> · <?=e($m['bname'])?> (<?=$m['batch_year']?>)</div>
<div class="lbl <?=$over?'text-danger':''?>"><?=$m['due_date']?'Due '.e(date('D j M Y',strtotime($m['due_date']))).($over?' (past, late work is still accepted)':''):'No due date'?><?=$m['max_marks']!==null?' · Max marks '.$fmt($m['max_marks']):''?></div>
<?php if($m['description']):?><p class="mt-2 mb-0"><?=nl2br(e($m['description']))?></p><?php endif;?>
<?php if($m['stored_name']):?><a class="btn btn-light rounded-pill btn-sm mt-2" href="learn_file.php?id=<?=$id?>"><i class="bi bi-download"></i> <?=e($m['original_name'])?></a><?php endif;?></div>
<?php if($rec||$sub):?><div class="card-ui mb-3"><div class="fw-bold mb-1">Your submission</div>
<?php if($rec):?><span class="badge rounded-pill text-bg-<?=['submitted'=>'success','late'=>'warning','missing'=>'danger'][$rec['status']]?>"><?=e(ucfirst($rec['status']))?></span><?php endif;?>
<?php if($sub):?><div class="lbl mt-1"><?=e(date('j M Y, g:i A',strtotime($sub['submitted_at'])))?><?php if($sub['original_name']):?> · <a href="submission.php?id=<?=$sub['id']?>"><i class="bi bi-paperclip"></i> <?=e($sub['original_name'])?></a><?php endif;?></div><?php if($sub['note']):?><div class="mt-1"><?=nl2br(e($sub['note']))?></div><?php endif;?><?php endif;?>
<?php if($rec&&$rec['marks']!==null):?><div class="mt-2"><div class="stat" style="font-size:1.4rem"><?=$fmt($rec['marks'])?><?=$m['max_marks']!==null?' / '.$fmt($m['max_marks']):''?></div><?php if($rec['remarks']):?><div class="lbl">Teacher's feedback: <?=e($rec['remarks'])?></div><?php endif;?></div><?php endif;?></div><?php endif;?>
<?php if(!$locked):?><form method="post" enctype="multipart/form-data" class="card-ui"><input type="hidden" name="csrf" value="<?=csrf()?>"><div class="fw-bold mb-2"><?=$sub?'Replace your submission':'Submit your work'?></div>
<?php if($err):?><div class="alert alert-danger rounded-4 py-2"><?=e($err)?></div><?php endif;?>
<div class="mb-2"><label class="lbl">File (max 10 MB: <?=e(implode(', ',UP_EXT))?>)</label><input class="form-control" type="file" name="file" accept=".<?=e(implode(',.',UP_EXT))?>"></div>
<textarea class="form-control mb-3" name="note" rows="3" maxlength="500" placeholder="Your answer or a note for the teacher (optional if you attach a file)"><?=e($sub['note']??'')?></textarea>
<button class="btn-ui w-100"><?=$sub?'Resubmit':'Submit'?></button></form><?php endif;?>
<?php require __DIR__.'/includes/footer.php';
