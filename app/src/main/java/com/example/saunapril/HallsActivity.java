package com.example.saunapril;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class HallsActivity extends BaseActivity {

    private TextView tvResult;
    private ProgressBar progressBar;

    private static final String API_URL = "http://10.51.185.164/api/admin/hall_list";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_halls);

        // Инициализация меню
        initMenu();

        tvResult = findViewById(R.id.tv_result);
        progressBar = findViewById(R.id.progress_bar);

        loadHalls();
    }


    private void loadHalls() {
        progressBar.setVisibility(View.VISIBLE);
        tvResult.setText("");

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String token = getSharedPreferences("auth_prefs", MODE_PRIVATE)
                            .getString("jwt_token", "");

                    if (token.isEmpty()) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                progressBar.setVisibility(View.GONE);
                                Toast.makeText(HallsActivity.this,
                                        "Требуется авторизация", Toast.LENGTH_SHORT).show();
                                finish();
                            }
                        });
                        return;
                    }

                    URL url = new URL(API_URL);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("Authorization", "Bearer " + token);
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(10000);

                    int responseCode = conn.getResponseCode();

                    BufferedReader reader;
                    if (responseCode >= 200 && responseCode < 300) {
                        reader = new BufferedReader(
                                new InputStreamReader(conn.getInputStream(), "UTF-8"));
                    } else {
                        reader = new BufferedReader(
                                new InputStreamReader(conn.getErrorStream(), "UTF-8"));
                    }

                    StringBuilder result = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        result.append(line);
                    }
                    reader.close();

                    final String response = result.toString();
                    final int finalResponseCode = responseCode;

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            progressBar.setVisibility(View.GONE);

                            if (finalResponseCode != 200) {
                                Toast.makeText(HallsActivity.this,
                                        "Ошибка сервера: " + finalResponseCode,
                                        Toast.LENGTH_SHORT).show();
                                tvResult.setText("Не удалось загрузить данные");
                                return;
                            }

                            try {
                                parseAndDisplay(response);
                            } catch (Exception e) {
                                Toast.makeText(HallsActivity.this,
                                        "Ошибка обработки данных", Toast.LENGTH_SHORT).show();
                                tvResult.setText("Ошибка: " + e.getMessage());
                            }
                        }
                    });

                } catch (final Exception e) {
                    e.printStackTrace();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(HallsActivity.this,
                                    "Ошибка сети: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            tvResult.setText("Ошибка подключения");
                        }
                    });
                }
            }
        }).start();
    }

    /**
     * парс jsonчика отображение залов
     */
    private void parseAndDisplay(String response) throws Exception {
        JSONObject json = new JSONObject(response);
        boolean success = json.getBoolean("success");

        if (!success) {
            String message = json.optString("message", "Неизвестная ошибка");
            tvResult.setText("Ошибка: " + message);
            return;
        }

        JSONArray halls = json.getJSONArray("halls");
        int count = halls.length();

        if (count == 0) {
            tvResult.setText("Залы не найдены");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Найдено залов: ").append(count).append("\n\n");

        for (int i = 0; i < count; i++) {
            JSONObject hall = halls.getJSONObject(i);

            String name = hall.getString("name");
            String description = hall.optString("description", "");
            int price = hall.getInt("price_hourly");
            int capacity = hall.getInt("capacity");
            boolean isActive = hall.getBoolean("is_active");
            int bookings = hall.getInt("active_bookings_count");

            sb.append("Зал: ").append(name).append("\n");
            sb.append("Описание: ").append(description).append("\n");
            sb.append("Цена: ").append(price).append(" руб./час\n");
            sb.append("Вместимость: ").append(capacity).append(" чел.\n");
            sb.append("Статус: ").append(isActive ? "Активен" : "Не активен").append("\n");
            sb.append("Активных бронирований: ").append(bookings).append("\n");
            sb.append("────────────────────────────\n\n");
        }

        tvResult.setText(sb.toString());
    }
}