<?php
function money($v): string {
  static $c; if ($c===null) { $c=db()->query("SELECT v FROM settings WHERE k='currency'")->fetchColumn() ?: 'LKR'; }
  return $c.' '.number_format((float)$v,2);
}
function add_months(string $d,int $m): string { $q=db()->prepare('SELECT DATE_ADD(?, INTERVAL ? MONTH)'); $q->execute([$d,$m]); return $q->fetchColumn(); }
/** Fee account for an enrolment, created on first use from the batch fee and payment plan.
 *  installments_3: batch fee = total course fee, split in 3. monthly: batch fee = fee PER MONTH x duration. custom: one flexible balance. */
function ensure_account(int $enrId): ?array {
  $pdo=db();
  $q=$pdo->prepare('SELECT * FROM fee_accounts WHERE enrolment_id=?'); $q->execute([$enrId]); $a=$q->fetch();
  if ($a) return $a;
  $q=$pdo->prepare('SELECT e.id,e.student_id,e.batch_id,b.fee,b.fee_plan,b.start_date,b.duration_months FROM enrolments e JOIN batches b ON b.id=e.batch_id WHERE e.id=?');
  $q->execute([$enrId]); $e=$q->fetch(); if(!$e) return null;
  $dur=max(1,(int)$e['duration_months']); $fee=round((float)$e['fee'],2); $plan=$e['fee_plan'];
  $total = $plan==='monthly' ? round($fee*$dur,2) : $fee;
  $pdo->beginTransaction();
  try {
    $pdo->prepare('INSERT INTO fee_accounts (enrolment_id,student_id,batch_id,total_fee,plan) VALUES (?,?,?,?,?)')->execute([$enrId,$e['student_id'],$e['batch_id'],$total,$plan]);
    $aid=(int)$pdo->lastInsertId(); $rows=[];
    if ($plan==='installments_3') {
      $step=max(1,intdiv($dur,3)); $each=round($total/3,2);
      for($k=0;$k<3;$k++) $rows[]=[$k+1,'Installment '.($k+1),add_months($e['start_date'],$k*$step),$k<2?$each:round($total-2*$each,2)];
    } elseif ($plan==='monthly') {
      for($k=0;$k<$dur;$k++) $rows[]=[$k+1,'Month '.($k+1),add_months($e['start_date'],$k),$fee];
    } else $rows[]=[1,'Full fee',$e['start_date'],$total];
    $ins=$pdo->prepare('INSERT INTO installments (account_id,no,label,due_date,amount) VALUES (?,?,?,?,?)');
    foreach($rows as $r) $ins->execute([$aid,$r[0],$r[1],$r[2],$r[3]]);
    $pdo->commit();
  } catch (PDOException $x) { if($pdo->inTransaction()) $pdo->rollBack(); }
  $q=$pdo->prepare('SELECT * FROM fee_accounts WHERE enrolment_id=?'); $q->execute([$enrId]); return $q->fetch() ?: null;
}
function account_summary(int $accId): array {
  $q=db()->prepare('SELECT COALESCE(SUM(amount),0) net, COALESCE(SUM(paid),0) paid FROM installments WHERE account_id=?'); $q->execute([$accId]); $r=$q->fetch();
  return ['net'=>(float)$r['net'],'paid'=>(float)$r['paid'],'balance'=>round((float)$r['net']-(float)$r['paid'],2)];
}
/** Returns new payment id, or an error string. Allocates oldest-due installments first. */
function record_payment(array $acc,float $amount,string $method,string $ref,string $date,string $note,int $by,string $branchCode,int $branchId) {
  $pdo=db(); $amount=round($amount,2);
  if ($amount<=0) return 'Enter an amount greater than zero.';
  if (!in_array($method,['cash','card','bank_transfer','other'],true)) $method='cash';
  if (!strtotime($date) || $date>date('Y-m-d')) return 'Payment date is not valid.';
  try {
    $pdo->beginTransaction();
    $q=$pdo->prepare('SELECT * FROM installments WHERE account_id=? ORDER BY due_date,no FOR UPDATE'); $q->execute([$acc['id']]); $ins=$q->fetchAll();
    $out=0; foreach($ins as $i) $out+=$i['amount']-$i['paid']; $out=round($out,2);
    if ($out<=0) { $pdo->rollBack(); return 'This account is fully paid.'; }
    if ($amount>$out+0.004) { $pdo->rollBack(); return 'Amount is more than the outstanding balance ('.number_format($out,2).').'; }
    $prefix='RC-'.$branchCode.'-'.date('ym');
    $q=$pdo->prepare('SELECT COALESCE(MAX(seq_no),0)+1 FROM payments WHERE receipt_prefix=? FOR UPDATE'); $q->execute([$prefix]); $seq=(int)$q->fetchColumn();
    $rno=$prefix.'-'.str_pad($seq,4,'0',STR_PAD_LEFT);
    $pdo->prepare('INSERT INTO payments (receipt_prefix,seq_no,receipt_no,student_id,batch_id,account_id,branch_id,amount,method,reference,paid_on,note,received_by) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)')
        ->execute([$prefix,$seq,$rno,$acc['student_id'],$acc['batch_id'],$acc['id'],$branchId,$amount,$method,$ref?:null,$date,$note?:null,$by]);
    $pid=(int)$pdo->lastInsertId(); $left=$amount;
    $up=$pdo->prepare('UPDATE installments SET paid=paid+? WHERE id=?'); $al=$pdo->prepare('INSERT INTO payment_allocations (payment_id,installment_id,amount) VALUES (?,?,?)');
    foreach ($ins as $i) {
      if ($left<=0.004) break; $room=round($i['amount']-$i['paid'],2); if ($room<=0) continue;
      $take=min($room,$left); $up->execute([$take,$i['id']]); $al->execute([$pid,$i['id'],$take]); $left=round($left-$take,2);
    }
    $pdo->commit(); return $pid;
  } catch (PDOException $x) { if($pdo->inTransaction()) $pdo->rollBack(); return 'Could not save the payment. Please try again.'; }
}
/** One-time scholarship: reduces the unpaid installments, starting from the last one. */
function apply_scholarship(int $accId,float $amt): ?string {
  $pdo=db(); $amt=round($amt,2); if ($amt<=0) return 'Enter a scholarship amount greater than zero.';
  try {
    $pdo->beginTransaction();
    $a=$pdo->prepare('SELECT scholarship FROM fee_accounts WHERE id=? FOR UPDATE'); $a->execute([$accId]); $cur=(float)$a->fetchColumn();
    if ($cur>0) { $pdo->rollBack(); return 'A scholarship is already applied to this account.'; }
    $q=$pdo->prepare('SELECT * FROM installments WHERE account_id=? ORDER BY due_date DESC,no DESC FOR UPDATE'); $q->execute([$accId]); $ins=$q->fetchAll();
    $room=0; foreach($ins as $i) $room+=$i['amount']-$i['paid'];
    if ($amt>round($room,2)+0.004) { $pdo->rollBack(); return 'Scholarship is larger than the unpaid balance.'; }
    $left=$amt; $up=$pdo->prepare('UPDATE installments SET amount=amount-? WHERE id=?');
    foreach ($ins as $i) { if($left<=0.004) break; $cut=min(round($i['amount']-$i['paid'],2),$left); if($cut>0){ $up->execute([$cut,$i['id']]); $left=round($left-$cut,2);} }
    $pdo->prepare('UPDATE fee_accounts SET scholarship=? WHERE id=?')->execute([$amt,$accId]);
    $pdo->commit(); return null;
  } catch (PDOException $x) { if($pdo->inTransaction()) $pdo->rollBack(); return 'Could not apply the scholarship.'; }
}

