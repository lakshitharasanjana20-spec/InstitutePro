<?php
require_once __DIR__.'/includes/bootstrap.php';
require_menu('staff'); require_once __DIR__.'/includes/users_admin.php';
users_handle('staff');
$pageKey='staff'; require __DIR__.'/includes/header.php';
users_render('staff');
require __DIR__.'/includes/footer.php';
