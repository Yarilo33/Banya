<?php
declare(strict_types=1);
require_once 'config.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    http_response_code(405);
    echo json_encode(['error' => 'Метод не поддерживается']);
    exit;
}

$input = json_decode(file_get_contents('php://input'), true);
$phone = trim($input['phone'] ?? '');
$password = $input['password'] ?? '';

// Валидация
if (empty($phone) || empty($password)) {
    http_response_code(400);
    echo json_encode(['error' => 'Телефон и пароль обязательны']);
    exit;
}

try {
    // Ищем пользователя по телефону
    $stmt = $pdo->prepare("
        SELECT user_id, phone, password_hash, role 
        FROM users 
        WHERE phone = ?
    ");
    $stmt->execute([$phone]);
    $user = $stmt->fetch();

    // Проверяем существование и пароль (сравнение без хеша)
    if (!$user || $user['password_hash'] !== $password) {
        http_response_code(401);
        echo json_encode(['error' => 'Неверный телефон или пароль']);
        exit;
    }

    // Генерируем JWT токен
    $tokenPayload = [
        'user_id' => (int)$user['user_id'],
        'phone' => $user['phone'],
        'role' => $user['role']
    ];

    $token = generateJWT($tokenPayload);


    echo json_encode([
        'success' => true,
        'message' => 'Вход выполнен успешно',
        'user' => [
            'id' => (int)$user['user_id'],
            'phone' => $user['phone'],
            'role' => $user['role']
        ]
    ]);

} catch (PDOException $e) {
    http_response_code(500);
    echo json_encode(['error' => 'Ошибка базы данных']);
}