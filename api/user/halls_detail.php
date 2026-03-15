<?php
declare(strict_types=1);
require_once '../config.php';

if ($_SERVER['REQUEST_METHOD'] !== 'GET') {
    http_response_code(405);
    echo json_encode(['error' => 'Метод не поддерживается']);
    exit;
}

// Проверка авторизации через JWT
$currentUser = requireAuth();

// Получаем ID зала
$hallId = (int)($_GET['id'] ?? 0);
if ($hallId <= 0) {
    http_response_code(400);
    echo json_encode(['error' => 'Не указан ID зала']);
    exit;
}

// Дата для проверки доступности (по умолчанию сегодня)
$date = $_GET['date'] ?? date('Y-m-d');
if (!preg_match('/^\d{4}-\d{2}-\d{2}$/', $date)) {
    http_response_code(400);
    echo json_encode(['error' => 'Неверный формат даты. Используйте YYYY-MM-DD']);
    exit;
}

// Проверяем, что дата не в прошлом
if ($date < date('Y-m-d')) {
    http_response_code(400);
    echo json_encode(['error' => 'Нельзя бронировать на прошедшую дату']);
    exit;
}

try {
    // Информация о зале
    $stmt = $pdo->prepare("
        SELECT 
            h.*,
            GROUP_CONCAT(DISTINCT bt.type_id) as type_ids,
            GROUP_CONCAT(DISTINCT bt.display_name ORDER BY bt.type_id SEPARATOR ', ') as type_names,
            GROUP_CONCAT(DISTINCT bt.name) as type_codes
        FROM bath_halls h
        LEFT JOIN hall_bath_types hbt ON h.hall_id = hbt.hall_id
        LEFT JOIN bath_types bt ON hbt.type_id = bt.type_id
        WHERE h.hall_id = ? AND h.is_active = 1
        GROUP BY h.hall_id
    ");
    $stmt->execute([$hallId]);
    $hall = $stmt->fetch();

    if (!$hall) {
        http_response_code(404);
        echo json_encode(['error' => 'Зал не найден или неактивен']);
        exit;
    }

    // Получаем фото зала
    $stmt = $pdo->prepare("
        SELECT photo_id, photo_url 
        FROM hall_photos 
        WHERE hall_id = ? 
        ORDER BY photo_id ASC
    ");
    $stmt->execute([$hallId]);
    $photos = $stmt->fetchAll();

    $photosList = [];
    foreach ($photos as $photo) {
        $photosList[] = [
            'id' => (int)$photo['photo_id'],
            'url' => $photo['photo_url']
        ];
    }

    // Парсим типы бань
    $types = [];
    if ($hall['type_ids']) {
        $typeIdsArray = explode(',', $hall['type_ids']);
        $typeNamesArray = explode(', ', $hall['type_names']);
        $typeCodesArray = explode(',', $hall['type_codes']);
        
        foreach ($typeIdsArray as $i => $id) {
            $types[] = [
                'id' => (int)$id,
                'name' => $typeNamesArray[$i] ?? '',
                'code' => $typeCodesArray[$i] ?? ''
            ];
        }
    }

    // Получаем расписание на выбранный день недели
    $dayOfWeek = (int)date('w', strtotime($date));
    $stmt = $pdo->prepare("
        SELECT open_time, close_time, is_working 
        FROM hall_schedule_templates 
        WHERE hall_id = ? AND day_of_week = ?
    ");
    $stmt->execute([$hallId, $dayOfWeek]);
    $schedule = $stmt->fetch();

    $timeSlots = [];
    $isWorking = true;
    $openTime = '07:00:00';
    $closeTime = '23:00:00';

    if ($schedule) {
        $isWorking = (bool)$schedule['is_working'];
        $openTime = $schedule['open_time'];
        $closeTime = $schedule['close_time'];
    }

    // Если зал работает в этот день, формируем слоты
    if ($isWorking) {
        // Получаем существующие бронирования на эту дату
        $stmt = $pdo->prepare("
            SELECT start_time, end_time 
            FROM bookings 
            WHERE hall_id = ? 
              AND booking_date = ? 
              AND status IN ('pending', 'confirmed')
        ");
        $stmt->execute([$hallId, $date]);
        $bookings = $stmt->fetchAll();

        // Формируем слоты по часу
        $start = strtotime($openTime);
        $end = strtotime($closeTime);
        $slotDuration = 3600;

        for ($time = $start; $time < $end; $time += $slotDuration) {
            $slotStart = date('H:i:s', $time);
            $slotEnd = date('H:i:s', $time + $slotDuration);
            
            // Проверяем, не занят ли слот
            $isAvailable = true;
            foreach ($bookings as $booking) {
                $bookStart = strtotime($booking['start_time']);
                $bookEnd = strtotime($booking['end_time']);
                $slotStartSec = strtotime($slotStart);
                $slotEndSec = strtotime($slotEnd);

                // Проверка пересечения
                if ($slotStartSec < $bookEnd && $slotEndSec > $bookStart) {
                    $isAvailable = false;
                    break;
                }
            }

            // Скрываем прошедшие слоты
            if ($date === date('Y-m-d')) {
                if ($time <= time()) {
                    $isAvailable = false;
                }
            }

            $timeSlots[] = [
                'start' => substr($slotStart, 0, 5),
                'end' => substr($slotEnd, 0, 5),
                'available' => $isAvailable
            ];
        }
    }

    echo json_encode([
        'success' => true,
        'hall' => [
            'id' => (int)$hall['hall_id'],
            'name' => $hall['name'],
            'description' => $hall['description'],
            'price_hourly' => (float)$hall['price_hourly'],
            'capacity' => (int)$hall['capacity'],
            'types' => $types,
            'photos' => $photosList
        ],
        'selected_date' => $date,
        'schedule' => [
            'is_working' => $isWorking,
            'open_time' => substr($openTime, 0, 5),
            'close_time' => substr($closeTime, 0, 5)
        ],
        'time_slots' => $timeSlots
    ]);

} catch (PDOException $e) {
    http_response_code(500);
    echo json_encode(['error' => 'Ошибка базы данных: ' . $e->getMessage()]);
}