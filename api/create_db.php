<?php
/**
 * Скрипт создания базы данных банного комплекса
 * Для WAMP Server (MySQL)
 */

// Настройки подключения к MySQL (стандартные для WAMP)
$host = 'localhost';
$username = 'root';      // стандартный логин для WAMP
$password = '';          // стандартный пароль (пустой) для WAMP
$database = 'banya_booking';

// Создаем подключение без выбора базы данных
$conn = new mysqli($host, $username, $password);

// Проверка подключения
if ($conn->connect_error) {
    die("Ошибка подключения к MySQL: " . $conn->connect_error);
}

echo "Подключение к MySQL успешно<br>";

// Устанавливаем кодировку
$conn->set_charset("utf8mb4");

// ============================================
// 1. Создание базы данных
// ============================================
$sql = "CREATE DATABASE IF NOT EXISTS $database 
        CHARACTER SET utf8mb4 
        COLLATE utf8mb4_unicode_ci";

if ($conn->query($sql) === TRUE) {
    echo "База данных '$database' создана или уже существует<br>";
} else {
    die("Ошибка создания базы данных: " . $conn->error);
}

// Выбираем базу данных
$conn->select_db($database);
echo "База данных '$database' выбрана<br>";

// ============================================
// 2. Создание таблиц
// ============================================

// 2.1 Справочник типов бань
$sql = "CREATE TABLE IF NOT EXISTS bath_types (
    type_id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(50) NOT NULL UNIQUE,
    display_name VARCHAR(100) NOT NULL,
    icon_url VARCHAR(255)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";

if ($conn->query($sql) === TRUE) {
    echo "Таблица 'bath_types' создана<br>";
} else {
    die("Ошибка создания таблицы bath_types: " . $conn->error);
}

// 2.2 Пользователи системы
$sql = "CREATE TABLE IF NOT EXISTS users (
    user_id INT PRIMARY KEY AUTO_INCREMENT,
    phone VARCHAR(20) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role ENUM('user', 'admin') DEFAULT 'user',
    INDEX idx_phone (phone),
    INDEX idx_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";

if ($conn->query($sql) === TRUE) {
    echo "Таблица 'users' создана<br>";
} else {
    die("Ошибка создания таблицы users: " . $conn->error);
}

// 2.3 Залы банного комплекса
$sql = "CREATE TABLE IF NOT EXISTS bath_halls (
    hall_id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    price_hourly DECIMAL(10, 2) NOT NULL,
    capacity INT DEFAULT 4,
    is_active BOOLEAN DEFAULT TRUE,
    INDEX idx_active (is_active),
    INDEX idx_price (price_hourly)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";

if ($conn->query($sql) === TRUE) {
    echo "Таблица 'bath_halls' создана<br>";
} else {
    die("Ошибка создания таблицы bath_halls: " . $conn->error);
}

