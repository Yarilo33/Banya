package com.example.saunapril;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.widget.AppCompatTextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class MainActivity extends BaseActivity {

    // константы из конфига
    private static final String API_URL = Config.API_USER_HALLS;
    private static final String BASE_PHOTO_URL = Config.API_BASE;
    private static final String PREF_NAME = Config.PREF_NAME;
    private static final String KEY_TOKEN = Config.PREF_KEY_TOKEN;

    private LinearLayout hallsContainer, filtersContainer;
    private EditText etSearch;

    // типы бань из конфига
    private static final int[] TYPE_IDS = Config.BATH_TYPE_IDS;
    private final boolean[] selectedTypes = new boolean[TYPE_IDS.length];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        initMenu();
        initViews();
        initFilters();
        loadHalls();
    }

    private void initViews() {
        hallsContainer = findViewById(R.id.hallsContainer);
        filtersContainer = findViewById(R.id.filtersContainer);
        etSearch = findViewById(R.id.etSearch);

        if (etSearch != null) {
            etSearch.setOnEditorActionListener((v, actionId, event) -> {
                applyFilters();
                return true;
            });
        }
    }

    private void initFilters() {
        if (filtersContainer == null) return;

        // Получаем названия типов бань из resources
        String[] typeNames = getResources().getStringArray(R.array.bath_type_names);

        for (int i = 0; i < typeNames.length; i++) {
            final int index = i;

            AppCompatTextView chip = new AppCompatTextView(this);
            chip.setText(typeNames[i]);
            chip.setTextSize(16);
            chip.setPadding(24, 12, 24, 12);
            chip.setBackgroundResource(R.drawable.chip_background);
            chip.setTextColor(0xFF333333);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, 0, 12, 0);
            chip.setLayoutParams(params);

            chip.setOnClickListener(v -> {
                selectedTypes[index] = !selectedTypes[index];
                chip.setBackgroundResource(
                        selectedTypes[index]
                                ? R.drawable.chip_selected
                                : R.drawable.chip_background
                );
                applyFilters();
            });

            filtersContainer.addView(chip);
        }
    }

    private void applyFilters() {
        String query = etSearch != null ? etSearch.getText().toString().trim() : "";
        loadHalls(query);
    }

    private void loadHalls() {
        loadHalls("");
    }

    private void loadHalls(final String searchQuery) {
        if (hallsContainer != null) hallsContainer.removeAllViews();

        new Thread(() -> {
            try {
                String token = getSharedPreferences(PREF_NAME, MODE_PRIVATE)
                        .getString(KEY_TOKEN, "");

                StringBuilder urlBuilder = new StringBuilder(API_URL);
                boolean firstParam = true;

                if (!searchQuery.isEmpty()) {
                    urlBuilder.append("?search=").append(URLEncoder.encode(searchQuery, "UTF-8"));
                    firstParam = false;
                }

                URL url = new URL(urlBuilder.toString());
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();

                conn.setRequestMethod("GET");
                conn.setConnectTimeout(Config.CONNECT_TIMEOUT);
                conn.setReadTimeout(Config.READ_TIMEOUT);

                if (!token.isEmpty()) {
                    conn.setRequestProperty("Authorization", "Bearer " + token);
                }

                int code = conn.getResponseCode();

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), "UTF-8")
                );

                StringBuilder result = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) result.append(line);
                reader.close();

                runOnUiThread(() -> {
                    if (code == 200) {
                        parseAndDisplay(result.toString());
                    } else {
                        showError(getString(R.string.common_error) + ": " + code);
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() ->
                        showError(getString(R.string.auth_msg_network_error) + ": " + e.getMessage())
                );
                Log.e("MainActivity", "Load error", e);
            }
        }).start();
    }

    private void parseAndDisplay(String response) {
        try {
            JSONObject json = new JSONObject(cleanJson(response));

            if (!json.optBoolean("success", false)) {
                showError(json.optString("message", getString(R.string.common_error)));
                return;
            }

            JSONArray halls = json.optJSONArray("halls");

            if (halls == null || halls.length() == 0) {
                showError(getString(R.string.main_msg_halls_not_found));
                return;
            }

            hallsContainer.removeAllViews();

            for (int i = 0; i < halls.length(); i++) {
                JSONObject hall = halls.getJSONObject(i);

                JSONArray types = hall.optJSONArray("types");

                if (types != null && matchesAllTypes(types)) {
                    createHallCard(hall);
                }
            }

        } catch (Exception e) {
            showError(getString(R.string.common_error) + ": " + e.getMessage());
        }
    }

    private boolean matchesAllTypes(JSONArray hallTypes) {
        for (int i = 0; i < selectedTypes.length; i++) {
            if (selectedTypes[i]) {
                int requiredId = TYPE_IDS[i];

                boolean found = false;

                for (int j = 0; j < hallTypes.length(); j++) {
                    JSONObject type = hallTypes.optJSONObject(j);
                    if (type != null && type.optInt("id") == requiredId) {
                        found = true;
                        break;
                    }
                }

                if (!found) return false;
            }
        }
        return true;
    }

    private void createHallCard(JSONObject hall) throws Exception {
        String name = hall.optString("name", getString(R.string.detail_label_no_name));
        int price = hall.optInt("price_hourly", 0);
        String photoUrl = hall.optString("main_photo", "");
        JSONArray types = hall.optJSONArray("types");

        View card = getLayoutInflater().inflate(R.layout.item_hall_main, hallsContainer, false);

        ImageView ivPhoto = card.findViewById(R.id.ivHallPhoto);
        TextView tvName = card.findViewById(R.id.tvName);
        TextView tvPrice = card.findViewById(R.id.tvPrice);
        LinearLayout typesContainer = card.findViewById(R.id.typesContainer);

        tvName.setText(name);
        tvPrice.setText(price + " " + getString(R.string.main_unit_price));

        // Чипсы
        typesContainer.removeAllViews();

        if (types != null) {
            for (int i = 0; i < types.length() && i < 3; i++) {
                JSONObject type = types.optJSONObject(i);
                if (type != null) {
                    AppCompatTextView chip = new AppCompatTextView(this);
                    chip.setText(type.optString("name", ""));
                    chip.setTextSize(12);
                    chip.setPadding(16, 8, 16, 8);
                    chip.setBackgroundResource(R.drawable.chip_background);
                    chip.setTextColor(0xFF333333);

                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    );
                    params.setMargins(0, 0, 8, 0);
                    chip.setLayoutParams(params);

                    typesContainer.addView(chip);
                }
            }
        }

        // Фото
        if (!photoUrl.isEmpty()) {
            String fullUrl = photoUrl.startsWith("http")
                    ? photoUrl
                    : BASE_PHOTO_URL + photoUrl;

            loadImage(fullUrl, ivPhoto);
        }

        card.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, HallDetailActivity.class);
            intent.putExtra("hall_id", hall.optInt("id", 0));
            startActivity(intent);
        });

        hallsContainer.addView(card);
    }

    private void showError(String msg) {
        TextView tv = new AppCompatTextView(this);
        tv.setText("⚠️ " + msg);
        tv.setPadding(16, 16, 16, 16);
        tv.setTextColor(0xFFD32F2F);
        hallsContainer.addView(tv);
    }

    private void loadImage(String url, ImageView imageView) {
        new Thread(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.connect();

                if (conn.getResponseCode() == 200) {
                    Bitmap bitmap = BitmapFactory.decodeStream(conn.getInputStream());
                    runOnUiThread(() -> imageView.setImageBitmap(bitmap));
                }
            } catch (Exception e) {
                Log.e("MainActivity", "Image error: " + url, e);
            }
        }).start();
    }

    private String cleanJson(String response) {
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        return (start >= 0 && end > start)
                ? response.substring(start, end + 1)
                : response;
    }
}