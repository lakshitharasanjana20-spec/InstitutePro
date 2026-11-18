<?php
require_menu($pageKey); refresh_batches();
$u = $_SESSION['user']; $menu = allowed_menu($u['role']);
$initial = strtoupper(mb_substr($u['full_name'],0,1));
?><!doctype html><html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<script src="assets/js/view.js"></script>
<title><?=e(MENU[$pageKey][0])?> · <?=APP_NAME?></title>
<link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css" rel="stylesheet">
<link href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.3/font/bootstrap-icons.css" rel="stylesheet">
<link href="assets/css/oneui.css" rel="stylesheet"></head>
<body>
<div class="app" id="app">
  <aside class="sidebar" id="sidebar">
    <div class="brand"><i class="bi bi-mortarboard-fill"></i><span><?=APP_NAME?></span></div>
    <nav>
    <?php foreach ($menu as $k): [$label,$icon,$href]=MENU[$k]; ?>
      <a href="<?=$href?>" class="<?=$k===$pageKey?'active':''?>" title="<?=e($label)?>"><i class="bi <?=$icon?>"></i><span><?=e($label)?></span></a>
    <?php endforeach; ?>
    </nav>
  </aside>
  <div class="backdrop" id="backdrop"></div>
  <div class="main">
    <header class="topbar">
      <button class="icon-btn" id="toggle" aria-label="Menu"><i class="bi bi-list"></i></button>
      <h1><?=e(MENU[$pageKey][0])?></h1>
      <div class="ms-auto dropdown">
        <button class="avatar-btn" data-bs-toggle="dropdown"><span class="avatar"><?=e($initial)?></span><span class="d-none d-md-inline"><?=e($u['full_name'])?></span></button>
        <ul class="dropdown-menu dropdown-menu-end">
          <li><span class="dropdown-item-text small text-muted"><?=e(ucwords(str_replace('_',' ',$u['role'])))?></span></li>
          <li><a class="dropdown-item" href="<?=$u['role']==='student'?'my_profile.php':'profile.php'?>">My Profile</a></li>
          <li><hr class="dropdown-divider"></li>
          <li><a class="dropdown-item" href="#" onclick="setView('desktop');return false"><i class="bi bi-display"></i> Desktop view</a></li>
          <li><a class="dropdown-item" href="#" onclick="setView('phone');return false"><i class="bi bi-phone"></i> Phone view</a></li>
          <li><hr class="dropdown-divider"></li>
          <li><a class="dropdown-item" href="logout.php">Sign out</a></li>
        </ul>
      </div>
    </header>
    <main class="content">
