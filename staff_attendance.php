<?php
require_once __DIR__.'/includes/bootstrap.php';
require_once __DIR__.'/includes/attendance_lib.php';
require_menu('attendance');
if (!can('mark_staff_attendance')) { http_response_code(403); exit('Access denied.'); }
$s=branch_scope(); $tab=($_GET['tab']??'')==='import'?'import':'mark';
const ST_LABELS=['present'=>'Present','half_day'=>'Half day','leave'=>'Leave','absent'=>'Absent'];
$branchId=$s!==null?$s:(int)($_GET['branch']??($_POST['branch_id']??0));

if ($_SERVER['REQUEST_METHOD']==='POST') {
  csrf_check(); $act=$_POST['act']??''; $me=(int)$_SESSION['user']['id'];
  if ($act==='save') {
    $date=$_POST['date']??''; $n=0;
    if (!strtotime($date)||$date>date('Y-m-d')) flash_set('msg','Invalid date.');
    else {
      $q=db()->prepare("SELECT id FROM users WHERE status='active'".($s!==null?' AND branch_id='.(int)$s:($branchId?' AND branch_id='.(int)$branchId:''))); $q->execute(); $valid=array_flip($q->fetchAll(PDO::FETCH_COLUMN));
      $up=db()->prepare('INSERT INTO staff_attendance (user_id,att_date,status,source,marked_by) VALUES (?,?,?,\'manual\',?) ON DUPLICATE KEY UPDATE status=VALUES(status),source=\'manual\',marked_by=VALUES(marked_by)');
      $posted=$_POST['st']??[]; if(!is_array($posted)) $posted=[];
      foreach ($posted as $uid=>$v) { if (!isset($valid[(int)$uid])||!isset(ST_LABELS[$v])) continue; $up->execute([(int)$uid,$date,$v,$me]); $n++; }
      log_activity('staff_attendance',"$date ($n staff)"); flash_set('msg',"Staff attendance saved for $n people.");
    }
    header('Location: staff_attendance.php?date='.urlencode($date).($branchId&&$s===null?'&branch='.$branchId:'')); exit;
  }
  if ($act==='import') {
    $f=$_FILES['file']??null;
    if (!$branchId) flash_set('msg','Choose a branch.');
    elseif (!$f||$f['error']!==UPLOAD_ERR_OK||$f['size']>2*1024*1024) flash_set('msg','Please choose a CSV/TXT file under 2 MB.');
    else {
      $q=db()->prepare("SELECT id,fingerprint_id FROM users WHERE status='active' AND branch_id=? AND fingerprint_id IS NOT NULL"); $q->execute([$branchId]);
      $map=[]; foreach($q->fetchAll() as $r) $map[fp_key($r['fingerprint_id'])]=(int)$r['id'];
      $unknown=[]; $cnt=0; $skipped=0;
      $get=db()->prepare('SELECT status,source FROM staff_attendance WHERE user_id=? AND att_date=?');
      $up=db()->prepare("INSERT INTO staff_attendance (user_id,att_date,status,check_in,check_out,source,marked_by) VALUES (?,?,'present',?,?,'fingerprint',?) ON DUPLICATE KEY UPDATE status='present',check_in=VALUES(check_in),check_out=VALUES(check_out),source='fingerprint',marked_by=VALUES(marked_by)");
      foreach (parse_punches(file_get_contents($f['tmp_name'])) as $fid=>$days) {
        $k=fp_key($fid); if (!isset($map[$k])) { $unknown[$fid]=1; continue; }
        foreach ($days as $d=>$times) {
          sort($times); $get->execute([$map[$k],$d]); $ex=$get->fetch();
          if ($ex && in_array($ex['status'],['leave','half_day'],true)) { $skipped++; continue; }
          $up->execute([$map[$k],$d,$times[0],count($times)>1?end($times):null,(int)$_SESSION['user']['id']]); $cnt++;
        }
      }
      log_activity('staff_attendance_import',"branch $branchId: $cnt records");
      $m="Import finished: $cnt present record(s).".($skipped?" $skipped skipped (marked Leave/Half day manually).":'');
      if ($unknown) $m.=' Unknown fingerprint IDs ignored: '.implode(', ',array_slice(array_keys($unknown),0,15)).'.';
      if (!$cnt && !$unknown) $m='No usable punch records found. Each line needs a fingerprint ID, a date, and a time.';
      flash_set('msg',$m);
    }
    header('Location: staff_attendance.php?tab=import'.($branchId&&$s===null?'&branch='.$branchId:'')); exit;
  }
}
$pageKey='attendance'; require __DIR__.'/includes/header.php'; $msg=flash_get('msg');
$branches=db()->query("SELECT id,name FROM branches WHERE status='active' ORDER BY name")->fetchAll();
$date=$_GET['date']??date('Y-m-d'); if(!strtotime($date)||$date>date('Y-m-d')) $date=date('Y-m-d');
?>
<?php if($msg):?><div class="alert alert-info rounded-4"><?=e($msg)?></div><?php endif;?>
<ul class="nav nav-pills mb-3 flex-nowrap overflow-auto">
 <li class="nav-item"><a class="nav-link" href="attendance.php">Student attendance</a></li>
 <li class="nav-item"><a class="nav-link <?=$tab==='mark'?'active':''?>" href="staff_attendance.php">Staff attendance</a></li>
 <li class="nav-item"><a class="nav-link <?=$tab==='import'?'active':''?>" href="staff_attendance.php?tab=import">Staff fingerprint import</a></li></ul>
