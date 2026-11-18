<?php
const NIC_PATTERN='/^(\d{9}[VvXx]|\d{12})$/';
function age_of(string $dob): int { return (new DateTime($dob))->diff(new DateTime())->y; }
/** Saves a profile photo (JPG/PNG/WebP, max 2 MB) under uploads/. Returns [stored name, error]. */
function save_photo(?array $f,?string $old): array {
  if(!$f||$f['error']===UPLOAD_ERR_NO_FILE) return [null,'Choose a photo first.'];
  if($f['error']!==UPLOAD_ERR_OK||$f['size']>2*1024*1024) return [null,'The photo must be smaller than 2 MB.'];
  $info=@getimagesize($f['tmp_name']); $map=[IMAGETYPE_JPEG=>'jpg',IMAGETYPE_PNG=>'png',IMAGETYPE_WEBP=>'webp'];
  if(!$info||!isset($map[$info[2]])) return [null,'Use a JPG, PNG or WebP image.'];
  if($info[0]>6000||$info[1]>6000) return [null,'The image dimensions are too large.'];
  $dir=dirname(__DIR__).'/uploads'; if(!is_dir($dir)) @mkdir($dir,0755,true);
  $name='ph_'.bin2hex(random_bytes(12)).'.'.$map[$info[2]];
  if(!move_uploaded_file($f['tmp_name'],$dir.'/'.$name)) return [null,'Could not save the photo on the server.'];
  if($old) @unlink($dir.'/'.basename($old));
  return [$name,null];
}
function avatar(string $type,int $id,?string $photo,string $name,int $px=64): string {
  if($photo) return '<img class="avatar-img" style="width:'.$px.'px;height:'.$px.'px" src="photo.php?t='.$type.'&amp;id='.$id.'&amp;v='.e(substr($photo,3,8)).'" alt="">';
  return '<span class="avatar" style="width:'.$px.'px;height:'.$px.'px;font-size:'.round($px/2.4).'px">'.e(mb_strtoupper(mb_substr($name,0,1))).'</span>';
}
/** Validates student personal details (same rules as registration). Returns [errors, clean values keyed by column name]. */
function validate_student(array $in): array {
  $e=[]; $c=[];
  $c['first_name']=mb_substr(trim($in['first_name']??''),0,60); $c['last_name']=mb_substr(trim($in['last_name']??''),0,60);
  $c['full_name']=mb_substr(trim($in['full_name']??'')?:trim($c['first_name'].' '.$c['last_name']),0,150);
  $c['date_of_birth']=$in['dob']??''; $c['address']=mb_substr(trim($in['address']??''),0,255); $c['phone']=trim($in['phone']??''); $c['email']=trim($in['email']??'');
  $nic=strtoupper(trim($in['nic']??'')); $gn=mb_substr(trim($in['g_name']??''),0,120); $gr=$in['g_rel']??''; $gp=trim($in['g_phone']??''); $fp=trim($in['fingerprint_id']??'');
  if($c['first_name']===''||$c['last_name']==='') $e[]='First name and last name are required.';
  $age=null;
  if(!$c['date_of_birth']||!strtotime($c['date_of_birth'])) $e[]='A valid date of birth is required.';
  elseif(strtotime($c['date_of_birth'])>time()) $e[]='Date of birth cannot be in the future.';
  else $age=age_of($c['date_of_birth']);
  if(strlen(preg_replace('/\D/','',$c['phone']))<9) $e[]='Enter a valid contact number.';
  if($c['email']!==''&&!filter_var($c['email'],FILTER_VALIDATE_EMAIL)) $e[]='Email address is not valid.';
  if($age!==null){
    if($age>=18){ if(!preg_match(NIC_PATTERN,$nic)) $e[]='NIC number is required for students aged 18 or over (9 digits + V/X, or 12 digits).'; }
    elseif($gn===''||$gp===''||!in_array($gr,['Mother','Father','Guardian'],true)) $e[]='Guardian name, relationship and contact number are required for students under 18.';
  }
  $adult=$age!==null&&$age>=18;
  $c['nic']=$adult?$nic:null; $c['guardian_name']=$adult?null:($gn?:null); $c['guardian_relation']=($adult||!in_array($gr,['Mother','Father','Guardian'],true))?null:$gr; $c['guardian_phone']=$adult?null:($gp?:null);
  $c['fingerprint_id']=$fp!==''?mb_substr($fp,0,20):null; $c['email']=$c['email']?:null;
  return [$e,$c];
}
