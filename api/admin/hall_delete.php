<?php
declare(strict_types=1);
require_once '../config.php';


if ($_SERVER['REQUEST_METHOD'] !== 'DELETE') {
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

    // Проверяем, есть ли активные бронирования
    $stmt = $pdo->prepare("
        SELECT COUNT(*) as count 
        FROM bookings 
        WHERE hall_id = ? AND status IN ('pending', 'confirmed') AND booking_date >= CURDATE()
    ");
    $stmt->execute([$hallId]);
    $result = $stmt->fetch();

    if ($result['count'] > 0) {
        http_response_code(409);
        echo json_encode([
            'error' => 'Невозможно удалить зал с активными бронированиями',
            'active_bookings' => (int)$result['count']
        ]);
        exit;
    }

    // Получаем список фото для удаления файлов
    $stmt = $pdo->prepare("SELECT photo_url FROM hall_photos WHERE hall_id = ?");
    $stmt->execute([$hallId]);
    $photos = $stmt->fetchAll();

    // Удаляем файлы фото
    foreach ($photos as $photo) {
        $filePath = '..' . $photo['photo_url'];
        if (file_exists($filePath)) {
            unlink($filePath);
        }
    }

    // Удаляем папку зала, если она существует
    $hallDir = '../uploads/halls/' . $hallId . '/';
    if (is_dir($hallDir)) {
        rmdir($hallDir); // удаляет только пустую папку
    }

    // Удаляем зал (каскадно удалятся связи в hall_bath_types, hall_photos, hall_schedule_templates)
    $stmt = $pdo->prepare("DELETE FROM bath_halls WHERE hall_id = ?");
    $stmt->execute([$hallId]);

    $pdo->commit();

    echo json_encode([
        'success' => true,
        'message' => 'Зал успешно удален',
        'deleted_hall_id' => $hallId
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