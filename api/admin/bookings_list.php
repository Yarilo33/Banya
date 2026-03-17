<?php
declare(strict_types=1);
require_once '../config.php';

if ($_SERVER['REQUEST_METHOD'] !== 'GET') {
    http_response_code(405);
    echo json_encode(['error' => 'Метод не поддерживается']);
    exit;
}

//JWT
$currentUser = requireAdmin();

//Пагинация
$page = max(1, (int)($_GET['page'] ?? 1));
$perPage = min(50, max(1, (int)($_GET['per_page'] ?? 20)));
$offset = ($page - 1) * $perPage;

try {
    // Получаем общее количество только актуальных броней
    $countSql = "
        SELECT COUNT(*) as total 
        FROM bookings b
        WHERE b.booking_date >= CURDATE()
          AND b.status IN ('pending', 'confirmed')
    ";
    $stmt = $pdo->query($countSql);
    $totalCount = (int)$stmt->fetch()['total'];
    $totalPages = (int)ceil($totalCount / $perPage);

    //Сортировка по дате бронирования
    $sql = "
        SELECT 
            b.booking_id,
            b.booking_date,
            b.start_time,
            b.end_time,
            b.total_price,
            b.status,
            h.hall_id,
            h.name as hall_name,
            h.price_hourly as hall_price,
            u.user_id,
            u.phone as user_phone,
            u.role as user_role,
            TIMESTAMPDIFF(MINUTE, b.start_time, b.end_time) / 60 as duration_hours
        FROM bookings b
        LEFT JOIN bath_halls h ON b.hall_id = h.hall_id
        LEFT JOIN users u ON b.user_id = u.user_id
        WHERE b.booking_date >= CURDATE()
          AND b.status IN ('pending', 'confirmed')
        ORDER BY b.booking_date DESC, b.start_time DESC
        LIMIT ? OFFSET ?
    ";
    
    $stmt = $pdo->prepare($sql);
    $stmt->execute([$perPage, $offset]);
    $bookings = $stmt->fetchAll();

    $result = [];
    foreach ($bookings as $booking) {
        $result[] = [
            'id' => (int)$booking['booking_id'],
            'date' => $booking['booking_date'],
            'start_time' => substr($booking['start_time'], 0, 5),
            'end_time' => substr($booking['end_time'], 0, 5),
            'duration_hours' => round((float)$booking['duration_hours'], 1),
            'total_price' => (float)$booking['total_price'],
            'status' => $booking['status'],
            'hall' => [
                'id' => (int)$booking['hall_id'],
                'name' => $booking['hall_name'],
                'price_hourly' => (float)$booking['hall_price']
            ],
            'user' => $booking['user_id'] ? [
                'id' => (int)$booking['user_id'],
                'phone' => $booking['user_phone'],
                'role' => $booking['user_role']
            ] : null
        ];
    }

    echo json_encode([
        'success' => true,
        'pagination' => [
            'current_page' => $page,
            'per_page' => $perPage,
            'total_pages' => $totalPages,
            'total_count' => $totalCount
        ],
        'bookings' => $result
    ]);

} catch (PDOException $e) {
    http_response_code(500);
    echo json_encode(['error' => 'Ошибка базы данных: ' . $e->getMessage()]);
}