<?php
require_once __DIR__.'/../config.php';
date_default_timezone_set(defined('APP_TZ')?APP_TZ:'Asia/Colombo');
session_set_cookie_params(['httponly'=>true,'samesite'=>'Lax','secure'=>(!empty($_SERVER['HTTPS'])&&$_SERVER['HTTPS']!=='off')]);
session_start();
function db(): PDO {
  static $pdo; if ($pdo) return $pdo;
  $pdo = new PDO('mysql:host='.DB_HOST.';dbname='.DB_NAME.';charset=utf8mb4', DB_USER, DB_PASS,
    [PDO::ATTR_ERRMODE=>PDO::ERRMODE_EXCEPTION, PDO::ATTR_DEFAULT_FETCH_MODE=>PDO::FETCH_ASSOC]);
  $pdo->exec("SET time_zone='".date('P')."'");   // keep MySQL NOW()/CURDATE() in the same timezone as PHP
  return $pdo;
}
function e($s){ return htmlspecialchars((string)$s, ENT_QUOTES, 'UTF-8'); }
function csrf(): string { return $_SESSION['csrf'] ??= bin2hex(random_bytes(16)); }
function csrf_check(): void {
  if (!hash_equals($_SESSION['csrf'] ?? '', $_POST['csrf'] ?? '')) { http_response_code(419); exit('Invalid session token.'); }
}
function log_activity(string $action, string $details=''): void {
  $u = $_SESSION['user'] ?? null;
  db()->prepare('INSERT INTO activity_logs (user_id,branch_id,action,details,ip_address) VALUES (?,?,?,?,?)')
    ->execute([$u['id']??null, $u['branch_id']??null, $action, $details, $_SERVER['REMOTE_ADDR']??'']);
}
const MENU = [
  'dashboard'=>['Dashboard','bi-grid-1x2-fill','dashboard.php'],
  'students'=>['Students','bi-people-fill','students.php'],
  'courses'=>['Courses','bi-journal-bookmark-fill','courses.php'],
  'branches'=>['Branches','bi-building','branches.php'],
  'schedule'=>['Schedule','bi-calendar3','schedule.php'],
  'attendance'=>['Attendance','bi-check2-square','attendance.php'],
  'exams'=>['Exams','bi-pencil-square','exams.php'],
  'materials'=>['Study Materials','bi-folder-fill','materials.php'],
  'staff'=>['Staff','bi-person-badge-fill','staff.php'],
  'teachers'=>['Teachers','bi-mortarboard-fill','teachers.php'],
  'leaves'=>['Leave','bi-calendar-x-fill','leaves.php'],
  'payments'=>['Payments','bi-credit-card-fill','payments.php'],
  'salaries'=>['Salaries','bi-cash-stack','salaries.php'],
  'reports'=>['Reports','bi-bar-chart-fill','reports.php'],
  'settings'=>['Settings','bi-gear-fill','settings.php'],
  'activity'=>['Activity Log','bi-clock-history','activity.php'],
  'profile'=>['My Profile','bi-person-circle','profile.php'],
  'sdash'=>['Home','bi-house-door-fill','my_home.php'],
  'sclasses'=>['My Classes','bi-journal-bookmark-fill','my_classes.php'],
  'sschedule'=>['Timetable','bi-calendar3','my_schedule.php'],
  'sassign'=>['Assignments','bi-journal-check','my_assignments.php'],
  'squiz'=>['Quizzes','bi-ui-checks','my_quizzes.php'],
  'sresults'=>['Results','bi-award-fill','my_results.php'],
  'sattendance'=>['Attendance','bi-check2-square','my_attendance.php'],
  'sfees'=>['Fees','bi-wallet2','my_fees.php'],
  'sprofile'=>['My Profile','bi-person-circle','my_profile.php'],
];
const STUDENT_MENU=['sdash','sclasses','sschedule','sassign','squiz','sresults','sattendance','sfees','sprofile'];
function allowed_menu(string $role): array {
  $st = db()->prepare('SELECT menu_key FROM role_menu WHERE role=?'); $st->execute([$role]);
  $keys = $st->fetchAll(PDO::FETCH_COLUMN);
  return array_values(array_filter(array_keys(MENU), fn($k)=>in_array($k,$keys,true)));
}
function is_owner(): bool { return in_array($_SESSION['user']['role']??'', ['super_admin','admin'], true); }
/** null = all branches (owners); otherwise the user's branch id. Use in every query. */
function branch_scope(): ?int { return is_owner() ? null : (int)($_SESSION['user']['branch_id'] ?? 0); }
function require_login(): void {
  if (empty($_SESSION['user'])) { header('Location: login.php'); exit; }
  if (time() - ($_SESSION['last'] ?? 0) > ((int)setting('session_minutes',30))*60) { session_destroy(); header('Location: login.php?timeout=1'); exit; }
  $_SESSION['last'] = time(); $f=basename($_SERVER['PHP_SELF']);
  if (($_SESSION['user']['role']??'')==='student') {   // students may only use the student portal pages
    $ok=['my_home.php','my_classes.php','my_class.php','my_assignment.php','my_assignments.php','my_quizzes.php','my_schedule.php','my_attendance.php','my_results.php','my_fees.php','my_profile.php','receipt.php','submission.php','change_password.php','logout.php','learn_quiz.php','learn_file.php','photo.php'];
    if (!in_array($f,$ok,true)) { header('Location: my_home.php'); exit; }
  }
  if (!empty($_SESSION['user']['must_change_pw']) && !in_array($f,['change_password.php','logout.php'],true)) { header('Location: change_password.php'); exit; }
}
function is_student_user(): bool { return ($_SESSION['user']['role']??'')==='student'; }
function require_menu(string $key): void {
  require_login();
  if (!in_array($key, allowed_menu($_SESSION['user']['role']), true)) { http_response_code(403); exit('Access denied.'); }
}
function can(string $a): bool {
  $r = $_SESSION['user']['role'] ?? '';
  $m = [
    'register_student'=>['super_admin','admin','branch_manager','administrator','receptionist'],
    'manage_batches'=>['super_admin','admin','branch_manager','administrator'],
    'manage_users'=>['super_admin','admin','branch_manager'],
    'mark_attendance'=>['super_admin','admin','branch_manager','administrator','receptionist'],
    'take_payment'=>['super_admin','admin','branch_manager','administrator','receptionist','finance'],
    'set_scholarship'=>['super_admin','admin','branch_manager'],
    'schedule_exam'=>['super_admin','admin','branch_manager','administrator'],
    'enter_marks'=>['super_admin','admin','branch_manager','administrator','teacher'],
    'manage_payroll'=>['super_admin','admin','branch_manager','finance'],
    'manage_expenses'=>['super_admin','admin','branch_manager','finance'],
    'void_payment'=>['super_admin','admin','branch_manager'],
    'mark_staff_attendance'=>['super_admin','admin','branch_manager','administrator'],
    'approve_leave'=>['super_admin','admin','branch_manager'],
    'manage_substitutes'=>['super_admin','admin','branch_manager','administrator'],
    'upload_material'=>['super_admin','admin','branch_manager','administrator','teacher'],
    'manage_student_accounts'=>['super_admin','admin','branch_manager','administrator','receptionist'],
    'edit_student'=>['super_admin','admin','branch_manager','administrator','receptionist'],
    'delete_student'=>['super_admin','admin'],
    'comment'=>['super_admin','admin','branch_manager','administrator','teacher'],
  ];
  return in_array($r, $m[$a] ?? [], true);
}
/** Batches whose end date has passed become "completed" automatically. */
function refresh_batches(): void {
  db()->exec("UPDATE batches SET status='completed' WHERE status='active' AND end_date < CURDATE()");
}
function flash_set(string $k, $v): void { $_SESSION['flash'][$k]=$v; }
function flash_get(string $k) { $v=$_SESSION['flash'][$k]??null; unset($_SESSION['flash'][$k]); return $v; }

