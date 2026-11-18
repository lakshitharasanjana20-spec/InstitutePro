<?php
/** Returns the name of a class that overlaps for this teacher (same dates, same time, shared class day), or null. */
function teacher_clash(int $tid,string $start,string $end,string $st,string $et,string $days,?int $exclude): ?string {
  $q=db()->prepare("SELECT id,name,batch_year,class_days FROM batches WHERE teacher_id=? AND status='active' AND NOT (end_date<? OR start_date>?) AND start_time<? AND end_time>?".($exclude?' AND id<>'.(int)$exclude:''));
  $q->execute([$tid,$start,$end,$et,$st]);
  $mine=$days===''?[]:explode(',',$days);
  foreach ($q->fetchAll() as $o) {
    $theirs=($o['class_days']===''||$o['class_days']===null)?[]:explode(',',$o['class_days']);
    if (!$mine||!$theirs||array_intersect($mine,$theirs)) return $o['name'].' ('.$o['batch_year'].')';
  }
  return null;
}

/** Why a teacher cannot take a class at this date/time ('on leave', 'has a class', 'covering another class'), or null if free. */
function teacher_busy(int $tid,string $date,string $st,string $et,?int $exceptBatch=null): ?string {
  $q=db()->prepare("SELECT 1 FROM leave_requests WHERE user_id=? AND status='approved' AND from_date<=? AND to_date>=?"); $q->execute([$tid,$date,$date]); if($q->fetch()) return 'on leave';
  $wd=date('D',strtotime($date)); $ex=$exceptBatch?' AND b.id<>'.(int)$exceptBatch:'';
  $q=db()->prepare("SELECT b.class_days FROM batches b WHERE b.teacher_id=? AND b.status='active' AND b.start_date<=? AND b.end_date>=? AND b.start_time<? AND b.end_time>? AND NOT EXISTS (SELECT 1 FROM substitutions sb WHERE sb.batch_id=b.id AND sb.class_date=?)$ex");
  $q->execute([$tid,$date,$date,$et,$st,$date]);
  foreach ($q->fetchAll() as $r) if ($r['class_days']===''||$r['class_days']===null||in_array($wd,explode(',',$r['class_days']),true)) return 'has a class';
  $q=db()->prepare("SELECT 1 FROM substitutions sb JOIN batches b ON b.id=sb.batch_id WHERE sb.substitute_id=? AND sb.class_date=? AND b.start_time<? AND b.end_time>?$ex"); $q->execute([$tid,$date,$et,$st]);
  return $q->fetch() ? 'covering another class' : null;
}
