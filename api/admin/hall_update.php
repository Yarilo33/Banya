<?php
declare(strict_types=1);
require_once '../config.php';

if (!in_array($_SERVER['REQUEST_METHOD'], ['PUT', 'POST'])) {
    http_response_code(405);
    echo json_encode(['error' => 'Метод не поддерживается']);
    exit;
}

// Админчик
if (!isset($_SESSION['user_id']) || $_SESSION['role'] !== 'admin') {
    http_response_code(403);
    echo json_encode(['error' => 'Доступ запрещен']);
    exit;
}

// Получаем ID зала
$hallId = (int)($_GET['id'] ?? 0);
if ($hallId <= 0) {
    http_response_code(400);
    echo json_encode(['error' => 'Не указан ID зала']);
    exit;
}

// Получаем данные
if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    // multipart/form-data
    $name = trim($_POST['name'] ?? '');
    $description = trim($_POST['description'] ?? '');
    $price_hourly = $_POST['price_hourly'] !== '' ? (float)$_POST['price_hourly'] : null;
    $capacity = $_POST['capacity'] !== '' ? (int)$_POST['capacity'] : null;
    $is_active = isset($_POST['is_active']) ? filter_var($_POST['is_active'], FILTER_VALIDATE_BOOLEAN) : null;
    $bath_types = $_POST['bath_types'] ?? '[]';
    $bath_types = json_decode($bath_types, true) ?: [];
    $photos_to_delete = $_POST['photos_to_delete'] ?? '[]';
    $photos_to_delete = json_decode($photos_to_delete, true) ?: [];
} else {
    //JSON
    $input = json_decode(file_get_contents('php://input'), true);
    $name = trim($input['name'] ?? '');
    $description = trim($input['description'] ?? '');
    $price_hourly = $input['price_hourly'] ?? null;
    $capacity = $input['capacity'] ?? null;
    $is_active = $input['is_active'] ?? null;
    $bath_types = $input['bath_types'] ?? null;
    $photos_to_delete = $input['photos_to_delete'] ?? [];
}

try {
    $pdo->beginTransaction();

    // Проверяем существование зала
    $stmt = $pdo->prepare("SELECT hall_id FROM bath_halls WHERE hall_id = ?");
    $stmt->execute([$hallId]);
    if (!$stmt->fetch()) {
        http_response_code(404);
        echo json_encode(['error' => 'Зал не найден']);
        exit;
    }

    // Обнова
    $updates = [];
    $params = [];

    if (!empty($name)) {
        $updates[] = "name = ?";
        $params[] = $name;
    }
    if ($description !== '') {
        $updates[] = "description = ?";
        $params[] = $description;
    }
    if ($price_hourly !== null) {
        $updates[] = "price_hourly = ?";
        $params[] = $price_hourly;
    }
    if ($capacity !== null) {
        $updates[] = "capacity = ?";
        $params[] = $capacity;
    }
    if ($is_active !== null) {
        $updates[] = "is_active = ?";
        $params[] = $is_active ? 1 : 0;
    }

    // Обновляем основные данные
    if (!empty($updates)) {
        $sql = "UPDATE bath_halls SET " . implode(', ', $updates) . " WHERE hall_id = ?";
        $params[] = $hallId;
        $stmt = $pdo->prepare($sql);
        $stmt->execute($params);
    }

    // Обновляем типы бань (если переданы)
    if ($bath_types !== null) {
        if (empty($bath_types) || !is_array($bath_types)) {
            http_response_code(400);
            echo json_encode(['error' => 'Выберите хотя бы один тип бани']);
            exit;
        }

        // Удаляем старые связи
        $stmt = $pdo->prepare("DELETE FROM hall_bath_types WHERE hall_id = ?");
        $stmt->execute([$hallId]);

        // Добавляем новые
        $stmt = $pdo->prepare("INSERT INTO hall_bath_types (hall_id, type_id) VALUES (?, ?)");
        foreach ($bath_types as $typeId) {
            $stmt->execute([$hallId, (int)$typeId]);
        }
    }

    // Удаляем указанные фото
    if (!empty($photos_to_delete)) {
        $placeholders = implode(',', array_fill(0, count($photos_to_delete), '?'));
        $stmt = $pdo->prepare("SELECT photo_id, photo_url FROM hall_photos WHERE photo_id IN ($placeholders) AND hall_id = ?");
        $stmt->execute(array_merge($photos_to_delete, [$hallId]));
        $photos = $stmt->fetchAll();

        foreach ($photos as $photo) {
            // Удаляем файл
            $filePath = '..' . $photo['photo_url'];
            if (file_exists($filePath)) {
                unlink($filePath);
            }
            // Удаляем из БД
            $stmt = $pdo->prepare("DELETE FROM hall_photos WHERE photo_id = ?");
            $stmt->execute([$photo['photo_id']]);
        }
    }

    // Добавляем новые фото (если есть потому что пока могут и не быть)
    $newPhotos = [];
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
                    $newPhotos[] = $photoUrl;
                    
                    $stmt = $pdo->prepare("INSERT INTO hall_photos (hall_id, photo_url) VALUES (?, ?)");
                    $stmt->execute([$hallId, $photoUrl]);
                }
            }
        }
    }

    $pdo->commit();

    // Получаем обновленные данные
    $stmt = $pdo->prepare("
        SELECT h.*, 
               GROUP_CONCAT(DISTINCT bt.type_id) as type_ids, 
               GROUP_CONCAT(DISTINCT bt.display_name) as type_names,
               GROUP_CONCAT(DISTINCT hp.photo_id, ':', hp.photo_url SEPARATOR '|') as photos
        FROM bath_halls h
        LEFT JOIN hall_bath_types hbt ON h.hall_id = hbt.hall_id
        LEFT JOIN bath_types bt ON hbt.type_id = bt.type_id
        LEFT JOIN hall_photos hp ON h.hall_id = hp.hall_id
        WHERE h.hall_id = ?
        GROUP BY h.hall_id
    ");
    $stmt->execute([$hallId]);
    $hall = $stmt->fetch();

    // Парсим фото
    $photosList = [];
    if ($hall['photos']) {
        foreach (explode('|', $hall['photos']) as $photoStr) {
            list($id, $url) = explode(':', $photoStr, 2);
            $photosList[] = ['id' => (int)$id, 'url' => $url];
        }
    }

    echo json_encode([
        'success' => true,
        'message' => 'Зал успешно обновлен',
        'hall' => [
            'id' => $hallId,
            'name' => $hall['name'],
            'description' => $hall['description'],
            'price_hourly' => (float)$hall['price_hourly'],
            'capacity' => (int)$hall['capacity'],
            'is_active' => (bool)$hall['is_active'],
            'bath_types' => $hall['type_ids'] ? array_map('intval', explode(',', $hall['type_ids'])) : [],
            'bath_type_names' => $hall['type_names'] ? explode(',', $hall['type_names']) : [],
            'photos' => $photosList,
            'new_photos' => $newPhotos
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