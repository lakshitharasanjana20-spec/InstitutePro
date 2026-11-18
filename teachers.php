<?php
require_once __DIR__.'/includes/bootstrap.php';
require_menu('teachers'); require_once __DIR__.'/includes/users_admin.php';
users_handle('teachers');
$pageKey='teachers'; require __DIR__.'/includes/header.php';
users_render('teachers');
require __DIR__.'/includes/footer.php';