<?php if($tab==='import'):?>
<div class="card-ui"><p class="lbl">Upload the machine export (CSV or TXT) with a <b>fingerprint ID</b>, <b>date</b> and <b>time</b> on each line, for example <code>7,2026-11-02 08:31:00</code>. Staff need their fingerprint ID set on the Staff page first. Anyone with a punch is marked Present (first punch = in, last = out). People with no punch are <b>not</b> auto-marked absent, so mark absences and leave by hand.</p>
<form method="post" enctype="multipart/form-data" class="row g-2"><input type="hidden" name="csrf" value="<?=csrf()?>"><input type="hidden" name="act" value="import">
<?php if($s===null):?><div class="col-md-4"><select class="form-select" name="branch_id" required><option value="">Branch</option><?php foreach($branches as $b):?><option value="<?=$b['id']?>" <?=$branchId===(int)$b['id']?'selected':''?>><?=e($b['name'])?></option><?php endforeach;?></select></div><?php endif;?>
<div class="col"><input class="form-control" type="file" name="file" accept=".csv,.txt,.dat" required></div><div class="col-md-2"><button class="btn-ui w-100">Import</button></div></form></div>
<?php else:
  $w="u.status='active'".($s!==null?' AND u.branch_id='.(int)$s:($branchId?' AND u.branch_id='.(int)$branchId:''));
  $q=db()->prepare("SELECT u.id,u.full_name,u.role,u.job_title,a.status,a.check_in,a.check_out FROM users u LEFT JOIN staff_attendance a ON a.user_id=u.id AND a.att_date=? WHERE $w ORDER BY u.full_name"); $q->execute([$date]); $staff=$q->fetchAll();?>
<form method="get" class="card-ui mb-3 row g-2">
 <?php if($s===null):?><div class="col-12 col-md-5"><select class="form-select" name="branch"><option value="">All branches</option><?php foreach($branches as $b):?><option value="<?=$b['id']?>" <?=$branchId===(int)$b['id']?'selected':''?>><?=e($b['name'])?></option><?php endforeach;?></select></div><?php endif;?>
 <div class="col-8 col-md-4"><input class="form-control" type="date" name="date" value="<?=e($date)?>" max="<?=date('Y-m-d')?>"></div><div class="col-4 col-md-3"><button class="btn-ui w-100">Load</button></div></form>
<form method="post"><input type="hidden" name="csrf" value="<?=csrf()?>"><input type="hidden" name="act" value="save"><input type="hidden" name="date" value="<?=e($date)?>"><?php if($s===null&&$branchId):?><input type="hidden" name="branch_id" value="<?=$branchId?>"><?php endif;?>
<div class="card-ui"><div class="mb-2"><button type="button" class="btn btn-sm btn-light rounded-pill" onclick="document.querySelectorAll('.p-in').forEach(r=>r.checked=true)">All present</button></div>
<?php foreach($staff as $z):?><div class="d-flex align-items-center gap-2 py-2 border-top flex-wrap">
 <div class="me-auto"><div><?=e($z['full_name'])?></div><div class="lbl"><?=e($z['job_title']?:ucwords(str_replace('_',' ',$z['role'])))?><?=$z['check_in']?' · in '.e(substr($z['check_in'],0,5)).($z['check_out']?' · out '.e(substr($z['check_out'],0,5)):''):''?></div></div>
 <div class="btn-group btn-group-sm"><?php foreach(ST_LABELS as $k=>$l): $cls=['present'=>'success','half_day'=>'warning','leave'=>'primary','absent'=>'danger'][$k];?>
  <input type="radio" class="btn-check <?=$k==='present'?'p-in':''?>" name="st[<?=$z['id']?>]" id="s<?=$z['id'].$k?>" value="<?=$k?>" <?=$z['status']===$k?'checked':''?>><label class="btn btn-outline-<?=$cls?>" for="s<?=$z['id'].$k?>"><?=$l?></label><?php endforeach;?></div></div>
<?php endforeach; if(!$staff):?><p class="lbl mb-0">No active staff found.</p><?php endif;?>
<?php if($staff):?><button class="btn-ui mt-3 w-100">Save staff attendance</button><?php endif;?></div></form>
<?php endif;?>
<?php require __DIR__.'/includes/footer.php';
