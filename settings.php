<?php
require_once __DIR__.'/includes/bootstrap.php';
require_menu('settings');
$s=branch_scope(); $owner=is_owner(); $super=($_SESSION['user']['role']==='super_admin');
$ROLES=['admin'=>'Admin','branch_manager'=>'Branch Manager','administrator'=>'Administrator','receptionist'=>'Receptionist','teacher'=>'Teacher','finance'=>'Finance','other'=>'Other'];
function save_setting(string $k,string $v): void { db()->prepare('INSERT INTO settings (k,v) VALUES (?,?) ON DUPLICATE KEY UPDATE v=VALUES(v)')->execute([$k,$v]); }
if ($_SERVER['REQUEST_METHOD']==='POST') {
  csrf_check(); $act=$_POST['act']??''; $to='settings.php';
  if ($act==='general' && $owner) {
    $name=trim($_POST['institute_name']??''); $cur=strtoupper(preg_replace('/[^A-Za-z$€£.]/','',$_POST['currency']??'')); $min=(int)($_POST['session_minutes']??0);
    if ($name===''||$cur===''||strlen($cur)>5||$min<5||$min>480) flash_set('msg','Name, a currency (up to 5 letters) and a timeout between 5 and 480 minutes are required.');
    else { save_setting('institute_name',$name); save_setting('currency',$cur); save_setting('session_minutes',(string)$min); save_setting('payment_info',mb_substr(trim($_POST['payment_info']??''),0,1000)); log_activity('settings_general',$name); flash_set('msg','General settings saved.'); }
  } elseif ($act==='grading' && $owner) {
    $v=[(float)$_POST['grade_a'],(float)$_POST['grade_b'],(float)$_POST['grade_c'],(float)$_POST['grade_s']]; $to='settings.php?tab=grading';
    if (!($v[0]<=100 && $v[0]>$v[1] && $v[1]>$v[2] && $v[2]>$v[3] && $v[3]>0)) flash_set('msg','Each grade must be lower than the one above it, between 0 and 100.');
    else { foreach(['grade_a','grade_b','grade_c','grade_s'] as $i=>$k) save_setting($k,(string)$v[$i]); log_activity('settings_grading',implode('/',$v)); flash_set('msg','Grading saved. C is the pass mark for annual-intake final exams.'); }
  } elseif ($act==='access' && $super) {
    $to='settings.php?tab=access'; $pdo=db(); $keys=array_values(array_diff(array_keys(MENU),STUDENT_MENU));
    try {
      $pdo->beginTransaction();
      foreach (array_keys($ROLES) as $role) {
        $sel=array_values(array_intersect($keys,$_POST['m'][$role]??[]));
        foreach (['dashboard','profile'] as $must) if (!in_array($must,$sel,true)) $sel[]=$must;
        if ($role==='admin' && !in_array('settings',$sel,true)) $sel[]='settings';
        $pdo->prepare('DELETE FROM role_menu WHERE role=?')->execute([$role]);
        $ins=$pdo->prepare('INSERT INTO role_menu (role,menu_key) VALUES (?,?)'); foreach($sel as $k) $ins->execute([$role,$k]);
      }
      $pdo->commit(); log_activity('settings_access','Role menu access updated'); flash_set('msg','Access updated. Changes apply the next time each person loads a page.');
    } catch (PDOException $x) { if($pdo->inTransaction()) $pdo->rollBack(); flash_set('msg','Could not save access settings.'); }
  } elseif ($act==='branch' && !$owner && $_SESSION['user']['role']==='branch_manager' && $s) {
    $to='settings.php?tab=branch';
    db()->prepare('UPDATE branches SET address=?,phone=? WHERE id=?')->execute([trim($_POST['address']??''),trim($_POST['phone']??''),$s]); log_activity('branch_contact','Branch contact updated'); flash_set('msg','Branch details saved.');
  }
  header("Location: $to"); exit;
}
$pageKey='settings'; require __DIR__.'/includes/header.php';
$msg=flash_get('msg');
$tabs=$owner?['general'=>'General','grading'=>'Grading']+($super?['access'=>'Role access']:[]):['branch'=>'My branch'];
$tab=$_GET['tab']??array_key_first($tabs); if(!isset($tabs[$tab])) $tab=array_key_first($tabs);
?>
<?php if($msg):?><div class="alert alert-info rounded-4"><?=e($msg)?></div><?php endif;?>
<ul class="nav nav-pills mb-3 flex-nowrap overflow-auto"><?php foreach($tabs as $k=>$l):?><li class="nav-item"><a class="nav-link <?=$tab===$k?'active':''?>" href="settings.php?tab=<?=$k?>"><?=$l?></a></li><?php endforeach;?></ul>
<?php if($tab==='general'):?>
<form method="post" class="card-ui row g-3"><input type="hidden" name="csrf" value="<?=csrf()?>"><input type="hidden" name="act" value="general">
 <div class="col-12"><label class="lbl">Institute name (shown on receipts and payslips)</label><input class="form-control" name="institute_name" value="<?=e(setting('institute_name',''))?>" required></div>
 <div class="col-6"><label class="lbl">Currency</label><input class="form-control" name="currency" maxlength="5" value="<?=e(setting('currency','LKR'))?>" required></div>
 <div class="col-6"><label class="lbl">Auto sign-out after (minutes)</label><input class="form-control" type="number" min="5" max="480" name="session_minutes" value="<?=e(setting('session_minutes',30))?>" required></div>
 <div class="col-12"><label class="lbl">How students can pay fees (shown in the student portal: bank details, branch cash desk, etc.)</label><textarea class="form-control" name="payment_info" rows="3" maxlength="1000"><?=e(setting('payment_info',''))?></textarea></div>
 <div class="col-12"><button class="btn-ui">Save</button></div></form>
