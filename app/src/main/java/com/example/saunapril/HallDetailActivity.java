package com.example.saunapril;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class HallDetailActivity extends AppCompatActivity {

    private static final String BASE_URL = "http://10.51.185.164/api/user/halls_detail.php?id=";
    private static final String PHOTO_BASE_URL = "http://10.51.185.164/api";

    private TextView tvName, tvPrice, tvDescription, tvPhotoCounter;
    private LinearLayout typesContainer, dotsContainer;
    private ImageView ivCurrentPhoto;
    private Button btnBook;
    private ImageButton btnBack, btnPrev, btnNext;

    private int hallId;
    private List<String> photoUrls = new ArrayList<>();
    private int currentPhotoIndex = 0;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hall_detail);

        hallId = getIntent().getIntExtra("hall_id", 0);
        if (hallId == 0) {
            Toast.makeText(this, "Ошибка: ID зала не передан", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        loadHallDetails();
    }

    private void initViews() {
        tvName = findViewById(R.id.tvName);
        tvPrice = findViewById(R.id.tvPrice);
        tvDescription = findViewById(R.id.tvDescription);
        tvPhotoCounter = findViewById(R.id.tvPhotoCounter);
        typesContainer = findViewById(R.id.typesContainer);
        dotsContainer = findViewById(R.id.dotsContainer);
        ivCurrentPhoto = findViewById(R.id.ivCurrentPhoto);
        btnBook = findViewById(R.id.btnBook);
        btnBack = findViewById(R.id.btnBack);
        btnPrev = findViewById(R.id.btnPrev);
        btnNext = findViewById(R.id.btnNext);

        btnBack.setImageResource(android.R.drawable.ic_menu_revert);
        btnBack.setOnClickListener(v -> finish());

        btnBook.setOnClickListener(v -> {
            Toast.makeText(this, "Переход к бронированию...", Toast.LENGTH_SHORT).show();
        });

        // Кнопки слайдера
        btnPrev.setOnClickListener(v -> showPreviousPhoto());
        btnNext.setOnClickListener(v -> showNextPhoto());
    }

    private void showPreviousPhoto() {
        if (photoUrls.size() <= 1) return;
        currentPhotoIndex = (currentPhotoIndex - 1 + photoUrls.size()) % photoUrls.size();
        updatePhotoDisplay();
    }

    private void showNextPhoto() {
        if (photoUrls.size() <= 1) return;
        currentPhotoIndex = (currentPhotoIndex + 1) % photoUrls.size();
        updatePhotoDisplay();
    }

    private void updatePhotoDisplay() {
        if (photoUrls.isEmpty()) return;

        String url = photoUrls.get(currentPhotoIndex);
        if (url.isEmpty()) {
            ivCurrentPhoto.setImageResource(android.R.drawable.ic_menu_gallery);
            ivCurrentPhoto.setBackgroundColor(0xFFEEEEEE);
        } else {
            loadImage(url, ivCurrentPhoto);
        }

        tvPhotoCounter.setText((currentPhotoIndex + 1) + "/" + photoUrls.size());
        updateDots(currentPhotoIndex);
    }

    private void loadHallDetails() {
        new Thread(() -> {
            try {
                URL url = new URL(BASE_URL + hallId);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), "UTF-8")
                );
                StringBuilder result = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) result.append(line);
                reader.close();

                mainHandler.post(() -> parseAndDisplay(result.toString()));

            } catch (Exception e) {
                mainHandler.post(() ->
                        Toast.makeText(this, "Ошибка сети: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
            }
        }).start();
    }

    private void parseAndDisplay(String response) {
        try {
            JSONObject json = new JSONObject(cleanJson(response));
            if (!json.optBoolean("success", false)) {
                Toast.makeText(this, json.optString("message", "Ошибка"), Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            JSONObject hall = json.optJSONObject("hall");
            if (hall == null) return;

            tvName.setText(hall.optString("name", "Без названия"));
            tvPrice.setText(hall.optInt("price_hourly", 0) + " ₽/час");
            tvDescription.setText(hall.optString("description", "Описание отсутствует"));

            // Особенности
            JSONArray types = hall.optJSONArray("types");
            typesContainer.removeAllViews();
            if (types != null) {
                for (int i = 0; i < types.length(); i++) {
                    JSONObject type = types.optJSONObject(i);
                    if (type != null) {
                        typesContainer.addView(createChip(type.optString("name", "")));
                    }
                }
            }

            // Фотографии
            JSONArray photos = hall.optJSONArray("photos");
            photoUrls.clear();

            if (photos != null) {
                for (int i = 0; i < photos.length(); i++) {
                    JSONObject photo = photos.optJSONObject(i);
                    if (photo != null) {
                        String url = photo.optString("url", "");
                        if (!url.isEmpty()) {
                            String fullUrl = url.startsWith("http") ? url : PHOTO_BASE_URL + url;
                            photoUrls.add(fullUrl);
                        }
                    }
                }
            }

            if (photoUrls.isEmpty()) {
                photoUrls.add("");
            }

            currentPhotoIndex = 0;
            updatePhotoDisplay();
            createDots(photoUrls.size());

        } catch (Exception e) {
            Toast.makeText(this, "Ошибка parsing: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private TextView createChip(String text) {
        TextView chip = new TextView(this);
        chip.setText(text);
        chip.setTextSize(14);
        chip.setPadding(16, 8, 16, 8);
        chip.setTextColor(0xFF333333);

        GradientDrawable background = new GradientDrawable();
        background.setColor(0xFFFFFFFF);
        background.setCornerRadius(16f);
        chip.setBackground(background);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 8, 0);
        chip.setLayoutParams(params);

        return chip;
    }

    private void createDots(int count) {
        dotsContainer.removeAllViews();
        for (int i = 0; i < count; i++) {
            ImageView dot = new ImageView(this);
            int sizePx = (int) (8 * getResources().getDisplayMetrics().density);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(sizePx, sizePx);
            params.setMargins(4, 0, 4, 0);
            dot.setLayoutParams(params);
            dot.setImageDrawable(createDotDrawable(0xFFCCCCCC));
            dotsContainer.addView(dot);
        }
        updateDots(0);
    }

    private void updateDots(int position) {
        for (int i = 0; i < dotsContainer.getChildCount(); i++) {
            ImageView dot = (ImageView) dotsContainer.getChildAt(i);
            if (i == position) {
                dot.setImageDrawable(createDotDrawable(0xFFF0A55B));
            } else {
                dot.setImageDrawable(createDotDrawable(0xFFCCCCCC));
            }
        }
    }

    private GradientDrawable createDotDrawable(int color) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColor(color);
        int size = (int) (8 * getResources().getDisplayMetrics().density);
        shape.setSize(size, size);
        return shape;
    }

    private void loadImage(String url, ImageView imageView) {
        new Thread(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.connect();
                if (conn.getResponseCode() == 200) {
                    Bitmap bitmap = BitmapFactory.decodeStream(conn.getInputStream());
                    mainHandler.post(() -> imageView.setImageBitmap(bitmap));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private String cleanJson(String response) {
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        return (start >= 0 && end > start) ? response.substring(start, end + 1) : response;
    }
}