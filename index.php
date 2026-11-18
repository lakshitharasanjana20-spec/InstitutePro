<?php
require_once __DIR__.'/includes/bootstrap.php';
header('Location: '.((($_SESSION['user']['role']??'')==='student')?'my_home.php':'dashboard.php'));
