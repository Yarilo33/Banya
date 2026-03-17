<?php
declare(strict_types=1);
require_once '../config.php';

if ($_SERVER['REQUEST_METHOD'] !== 'DELETE') {
    http_response_code(405);
    echo json_encode(['error' => 'Метод не поддерживается']);
    exit;
}

//JWT
$currentUser = requireAdmin();

// Получаем ID бронирования
$bookingId = (int)($_GET['id'] ?? 0);
if ($bookingId <= 0) {
    http_response_code(400);
    echo json_encode(['error' => 'Не указан ID бронирования']);
    exit;
}

try {
    // Проверяем существование бронирования и получаем детали
    $stmt = $pdo->prepare("
        SELECT 
            b.booking_id,
            b.booking_date,
            b.start_time,
            b.status,
            h.name as hall_name,
            u.phone as user_phone
        FROM bookings b
        LEFT JOIN bath_halls h ON b.hall_id = h.hall_id
        LEFT JOIN users u ON b.user_id = u.user_id
        WHERE b.booking_id = ?
    ");
    $stmt->execute([$bookingId]);
    $booking = $stmt->fetch();

    if (!$booking) {
        http_response_code(404);
        echo json_encode(['error' => 'Бронирование не найдено']);
        exit;
    }

    // Проверяем, не прошло ли уже время бронирования
    $bookingDateTime = $booking['booking_date'] . ' ' . $booking['start_time'];
    if ($bookingDateTime < date('Y-m-d H:i:s')) {
        http_response_code(409);
        echo json_encode([
            'error' => 'Нельзя удалить прошедшее бронирование',
            'booking_date' => $booking['booking_date'],
            'start_time' => substr($booking['start_time'], 0, 5)
        ]);
        exit;
    }

    // Удаляем бронирование
    $stmt = $pdo->prepare("DELETE FROM bookings WHERE booking_id = ?");
    $stmt->execute([$bookingId]);
    
    echo json_encode([
        'success' => true,
        'message' => 'Бронирование успешно удалено',
        'deleted_booking' => [
            'id' => $bookingId,
            'hall_name' => $booking['hall_name'],
            'date' => $booking['booking_date'],
            'start_time' => substr($booking['start_time'], 0, 5),
            'user_phone' => $booking['user_phone'],
            'previous_status' => $booking['status']
        ]
    ]);

} catch (PDOException $e) {
    http_response_code(500);
    echo json_encode(['error' => 'Ошибка базы данных: ' . $e->getMessage()]);
}