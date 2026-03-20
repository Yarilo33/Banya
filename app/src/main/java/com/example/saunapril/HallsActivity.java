package com.example.saunapril;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class HallsActivity extends BaseActivity {

    private LinearLayout hallsContainer;
    private int currentHallIdForUpload = 0;

    // Фиксированные типы бань из БД
    private static final int[] BATH_TYPE_IDS = {1, 2, 3, 4};
    private static final String[] BATH_TYPE_NAMES = {"Хамам", "Русская", "Сибирская", "Турецкая"};

    private static final String BASE_URL = "http://10.51.185.164/api";
    private static final String TAG = "HallsActivity";
    private static final int REQUEST_IMAGE_PICK = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_halls);

        initMenu();
        hallsContainer = findViewById(R.id.hallsContainer);
        loadHalls();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_IMAGE_PICK && resultCode == RESULT_OK && data != null) {
            Uri photoUri = data.getData();
            if (photoUri != null) {
                uploadPhoto(currentHallIdForUpload, photoUri);
            }
        }
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

                URL url = new URL(BASE_URL + "/admin/hall_list.php");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization", "Bearer " + token);
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
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(this, "Ошибка сети: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void parseAndDisplay(String response) throws Exception {
        String cleanResponse = cleanJson(response);
        JSONObject json = new JSONObject(cleanResponse);

        if (!json.optBoolean("success", false)) {
            showError(json.optString("message", "Ошибка"));
            return;
        }

        JSONArray halls = json.optJSONArray("halls");
        if (halls == null || halls.length() == 0) {
            showError("Залы не найдены");
            return;
        }

        hallsContainer.removeAllViews();
        for (int i = 0; i < halls.length(); i++) {
            try {
                createHallCard(halls.getJSONObject(i));
            } catch (Exception e) {
                Log.e(TAG, "Error creating card", e);
            }
        }
    }

    private void showError(String msg) {
        TextView tv = new TextView(this);
        tv.setText("Ошибка: " + msg);
        tv.setPadding(16, 16, 16, 16);
        hallsContainer.addView(tv);
    }

    private void createHallCard(JSONObject hall) throws Exception {
        int hallId = hall.optInt("id", 0);
        String name = hall.optString("name", "Без названия");
        String description = hall.optString("description", "");
        int price = hall.optInt("price_hourly", 0);
        int capacity = hall.optInt("capacity", 0);
        JSONArray bathTypeNames = hall.optJSONArray("bath_type_names");
        JSONArray photos = hall.optJSONArray("photos");
        JSONArray bathTypes = hall.optJSONArray("bath_types");

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(16, 16, 16, 16);
        card.setBackgroundResource(android.R.drawable.dialog_holo_light_frame);

        TextView tvName = new TextView(this);
        tvName.setText(name);
        tvName.setTextSize(20);
        tvName.setTypeface(null, android.graphics.Typeface.BOLD);
        tvName.setPadding(0, 0, 0, 8);
        card.addView(tvName);

        if (bathTypeNames != null && bathTypeNames.length() > 0) {
            StringBuilder types = new StringBuilder();
            for (int i = 0; i < bathTypeNames.length(); i++) {
                if (i > 0) types.append(", ");
                types.append(bathTypeNames.optString(i, ""));
            }
            TextView tvTypes = new TextView(this);
            tvTypes.setText("Особенности: " + types.toString());
            tvTypes.setTextSize(14);
            tvTypes.setPadding(0, 0, 0, 8);
            tvTypes.setTextColor(0xFF666666);
            card.addView(tvTypes);
        }

        if (!description.isEmpty()) {
            TextView tvDescLabel = new TextView(this);
            tvDescLabel.setText("Описание:");
            tvDescLabel.setTextSize(14);
            tvDescLabel.setTypeface(null, android.graphics.Typeface.BOLD);
            tvDescLabel.setPadding(0, 8, 0, 4);
            card.addView(tvDescLabel);

            TextView tvDesc = new TextView(this);
            tvDesc.setText(description);
            tvDesc.setTextSize(14);
            tvDesc.setPadding(0, 0, 0, 8);
            card.addView(tvDesc);
        }

        TextView tvInfo = new TextView(this);
        tvInfo.setText("Цена: " + price + " руб./час | Вместимость: " + capacity + " чел.");
        tvInfo.setTextSize(14);
        tvInfo.setPadding(0, 0, 0, 16);
        card.addView(tvInfo);

        TextView tvPhotosLabel = new TextView(this);
        tvPhotosLabel.setText("Фотографии зала:");
        tvPhotosLabel.setTextSize(14);
        tvPhotosLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        tvPhotosLabel.setPadding(0, 8, 0, 8);
        card.addView(tvPhotosLabel);

        ScrollView photosScroll = new ScrollView(this);
        photosScroll.setHorizontalScrollBarEnabled(true);

        LinearLayout photosRow = new LinearLayout(this);
        photosRow.setOrientation(LinearLayout.HORIZONTAL);
        photosRow.setPadding(0, 0, 0, 16);

        Button btnAddPhoto = new Button(this);
        btnAddPhoto.setText("+ Добавить фото");
        btnAddPhoto.setTextSize(12);
        LinearLayout.LayoutParams addBtnParams = new LinearLayout.LayoutParams(200, 260);
        addBtnParams.setMargins(0, 0, 12, 0);
        btnAddPhoto.setLayoutParams(addBtnParams);
        btnAddPhoto.setOnClickListener(v -> {
            currentHallIdForUpload = hallId;
            pickImage();
        });
        photosRow.addView(btnAddPhoto);

        if (photos != null && photos.length() > 0) {
            for (int j = 0; j < photos.length(); j++) {
                try {
                    JSONObject photo = photos.optJSONObject(j);
                    if (photo == null) continue;

                    int photoId = photo.optInt("id", 0);
                    String photoUrl = photo.optString("url", "");

                    if (photoUrl.isEmpty()) continue;

                    LinearLayout photoContainer = new LinearLayout(this);
                    photoContainer.setOrientation(LinearLayout.VERTICAL);
                    LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(200, 260);
                    containerParams.setMargins(0, 0, 12, 0);
                    photoContainer.setLayoutParams(containerParams);

                    ImageView iv = new ImageView(this);
                    LinearLayout.LayoutParams ivParams = new LinearLayout.LayoutParams(200, 200);
                    iv.setLayoutParams(ivParams);
                    iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    iv.setBackgroundResource(android.R.drawable.picture_frame);

                    photoContainer.addView(iv);

                    Button btnDeletePhoto = new Button(this);
                    btnDeletePhoto.setText("Удалить фото");
                    btnDeletePhoto.setTextSize(10);
                    LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT);
                    btnDeletePhoto.setLayoutParams(btnParams);
                    btnDeletePhoto.setPadding(4, 4, 4, 4);
                    btnDeletePhoto.setOnClickListener(v -> {
                        new AlertDialog.Builder(this)
                                .setTitle("Удалить фотографию")
                                .setMessage("Вы уверены?")
                                .setPositiveButton("Удалить", (dialog, which) -> {
                                    deletePhoto(hallId, photoId, photoContainer);
                                })
                                .setNegativeButton("Отмена", null)
                                .show();
                    });

                    photoContainer.addView(btnDeletePhoto);
                    photosRow.addView(photoContainer);

                    loadImage(BASE_URL + photoUrl, iv);
                } catch (Exception e) {
                    Log.e(TAG, "Error loading photo", e);
                }
            }
        }

        photosScroll.addView(photosRow);
        card.addView(photosScroll);

        View divider = new View(this);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 2);
        dividerParams.setMargins(0, 8, 0, 16);
        divider.setLayoutParams(dividerParams);
        divider.setBackgroundColor(0xFFCCCCCC);
        card.addView(divider);

        TextView tvActions = new TextView(this);
        tvActions.setText("Действия:");
        tvActions.setTextSize(14);
        tvActions.setTypeface(null, android.graphics.Typeface.BOLD);
        tvActions.setPadding(0, 0, 0, 8);
        card.addView(tvActions);

        LinearLayout buttonsLayout = new LinearLayout(this);
        buttonsLayout.setOrientation(LinearLayout.HORIZONTAL);
        buttonsLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        Button btnEdit = new Button(this);
        btnEdit.setText("Изменить");
        btnEdit.setTextSize(12);
        LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(0, 40);
        editParams.weight = 1;
        editParams.setMargins(0, 0, 4, 0);
        btnEdit.setLayoutParams(editParams);
        btnEdit.setPadding(4, 4, 4, 4);
        btnEdit.setOnClickListener(v -> showEditDialog(hallId, name, description, price, capacity, bathTypes));

        Button btnDelete = new Button(this);
        btnDelete.setText("Удалить");
        btnDelete.setTextSize(12);
        LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(0, 40);
        deleteParams.weight = 1;
        deleteParams.setMargins(4, 0, 0, 0);
        btnDelete.setLayoutParams(deleteParams);
        btnDelete.setPadding(4, 4, 4, 4);
        btnDelete.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Удалить зал")
                    .setMessage("Удалить \"" + name + "\"?")
                    .setPositiveButton("Удалить", (dialog, which) -> deleteHall(hallId))
                    .setNegativeButton("Отмена", null)
                    .show();
        });

        buttonsLayout.addView(btnEdit);
        buttonsLayout.addView(btnDelete);
        card.addView(buttonsLayout);

        hallsContainer.addView(card);
    }

    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_IMAGE_PICK);
    }

    // Диалог редактирования с чекбоксами типов бань
    private void showEditDialog(int hallId, String name, String description, int price, int capacity, JSONArray originalBathTypes) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Редактирование зала");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 24);

        EditText etName = new EditText(this);
        etName.setHint("Название зала");
        etName.setText(name);
        layout.addView(etName);

        EditText etDescription = new EditText(this);
        etDescription.setHint("Описание");
        etDescription.setText(description);
        layout.addView(etDescription);

        EditText etPrice = new EditText(this);
        etPrice.setHint("Цена за час");
        etPrice.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etPrice.setText(String.valueOf(price));
        layout.addView(etPrice);

        EditText etCapacity = new EditText(this);
        etCapacity.setHint("Вместимость");
        etCapacity.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etCapacity.setText(String.valueOf(capacity));
        layout.addView(etCapacity);

        // Чекбоксы для типов бань
        TextView tvBathTypes = new TextView(this);
        tvBathTypes.setText("Типы бань:");
        tvBathTypes.setTextSize(14);
        tvBathTypes.setTypeface(null, android.graphics.Typeface.BOLD);
        tvBathTypes.setPadding(0, 16, 0, 8);
        layout.addView(tvBathTypes);

        LinearLayout bathTypesLayout = new LinearLayout(this);
        bathTypesLayout.setOrientation(LinearLayout.VERTICAL);

        // Массив для хранения состояния чекбоксов
        final boolean[] selectedTypes = new boolean[BATH_TYPE_IDS.length];

        // Отмечаем текущие выбранные типы
        if (originalBathTypes != null) {
            for (int i = 0; i < originalBathTypes.length(); i++) {
                int typeId = originalBathTypes.optInt(i, -1);
                for (int j = 0; j < BATH_TYPE_IDS.length; j++) {
                    if (BATH_TYPE_IDS[j] == typeId) {
                        selectedTypes[j] = true;
                        break;
                    }
                }
            }
        }

        // Создаем чекбоксы
        for (int i = 0; i < BATH_TYPE_NAMES.length; i++) {
            final int index = i;
            CheckBox checkBox = new CheckBox(this);
            checkBox.setText(BATH_TYPE_NAMES[i]);
            checkBox.setChecked(selectedTypes[i]);
            checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                selectedTypes[index] = isChecked;
            });
            bathTypesLayout.addView(checkBox);
        }

        layout.addView(bathTypesLayout);

        builder.setView(layout);

        builder.setPositiveButton("Сохранить", (dialog, which) -> {
            String newName = etName.getText().toString().trim();
            String newDesc = etDescription.getText().toString().trim();
            String newPrice = etPrice.getText().toString().trim();
            String newCapacity = etCapacity.getText().toString().trim();

            if (newName.isEmpty()) {
                Toast.makeText(this, "Введите название зала", Toast.LENGTH_SHORT).show();
                return;
            }

            // Проверяем, выбран ли хотя бы один тип бани
            boolean hasSelected = false;
            for (boolean selected : selectedTypes) {
                if (selected) {
                    hasSelected = true;
                    break;
                }
            }

            if (!hasSelected) {
                Toast.makeText(this, "Выберите хотя бы один тип бани", Toast.LENGTH_SHORT).show();
                return;
            }

            // Создаем массив выбранных ID типов бань
            JSONArray selectedBathTypes = new JSONArray();
            for (int i = 0; i < selectedTypes.length; i++) {
                if (selectedTypes[i]) {
                    selectedBathTypes.put(BATH_TYPE_IDS[i]);
                }
            }

            updateHall(hallId, newName, newDesc,
                    newPrice.isEmpty() ? 0 : parseInt(newPrice),
                    newCapacity.isEmpty() ? 0 : parseInt(newCapacity),
                    selectedBathTypes);
        });

        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    private void updateHall(int hallId, String name, String description, int price, int capacity, JSONArray bathTypes) {
        new Thread(() -> {
            try {
                String token = getSharedPreferences("auth_prefs", MODE_PRIVATE)
                        .getString("jwt_token", "");

                String urlStr = BASE_URL + "/admin/hall_update.php?id=" + hallId;

                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setDoOutput(true);

                JSONObject json = new JSONObject();
                json.put("name", name);
                if (!description.isEmpty()) json.put("description", description);
                if (price > 0) json.put("price_hourly", price);
                if (capacity > 0) json.put("capacity", capacity);
                json.put("bath_types", bathTypes);

                String jsonInputString = json.toString();
                Log.d(TAG, "Request body: " + jsonInputString);

                OutputStream os = conn.getOutputStream();
                byte[] input = jsonInputString.getBytes("UTF-8");
                os.write(input, 0, input.length);
                os.flush();
                os.close();

                int responseCode = conn.getResponseCode();
                Log.d(TAG, "Update Response Code: " + responseCode);

                BufferedReader reader = (responseCode >= 200 && responseCode < 300) ?
                        new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8")) :
                        new BufferedReader(new InputStreamReader(conn.getErrorStream(), "UTF-8"));

                StringBuilder result = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) result.append(line);
                reader.close();

                String response = result.toString();
                Log.d(TAG, "Update Response: " + response);

                runOnUiThread(() -> {
                    if (responseCode == 200) {
                        Toast.makeText(this, "Зал обновлен", Toast.LENGTH_SHORT).show();
                        hallsContainer.removeAllViews();
                        loadHalls();
                    } else {
                        try {
                            String cleanResponse = cleanJson(response);
                            JSONObject errorJson = new JSONObject(cleanResponse);
                            String message = errorJson.optString("error",
                                    errorJson.optString("message", "Ошибка " + responseCode));
                            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                        } catch (Exception e) {
                            Toast.makeText(this, "Ошибка: " + response, Toast.LENGTH_LONG).show();
                        }
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(this, "Ошибка сети: " + e.getMessage(), Toast.LENGTH_LONG).show());
                Log.e(TAG, "Update error", e);
            }
        }).start();
    }

    private void deleteHall(int hallId) {
        new Thread(() -> {
            try {
                String token = getSharedPreferences("auth_prefs", MODE_PRIVATE)
                        .getString("jwt_token", "");

                String urlStr = BASE_URL + "/admin/hall_delete.php?id=" + hallId;

                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("DELETE");
                conn.setRequestProperty("Authorization", "Bearer " + token);
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
                    if (finalCode == 200) {
                        Toast.makeText(this, "Зал удален", Toast.LENGTH_SHORT).show();
                        hallsContainer.removeAllViews();
                        loadHalls();
                    } else {
                        try {
                            String cleanResponse = cleanJson(response);
                            JSONObject errorJson = new JSONObject(cleanResponse);
                            String message = errorJson.optString("error",
                                    errorJson.optString("message", "Ошибка"));
                            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                        } catch (Exception e) {
                            Toast.makeText(this, "Ошибка: " + finalCode, Toast.LENGTH_SHORT).show();
                        }
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(this, "Ошибка сети: " + e.getMessage(), Toast.LENGTH_LONG).show());
                Log.e(TAG, "Delete error", e);
            }
        }).start();
    }

    private void deletePhoto(int hallId, int photoId, LinearLayout photoContainer) {
        new Thread(() -> {
            try {
                String token = getSharedPreferences("auth_prefs", MODE_PRIVATE)
                        .getString("jwt_token", "");

                String urlStr = BASE_URL + "/admin/hall_update.php?id=" + hallId;

                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setDoOutput(true);

                JSONObject json = new JSONObject();
                json.put("name", "placeholder");
                json.put("bath_types", new JSONArray().put(1));
                json.put("photos_to_delete", new JSONArray().put(photoId));

                String jsonInputString = json.toString();

                OutputStream os = conn.getOutputStream();
                byte[] input = jsonInputString.getBytes("UTF-8");
                os.write(input, 0, input.length);
                os.flush();
                os.close();

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
                    if (finalCode == 200) {
                        if (photoContainer != null && photoContainer.getParent() != null) {
                            LinearLayout parent = (LinearLayout) photoContainer.getParent();
                            parent.removeView(photoContainer);
                        }
                        Toast.makeText(this, "Фото удалено", Toast.LENGTH_SHORT).show();
                    } else {
                        try {
                            String cleanResponse = cleanJson(response);
                            JSONObject errorJson = new JSONObject(cleanResponse);
                            String message = errorJson.optString("error",
                                    errorJson.optString("message", "Ошибка"));
                            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                        } catch (Exception e) {
                            Toast.makeText(this, "Ошибка: " + finalCode, Toast.LENGTH_SHORT).show();
                        }
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(this, "Ошибка сети: " + e.getMessage(), Toast.LENGTH_LONG).show());
                Log.e(TAG, "Delete photo error", e);
            }
        }).start();
    }

    private void uploadPhoto(int hallId, Uri photoUri) {
        new Thread(() -> {
            try {
                String token = getSharedPreferences("auth_prefs", MODE_PRIVATE)
                        .getString("jwt_token", "");

                String urlStr = BASE_URL + "/admin/hall_update.php?id=" + hallId;
                URL url = new URL(urlStr);

                String boundary = "----" + System.currentTimeMillis();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                conn.setDoOutput(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);

                DataOutputStream dos = new DataOutputStream(conn.getOutputStream());

                writeField(dos, boundary, "name", "placeholder");
                writeField(dos, boundary, "bath_types", "[1]");

                String fileName = "photo_" + System.currentTimeMillis() + ".jpg";
                dos.writeBytes("--" + boundary + "\r\n");
                dos.writeBytes("Content-Disposition: form-data; name=\"photos[]\"; filename=\"" + fileName + "\"\r\n");
                dos.writeBytes("Content-Type: image/jpeg\r\n\r\n");

                byte[] buffer = new byte[4096];
                int bytesRead;
                InputStream inputStream = getContentResolver().openInputStream(photoUri);
                if (inputStream == null) {
                    throw new Exception("Не удалось открыть файл");
                }
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    dos.write(buffer, 0, bytesRead);
                }
                inputStream.close();

                dos.writeBytes("\r\n");
                dos.writeBytes("--" + boundary + "--\r\n");
                dos.flush();
                dos.close();

                int responseCode = conn.getResponseCode();
                BufferedReader reader = (responseCode >= 200 && responseCode < 300) ?
                        new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8")) :
                        new BufferedReader(new InputStreamReader(conn.getErrorStream(), "UTF-8"));

                StringBuilder result = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) result.append(line);
                reader.close();

                String response = result.toString();

                runOnUiThread(() -> {
                    if (responseCode == 200) {
                        Toast.makeText(this, "Фото загружено", Toast.LENGTH_SHORT).show();
                        hallsContainer.removeAllViews();
                        loadHalls();
                    } else {
                        try {
                            String clean = cleanJson(response);
                            JSONObject err = new JSONObject(clean);
                            String msg = err.optString("error", err.optString("message", "Ошибка " + responseCode));
                            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                        } catch (Exception e) {
                            Toast.makeText(this, "Ошибка: " + response, Toast.LENGTH_LONG).show();
                        }
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(this, "Ошибка: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void writeField(DataOutputStream dos, String boundary, String name, String value) throws Exception {
        dos.writeBytes("--" + boundary + "\r\n");
        dos.writeBytes("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n");
        dos.writeBytes(value + "\r\n");
    }

    private void loadImage(String url, ImageView imageView) {
        new Thread(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setDoInput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.connect();

                if (conn.getResponseCode() == 200) {
                    Bitmap bitmap = BitmapFactory.decodeStream(conn.getInputStream());
                    if (bitmap != null) {
                        runOnUiThread(() -> imageView.setImageBitmap(bitmap));
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Image error", e);
            }
        }).start();
    }

    private String cleanJson(String response) {
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return response.substring(start, end + 1);
        }
        return response;
    }

    private int parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }
}