<?php elseif($tab==='grading'):?>
<form method="post" class="card-ui row g-3"><input type="hidden" name="csrf" value="<?=csrf()?>"><input type="hidden" name="act" value="grading">
 <div class="lbl">Minimum percentage for each grade. Below S is W. C is also the pass mark for annual-intake final exams.</div>
 <?php foreach(['grade_a'=>['A',75],'grade_b'=>['B',65],'grade_c'=>['C (pass)',50],'grade_s'=>['S',35]] as $k=>$g):?>
 <div class="col-6 col-md-3"><label class="lbl"><?=$g[0]?></label><input class="form-control" type="number" step="0.01" min="1" max="100" name="<?=$k?>" value="<?=e(setting($k,$g[1]))?>" required></div><?php endforeach;?>
 <div class="col-12"><button class="btn-ui">Save</button></div></form>
<?php elseif($tab==='access'):
  $cur=[]; foreach(db()->query('SELECT role,menu_key FROM role_menu')->fetchAll() as $r) $cur[$r['role']][$r['menu_key']]=1; ?>
<form method="post" class="card-ui"><input type="hidden" name="csrf" value="<?=csrf()?>"><input type="hidden" name="act" value="access">
 <p class="lbl">Choose which sections each role sees in the sidebar. Super Admin always sees everything, and Dashboard and My Profile stay on for everyone. Sections that show money or staff pay (Payments, Salaries, Reports) still only open for roles allowed to handle them, and every role stays limited to its own branch.</p>
 <div class="table-wrap"><table class="table table-sm align-middle text-center"><thead><tr><th class="text-start">Section</th><?php foreach($ROLES as $l):?><th><?=e($l)?></th><?php endforeach;?></tr></thead><tbody>
 <?php foreach(MENU as $k=>$m): if(in_array($k,STUDENT_MENU,true)) continue;?><tr><td class="text-start"><i class="bi <?=$m[1]?>"></i> <?=e($m[0])?></td>
  <?php foreach($ROLES as $role=>$l): $locked=in_array($k,['dashboard','profile'],true)||($role==='admin'&&$k==='settings');?>
  <td><input type="checkbox" class="form-check-input" name="m[<?=$role?>][]" value="<?=$k?>" <?=!empty($cur[$role][$k])||$locked?'checked':''?> <?=$locked?'onclick="return false"':''?>></td><?php endforeach;?></tr><?php endforeach;?></tbody></table></div>
 <button class="btn-ui mt-2">Save access</button></form>
<?php else:
  $b=db()->prepare('SELECT * FROM branches WHERE id=?'); $b->execute([(int)$s]); $br=$b->fetch(); $edit=$_SESSION['user']['role']==='branch_manager';
  if(!$br):?><div class="card-ui lbl">No branch is assigned to your account.</div><?php else:?>
<form method="post" class="card-ui row g-3"><input type="hidden" name="csrf" value="<?=csrf()?>"><input type="hidden" name="act" value="branch">
 <div class="col-md-6"><label class="lbl">Branch</label><input class="form-control" value="<?=e($br['name'])?> (<?=e($br['code'])?>)" disabled></div>
 <div class="col-md-6"><label class="lbl">Phone</label><input class="form-control" name="phone" value="<?=e($br['phone'])?>" <?=$edit?'':'disabled'?>></div>
 <div class="col-12"><label class="lbl">Address</label><input class="form-control" name="address" value="<?=e($br['address'])?>" <?=$edit?'':'disabled'?>></div>
 <?php if($edit):?><div class="col-12"><button class="btn-ui">Save</button></div><?php else:?><div class="col-12 lbl">Only the Branch Manager can edit these details.</div><?php endif;?></form>
<?php endif; endif;?>
<?php require __DIR__.'/includes/footer.php';
