package com.example.saunapril;

public final class Config {
    private Config() {} // Запрет создания экземпляра

    // API Endpoints
    public static final String API_BASE = "http://10.51.185.164/api";
    public static final String API_LOGIN = API_BASE + "/login.php";
    public static final String API_USER_HALLS = API_BASE + "/user/halls.php";
    public static final String API_HALL_DETAIL = API_BASE + "/user/halls_detail.php?id=";
    public static final String API_BOOKING_CREATE = API_BASE + "/user/booking_create.php";
    public static final String API_ADMIN_HALL_LIST = API_BASE + "/admin/hall_list.php";
    public static final String API_ADMIN_HALL_CREATE = API_BASE + "/admin/hall_create.php";
    public static final String API_ADMIN_HALL_UPDATE = API_BASE + "/admin/hall_update.php?id=";
    public static final String API_ADMIN_HALL_DELETE = API_BASE + "/admin/hall_delete.php?id=";

    // SharedPreferences
    public static final String PREF_NAME = "auth_prefs";
    public static final String PREF_KEY_TOKEN = "jwt_token";
    public static final String PREF_KEY_USER = "user_data";
    public static final String PREF_KEY_ROLE = "user_role";

    // Типы бань (ID)
    public static final int[] BATH_TYPE_IDS = {1, 2, 3, 4};

    // Тайм-слоты
    public static final String[] TIME_SLOTS = {
            "10:00", "11:00", "12:00", "13:00", "14:00", "15:00",
            "16:00", "17:00", "18:00", "19:00", "20:00", "21:00"
    };


    public static final int CONNECT_TIMEOUT = 10000;
    public static final int READ_TIMEOUT = 10000;
    public static final int UPLOAD_TIMEOUT = 15000;

    // Request codes
    public static final int REQUEST_IMAGE_PICK = 1001;
}