function setting(string $k,$d=null) {
  static $all=null;
  if ($all===null) { $all=[]; try { foreach (db()->query('SELECT k,v FROM settings')->fetchAll() as $r) $all[$r['k']]=$r['v']; } catch (Throwable $e) {} }
  return $all[$k] ?? $d;
}

function is_teacher(): bool { return ($_SESSION['user']['role']??'')==='teacher'; }
/** For teachers: only the classes assigned to them. Alias must be the batches table alias. */
function tfrag(string $a): string {
  if (!is_teacher()) return ''; $id=(int)$_SESSION['user']['id'];
  return " AND ($a.teacher_id=$id OR EXISTS (SELECT 1 FROM substitutions sbx WHERE sbx.batch_id=$a.id AND sbx.substitute_id=$id AND sbx.class_date>=DATE_SUB(CURDATE(),INTERVAL 1 DAY)))";
}
/** True if the logged-in teacher teaches this batch, or covers it as a substitute (from the day before the cover date). */
function teacher_has_batch(int $batchId): bool {
  $id=(int)$_SESSION['user']['id'];
  $q=db()->prepare("SELECT 1 FROM batches b WHERE b.id=? AND (b.teacher_id=? OR EXISTS (SELECT 1 FROM substitutions sb WHERE sb.batch_id=b.id AND sb.substitute_id=? AND sb.class_date>=DATE_SUB(CURDATE(),INTERVAL 1 DAY)))");
  $q->execute([$batchId,$id,$id]); return (bool)$q->fetchColumn();
}
require_once __DIR__.'/paginate.php';
