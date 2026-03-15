<?php
declare(strict_types=1);
require_once '../config.php';

if ($_SERVER['REQUEST_METHOD'] !== 'GET') {
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

// Получаем параметры фильтрации
$typeIds = $_GET['types'] ?? '';
$search = trim($_GET['search'] ?? '');
$minPrice = $_GET['min_price'] ?? null;
$maxPrice = $_GET['max_price'] ?? null;
$minCapacity = $_GET['min_capacity'] ?? null;

try {
    $params = [];
    $whereConditions = ['h.is_active = 1']; // Только активные залы

    // Фильтр по типам бань
    $typeFilterJoin = '';
    if (!empty($typeIds)) {
        $typeArray = array_filter(array_map('intval', explode(',', $typeIds)));
        if (!empty($typeArray)) {
            $placeholders = implode(',', array_fill(0, count($typeArray), '?'));
            $typeFilterJoin = " INNER JOIN hall_bath_types hbt_filter ON h.hall_id = hbt_filter.hall_id 
                               AND hbt_filter.type_id IN ($placeholders)";
            $params = array_merge($params, $typeArray);
        }
    }

    // Поиск по названию/описанию
    if (!empty($search)) {
        $whereConditions[] = "(h.name LIKE ? OR h.description LIKE ?)";
        $searchParam = "%$search%";
        $params[] = $searchParam;
        $params[] = $searchParam;
    }

    $whereClause = implode(' AND ', $whereConditions);

    // Основной запрос(фото для зала в списке выбирается самое первое из добавленных)
    $sql = "
        SELECT 
            h.hall_id,
            h.name,
            h.description,
            h.price_hourly,
            h.capacity,
            GROUP_CONCAT(DISTINCT bt.type_id) as type_ids,
            GROUP_CONCAT(DISTINCT bt.display_name ORDER BY bt.type_id SEPARATOR ', ') as type_names,
            GROUP_CONCAT(DISTINCT bt.name) as type_codes,
            (
                SELECT photo_url 
                FROM hall_photos 
                WHERE hall_id = h.hall_id 
                ORDER BY photo_id ASC 
                LIMIT 1
            ) as main_photo
        FROM bath_halls h
        $typeFilterJoin
        LEFT JOIN hall_bath_types hbt ON h.hall_id = hbt.hall_id
        LEFT JOIN bath_types bt ON hbt.type_id = bt.type_id
        WHERE $whereClause
        GROUP BY h.hall_id
        ORDER BY h.price_hourly ASC
    ";

    $stmt = $pdo->prepare($sql);
    $stmt->execute($params);
    $halls = $stmt->fetchAll();

    $result = [];
    foreach ($halls as $hall) {
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

        $result[] = [
            'id' => (int)$hall['hall_id'],
            'name' => $hall['name'],
            'description' => $hall['description'],
            'price_hourly' => (float)$hall['price_hourly'],
            'capacity' => (int)$hall['capacity'],
            'types' => $types,
            'main_photo' => $hall['main_photo'] ?? null
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