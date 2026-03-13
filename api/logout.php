<?php
declare(strict_types=1);
require_once 'config.php';

// Очищаем сессию
$_SESSION = [];
session_destroy();

echo json_encode([
    'success' => true,
    'message' => 'Выход выполнен успешно'
]);