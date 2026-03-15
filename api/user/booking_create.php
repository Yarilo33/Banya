<?php
declare(strict_types=1);
require_once '../config.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    http_response_code(405);
    echo json_encode(['error' => 'Метод не поддерживается']);
    exit;
}

// Проверка авторизации
if (!isset($_SESSION['user_id'])) {
    http_response_code(401);
    echo json_encode(['error' => 'Требуется авторизация']);
    exit;
}

// Получаем данные
$input = json_decode(file_get_contents('php://input'), true);
$hallId = (int)($input['hall_id'] ?? 0);
$date = trim($input['date'] ?? '');
$startTime = trim($input['start_time'] ?? '');
$endTime = trim($input['end_time'] ?? '');

// Валидация
$errors = [];

if ($hallId <= 0) {
    $errors[] = 'Не указан зал';
}

if (empty($date) || !preg_match('/^\d{4}-\d{2}-\d{2}$/', $date)) {
    $errors[] = 'Неверный формат даты';
}

if (empty($startTime) || !preg_match('/^\d{2}:\d{2}$/', $startTime)) {
    $errors[] = 'Неверный формат времени начала';
}

if (empty($endTime) || !preg_match('/^\d{2}:\d{2}$/', $endTime)) {
    $errors[] = 'Неверный формат времени окончания';
}

if (!empty($errors)) {
    http_response_code(400);
    echo json_encode(['error' => 'Ошибка валидации', 'details' => $errors]);
    exit;
}

// Проверяем, что дата не в прошлом
if ($date < date('Y-m-d')) {
    http_response_code(400);
    echo json_encode(['error' => 'Нельзя бронировать на прошедшую дату']);
    exit;
}

// Проверяем, что время окончания позже времени начала
if (strtotime($endTime) <= strtotime($startTime)) {
    http_response_code(400);
    echo json_encode(['error' => 'Время окончания должно быть позже времени начала']);
    exit;
}

try {
    // Проверяем существование и активность зала
    $stmt = $pdo->prepare("
        SELECT hall_id, price_hourly, capacity, name 
        FROM bath_halls 
        WHERE hall_id = ? AND is_active = 1
    ");
    $stmt->execute([$hallId]);
    $hall = $stmt->fetch();

    if (!$hall) {
        http_response_code(404);
        echo json_encode(['error' => 'Зал не найден или неактивен']);
        exit;
    }

    // Проверяем расписание зала на этот день
    $dayOfWeek = (int)date('w', strtotime($date));
    $stmt = $pdo->prepare("
        SELECT open_time, close_time, is_working 
        FROM hall_schedule_templates 
        WHERE hall_id = ? AND day_of_week = ?
    ");
    $stmt->execute([$hallId, $dayOfWeek]);
    $schedule = $stmt->fetch();

    if (!$schedule || !$schedule['is_working']) {
        http_response_code(400);
        echo json_encode(['error' => 'Зал не работает в выбранный день']);
        exit;
    }

    // Проверяем, что время в рамках работы зала
    if ($startTime < substr($schedule['open_time'], 0, 5) || 
        $endTime > substr($schedule['close_time'], 0, 5)) {
        http_response_code(400);
        echo json_encode([
            'error' => 'Выбранное время вне рабочих часов зала',
            'working_hours' => substr($schedule['open_time'], 0, 5) . ' - ' . substr($schedule['close_time'], 0, 5)
        ]);
        exit;
    }

    // Проверяем пересечение с существующими бронированиями
    $stmt = $pdo->prepare("
        SELECT COUNT(*) as count 
        FROM bookings 
        WHERE hall_id = ? 
          AND booking_date = ? 
          AND status IN ('pending', 'confirmed')
          AND (
              (CAST(? AS TIME) < end_time AND CAST(? AS TIME) > start_time)
          )
    ");
    $stmt->execute([
        $hallId, 
        $date, 
        $startTime . ':00',
        $endTime . ':00'
    ]);
    $overlap = $stmt->fetch();

    if ($overlap['count'] > 0) {
        http_response_code(409);
        echo json_encode(['error' => 'Выбранное время уже забронировано']);
        exit;
    }

    // Рассчитываем стоимость
    $hours = (strtotime($endTime) - strtotime($startTime)) / 3600;
    $totalPrice = $hall['price_hourly'] * $hours;

    // Создаем бронирование
    $stmt = $pdo->prepare("
        INSERT INTO bookings (
            user_id, 
            hall_id, 
            booking_date, 
            start_time, 
            end_time, 
            total_price, 
            status
        ) VALUES (?, ?, ?, ?, ?, ?, 'confirmed')
    ");
    
    $stmt->execute([
        $_SESSION['user_id'],
        $hallId,
        $date,
        $startTime . ':00',
        $endTime . ':00',
        $totalPrice
    ]);

    $bookingId = (int)$pdo->lastInsertId();

    echo json_encode([
        'success' => true,
        'message' => 'Бронирование успешно создано',
        'booking' => [
            'id' => $bookingId,
            'hall_name' => $hall['name'],
            'date' => $date,
            'start_time' => $startTime,
            'end_time' => $endTime,
            'hours' => $hours,
            'total_price' => (float)$totalPrice,
            'status' => 'confirmed'
        ]
    ]);

} catch (PDOException $e) {
    // Проверяем, это ошибка триггера о пересечении
    if (strpos($e->getMessage(), 'Временной слот уже забронирован') !== false) {
        http_response_code(409);
        echo json_encode(['error' => 'Выбранное время уже забронировано']);
    } else {
        http_response_code(500);
        echo json_encode(['error' => 'Ошибка базы данных: ' . $e->getMessage()]);
    }
}