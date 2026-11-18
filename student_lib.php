<?php
function stu_id(): int { return (int)($_SESSION['user']['student_id']??0); }
/** Student pages: menu check plus a linked student record. */
function stu_guard(string $key): void { require_menu($key); if(!stu_id()){ http_response_code(403); exit('No student record is linked to this account. Please contact your institute.'); } }
function stu_row(): array { $q=db()->prepare('SELECT s.*,br.name brname,br.phone brphone FROM students s JOIN branches br ON br.id=s.branch_id WHERE s.id=?'); $q->execute([stu_id()]); return $q->fetch()?:[]; }
function stu_batches(): array {
  $q=db()->prepare('SELECT b.*,c.name cname,t.name tname,br.name brname,u.full_name tch,e.enrolled_on FROM enrolments e JOIN batches b ON b.id=e.batch_id JOIN courses c ON c.id=b.course_id JOIN course_types t ON t.id=c.type_id JOIN branches br ON br.id=b.branch_id LEFT JOIN users u ON u.id=b.teacher_id WHERE e.student_id=? ORDER BY (b.status=\'active\') DESC,b.start_date DESC');
  $q->execute([stu_id()]); return $q->fetchAll();
}
function stu_ids(): string { $ids=array_map('intval',array_column(stu_batches(),'id')); return $ids?implode(',',$ids):'0'; }
function stu_in_batch(int $bid): bool { $q=db()->prepare('SELECT 1 FROM enrolments WHERE student_id=? AND batch_id=?'); $q->execute([stu_id(),$bid]); return (bool)$q->fetchColumn(); }
function gen_password(int $len=9): string { $c='abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789'; $p=''; for($i=0;$i<$len;$i++) $p.=$c[random_int(0,strlen($c)-1)]; return $p; }
/** Creates the portal login for a student. $st needs id, branch_id, full_name, phone. Returns [true, temporary password] or [false, message]. */
function create_student_login(array $st,string $email): array {
  $email=trim($email); if(!filter_var($email,FILTER_VALIDATE_EMAIL)) return [false,'A valid email is needed for the login.'];
  $x=db()->prepare('SELECT 1 FROM users WHERE student_id=?'); $x->execute([$st['id']]); if($x->fetch()) return [false,'This student already has a login.'];
  $pw=gen_password();
  try { db()->prepare("INSERT INTO users (branch_id,role,full_name,email,password_hash,phone,student_id,must_change_pw) VALUES (?,'student',?,?,?,?,?,1)")->execute([$st['branch_id'],$st['full_name'],$email,password_hash($pw,PASSWORD_DEFAULT),$st['phone']?:null,$st['id']]); }
  catch (PDOException $e) { return [false,'That email is already used by another account.']; }
  return [true,$pw];
}
function reset_student_login(int $studentId): ?string {
  $pw=gen_password(); $u=db()->prepare('UPDATE users SET password_hash=?,must_change_pw=1,failed_logins=0,locked_until=NULL WHERE student_id=?'); $u->execute([password_hash($pw,PASSWORD_DEFAULT),$studentId]);
  return $u->rowCount()?$pw:null;
}
function pct_color(float $p): string { return $p>=75?'#1e9e5a':($p>=50?'#e08a00':'#d9534f'); }
