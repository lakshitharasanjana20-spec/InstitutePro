<?php
require_once __DIR__.'/includes/bootstrap.php';
require_menu('leaves');
$s=branch_scope(); $me=(int)$_SESSION['user']['id']; $approver=can('approve_leave');
const LEAVE_TYPES=['casual'=>'Casual','medical'=>'Medical','annual'=>'Annual','other'=>'Other'];
function leave_target_ok(array $r): bool {
  if ((int)$r['user_id']===(int)$_SESSION['user']['id']) return false;
  $s=branch_scope(); if ($s!==null && (int)$r['branch_id']!==$s) return false;
  if (!is_owner() && !in_array($r['role'],['administrator','receptionist','teacher','finance','other'],true)) return false;
  return true;
}
if ($_SERVER['REQUEST_METHOD']==='POST') {
  csrf_check(); $act=$_POST['act']??''; $go='leaves.php';
  if ($act==='request') {
    $type=$_POST['leave_type']??''; $f=$_POST['from_date']??''; $t=$_POST['to_date']??''; $why=trim($_POST['reason']??'');
    if (!isset(LEAVE_TYPES[$type])) $m='Choose a leave type.';
    elseif (!strtotime($f)||!strtotime($t)||$f>$t) $m='Enter a valid date range.';
    elseif ($f<date('Y-m-d',strtotime('-7 days'))) $m='Leave cannot start more than 7 days in the past.';
    elseif ((strtotime($t)-strtotime($f))/86400>59) $m='Leave cannot be longer than 60 days.';
    elseif (mb_strlen($why)<3) $m='Please give a reason.';
    else {
      $c=db()->prepare("SELECT 1 FROM leave_requests WHERE user_id=? AND status IN ('pending','approved') AND NOT (to_date<? OR from_date>?)"); $c->execute([$me,$f,$t]);
      if ($c->fetch()) $m='You already have a pending or approved leave in these dates.';
      else { db()->prepare('INSERT INTO leave_requests (user_id,branch_id,leave_type,from_date,to_date,reason) VALUES (?,?,?,?,?,?)')->execute([$me,$_SESSION['user']['branch_id']?:null,$type,$f,$t,mb_substr($why,0,255)]); log_activity('leave_request',"$f to $t"); $m='Leave request sent.'; }
    }
    flash_set('msg',$m);
  } elseif ($act==='cancel') {
    $id=(int)($_POST['id']??0);
    $u=db()->prepare("UPDATE leave_requests SET status='cancelled' WHERE id=? AND user_id=? AND (status='pending' OR (status='approved' AND from_date>CURDATE()))"); $u->execute([$id,$me]);
    if ($u->rowCount()) { db()->prepare('DELETE FROM substitutions WHERE leave_id=?')->execute([$id]); log_activity('leave_cancel','#'.$id); flash_set('msg','Leave request cancelled.'); } else flash_set('msg','That request cannot be cancelled.');
  } elseif ($act==='decide' && $approver) {
    $go='leaves.php?tab=requests'; $id=(int)($_POST['id']??0); $dec=$_POST['decision']??'';
    $q=db()->prepare('SELECT lr.*,u.role,u.full_name,u.branch_id ubranch FROM leave_requests lr JOIN users u ON u.id=lr.user_id WHERE lr.id=?'); $q->execute([$id]); $r=$q->fetch();
    if (!$r||!leave_target_ok(['user_id'=>$r['user_id'],'role'=>$r['role'],'branch_id'=>$r['ubranch']])) flash_set('msg','You cannot decide that request.');
    elseif ($r['status']!=='pending') flash_set('msg','That request was already decided.');
    elseif (!in_array($dec,['approved','rejected'],true)) flash_set('msg','Invalid decision.');
    else {
      db()->prepare('UPDATE leave_requests SET status=?,decided_by=?,decided_at=NOW(),decision_note=? WHERE id=?')->execute([$dec,$me,mb_substr(trim($_POST['note']??''),0,255)?:null,$id]);
      log_activity('leave_'.$dec,$r['full_name'].' '.$r['from_date'].' to '.$r['to_date']);
      flash_set('msg',ucfirst($dec).'.'.($dec==='approved'&&$r['role']==='teacher'?' Arrange cover for their classes under Schedule → Substitute classes.':''));
    }
  }
  header("Location: $go"); exit;
}
$pageKey='leaves'; require __DIR__.'/includes/header.php'; $msg=flash_get('msg');
$tab=($_GET['tab']??'')==='requests'&&$approver?'requests':'mine';
$cls=['pending'=>'warning','approved'=>'success','rejected'=>'danger','cancelled'=>'secondary'];
?>
<?php if($msg):?><div class="alert alert-info rounded-4"><?=e($msg)?></div><?php endif;?>
<ul class="nav nav-pills mb-3"><li class="nav-item"><a class="nav-link <?=$tab==='mine'?'active':''?>" href="leaves.php">My leave</a></li>
<?php if($approver):?><li class="nav-item"><a class="nav-link <?=$tab==='requests'?'active':''?>" href="leaves.php?tab=requests">Requests</a></li><?php endif;?></ul>
<?php if($tab==='mine'):
  $fs=genum('status',array_keys($cls)); $w='lr.user_id=?'; $p=[$me]; if($fs){$w.=' AND lr.status=?';$p[]=$fs;}
  $cq=db()->prepare("SELECT COUNT(*) FROM leave_requests lr WHERE $w"); $cq->execute($p); $total=(int)$cq->fetchColumn(); $per=pg_per(); [$pg,$pages,$off]=pg_page($total,$per);
  $q=db()->prepare("SELECT lr.*,(SELECT full_name FROM users WHERE id=lr.decided_by) dby,DATEDIFF(lr.to_date,lr.from_date)+1 days FROM leave_requests lr WHERE $w ORDER BY lr.id DESC LIMIT $per OFFSET $off"); $q->execute($p); $rows=$q->fetchAll();?>
