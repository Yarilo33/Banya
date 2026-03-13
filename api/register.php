<?php
declare(strict_types=1);
require_once 'config.php';

// Только POST
if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    http_response_code(405);
    echo json_encode(['error' => 'Метод не поддерживается']);
    exit;
}

// Получаем данные
$input = json_decode(file_get_contents('php://input'), true);
$phone = trim($input['phone'] ?? '');
$password = $input['password'] ?? '';
$role = $input['role'] ?? 'user'; // По умолчанию user, admin только явно

// Валидация
$errors = [];

if (empty($phone)) {
    $errors[] = 'Номер телефона обязателен';
} elseif (!preg_match('/^\+7\d{10}$/', $phone)) {
    $errors[] = 'Неверный формат телефона. Используйте +7XXXXXXXXXX';
}

if (empty($password)) {
    $errors[] = 'Пароль обязателен';
} elseif (strlen($password) < 4) {
    $errors[] = 'Пароль должен быть не менее 4 символов';
}

// Роль только user или admin
if (!in_array($role, ['user', 'admin'], true)) {
    $role = 'user';
}

if (!empty($errors)) {
    http_response_code(400);
    echo json_encode(['error' => 'Ошибка валидации', 'details' => $errors]);
    exit;
}

try {
    // Проверяем, есть ли такой телефон
    $stmt = $pdo->prepare("SELECT user_id FROM users WHERE phone = ?");
    $stmt->execute([$phone]);
    
    if ($stmt->fetch()) {
        http_response_code(409);
        echo json_encode(['error' => 'Пользователь с таким номером уже существует']);
        exit;
    }

    // Добавляем пользователя (пароль храним как есть, без хеша)
    $stmt = $pdo->prepare("
        INSERT INTO users (phone, password_hash, role) 
        VALUES (?, ?, ?)
    ");
    $stmt->execute([$phone, $password, $role]);
    
    $userId = (int)$pdo->lastInsertId();

    // Автоматически входим после регистрации
    $_SESSION['user_id'] = $userId;
    $_SESSION['phone'] = $phone;
    $_SESSION['role'] = $role;

    echo json_encode([
        'success' => true,
        'message' => 'Регистрация успешна',
        'user' => [
            'id' => $userId,
            'phone' => $phone,
            'role' => $role
        ]
    ]);

} catch (PDOException $e) {
    http_response_code(500);
    echo json_encode(['error' => 'Ошибка базы данных']);
}