<?php
require_once __DIR__.'/includes/bootstrap.php';
require_once __DIR__.'/includes/attendance_lib.php';
require_menu('attendance'); refresh_batches();
$s=branch_scope(); $canMark=can('mark_attendance');
$tab=($_GET['tab']??'')==='import' && $canMark ? 'import' : 'mark';
$bw=$s!==null?' AND b.branch_id='.(int)$s:''; $bw.=tfrag('b');

if ($_SERVER['REQUEST_METHOD']==='POST') {
  csrf_check();
  if (!$canMark) { http_response_code(403); exit('Access denied.'); }
  $alerts=['reactivated'=>[],'inactive'=>[]]; $act=$_POST['act']??'';
  if ($act==='save') {
    $bid=(int)$_POST['batch_id']; $date=$_POST['date']??'';
    $q=db()->prepare("SELECT b.id FROM batches b WHERE b.id=?$bw"); $q->execute([$bid]);
    if (!$q->fetch() || !strtotime($date) || $date>date('Y-m-d')) { flash_set('msg','Invalid class or date.'); header('Location: attendance.php'); exit; }
    $m=db()->prepare('SELECT student_id FROM enrolments WHERE batch_id=?'); $m->execute([$bid]); $valid=array_flip($m->fetchAll(PDO::FETCH_COLUMN));
    $n=0; $posted=$_POST['st']??[]; if(is_array($posted)) ksort($posted); else $posted=[];
    foreach ($posted as $sid=>$v) {
      if (!isset($valid[(int)$sid]) || !in_array($v,['present','absent'],true)) continue;
      mark_attendance((int)$sid,$bid,$date,$v,null,null,'manual',(int)$_SESSION['user']['id'],$alerts); $n++;
    }
    log_activity('attendance_mark',"batch $bid $date ($n students)");
    flash_set('msg',"Attendance saved for $n students."); flash_set('alerts',$alerts);
    header("Location: attendance.php?batch_id=$bid&date=$date"); exit;
  }
  if ($act==='import') {
    $branch=is_owner()?(int)($_POST['branch_id']??0):(int)$s;
    $f=$_FILES['file']??null;
    if (!$branch) flash_set('msg','Choose a branch.');
    elseif (!$f || $f['error']!==UPLOAD_ERR_OK || $f['size']>2*1024*1024) flash_set('msg','Please choose a CSV/TXT file under 2 MB.');
    else {
      $punch=parse_punches(file_get_contents($f['tmp_name']));
      $st=db()->prepare('SELECT id,fingerprint_id FROM students WHERE branch_id=? AND fingerprint_id IS NOT NULL'); $st->execute([$branch]);
      $map=[]; foreach($st->fetchAll() as $r) $map[fp_key($r['fingerprint_id'])]=(int)$r['id'];
      $byDate=[]; $unknown=[];
      foreach ($punch as $fid=>$days) {
        $k=fp_key($fid);
        if (!isset($map[$k])) { $unknown[$fid]=1; continue; }
        foreach ($days as $d=>$times) { sort($times); $byDate[$d][$map[$k]]=[$times[0], count($times)>1?end($times):null]; }
      }
      ksort($byDate); $pres=0;$abs=0;$dates=0;
      foreach ($byDate as $date=>$people) {
        $dates++; $wd=date('D',strtotime($date));
        $b=db()->prepare('SELECT id,class_days FROM batches WHERE branch_id=? AND start_date<=? AND end_date>=?'); $b->execute([$branch,$date,$date]);
        foreach ($b->fetchAll() as $batch) {
          if ($batch['class_days']!=='' && $batch['class_days']!==null && !in_array($wd,explode(',',$batch['class_days']),true)) continue;
          $m=db()->prepare('SELECT student_id FROM enrolments WHERE batch_id=?'); $m->execute([$batch['id']]);
          foreach ($m->fetchAll(PDO::FETCH_COLUMN) as $sid) {
            if (isset($people[$sid])) { mark_attendance($sid,$batch['id'],$date,'present',$people[$sid][0],$people[$sid][1],'fingerprint',(int)$_SESSION['user']['id'],$alerts); $pres++; }
            else {
              $e=db()->prepare("SELECT 1 FROM attendance WHERE student_id=? AND batch_id=? AND att_date=? AND status='present'"); $e->execute([$sid,$batch['id'],$date]);
              if (!$e->fetch()) { mark_attendance($sid,$batch['id'],$date,'absent',null,null,'fingerprint',(int)$_SESSION['user']['id'],$alerts); $abs++; }
            }
          }
        }
      }
      log_activity('attendance_import',"branch $branch: $dates days, $pres present, $abs absent");
      $msg="Import finished: $dates day(s), $pres present, $abs absent.";
      if ($unknown) $msg.=' Unknown fingerprint IDs ignored: '.implode(', ',array_slice(array_keys($unknown),0,15)).'.';
      if (!$dates) $msg='No usable punch records found. Each line needs a fingerprint ID, a date, and a time.';
      flash_set('msg',$msg); flash_set('alerts',$alerts);
    }
    header('Location: attendance.php?tab=import'); exit;
  }
}
$pageKey='attendance'; require __DIR__.'/includes/header.php';
$msg=flash_get('msg'); $alerts=flash_get('alerts');
$batches=db()->query("SELECT b.id,b.name,b.batch_year,c.name cname,br.name brname FROM batches b JOIN courses c ON c.id=b.course_id JOIN branches br ON br.id=b.branch_id WHERE b.status='active'$bw ORDER BY br.name,b.name")->fetchAll();
$bid=(int)($_GET['batch_id']??0); $date=$_GET['date']??date('Y-m-d'); if(!strtotime($date)||$date>date('Y-m-d')) $date=date('Y-m-d');
$students=[]; $recs=[];
if ($bid && $tab==='mark') {
  $q=db()->prepare("SELECT s.id,s.student_code,s.full_name,s.status FROM enrolments e JOIN students s ON s.id=e.student_id JOIN batches b ON b.id=e.batch_id WHERE e.batch_id=?$bw ORDER BY s.full_name");
  $q->execute([$bid]); $students=$q->fetchAll();
  $r=db()->prepare('SELECT student_id,status,check_in,check_out FROM attendance WHERE batch_id=? AND att_date=?'); $r->execute([$bid,$date]);
  foreach($r->fetchAll() as $x) $recs[$x['student_id']]=$x;
}
$branches=db()->query("SELECT id,name FROM branches WHERE status='active' ORDER BY name")->fetchAll();
?>
<?php if($msg):?><div class="alert alert-info rounded-4"><?=e($msg)?></div><?php endif;?>
<ul class="nav nav-pills mb-3 flex-nowrap overflow-auto">
 <li class="nav-item"><a class="nav-link <?=$tab==='mark'?'active':''?>" href="attendance.php"><?=$canMark?'Mark attendance':'View attendance'?></a></li>
 <?php if($canMark):?><li class="nav-item"><a class="nav-link <?=$tab==='import'?'active':''?>" href="attendance.php?tab=import">Fingerprint import</a></li><?php endif;?>
 <?php if(can('mark_staff_attendance')):?><li class="nav-item"><a class="nav-link" href="staff_attendance.php">Staff attendance</a></li><?php endif;?>
