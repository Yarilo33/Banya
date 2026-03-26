<?php
declare(strict_types=1);

class NotificationService
{
    private string $appId;
    private string $restApiKey;
    private string $apiUrl;

    public function __construct()
    {
        $this->appId = '092843ce-4b4d-4ead-9220-62279708f1fa';
        $this->restApiKey = 'yb5m6hb26ukauns2ljf5qaz5o';
        $this->apiUrl = 'https://onesignal.com/api/v1/notifications';
    }

    public function sendBookingDeleted(string $playerId, string $hallName, string $date, string $time): bool
    {
        $payload = [
            'app_id' => $this->appId,
            'include_player_ids' => [$playerId],
            'headings' => ['ru' => 'Бронирование отменено'],
            'contents' => ['ru' => "Ваше бронирование зала '{$hallName}' на {$date} {$time} было отменено"],
            'data' => [
                'type' => 'booking_deleted',
                'hall_name' => $hallName,
                'date' => $date
            ]
        ];

        $ch = curl_init($this->apiUrl);
        curl_setopt($ch, CURLOPT_HTTPHEADER, [
            'Content-Type: application/json',
            'Authorization: Basic ' . $this->restApiKey
        ]);
        curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
        curl_setopt($ch, CURLOPT_POST, true);
        curl_setopt($ch, CURLOPT_POSTFIELDS, json_encode($payload));
        curl_setopt($ch, CURLOPT_SSL_VERIFYPEER, false);

        $response = curl_exec($ch);
        $httpCode = curl_getinfo($ch, CURLINFO_HTTP_CODE);
        curl_close($ch);

        return $httpCode === 200;
    }
}