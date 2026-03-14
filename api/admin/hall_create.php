<?php
declare(strict_types=1);
require_once '../config.php';

// Только POST
if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    http_response_code(405);
    echo json_encode(['error' => 'Метод не поддерживается']);
    exit;
}

// Проверка авторизации админа, пользователь не имеет доступ
if (!isset($_SESSION['user_id']) || $_SESSION['role'] !== 'admin') {
    http_response_code(403);
    echo json_encode(['error' => 'Доступ запрещен. Требуется авторизация администратора']);
    exit;
}

$contentType = $_SERVER['CONTENT_TYPE'] ?? '';

if (strpos($contentType, 'multipart/form-data') !== false) {
    // Форма с файлами
    $name = trim($_POST['name'] ?? '');
    $description = trim($_POST['description'] ?? '');
    $price_hourly = (float)($_POST['price_hourly'] ?? 0);
    $capacity = (int)($_POST['capacity'] ?? 4);
    $bath_types = $_POST['bath_types'] ?? '[]'; // JSON массив 
    $bath_types = json_decode($bath_types, true) ?: [];
} else {
    // JSON
    $input = json_decode(file_get_contents('php://input'), true);
    $name = trim($input['name'] ?? '');
    $description = trim($input['description'] ?? '');
    $price_hourly = (float)($input['price_hourly'] ?? 0);
    $capacity = (int)($input['capacity'] ?? 4);
    $bath_types = $input['bath_types'] ?? [];
}

// Валидация
$errors = [];

if (empty($name)) {
    $errors[] = 'Название зала обязательно';
} elseif (strlen($name) > 200) {
    $errors[] = 'Название не должно превышать 200 символов';
}

if ($price_hourly <= 0) {
    $errors[] = 'Цена за час должна быть больше 0';
}

if ($capacity < 1 || $capacity > 50) {
    $errors[] = 'Вместимость должна быть от 1 до 50 человек';
}

if (empty($bath_types) || !is_array($bath_types)) {
    $errors[] = 'Выберите хотя бы один тип бани';
}

// Проверяем, что типы бань существуют
if (!empty($bath_types)) {
    $placeholders = implode(',', array_fill(0, count($bath_types), '?'));
    $stmt = $pdo->prepare("SELECT type_id FROM bath_types WHERE type_id IN ($placeholders)");
    $stmt->execute($bath_types);
    $validTypes = $stmt->fetchAll(PDO::FETCH_COLUMN);
    
    if (count($validTypes) !== count($bath_types)) {
        $errors[] = 'Указаны несуществующие типы бань';
    }
}

if (!empty($errors)) {
    http_response_code(400);
    echo json_encode(['error' => 'Ошибка валидации', 'details' => $errors]);
    exit;
}

try {
    $pdo->beginTransaction();

    // Создание зала
    $stmt = $pdo->prepare("
        INSERT INTO bath_halls (name, description, price_hourly, capacity, is_active) 
        VALUES (?, ?, ?, ?, TRUE)
    ");
    $stmt->execute([$name, $description, $price_hourly, $capacity]);
    $hallId = (int)$pdo->lastInsertId();

    // Добавляем типы
    $stmt = $pdo->prepare("INSERT INTO hall_bath_types (hall_id, type_id) VALUES (?, ?)");
    foreach ($bath_types as $typeId) {
        $stmt->execute([$hallId, (int)$typeId]);
    }

    // Создаем шаблоны расписания (по умолчанию 07:00-23:00 каждый день, потом редактируем)
    $stmt = $pdo->prepare("
        INSERT INTO hall_schedule_templates (hall_id, day_of_week, open_time, close_time, is_working) 
        VALUES (?, ?, '07:00:00', '23:00:00', TRUE)
    ");
    for ($day = 0; $day <= 6; $day++) {
        $stmt->execute([$hallId, $day]);
    }

    // Обрабатываем загруженные фото (если есть потому что пока могут и не быть)
    $photos = [];
    if (!empty($_FILES['photos'])) {
        $uploadDir = '../uploads/halls/' . $hallId . '/';
        if (!is_dir($uploadDir)) {
            mkdir($uploadDir, 0755, true);
        }

        $files = $_FILES['photos'];
        $fileCount = is_array($files['name']) ? count($files['name']) : 1;

        for ($i = 0; $i < $fileCount; $i++) {
            $fileName = is_array($files['name']) ? $files['name'][$i] : $files['name'];
            $tmpName = is_array($files['tmp_name']) ? $files['tmp_name'][$i] : $files['tmp_name'];
            $error = is_array($files['error']) ? $files['error'][$i] : $files['error'];

            if ($error === UPLOAD_ERR_OK) {
                $ext = pathinfo($fileName, PATHINFO_EXTENSION);
                $newName = uniqid() . '.' . $ext;
                $destination = $uploadDir . $newName;

                if (move_uploaded_file($tmpName, $destination)) {
                    $photoUrl = '/uploads/halls/' . $hallId . '/' . $newName;
                    $photos[] = $photoUrl;
                    
                    // Сохраняем в БД
                    $stmt = $pdo->prepare("INSERT INTO hall_photos (hall_id, photo_url) VALUES (?, ?)");
                    $stmt->execute([$hallId, $photoUrl]);
                }
            }
        }
    }

    $pdo->commit();

    // Получаем полные данные созданного зала
    $stmt = $pdo->prepare("
        SELECT h.*, GROUP_CONCAT(bt.type_id) as type_ids, GROUP_CONCAT(bt.display_name) as type_names
        FROM bath_halls h
        LEFT JOIN hall_bath_types hbt ON h.hall_id = hbt.hall_id
        LEFT JOIN bath_types bt ON hbt.type_id = bt.type_id
        WHERE h.hall_id = ?
        GROUP BY h.hall_id
    ");
    $stmt->execute([$hallId]);
    $hall = $stmt->fetch();

    echo json_encode([
        'success' => true,
        'message' => 'Зал успешно создан',
        'hall' => [
            'id' => $hallId,
            'name' => $hall['name'],
            'description' => $hall['description'],
            'price_hourly' => (float)$hall['price_hourly'],
            'capacity' => (int)$hall['capacity'],
            'is_active' => (bool)$hall['is_active'],
            'bath_types' => $hall['type_ids'] ? array_map('intval', explode(',', $hall['type_ids'])) : [],
            'bath_type_names' => $hall['type_names'] ? explode(',', $hall['type_names']) : [],
            'photos' => $photos
        ]
    ]);

} catch (PDOException $e) {
    $pdo->rollBack();
    http_response_code(500);
    echo json_encode(['error' => 'Ошибка базы данных: ' . $e->getMessage()]);
} catch (Exception $e) {
    $pdo->rollBack();
    http_response_code(500);
    echo json_encode(['error' => 'Ошибка: ' . $e->getMessage()]);
}