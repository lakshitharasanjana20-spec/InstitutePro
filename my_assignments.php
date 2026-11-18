<?php
require_once __DIR__.'/includes/bootstrap.php';
require_once __DIR__.'/includes/student_lib.php';
stu_guard('sassign'); $pageKey='sassign'; require __DIR__.'/includes/header.php';
$sid=stu_id(); $ids=stu_ids(); $bs=stu_batches();
$stx="CASE WHEN h.marks IS NOT NULL THEN 'graded' WHEN h.status IS NOT NULL THEN h.status WHEN sb.id IS NOT NULL THEN 'submitted' ELSE 'pending' END";
$fq=gstr('q'); $fb=gint('batch'); $fs=genum('status',['pending','submitted','late','missing','graded']);
$w=''; $p=[$sid,$sid]; if($fq!==''){$w.=' AND (m.title LIKE ? OR m.description LIKE ?)';array_push($p,"%$fq%","%$fq%");} if($fb&&in_array($fb,array_map('intval',array_column($bs,'id')),true)){$w.=' AND m.batch_id=?';$p[]=$fb;} if($fs){$w.=" AND $stx=?";$p[]=$fs;}
$base="FROM materials m JOIN batches b ON b.id=m.batch_id JOIN courses c ON c.id=b.course_id LEFT JOIN assignment_submissions sb ON sb.material_id=m.id AND sb.student_id=? LEFT JOIN homework_records h ON h.material_id=m.id AND h.student_id=? WHERE m.batch_id IN ($ids) AND m.kind='homework' AND m.publish=1$w";
$cq=db()->prepare("SELECT COUNT(*) $base"); $cq->execute($p); $total=(int)$cq->fetchColumn(); $per=pg_per(); [$pg,$pages,$off]=pg_page($total,$per);
$q=db()->prepare("SELECT m.id,m.title,m.due_date,m.max_marks,c.name cname,b.name bname,h.marks,$stx st $base ORDER BY (m.due_date IS NULL),m.due_date DESC LIMIT $per OFFSET $off"); $q->execute($p); $rows=$q->fetchAll();
$col=['pending'=>'secondary','submitted'=>'success','late'=>'warning','missing'=>'danger','graded'=>'primary']; $fmt=fn($v)=>rtrim(rtrim(number_format((float)$v,2,'.',''),'0'),'.');
f_open(); f_input('q','Search assignments','text','col-12 col-md-4'); f_select('batch',array_column($bs,'name','id'),'All classes','col-6 col-md-3'); f_select('status',array_combine(array_keys($col),array_map('ucfirst',array_keys($col))),'Any status','col-6 col-md-3'); f_close();
?>
<div class="lbl mb-2"><?=$total?> assignment<?=$total===1?'':'s'?></div>
<?php foreach($rows as $r): $over=$r['due_date']&&$r['due_date']<date('Y-m-d')&&in_array($r['st'],['pending','missing'],true);?>
<a href="my_assignment.php?id=<?=$r['id']?>" class="card-ui d-block mb-2 text-decoration-none text-reset"><div class="d-flex justify-content-between gap-2"><div><b><?=e($r['title'])?></b><div class="lbl"><?=e($r['cname'])?> · <?=e($r['bname'])?></div><div class="lbl <?=$over?'text-danger':''?>"><?=$r['due_date']?'Due '.e(date('j M Y',strtotime($r['due_date']))):'No due date'?></div></div>
<div class="text-end"><span class="badge rounded-pill text-bg-<?=$col[$r['st']]?>"><?=e(ucfirst($r['st']))?></span><?php if($r['marks']!==null):?><div class="fw-bold"><?=$fmt($r['marks'])?><?=$r['max_marks']!==null?' / '.$fmt($r['max_marks']):''?></div><?php endif;?></div></div></a>
<?php endforeach; if(!$rows):?><div class="card-ui lbl">No assignments found.</div><?php endif;?>
<?php pg_render($total,$per); require __DIR__.'/includes/footer.php';
