<?php
/** Grade bands (percentage minimums) come from Settings: defaults A 75, B 65, C 50, S 35, W below.
 *  Pass for an annual-intake final = C or better. */
function grade_for(float $pct): string {
  if ($pct>=(float)setting('grade_a',75)) return 'A';
  if ($pct>=(float)setting('grade_b',65)) return 'B';
  if ($pct>=(float)setting('grade_c',50)) return 'C';
  if ($pct>=(float)setting('grade_s',35)) return 'S';
  return 'W';
}
function is_pass(float $pct): bool { return $pct>=(float)setting('grade_c',50); }
function exam_outcome(string $type,string $courseTypeCode,float $pct): string {
  if ($type!=='final') return '';
  if ($courseTypeCode==='AI') return is_pass($pct) ? 'Course completed' : 'Repeat required';
  if ($courseTypeCode==='GR') return 'Eligible for next grade';
  return '';
}
