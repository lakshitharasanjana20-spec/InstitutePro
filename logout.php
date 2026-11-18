<?php
require_once __DIR__.'/includes/bootstrap.php';
if (!empty($_SESSION['user'])) log_activity('logout','Signed out');
session_destroy(); header('Location: login.php');
