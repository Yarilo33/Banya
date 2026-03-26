<?php
declare(strict_types=1);
require_once '../config.php';

$user = requireAuth();

$data = json_decode(file_get_contents('php://input'), true);
$playerId = $data['player_id'] ?? null;

if (!$playerId) {
    http_response_code(400);
    echo json_encode(['error' => 'Player ID не указан']);
    exit;
}

$stmt = $pdo->prepare("UPDATE users SET onesignal_player_id = ? WHERE user_id = ?");
$stmt->execute([$playerId, $user['user_id']]);

echo json_encode(['success' => true]);