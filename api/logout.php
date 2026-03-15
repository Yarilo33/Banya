<?php
declare(strict_types=1);
require_once 'config.php';

// Для JWT logout выполняется на стороне клиента, поэтому пока просто подтверждаем выход

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    http_response_code(405);
    echo json_encode(['error' => 'Метод не поддерживается']);
    exit;
}

echo json_encode([
    'success' => true,
    'message' => 'Выход выполнен успешно'
]);