</ul>
<?php if($tab==='import'):?>
<div class="card-ui"><p class="lbl">Upload the daily export from the fingerprint machine (CSV or TXT). Each line needs the <b>fingerprint ID</b>, a <b>date</b> and a <b>time</b>, for example <code>12,2026-11-02 08:55:10</code>. The first punch of the day is check-in and the last is check-out. Students in a class that day with no punch are marked Absent.</p>
<form method="post" enctype="multipart/form-data" class="row g-2"><input type="hidden" name="csrf" value="<?=csrf()?>"><input type="hidden" name="act" value="import">
<?php if(is_owner()):?><div class="col-md-4"><select class="form-select" name="branch_id" required><option value="">Branch</option><?php foreach($branches as $b):?><option value="<?=$b['id']?>"><?=e($b['name'])?></option><?php endforeach;?></select></div><?php endif;?>
<div class="col"><input class="form-control" type="file" name="file" accept=".csv,.txt,.dat" required></div>
<div class="col-md-2"><button class="btn-ui w-100">Import</button></div></form></div>
<?php else:?>
<form method="get" class="card-ui mb-3 row g-2">
 <div class="col-md-6"><select class="form-select" name="batch_id" required><option value="">Choose class / batch</option>
 <?php foreach($batches as $b):?><option value="<?=$b['id']?>" <?=$bid===(int)$b['id']?'selected':''?>><?=e(($s===null?$b['brname'].' · ':'').$b['cname'].' · '.$b['name'].' ('.$b['batch_year'].')')?></option><?php endforeach;?></select></div>
 <div class="col-8 col-md-4"><input class="form-control" type="date" name="date" value="<?=e($date)?>" max="<?=date('Y-m-d')?>"></div>
 <div class="col-4 col-md-2"><button class="btn-ui w-100">Load</button></div></form>