<form method="post" class="card-ui mb-3 row g-2"><input type="hidden" name="csrf" value="<?=csrf()?>"><input type="hidden" name="act" value="request">
 <div class="fw-bold">Request leave</div>
 <div class="col-6 col-md-3"><select class="form-select" name="leave_type"><?php foreach(LEAVE_TYPES as $k=>$l):?><option value="<?=$k?>"><?=$l?></option><?php endforeach;?></select></div>
 <div class="col-6 col-md-3"><input class="form-control" type="date" name="from_date" min="<?=date('Y-m-d',strtotime('-7 days'))?>" required></div>
 <div class="col-6 col-md-3"><input class="form-control" type="date" name="to_date" required></div>
 <div class="col-12 col-md-9"><input class="form-control" name="reason" maxlength="255" placeholder="Reason" required></div>
 <div class="col-12 col-md-3"><button class="btn-ui w-100">Send request</button></div></form>
<?php f_open(); f_select('status',array_combine(array_keys($cls),array_map('ucfirst',array_keys($cls))),'Any status','col-6 col-md-3'); f_close();?>
<div class="card-ui table-wrap"><table class="table align-middle"><thead><tr><th>Dates</th><th>Type</th><th>Status</th><th></th></tr></thead><tbody>
<?php foreach($rows as $r):?><tr><td><?=e($r['from_date'])?> → <?=e($r['to_date'])?><div class="lbl"><?=$r['days']?> day<?=$r['days']>1?'s':''?> · <?=e($r['reason'])?></div></td><td><?=e(LEAVE_TYPES[$r['leave_type']])?></td>
<td><span class="badge rounded-pill text-bg-<?=$cls[$r['status']]?>"><?=e($r['status'])?></span><?php if($r['decision_note']||$r['dby']):?><div class="lbl"><?=e($r['dby']??'')?><?=$r['decision_note']?': '.e($r['decision_note']):''?></div><?php endif;?></td>
<td><?php if($r['status']==='pending'||($r['status']==='approved'&&$r['from_date']>date('Y-m-d'))):?><form method="post" onsubmit="return confirm('Cancel this leave?')"><input type="hidden" name="csrf" value="<?=csrf()?>"><input type="hidden" name="act" value="cancel"><input type="hidden" name="id" value="<?=$r['id']?>"><button class="btn btn-sm btn-light rounded-pill">Cancel</button></form><?php endif;?></td></tr>
<?php endforeach; if(!$rows):?><tr><td colspan="4" class="lbl text-center py-4">No leave requests.</td></tr><?php endif;?></tbody></table></div>
<?php pg_render($total,$per);
else:
  $fs=genum('status',array_keys($cls)); if($fs===''&&!isset($_GET['status'])) $fs='pending'; $fq=gstr('q'); $ft=genum('type',array_keys(LEAVE_TYPES)); $d1=gdate('from'); $d2=gdate('to');
  $w=['1=1']; $p=[]; if($s!==null){$w[]='u.branch_id=?';$p[]=$s;} if($fs){$w[]='lr.status=?';$p[]=$fs;} if($ft){$w[]='lr.leave_type=?';$p[]=$ft;}
  if($fq!==''){$w[]='u.full_name LIKE ?';$p[]="%$fq%";} if($d1){$w[]='lr.to_date>=?';$p[]=$d1;} if($d2){$w[]='lr.from_date<=?';$p[]=$d2;}
  $wh=implode(' AND ',$w); $cq=db()->prepare("SELECT COUNT(*) FROM leave_requests lr JOIN users u ON u.id=lr.user_id WHERE $wh"); $cq->execute($p); $total=(int)$cq->fetchColumn(); $per=pg_per(); [$pg,$pages,$off]=pg_page($total,$per);
  $q=db()->prepare("SELECT lr.*,u.full_name,u.role,u.branch_id ubranch,DATEDIFF(lr.to_date,lr.from_date)+1 days FROM leave_requests lr JOIN users u ON u.id=lr.user_id WHERE $wh ORDER BY (lr.status='pending') DESC,lr.from_date DESC LIMIT $per OFFSET $off"); $q->execute($p); $rows=$q->fetchAll();
  f_open(['tab']); f_input('q','Search name','text','col-12 col-md-3'); f_select('status',array_combine(array_keys($cls),array_map('ucfirst',array_keys($cls))),'Any status','col-6 col-md-2'); f_select('type',LEAVE_TYPES,'Any type','col-6 col-md-2');
  f_input('from','Leave from','date','col-6 col-md-2'); f_input('to','Leave to','date','col-6 col-md-2'); f_close(['tab']);?>
