<?php
declare(strict_types=1);
require_once '../config.php';

// Только GET
if ($_SERVER['REQUEST_METHOD'] !== 'GET') {
    http_response_code(405);
    echo json_encode(['error' => 'Метод не поддерживается']);
    exit;
}

// Проверка авторизации админа через JWT
$currentUser = requireAdmin();

try {
    // Получаем все залы с полной информацией
    $stmt = $pdo->query("
        SELECT 
            h.hall_id,
            h.name,
            h.description,
            h.price_hourly,
            h.capacity,
            h.is_active,
            h.created_at,
            GROUP_CONCAT(DISTINCT bt.type_id) as type_ids,
            GROUP_CONCAT(DISTINCT bt.display_name ORDER BY bt.type_id SEPARATOR ', ') as type_names,
            GROUP_CONCAT(DISTINCT hp.photo_id, ':', hp.photo_url SEPARATOR '|') as photos,
            (
                SELECT COUNT(*) 
                FROM bookings b 
                WHERE b.hall_id = h.hall_id 
                AND b.status IN ('pending', 'confirmed') 
                AND b.booking_date >= CURDATE()
            ) as active_bookings_count
        FROM bath_halls h
        LEFT JOIN hall_bath_types hbt ON h.hall_id = hbt.hall_id
        LEFT JOIN bath_types bt ON hbt.type_id = bt.type_id
        LEFT JOIN hall_photos hp ON h.hall_id = hp.hall_id
        GROUP BY h.hall_id
        ORDER BY h.created_at DESC
    ");
    
    $halls = $stmt->fetchAll();

    $result = [];
    foreach ($halls as $hall) {
        // Парсим фото
        $photosList = [];
        if ($hall['photos']) {
            foreach (explode('|', $hall['photos']) as $photoStr) {
                list($id, $url) = explode(':', $photoStr, 2);
                $photosList[] = ['id' => (int)$id, 'url' => $url];
            }
        }

        $result[] = [
            'id' => (int)$hall['hall_id'],
            'name' => $hall['name'],
            'description' => $hall['description'],
            'price_hourly' => (float)$hall['price_hourly'],
            'capacity' => (int)$hall['capacity'],
            'is_active' => (bool)$hall['is_active'],
            'created_at' => $hall['created_at'],
            'bath_types' => $hall['type_ids'] ? array_map('intval', explode(',', $hall['type_ids'])) : [],
            'bath_type_names' => $hall['type_names'] ? explode(', ', $hall['type_names']) : [],
            'photos' => $photosList,
            'active_bookings_count' => (int)$hall['active_bookings_count']
        ];
    }

    echo json_encode([
        'success' => true,
        'count' => count($result),
        'halls' => $result
    ]);

} catch (PDOException $e) {
    http_response_code(500);
    echo json_encode(['error' => 'Ошибка базы данных: ' . $e->getMessage()]);
}