<?php
require_once __DIR__.'/includes/bootstrap.php';
$pageKey='activity'; require __DIR__.'/includes/header.php';
$s=branch_scope();
$fq=gstr('q'); $fu=gint('user'); $fa=gstr('action',60); $d1=gdate('from'); $d2=gdate('to'); if($d1&&$d2&&$d1>$d2)[$d1,$d2]=[$d2,$d1];
$w=[]; $p=[];
if($s!==null){ $w[]='a.branch_id=?'; $p[]=$s; }
if($fq!==''){ $w[]='(a.action LIKE ? OR a.details LIKE ?)'; array_push($p,"%$fq%","%$fq%"); }
if($fu){ $w[]='a.user_id=?'; $p[]=$fu; }
if($fa!==''){ $w[]='a.action=?'; $p[]=$fa; }
if($d1){ $w[]='a.created_at>=?'; $p[]=$d1.' 00:00:00'; }
if($d2){ $w[]='a.created_at<=?'; $p[]=$d2.' 23:59:59'; }
$wh=$w?' WHERE '.implode(' AND ',$w):'';
$cq=db()->prepare('SELECT COUNT(*) FROM activity_logs a'.$wh); $cq->execute($p); $total=(int)$cq->fetchColumn();
$per=pg_per(); [$pg,$pages,$off]=pg_page($total,$per);
$st=db()->prepare('SELECT a.*,u.full_name FROM activity_logs a LEFT JOIN users u ON u.id=a.user_id'.$wh.' ORDER BY a.id DESC LIMIT '.$per.' OFFSET '.$off); $st->execute($p); $rows=$st->fetchAll();
$sc=$s!==null?' WHERE branch_id='.(int)$s:'';
$users=db()->query('SELECT id,full_name FROM users'.($s!==null?' WHERE branch_id='.(int)$s:'').' ORDER BY full_name')->fetchAll();
$actions=db()->query('SELECT DISTINCT action FROM activity_logs'.$sc.' ORDER BY action')->fetchAll(PDO::FETCH_COLUMN);
f_open(); f_input('q','Search action or details','text','col-12 col-md-4');
f_select('user',array_column($users,'full_name','id'),'Any user','col-6 col-md-3');
f_select('action',array_combine($actions,$actions),'Any action','col-6 col-md-3');
f_input('from','From','date','col-6 col-md-2'); f_input('to','To','date','col-6 col-md-2'); f_close();
?>
<div class="card-ui table-wrap"><table class="table align-middle"><thead><tr><th>When</th><th>User</th><th>Action</th><th>Details</th><th>IP</th></tr></thead><tbody>
<?php foreach($rows as $r):?><tr><td class="text-nowrap"><?=e($r['created_at'])?></td><td><?=e($r['full_name']??'—')?></td><td><?=e($r['action'])?></td><td><?=e($r['details'])?></td><td><?=e($r['ip_address'])?></td></tr><?php endforeach;
if(!$rows):?><tr><td colspan="5" class="lbl text-center py-4">No activity found.</td></tr><?php endif;?></tbody></table></div>
<?php pg_render($total,$per); require __DIR__.'/includes/footer.php';