<div class="card-ui table-wrap"><table class="table align-middle"><thead><tr><th>Person</th><th>Dates</th><th>Status</th><th></th></tr></thead><tbody>
<?php foreach($rows as $r): $ok=leave_target_ok(['user_id'=>$r['user_id'],'role'=>$r['role'],'branch_id'=>$r['ubranch']]);?>
<tr><td><?=e($r['full_name'])?><div class="lbl"><?=e(ucwords(str_replace('_',' ',$r['role'])))?> · <?=e(LEAVE_TYPES[$r['leave_type']])?></div></td>
<td><?=e($r['from_date'])?> → <?=e($r['to_date'])?><div class="lbl"><?=$r['days']?> day<?=$r['days']>1?'s':''?> · <?=e($r['reason'])?></div></td>
<td><span class="badge rounded-pill text-bg-<?=$cls[$r['status']]?>"><?=e($r['status'])?></span></td>
<td class="text-nowrap"><?php if($r['status']==='pending'&&$ok):?>
 <button class="btn btn-sm btn-light rounded-pill" data-bs-toggle="modal" data-bs-target="#decModal" onclick="dec(<?=$r['id']?>,'<?=e(addslashes($r['full_name']))?>')">Decide</button>
<?php elseif($r['status']==='approved'&&$r['role']==='teacher'&&can('manage_substitutes')):?><a class="btn btn-sm btn-light rounded-pill" href="substitutes.php?from=<?=e($r['from_date'])?>&to=<?=e($r['to_date'])?>">Arrange cover</a><?php endif;?></td></tr>
<?php endforeach; if(!$rows):?><tr><td colspan="4" class="lbl text-center py-4">No requests found.</td></tr><?php endif;?></tbody></table></div>
<?php pg_render($total,$per);?>
<div class="modal fade" id="decModal" tabindex="-1"><div class="modal-dialog modal-dialog-centered"><div class="modal-content" style="border-radius:24px"><form method="post">
<input type="hidden" name="csrf" value="<?=csrf()?>"><input type="hidden" name="act" value="decide"><input type="hidden" name="id" id="decId">
<div class="modal-header"><h5 class="modal-title" id="decName">Decide</h5><button type="button" class="btn-close" data-bs-dismiss="modal"></button></div>
<div class="modal-body"><input class="form-control" name="note" maxlength="255" placeholder="Note (optional)"></div>
<div class="modal-footer"><button class="btn btn-light rounded-pill" name="decision" value="rejected">Reject</button><button class="btn-ui" name="decision" value="approved">Approve</button></div></form></div></div></div>
<?php ob_start();?><script>function dec(i,n){document.getElementById('decId').value=i;document.getElementById('decName').textContent='Leave: '+n;}</script>
<?php $pageScript=ob_get_clean(); endif;?>
<?php require __DIR__.'/includes/footer.php';