<?php if($bid):?>
<form method="post"><input type="hidden" name="csrf" value="<?=csrf()?>"><input type="hidden" name="act" value="save"><input type="hidden" name="batch_id" value="<?=$bid?>"><input type="hidden" name="date" value="<?=e($date)?>">
<input class="form-control mb-2" id="stFilter" placeholder="Search student name or ID">
<div class="card-ui" data-pager="10" data-filter="#stFilter">
<?php if($canMark && $students):?><div class="mb-2" data-pg-skip><button type="button" class="btn btn-sm btn-light rounded-pill" onclick="document.querySelectorAll('.p-in').forEach(r=>r.checked=true)">All present</button></div><?php endif;?>
<?php foreach($students as $r): $cur=$recs[$r['id']]['status']??''; ?>
<div class="d-flex align-items-center gap-2 py-2 border-top flex-wrap">
 <div class="me-auto"><div><?=e($r['full_name'])?> <?php if($r['status']==='inactive'):?><span class="badge rounded-pill text-bg-secondary">inactive</span><?php endif;?></div>
 <div class="lbl"><?=e($r['student_code'])?><?php if(!empty($recs[$r['id']]['check_in'])):?> · in <?=e(substr($recs[$r['id']]['check_in'],0,5))?><?=!empty($recs[$r['id']]['check_out'])?' · out '.e(substr($recs[$r['id']]['check_out'],0,5)):''?><?php endif;?></div></div>
 <div class="btn-group">
  <input type="radio" class="btn-check p-in" name="st[<?=$r['id']?>]" id="p<?=$r['id']?>" value="present" <?=$cur==='present'?'checked':''?> <?=$canMark?'':'disabled'?>><label class="btn btn-outline-success rounded-start-pill" for="p<?=$r['id']?>">Present</label>
  <input type="radio" class="btn-check" name="st[<?=$r['id']?>]" id="a<?=$r['id']?>" value="absent" <?=$cur==='absent'?'checked':''?> <?=$canMark?'':'disabled'?>><label class="btn btn-outline-danger rounded-end-pill" for="a<?=$r['id']?>">Absent</label></div>
</div>
<?php endforeach; if(!$students):?><p class="lbl mb-0" data-pg-skip>No students enrolled in this class yet.</p><?php endif;?>
<?php if($canMark && $students):?><button class="btn-ui mt-3 w-100" data-pg-skip>Save attendance</button><?php endif;?>
</div></form>
<?php endif; endif;?>

<?php if($alerts && ($alerts['reactivated']||$alerts['inactive'])):?>
<div class="modal fade" id="alertModal" tabindex="-1"><div class="modal-dialog modal-dialog-centered"><div class="modal-content p-2" style="border-radius:24px"><div class="modal-body">
<?php if($alerts['reactivated']):?><div class="fw-bold text-success mb-1"><i class="bi bi-arrow-repeat"></i> Student reactivated</div>
<p class="lbl mb-2">These students were Inactive and are now Active again:</p><?php foreach($alerts['reactivated'] as $a):?><div><?=e($a[1])?> <span class="lbl">(<?=e($a[0])?>)</span></div><?php endforeach;?><?php endif;?>
<?php if($alerts['inactive']):?><div class="fw-bold text-danger mt-3 mb-1"><i class="bi bi-exclamation-triangle-fill"></i> Marked Inactive</div>
<p class="lbl mb-2">Absent for 2 class days in a row:</p><?php foreach($alerts['inactive'] as $a):?><div><?=e($a[1])?> <span class="lbl">(<?=e($a[0])?>)</span></div><?php endforeach;?><?php endif;?>
<div class="text-center"><button class="btn-ui mt-3" data-bs-dismiss="modal">OK</button></div></div></div></div></div>
<?php ob_start();?><script>new bootstrap.Modal(document.getElementById('alertModal')).show();</script>
<?php $pageScript=ob_get_clean(); endif;?>
<?php require __DIR__.'/includes/footer.php';
