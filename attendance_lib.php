<?php
/** Save one attendance record and apply the Active/Inactive rules.
 *  Rule: absent on 2 consecutive recorded class days -> Inactive.
 *  Marking Present on an Inactive student -> Active again (and reported in $alerts['reactivated']). */
function mark_attendance(int $sid,int $bid,string $date,string $status,?string $in,?string $out,string $src,?int $by,array &$alerts): void {
  $pdo=db();
  $pdo->prepare('INSERT INTO attendance (student_id,batch_id,att_date,status,check_in,check_out,source,marked_by) VALUES (?,?,?,?,?,?,?,?)
    ON DUPLICATE KEY UPDATE status=VALUES(status),check_in=VALUES(check_in),check_out=VALUES(check_out),source=VALUES(source),marked_by=VALUES(marked_by)')
    ->execute([$sid,$bid,$date,$status,$in,$out,$src,$by]);
  $q=$pdo->prepare('SELECT student_code,full_name,status FROM students WHERE id=?'); $q->execute([$sid]); $s=$q->fetch(); if(!$s) return;
  if ($status==='present') {
    if ($s['status']==='inactive') {
      $pdo->prepare("UPDATE students SET status='active' WHERE id=?")->execute([$sid]);
      $alerts['reactivated'][$sid]=[$s['student_code'],$s['full_name']];
      unset($alerts['inactive'][$sid]);
    }
  } else {
    $l=$pdo->prepare('SELECT status FROM attendance WHERE student_id=? AND batch_id=? AND att_date<=? ORDER BY att_date DESC LIMIT 2');
    $l->execute([$sid,$bid,$date]); $last=$l->fetchAll(PDO::FETCH_COLUMN);
    if (count($last)==2 && $last[0]==='absent' && $last[1]==='absent' && $s['status']==='active') {
      $pdo->prepare("UPDATE students SET status='inactive' WHERE id=?")->execute([$sid]);
      $alerts['inactive'][$sid]=[$s['student_code'],$s['full_name']];
    }
  }
}
/** Parse fingerprint machine export (CSV/TXT, one punch per line: ID, date, time). Returns [id => [Y-m-d => [H:i:s,...]]]. */
function parse_punches(string $text): array {
  $out=[];
  foreach (preg_split('/\r\n|\r|\n/',$text) as $line) {
    $line=trim($line); if($line==='') continue;
    $parts=preg_split('/[,\t;]+/',$line); $id=trim($parts[0]," \"'\xEF\xBB\xBF"); if($id===''||strpos($id,'-')!==false||strpos($id,'/')!==false||strpos($id,':')!==false) continue;
    if (!preg_match('/(\d{4}-\d{2}-\d{2}|\d{2}\/\d{2}\/\d{4})[ T,;\t"]*(\d{1,2}):(\d{2})(?::(\d{2}))?/',$line,$m)) continue;
    $d=$m[1]; if (strpos($d,'/')!==false) { [$dd,$mm,$yy]=explode('/',$d); $d="$yy-$mm-$dd"; }
    if (!checkdate((int)substr($d,5,2),(int)substr($d,8,2),(int)substr($d,0,4))) continue;
    $out[$id][$d][]=sprintf('%02d:%s:%s',$m[2],$m[3],$m[4]??'00');
  }
  return $out;
}
function fp_key(string $id): string { $k=ltrim($id,'0'); return $k===''?'0':$k; }
