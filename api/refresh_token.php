<?php
declare(strict_types=1);
require_once 'config.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    http_response_code(405);
    echo json_encode(['error' => 'Метод не поддерживается']);
    exit;
}

$headers = getallheaders();
$authHeader = $headers['Authorization'] ?? '';

if (!preg_match('/Bearer\s+(\S+)/', $authHeader, $matches)) {
    http_response_code(401);
    echo json_encode(['error' => 'Токен не предоставлен']);
    exit;
}

$token = $matches[1];
$payload = validateJWT($token);

// Если токен валиден, но скоро истечёт (менее 1 часа), выдаём новый
if ($payload && isset($payload['exp']) && $payload['exp'] > time()) {
    // Продлеваем срок действия
    $newPayload = [
        'user_id' => $payload['user_id'],
        'phone' => $payload['phone'],
        'role' => $payload['role']
    ];
    
    $newToken = generateJWT($newPayload);
    
    echo json_encode([
        'success' => true,
        'token' => $newToken,
        'expires_in' => JWT_EXPIRE_HOURS * 3600
    ]);
} else {
    http_response_code(401);
    echo json_encode(['error' => 'Токен недействителен или истёк']);
}