<?php
require_once __DIR__.'/includes/bootstrap.php';
require_once __DIR__.'/includes/student_lib.php';
stu_guard('sattendance'); $pageKey='sattendance'; require __DIR__.'/includes/header.php';
$sid=stu_id(); $bs=stu_batches(); $st=stu_row();
$fb=gint('batch'); if($fb&&!in_array($fb,array_map('intval',array_column($bs,'id')),true)) $fb=0;
$mo=gstr('month',7); if(!preg_match('/^\d{4}-(0[1-9]|1[0-2])$/',$mo)) $mo='';
$w=''; $p=[$sid]; if($fb){$w.=' AND a.batch_id=?';$p[]=$fb;} if($mo){$w.=" AND DATE_FORMAT(a.att_date,'%Y-%m')=?";$p[]=$mo;}
$s1=db()->prepare("SELECT a.status,COUNT(*) c FROM attendance a WHERE a.student_id=?$w GROUP BY a.status"); $s1->execute($p); $sum=array_column($s1->fetchAll(),'c','status'); $pr=(int)($sum['present']??0); $ab=(int)($sum['absent']??0); $pc=($pr+$ab)?round($pr/($pr+$ab)*100):null;
$cq=db()->prepare("SELECT COUNT(*) FROM attendance a WHERE a.student_id=?$w"); $cq->execute($p); $total=(int)$cq->fetchColumn(); $per=pg_per(); [$pg,$pages,$off]=pg_page($total,$per);
$q=db()->prepare("SELECT a.att_date,a.status,a.check_in,a.check_out,b.name bname,c.name cname FROM attendance a JOIN batches b ON b.id=a.batch_id JOIN courses c ON c.id=b.course_id WHERE a.student_id=?$w ORDER BY a.att_date DESC,a.id DESC LIMIT $per OFFSET $off"); $q->execute($p); $rows=$q->fetchAll();
f_open(); f_select('batch',array_column($bs,'name','id'),'All classes','col-6 col-md-4'); echo '<div class="col-6 col-md-3"><label class="lbl">Month</label><input class="form-control" type="month" name="month" value="'.e($mo).'"></div>'; f_close();
?>
<?php if(($st['status']??'')==='inactive'):?><div class="alert alert-warning rounded-4">Your status is <b>inactive</b> after missing 2 classes in a row. Attend your next class to become active again.</div><?php endif;?>
<div class="row g-2 text-center mb-3"><div class="col-4"><div class="card-ui"><div class="stat" style="font-size:1.4rem;color:<?=$pc===null?'inherit':pct_color($pc)?>"><?=$pc===null?'—':$pc.'%'?></div><div class="lbl">Attendance</div></div></div>
<div class="col-4"><div class="card-ui"><div class="stat" style="font-size:1.4rem;color:#1e9e5a"><?=$pr?></div><div class="lbl">Present</div></div></div><div class="col-4"><div class="card-ui"><div class="stat" style="font-size:1.4rem;color:#d9534f"><?=$ab?></div><div class="lbl">Absent</div></div></div></div>
<div class="card-ui table-wrap"><table class="table align-middle"><thead><tr><th>Date</th><th>Class</th><th>Status</th><th>In / Out</th></tr></thead><tbody>
<?php foreach($rows as $r):?><tr><td><?=e(date('D j M Y',strtotime($r['att_date'])))?></td><td><?=e($r['cname'])?><div class="lbl"><?=e($r['bname'])?></div></td><td><span class="badge rounded-pill text-bg-<?=$r['status']==='present'?'success':'danger'?>"><?=e(ucfirst($r['status']))?></span></td><td class="lbl"><?=$r['check_in']?e(substr($r['check_in'],0,5)):'—'?> / <?=$r['check_out']?e(substr($r['check_out'],0,5)):'—'?></td></tr>
<?php endforeach; if(!$rows):?><tr><td colspan="4" class="lbl text-center py-4">No attendance recorded.</td></tr><?php endif;?></tbody></table></div>
<?php pg_render($total,$per); require __DIR__.'/includes/footer.php';
