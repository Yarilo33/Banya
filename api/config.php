<?php
declare(strict_types=1);

header('Content-Type: application/json; charset=utf-8');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: POST, GET, PUT, DELETE, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type, Authorization');

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(200);
    exit;
}

$host = 'localhost';
$dbname = 'banya_booking';
$username = 'root';      // замените на вашего пользователя(пока не меняем, чтобы были все права)
$password = '';          // замените на ваш пароль

try {
    $pdo = new PDO(
        "mysql:host=$host;dbname=$dbname;charset=utf8mb4",
        $username,
        $password,
        [
            PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
            PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
            PDO::ATTR_EMULATE_PREPARES => false,
        ]
    );
} catch (PDOException $e) {
    http_response_code(500);
    echo json_encode(['error' => 'Ошибка подключения к базе данных']);
    exit;
}

// JWT Configuration
define('JWT_SECRET', 'your-256-bit-secret-key-here-change-in-production');
define('JWT_EXPIRE_HOURS', 24); // Время жизни токена в часах

//Генерация JWT токена
function generateJWT(array $payload): string {
    $header = json_encode(['typ' => 'JWT', 'alg' => 'HS256']);
    $time = time();

    $payload['iat'] = $time;
    $payload['exp'] = $time + (JWT_EXPIRE_HOURS * 3600);

    $payloadJson = json_encode($payload);

    $base64Header = str_replace(['+', '/', '='], ['-', '_', ''], base64_encode($header));
    $base64Payload = str_replace(['+', '/', '='], ['-', '_', ''], base64_encode($payloadJson));

    $signature = hash_hmac('sha256', "$base64Header.$base64Payload", JWT_SECRET, true);
    $base64Signature = str_replace(['+', '/', '='], ['-', '_', ''], base64_encode($signature));

    return "$base64Header.$base64Payload.$base64Signature";
}

//Валидация и декодирование JWT токена
function validateJWT(string $token): ?array {
    $parts = explode('.', $token);
    if (count($parts) !== 3) {
        return null;
    }

    [$base64Header, $base64Payload, $base64Signature] = $parts;

    // Проверяем подпись
    $signature = hash_hmac('sha256', "$base64Header.$base64Payload", JWT_SECRET, true);
    $expectedSignature = str_replace(['+', '/', '='], ['-', '_', ''], base64_encode($signature));

    if (!hash_equals($base64Signature, $expectedSignature)) {
        return null;
    }

    // Декодируем payload
    $payloadJson = base64_decode(str_replace(['-', '_'], ['+', '/'], $base64Payload));
    $payload = json_decode($payloadJson, true);

    if (!$payload || !isset($payload['exp'])) {
        return null;
    }

    // Проверяем срок действия
    if ($payload['exp'] < time()) {
        return null;
    }

    return $payload;
}

//Получение текущего пользователя из токена
function getCurrentUser(): ?array {
    $headers = getallheaders();
    $authHeader = $headers['Authorization'] ?? '';

    if (!preg_match('/Bearer\s+(\S+)/', $authHeader, $matches)) {
        return null;
    }

    $token = $matches[1];
    $payload = validateJWT($token);

    if (!$payload || !isset($payload['user_id'])) {
        return null;
    }

    return $payload;
}

//Проверка авторизации (для защищённых endpoint)
function requireAuth(): array {
    $user = getCurrentUser();
    if (!$user) {
        http_response_code(401);
        echo json_encode(['error' => 'Требуется авторизация', 'code' => 'TOKEN_INVALID']);
        exit;
    }
    return $user;
}

//Проверка прав администратора
function requireAdmin(): array {
    $user = requireAuth();
    if ($user['role'] !== 'admin') {
        http_response_code(403);
        echo json_encode(['error' => 'Доступ запрещен', 'code' => 'FORBIDDEN']);
        exit;
    }
    return $user;
}