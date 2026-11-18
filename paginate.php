<?php
/* Shared helpers: validated filter inputs, server-side pagination, filter bar. */
function gstr(string $k,int $max=100): string { $v=$_GET[$k]??''; return is_string($v)?mb_substr(trim($v),0,$max):''; }
function gint(string $k): int { $v=$_GET[$k]??0; return (is_string($v)&&ctype_digit($v))?(int)$v:0; }
function gdate(string $k): string { $v=gstr($k,10); return (preg_match('/^\d{4}-\d{2}-\d{2}$/',$v)&&strtotime($v))?$v:''; }
function genum(string $k,array $ok): string { $v=gstr($k); return in_array($v,$ok,true)?$v:''; }
function pg_per(): int { $v=gint('pp'); return in_array($v,[10,25,50],true)?$v:10; }
/** Returns [current page, total pages, row offset]. Out-of-range or invalid page numbers are corrected. */
function pg_page(int $total,int $per,string $key='page'): array {
  $pages=max(1,(int)ceil($total/$per)); $p=gint($key); if($p<1)$p=1; if($p>$pages)$p=$pages; return [$p,$pages,($p-1)*$per];
}
function pg_url(array $over=[]): string {
  $q=$_GET; foreach($over as $k=>$v){ if($v===null||$v==='') unset($q[$k]); else $q[$k]=$v; }
  return basename($_SERVER['PHP_SELF']).($q?'?'.http_build_query($q):'');
}
function pg_render(int $total,int $per,string $key='page'): void {
  if ($total<=0) return;
  $pages=max(1,(int)ceil($total/$per)); $cur=min(max(1,gint($key)),$pages);
  echo '<div class="d-flex flex-wrap justify-content-between align-items-center gap-2 mt-3"><div class="lbl">Showing '.(($cur-1)*$per+1).'–'.min($cur*$per,$total).' of '.$total.'</div>';
  echo '<select class="form-select form-select-sm" style="width:auto" onchange="location=this.value" aria-label="Rows per page">';
  foreach([10,25,50] as $n) echo '<option value="'.e(pg_url(['pp'=>$n,$key=>1])).'"'.($n===$per?' selected':'').'>'.$n.' per page</option>';
  echo '</select>';
  if ($pages>1) {
    echo '<ul class="pagination pagination-sm mb-0 flex-wrap">';
    $li=function($label,$p,$dis=false,$act=false) use($key){ return '<li class="page-item'.($dis?' disabled':'').($act?' active':'').'"><a class="page-link" href="'.($dis?'#':e(pg_url([$key=>$p]))).'">'.$label.'</a></li>'; };
    echo $li('‹',$cur-1,$cur<=1);
    $a=max(1,$cur-2); $b=min($pages,$cur+2);
    if($a>1){ echo $li('1',1); if($a>2) echo $li('…',1,true); }
    for($i=$a;$i<=$b;$i++) echo $li((string)$i,$i,false,$i===$cur);
    if($b<$pages){ if($b<$pages-1) echo $li('…',1,true); echo $li((string)$pages,$pages); }
    echo $li('›',$cur+1,$cur>=$pages);
    echo '</ul>';
  }
  echo '</div>';
}
function f_open(array $keep=[]): void {
  echo '<form method="get" class="card-ui mb-3 row g-2 align-items-end">';
  foreach($keep as $k){ $v=gstr($k); if($v!=='') echo '<input type="hidden" name="'.e($k).'" value="'.e($v).'">'; }
}
function f_input(string $name,string $label,string $type='text',string $col='col-12 col-md-4'): void {
  echo '<div class="'.$col.'">'.($type==='date'?'<label class="lbl">'.e($label).'</label>':'').'<input class="form-control" type="'.$type.'" name="'.e($name).'" value="'.e(gstr($name)).'"'.($type==='date'?'':' placeholder="'.e($label).'"').'></div>';
}
function f_select(string $name,array $opts,string $ph,string $col='col-6 col-md-3'): void {
  $cur=gstr($name); echo '<div class="'.$col.'"><select class="form-select" name="'.e($name).'"><option value="">'.e($ph).'</option>';
  foreach($opts as $v=>$l) echo '<option value="'.e((string)$v).'"'.((string)$v===$cur?' selected':'').'>'.e((string)$l).'</option>';
  echo '</select></div>';
}
function f_close(array $keep=[]): void {
  $q=[]; foreach($keep as $k){ $v=gstr($k); if($v!=='') $q[$k]=$v; }
  echo '<div class="col-12 col-md-auto d-flex gap-2"><button class="btn-ui">Filter</button><a class="btn btn-light rounded-pill" href="'.e(basename($_SERVER['PHP_SELF']).($q?'?'.http_build_query($q):'')).'">Reset</a></div></form>';
}