function ensure_account_for(int $sid,int $bid): void {
  $q=db()->prepare('SELECT id FROM enrolments WHERE student_id=? AND batch_id=?'); $q->execute([$sid,$bid]); $e=$q->fetchColumn(); if($e) ensure_account((int)$e);
}
/** Backfill: create fee accounts for enrolments that have none (so overdue figures are complete). */
function ensure_all_accounts(?int $branch,int $limit=300): void {
  $sql='SELECT e.id FROM enrolments e JOIN students s ON s.id=e.student_id LEFT JOIN fee_accounts a ON a.enrolment_id=e.id WHERE a.id IS NULL'.($branch!==null?' AND s.branch_id='.(int)$branch:'').' LIMIT '.(int)$limit;
  foreach (db()->query($sql)->fetchAll(PDO::FETCH_COLUMN) as $id) ensure_account((int)$id);
}

/** Cancels a whole payment: puts the amounts back on the installments and keeps the receipt on record marked VOID. Returns an error string or null. */
function void_payment(int $id,string $reason,int $by): ?string {
  if (mb_strlen($reason)<5) return 'Please give a reason (at least 5 characters).';
  $pdo=db();
  try {
    $pdo->beginTransaction();
    $q=$pdo->prepare('SELECT id,voided_at FROM payments WHERE id=? FOR UPDATE'); $q->execute([$id]); $p=$q->fetch();
    if (!$p) { $pdo->rollBack(); return 'Payment not found.'; }
    if ($p['voided_at']) { $pdo->rollBack(); return 'This payment is already cancelled.'; }
    $a=$pdo->prepare('SELECT installment_id,amount FROM payment_allocations WHERE payment_id=?'); $a->execute([$id]);
    $u=$pdo->prepare('UPDATE installments SET paid=GREATEST(paid-?,0) WHERE id=?');
    foreach ($a->fetchAll() as $r) $u->execute([$r['amount'],$r['installment_id']]);
    $pdo->prepare('UPDATE payments SET voided_at=NOW(),voided_by=?,void_reason=? WHERE id=?')->execute([$by,mb_substr($reason,0,255),$id]);
    $pdo->commit(); return null;
  } catch (PDOException $x) { if($pdo->inTransaction()) $pdo->rollBack(); return 'Could not cancel the payment. Please try again.'; }
}