// 2.4 Связь залов с типами бань
$sql = "CREATE TABLE IF NOT EXISTS hall_bath_types (
    hall_type_id INT PRIMARY KEY AUTO_INCREMENT,
    hall_id INT NOT NULL,
    type_id INT NOT NULL,
    FOREIGN KEY (hall_id) REFERENCES bath_halls(hall_id) ON DELETE CASCADE,
    FOREIGN KEY (type_id) REFERENCES bath_types(type_id) ON DELETE CASCADE,
    UNIQUE KEY unique_hall_type (hall_id, type_id),
    INDEX idx_hall (hall_id),
    INDEX idx_type (type_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";

if ($conn->query($sql) === TRUE) {
    echo "Таблица 'hall_bath_types' создана<br>";
} else {
    die("Ошибка создания таблицы hall_bath_types: " . $conn->error);
}

// 2.5 Фотографии залов
$sql = "CREATE TABLE IF NOT EXISTS hall_photos (
    photo_id INT PRIMARY KEY AUTO_INCREMENT,
    hall_id INT NOT NULL,
    photo_url VARCHAR(500) NOT NULL,
    FOREIGN KEY (hall_id) REFERENCES bath_halls(hall_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";

if ($conn->query($sql) === TRUE) {
    echo "Таблица 'hall_photos' создана<br>";
} else {
    die("Ошибка создания таблицы hall_photos: " . $conn->error);
}

// 2.6 Бронирования
$sql = "CREATE TABLE IF NOT EXISTS bookings (
    booking_id INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT,
    hall_id INT NOT NULL,
    booking_date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    total_price DECIMAL(10, 2) NOT NULL,
    status ENUM('pending', 'confirmed', 'cancelled', 'completed') DEFAULT 'confirmed',
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE SET NULL,
    FOREIGN KEY (hall_id) REFERENCES bath_halls(hall_id) ON DELETE RESTRICT,
    INDEX idx_booking_time (hall_id, booking_date, start_time, end_time),
    INDEX idx_user_bookings (user_id, booking_date),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";

if ($conn->query($sql) === TRUE) {
    echo "Таблица 'bookings' создана<br>";
} else {
    die("Ошибка создания таблицы bookings: " . $conn->error);
}

// 2.7 Расписание/шаблоны доступности
$sql = "CREATE TABLE IF NOT EXISTS hall_schedule_templates (
    template_id INT PRIMARY KEY AUTO_INCREMENT,
    hall_id INT NOT NULL,
    day_of_week TINYINT NOT NULL CHECK (day_of_week BETWEEN 0 AND 6),
    open_time TIME NOT NULL DEFAULT '07:00:00',
    close_time TIME NOT NULL DEFAULT '23:00:00',
    is_working BOOLEAN DEFAULT TRUE,
    FOREIGN KEY (hall_id) REFERENCES bath_halls(hall_id) ON DELETE CASCADE,
    UNIQUE KEY unique_hall_day (hall_id, day_of_week)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";

if ($conn->query($sql) === TRUE) {
    echo "Таблица 'hall_schedule_templates' создана<br>";
} else {
    die("Ошибка создания таблицы hall_schedule_templates: " . $conn->error);
}

// ============================================
// 3. Заполнение справочников данными
// ============================================

// Проверяем, есть ли уже данные в таблице типов бань
$result = $conn->query("SELECT COUNT(*) as count FROM bath_types");
$row = $result->fetch_assoc();

if ($row['count'] == 0) {
    $sql = "INSERT INTO bath_types (type_id, name, display_name) VALUES
            (1, 'hammam', 'Хамам'),
            (2, 'russian', 'Русская'),
            (3, 'siberian', 'Сибирская'),
            (4, 'turkish', 'Турецкая')";
    
    if ($conn->query($sql) === TRUE) {
        echo "Справочник типов бань заполнен<br>";
    } else {
        echo "Ошибка заполнения справочника: " . $conn->error . "<br>";
    }
} else {
    echo "Справочник типов бань уже содержит данные<br>";
}

// ============================================
// 4. Создание триггера для проверки пересечения бронирований
// ============================================

// Проверяем существование триггера
$result = $conn->query("SHOW TRIGGERS LIKE 'check_booking_overlap'");
if ($result->num_rows == 0) {
    // Удаляем старый триггер если есть (для обновления)
    $conn->query("DROP TRIGGER IF EXISTS check_booking_overlap");
    
    $sql = "CREATE TRIGGER check_booking_overlap 
            BEFORE INSERT ON bookings
            FOR EACH ROW
            BEGIN
                DECLARE overlap_count INT;
                
                SELECT COUNT(*) INTO overlap_count
                FROM bookings
                WHERE hall_id = NEW.hall_id 
                  AND booking_date = NEW.booking_date
                  AND status NOT IN ('cancelled')
                  AND (
                      (NEW.start_time < end_time AND NEW.end_time > start_time)
                  );
                
                IF overlap_count > 0 THEN
                    SIGNAL SQLSTATE '45000'
                    SET MESSAGE_TEXT = 'Временной слот уже забронирован';
                END IF;
            END";
    
    if ($conn->query($sql) === TRUE) {
        echo "Триггер 'check_booking_overlap' создан<br>";
    } else {
        echo "Ошибка создания триггера: " . $conn->error . "<br>";
    }
} else {
    echo "Триггер 'check_booking_overlap' уже существует<br>";
}

// ============================================
// 5. Завершение
// ============================================

echo "<br><strong>База данных успешно создана и настроена!</strong><br>";
echo "<a href='#' onclick='history.back()'>Назад</a>";

// Закрываем соединение
$conn->close();
?>