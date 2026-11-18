<?php
require_once __DIR__.'/includes/bootstrap.php';
require_once __DIR__.'/includes/learn_lib.php';
require_menu('materials');
$s=branch_scope(); $bw=($s!==null?' AND b.branch_id='.(int)$s:'').tfrag('b');
$pageKey='materials'; require __DIR__.'/includes/header.php';
$fq=gstr('q'); $fk=genum('type',array_keys(LEARN_TYPES)); $fb=gint('batch'); $fbr=$s===null?gint('branch'):0; $fp=genum('vis',['1','0']);
$w=''; $p=[];
if($fq!==''){$w.=' AND (m.title LIKE ? OR m.description LIKE ?)';array_push($p,"%$fq%","%$fq%");} if($fk){$w.=' AND m.kind=?';$p[]=$fk;} if($fb){$w.=' AND m.batch_id=?';$p[]=$fb;} if($fbr){$w.=' AND b.branch_id=?';$p[]=$fbr;}
if($fp!==''){$w.=' AND m.publish=?';$p[]=(int)$fp;}
$base=" FROM materials m JOIN batches b ON b.id=m.batch_id JOIN courses c ON c.id=b.course_id WHERE 1=1$bw$w";
$cq=db()->prepare('SELECT COUNT(*)'.$base); $cq->execute($p); $total=(int)$cq->fetchColumn(); $per=pg_per(); [$pg,$pages,$off]=pg_page($total,$per);
$q=db()->prepare('SELECT m.*,b.name bname,b.batch_year,c.name cname'.$base.' ORDER BY m.id DESC LIMIT '.$per.' OFFSET '.$off); $q->execute($p); $rows=$q->fetchAll();
$fbs=db()->query("SELECT b.id,CONCAT(c.name,' · ',b.name,' (',b.batch_year,')') label FROM batches b JOIN courses c ON c.id=b.course_id WHERE 1=1$bw ORDER BY c.name,b.name LIMIT 300")->fetchAll();
$branches=$s===null?db()->query('SELECT id,name FROM branches ORDER BY name')->fetchAll():[];
$tn=[]; foreach(LEARN_TYPES as $k=>$t) $tn[$k]=$t[0];
?>
<div class="card-ui mb-3 lbl">Every class has its own <b>Learning hub</b> where lectures, live class links, materials, assignments and quizzes are added. Open a class from <a href="courses.php">Courses</a>, or use this page to search across your classes.</div>
<div class="lbl mb-2"><?=$total?> item<?=$total===1?'':'s'?></div>
<?php f_open(); f_input('q','Search title or notes','text','col-12 col-md-3'); f_select('type',$tn,'Any type','col-6 col-md-2'); f_select('batch',array_column($fbs,'label','id'),'Any class / batch','col-6 col-md-3');
 f_select('vis',['1'=>'Visible to students','0'=>'Hidden'],'Any visibility','col-6 col-md-2'); if($s===null) f_select('branch',array_column($branches,'name','id'),'All branches','col-6 col-md-2'); f_close();?>
<div class="card-ui table-wrap"><table class="table align-middle"><thead><tr><th>Title</th><th>Class</th><th>Added</th><th></th></tr></thead><tbody>
<?php foreach($rows as $r): $T=LEARN_TYPES[$r['kind']]??['','bi-file',$r['kind']];?>
<tr><td><i class="bi <?=$T[1]?>"></i> <span class="lbl"><?=e($T[2])?></span><div><b><?=e($r['title'])?></b> <span class="badge rounded-pill text-bg-<?=$r['publish']?'success':'secondary'?>"><?=$r['publish']?'Visible':'Hidden'?></span></div>
 <?php if($r['kind']==='live'&&$r['starts_at']): $ls=live_state($r['starts_at'],$r['duration_min']);?><div class="lbl"><?=e(date('D j M, g:i A',strtotime($r['starts_at'])))?> · <span class="text-<?=$ls[1]==='dark'?'muted':$ls[1]?>"><?=e($ls[0])?></span></div><?php endif;?>
 <?php if($r['url']):?><div class="lbl"><i class="bi bi-link-45deg"></i> <?=e(url_label($r['url']))?></div><?php endif;?></td>
<td><?=e($r['cname'])?><div class="lbl"><?=e($r['bname'])?> (<?=$r['batch_year']?>)</div></td><td><?=e(substr($r['created_at'],0,10))?></td>
<td class="text-nowrap"><?php if($r['url']):?><a class="btn btn-sm btn-light rounded-pill" href="<?=e($r['url'])?>" target="_blank" rel="noopener noreferrer">Open link</a> <?php endif;?>
<a class="btn btn-sm btn-light rounded-pill" href="learning.php?batch=<?=$r['batch_id']?>&type=<?=e($r['kind'])?>">Open in hub</a></td></tr>
<?php endforeach; if(!$rows):?><tr><td colspan="4" class="lbl text-center py-4">Nothing found.</td></tr><?php endif;?></tbody></table></div>
<?php pg_render($total,$per); require __DIR__.'/includes/footer.php';
