package com.example.saunapril;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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

    private LinearLayout hallsContainer;

    // Базовый URL для API и изображений
    private static final String BASE_URL = "http://10.51.185.164/api";
    private static final String API_URL = BASE_URL + "/admin/hall_list.php";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_halls);

        initMenu();

        hallsContainer = findViewById(R.id.hallsContainer);

        loadHalls();
    }

    private void loadHalls() {
        new Thread(() -> {
            try {
                String token = getSharedPreferences("auth_prefs", MODE_PRIVATE)
                        .getString("jwt_token", "");

                if (token.isEmpty()) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Требуется авторизация", Toast.LENGTH_SHORT).show();
                        finish();
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
                BufferedReader reader = (responseCode >= 200 && responseCode < 300) ?
                        new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8")) :
                        new BufferedReader(new InputStreamReader(conn.getErrorStream(), "UTF-8"));

                StringBuilder result = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) result.append(line);
                reader.close();

                String response = result.toString();
                int finalCode = responseCode;

                runOnUiThread(() -> {
                    if (finalCode != 200) {
                        Toast.makeText(this, "Ошибка: " + finalCode, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    try {
                        parseAndDisplay(response);
                    } catch (Exception e) {
                        Toast.makeText(this, "Ошибка: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        e.printStackTrace();
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(this, "Ошибка сети: " + e.getMessage(), Toast.LENGTH_LONG).show());
                e.printStackTrace();
            }
        }).start();
    }

    private void parseAndDisplay(String response) throws Exception {
        JSONObject json = new JSONObject(response);
        if (!json.getBoolean("success")) {
            showError(json.optString("message", "Ошибка"));
            return;
        }

        JSONArray halls = json.getJSONArray("halls");
        if (halls.length() == 0) {
            showError("Залы не найдены");
            return;
        }

        for (int i = 0; i < halls.length(); i++) {
            createHallCard(halls.getJSONObject(i));
        }
    }

    private void showError(String msg) {
        TextView tv = new TextView(this);
        tv.setText("Ошибка: " + msg);
        tv.setPadding(16, 16, 16, 16);
        hallsContainer.addView(tv);
    }

    private void createHallCard(JSONObject hall) throws Exception {
        // Основная карточка
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(0, 0, 0, 24);
        card.setBackgroundResource(android.R.drawable.dialog_holo_light_frame);

        // Название зала
        TextView tvName = new TextView(this);
        tvName.setText(hall.getString("name"));
        tvName.setTextSize(18);
        tvName.setTypeface(null, android.graphics.Typeface.BOLD);
        tvName.setPadding(16, 16, 16, 8);
        card.addView(tvName);

        // Описание
        String desc = hall.optString("description", "");
        if (!desc.isEmpty()) {
            TextView tvDesc = new TextView(this);
            tvDesc.setText(desc);
            tvDesc.setTextSize(14);
            tvDesc.setPadding(16, 0, 16, 8);
            card.addView(tvDesc);
        }

        // Цена и вместимость
        TextView tvInfo = new TextView(this);
        tvInfo.setText("Цена: " + hall.getInt("price_hourly") + " руб./час | Вместимость: " +
                hall.getInt("capacity") + " чел.");
        tvInfo.setTextSize(14);
        tvInfo.setPadding(16, 0, 16, 16);
        card.addView(tvInfo);

        // Контейнер для фотографий (горизонтальный)
        LinearLayout photosRow = new LinearLayout(this);
        photosRow.setOrientation(LinearLayout.HORIZONTAL);
        photosRow.setPadding(16, 0, 16, 16);

        // Получаем массив фотографий
        JSONArray photos = hall.optJSONArray("photos");
        if (photos != null && photos.length() > 0) {
            for (int j = 0; j < photos.length(); j++) {
                JSONObject photo = photos.getJSONObject(j);
                String photoUrl = photo.getString("url");

                // Создаем ImageView
                ImageView iv = new ImageView(this);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(150, 150);
                params.setMargins(0, 0, 8, 0);
                iv.setLayoutParams(params);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                iv.setBackgroundResource(android.R.drawable.picture_frame);

                photosRow.addView(iv);

                // Загружаем изображение
                loadImage(BASE_URL + photoUrl, iv);
            }
        } else {
            TextView noPhoto = new TextView(this);
            noPhoto.setText("Нет фотографий");
            noPhoto.setPadding(16, 0, 16, 16);
            card.addView(noPhoto);
        }

        card.addView(photosRow);
        hallsContainer.addView(card);
    }

    private void loadImage(String url, ImageView imageView) {
        new Thread(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setDoInput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.connect();

                int responseCode = conn.getResponseCode();

                if (responseCode == 200) {
                    Bitmap bitmap = BitmapFactory.decodeStream(conn.getInputStream());

                    if (bitmap != null) {
                        runOnUiThread(() -> imageView.setImageBitmap(bitmap));
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}