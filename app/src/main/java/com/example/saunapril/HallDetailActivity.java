package com.example.saunapril;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
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
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class HallDetailActivity extends AppCompatActivity {

    // Используем константы из Config
    private static final String API_HALL_DETAIL = Config.API_HALL_DETAIL;
    private static final String BASE_PHOTO_URL = Config.API_BASE;
    private static final String API_BOOKING_CREATE = Config.API_BOOKING_CREATE;
    private static final String PREF_NAME = Config.PREF_NAME;
    private static final String KEY_TOKEN = Config.PREF_KEY_TOKEN;

    private TextView tvName, tvPrice, tvDescription, tvPhotoCounter;
    private TextView tvMonthYear, tvSelectedDate, tvSelectedTime, tvTotalPrice;
    private TextView tvCapacity;
    private LinearLayout typesContainer, dotsContainer, weekdaysContainer;
    private LinearLayout calendarCard, timeSelectionCard;
    private ImageView ivCurrentPhoto;
    private GridLayout daysGrid, timeSlotsGrid;
    private Button btnBook, btnConfirmDate;
    private ImageButton btnBack, btnPrev, btnNext, btnPrevMonth, btnNextMonth;

    private int hallId;
    private int hourlyPrice = 0;
    private List<String> photoUrls = new ArrayList<>();
    private int currentPhotoIndex = 0;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    private Calendar currentCalendar;
    private int selectedDay = -1;
    private String selectedDateStr = "";
    private String selectedStartTimeStr = "";
    private String selectedEndTimeStr = "";
    private Button selectedStartButton = null;
    private Button selectedEndButton = null;
    private Set<String> bookedTimeSlots = new HashSet<>();

    // тайм слоты для записи из конфига
    private static final String[] TIME_SLOTS = Config.TIME_SLOTS;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hall_detail);

        hallId = getIntent().getIntExtra("hall_id", 0);
        if (hallId == 0) {
            Toast.makeText(this, R.string.common_error, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        currentCalendar = Calendar.getInstance();
        initViews();
        loadHallDetails();
    }

    private void initViews() {
        tvName = findViewById(R.id.tvName);
        tvPrice = findViewById(R.id.tvPrice);
        tvDescription = findViewById(R.id.tvDescription);
        tvPhotoCounter = findViewById(R.id.tvPhotoCounter);
        tvCapacity = findViewById(R.id.tvCapacity);
        tvMonthYear = findViewById(R.id.tvMonthYear);
        tvSelectedDate = findViewById(R.id.tvSelectedDate);
        tvSelectedTime = findViewById(R.id.tvSelectedTime);
        tvTotalPrice = findViewById(R.id.tvTotalPrice);
        typesContainer = findViewById(R.id.typesContainer);
        dotsContainer = findViewById(R.id.dotsContainer);
        weekdaysContainer = findViewById(R.id.weekdaysContainer);
        daysGrid = findViewById(R.id.daysGrid);
        timeSlotsGrid = findViewById(R.id.timeSlotsGrid);
        ivCurrentPhoto = findViewById(R.id.ivCurrentPhoto);
        btnBook = findViewById(R.id.btnBook);
        btnConfirmDate = findViewById(R.id.btnConfirmDate);
        btnBack = findViewById(R.id.btnBack);
        btnPrev = findViewById(R.id.btnPrev);
        btnNext = findViewById(R.id.btnNext);
        btnPrevMonth = findViewById(R.id.btnPrevMonth);
        btnNextMonth = findViewById(R.id.btnNextMonth);
        calendarCard = findViewById(R.id.calendarCard);
        timeSelectionCard = findViewById(R.id.timeSelectionCard);

        btnBack.setOnClickListener(v -> finish());

        btnBook.setOnClickListener(v -> {
            if (!isAuthenticated()) {
                redirectToAuth();
                return;
            }
            createBooking();
        });

        btnConfirmDate.setOnClickListener(v -> loadBookedTimes());
        btnPrev.setOnClickListener(v -> showPreviousPhoto());
        btnNext.setOnClickListener(v -> showNextPhoto());

        btnPrevMonth.setOnClickListener(v -> {
            currentCalendar.add(Calendar.MONTH, -1);
            resetSelection();
            updateCalendar();
        });

        btnNextMonth.setOnClickListener(v -> {
            currentCalendar.add(Calendar.MONTH, 1);
            resetSelection();
            updateCalendar();
        });

        initWeekdays();
        updateCalendar();
    }

    private boolean isAuthenticated() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        String token = prefs.getString(KEY_TOKEN, "");
        return token != null && !token.isEmpty();
    }

    private void redirectToAuth() {
        Toast.makeText(this, R.string.admin_msg_auth_required, Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(this, AuthActivity.class);
        startActivity(intent);
    }

    private void initWeekdays() {
        weekdaysContainer.removeAllViews();
        // Получаем дни недели из resources
        String[] weekdays = getResources().getStringArray(R.array.weekdays);
        for (String day : weekdays) {
            TextView tv = new TextView(this);
            tv.setText(day);
            tv.setTextSize(14);
            tv.setTextColor(0xFF666666);
            tv.setGravity(Gravity.CENTER_HORIZONTAL);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            tv.setLayoutParams(params);
            weekdaysContainer.addView(tv);
        }
    }

    private void updateCalendar() {
        // Получаем месяцы из resources
        String[] months = getResources().getStringArray(R.array.months);

        int month = currentCalendar.get(Calendar.MONTH);
        int year = currentCalendar.get(Calendar.YEAR);
        tvMonthYear.setText(months[month] + " " + year);
        daysGrid.removeAllViews();

        Calendar cal = (Calendar) currentCalendar.clone();
        cal.set(Calendar.DAY_OF_MONTH, 1);

        int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
        int offset = (dayOfWeek == Calendar.SUNDAY) ? 6 : dayOfWeek - Calendar.MONDAY;
        int daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);

        int cellSize = (int) (45 * getResources().getDisplayMetrics().density);

        for (int i = 0; i < offset; i++) {
            View empty = new View(this);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = cellSize;
            params.height = cellSize;
            params.setMargins(4, 4, 4, 4);
            empty.setLayoutParams(params);
            daysGrid.addView(empty);
        }

        for (int day = 1; day <= daysInMonth; day++) {
            Button dayBtn = createDayButton(day, cellSize);
            daysGrid.addView(dayBtn);
        }
    }

    private Button createDayButton(int day, int cellSize) {
        Button btn = new Button(this);
        btn.setText(String.valueOf(day));
        btn.setTextSize(14);
        btn.setTextColor(0xFFFFFFFF);
        btn.setPadding(0, 0, 0, 0);

        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = cellSize;
        params.height = cellSize;
        params.setMargins(2, 2, 2, 2);
        btn.setLayoutParams(params);

        Calendar cal = (Calendar) currentCalendar.clone();
        cal.set(Calendar.DAY_OF_MONTH, day);
        String dateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.getTime());

        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);

        boolean isPast = cal.before(today);

        if (isPast) {
            btn.setBackgroundResource(R.drawable.calendar_inactive);
            btn.setEnabled(false);
        } else {
            btn.setBackgroundResource(R.drawable.calendar_available);
            btn.setOnClickListener(v -> selectDay(day, dateStr, btn));
        }

        if (selectedDay == day && selectedDateStr.equals(dateStr)) {
            btn.setBackgroundResource(R.drawable.calendar_selected);
        }

        return btn;
    }

    private void selectDay(int day, String dateStr, Button btn) {
        selectedDay = day;
        selectedDateStr = dateStr;

        for (int i = 0; i < daysGrid.getChildCount(); i++) {
            View child = daysGrid.getChildAt(i);
            if (child instanceof Button) {
                Button b = (Button) child;
                if (b.isEnabled()) {
                    b.setBackgroundResource(R.drawable.calendar_available);
                }
            }
        }

        btn.setBackgroundResource(R.drawable.calendar_selected);
        tvSelectedDate.setText(getString(R.string.calendar_label_select_date) + " " + formatDateForDisplay(dateStr));
        tvSelectedDate.setVisibility(View.VISIBLE);
        btnConfirmDate.setVisibility(View.VISIBLE);

        resetTimeSelection();
        bookedTimeSlots.clear();
    }

    private void resetSelection() {
        selectedDay = -1;
        selectedDateStr = "";
        resetTimeSelection();
        tvSelectedDate.setVisibility(View.GONE);
        btnConfirmDate.setVisibility(View.GONE);
    }

    private void resetTimeSelection() {
        timeSelectionCard.setVisibility(View.GONE);
        btnBook.setVisibility(View.GONE);
        selectedStartTimeStr = "";
        selectedEndTimeStr = "";
        tvSelectedTime.setVisibility(View.GONE);
        tvTotalPrice.setVisibility(View.GONE);

        if (selectedStartButton != null) {
            selectedStartButton.setBackgroundResource(R.drawable.calendar_available);
            selectedStartButton = null;
        }
        if (selectedEndButton != null) {
            selectedEndButton.setBackgroundResource(R.drawable.calendar_available);
            selectedEndButton = null;
        }
        bookedTimeSlots.clear();
    }

    private void loadBookedTimes() {
        if (selectedDateStr.isEmpty()) return;

        btnConfirmDate.setEnabled(false);
        btnConfirmDate.setText(R.string.calendar_btn_loading);

        new Thread(() -> {
            try {
                URL url = new URL(API_HALL_DETAIL + hallId + "&date=" + selectedDateStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(Config.CONNECT_TIMEOUT);
                conn.setReadTimeout(Config.READ_TIMEOUT);

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), "UTF-8")
                );
                StringBuilder result = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) result.append(line);
                reader.close();

                String response = result.toString();

                mainHandler.post(() -> {
                    try {
                        JSONObject json = new JSONObject(cleanJson(response));
                        bookedTimeSlots.clear();

                        if (json.optBoolean("success", false)) {
                            JSONArray timeSlots = json.optJSONArray("time_slots");
                            if (timeSlots != null) {
                                for (int i = 0; i < timeSlots.length(); i++) {
                                    JSONObject slot = timeSlots.optJSONObject(i);
                                    if (slot != null && !slot.optBoolean("available", true)) {
                                        bookedTimeSlots.add(slot.optString("start", ""));
                                    }
                                }
                            }
                        }

                        showTimeSelection();
                        btnConfirmDate.setEnabled(true);
                        btnConfirmDate.setText(R.string.calendar_btn_confirm);

                    } catch (Exception e) {
                        Toast.makeText(this, R.string.calendar_msg_load_error, Toast.LENGTH_SHORT).show();
                        btnConfirmDate.setEnabled(true);
                        btnConfirmDate.setText(R.string.calendar_btn_confirm);
                    }
                });

            } catch (Exception e) {
                mainHandler.post(() -> {
                    Toast.makeText(this, R.string.auth_msg_network_error, Toast.LENGTH_SHORT).show();
                    btnConfirmDate.setEnabled(true);
                    btnConfirmDate.setText(R.string.calendar_btn_confirm);
                });
            }
        }).start();
    }

    private void showTimeSelection() {
        timeSelectionCard.setVisibility(View.VISIBLE);
        timeSlotsGrid.removeAllViews();

        int buttonWidth = (int) (90 * getResources().getDisplayMetrics().density);
        int buttonHeight = (int) (45 * getResources().getDisplayMetrics().density);

        for (int i = 0; i < TIME_SLOTS.length; i++) {
            String startTime = TIME_SLOTS[i];
            String endTime = getEndTime(startTime);
            String displayText = startTime + "-" + endTime;

            Button timeBtn = createTimeButton(startTime, endTime, displayText, buttonWidth, buttonHeight);
            timeSlotsGrid.addView(timeBtn);
        }

        tvSelectedTime.setVisibility(View.VISIBLE);
        tvTotalPrice.setVisibility(View.GONE);
    }

    private String getEndTime(String startTime) {
        String[] parts = startTime.split(":");
        int hour = Integer.parseInt(parts[0]);
        return String.format(Locale.getDefault(), "%02d:00", hour + 1);
    }

    private Button createTimeButton(String startTime, String endTime, String displayText, int buttonWidth, int buttonHeight) {
        Button btn = new Button(this);
        btn.setText(displayText);
        btn.setTextSize(12);
        btn.setTextColor(0xFFFFFFFF);
        btn.setPadding(4, 8, 4, 8);
        btn.setTag(startTime);

        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = buttonWidth;
        params.height = buttonHeight;
        params.setMargins(4, 4, 4, 4);
        btn.setLayoutParams(params);

        boolean isBooked = bookedTimeSlots.contains(startTime);

        if (isBooked) {
            btn.setBackgroundResource(R.drawable.calendar_inactive);
            btn.setEnabled(false);
        } else {
            btn.setBackgroundResource(R.drawable.calendar_available);
            btn.setOnClickListener(v -> selectTimeRange(startTime, endTime, btn));
        }

        return btn;
    }

    private void selectTimeRange(String startTime, String endTime, Button btn) {
        if (selectedStartButton == null) {
            selectedStartButton = btn;
            selectedStartTimeStr = startTime;
            selectedEndTimeStr = endTime;
            btn.setBackgroundResource(R.drawable.calendar_selected);
            tvSelectedTime.setText(getString(R.string.calendar_label_selected_time) + " " + startTime + "-" + endTime + " (" + getString(R.string.calendar_msg_select_different_time) + ")");
            tvTotalPrice.setVisibility(View.GONE);
        } else if (selectedEndButton == null) {
            int startHour = Integer.parseInt(selectedStartTimeStr.split(":")[0]);
            int endHour = Integer.parseInt(startTime.split(":")[0]);

            if (endHour < startHour) {
                Toast.makeText(this, R.string.calendar_msg_end_after_start, Toast.LENGTH_SHORT).show();
                return;
            }

            if (endHour == startHour) {
                Toast.makeText(this, R.string.calendar_msg_select_different_time, Toast.LENGTH_SHORT).show();
                return;
            }

            selectedEndButton = btn;
            selectedEndTimeStr = getEndTime(startTime);
            btn.setBackgroundResource(R.drawable.calendar_selected);

            highlightRange(selectedStartTimeStr, startTime);

            // Рассчитываем стоимость
            int hours = endHour - startHour + 1;
            int totalPrice = hourlyPrice * hours;

            tvSelectedTime.setText(getString(R.string.calendar_label_selected_time) + " " + selectedStartTimeStr + " - " + selectedEndTimeStr);
            tvTotalPrice.setText(getString(R.string.calendar_label_total) + " " + totalPrice + " " + getString(R.string.detail_unit_total) + " (" + hours + " " + getString(R.string.detail_label_hours) + ")");
            tvTotalPrice.setVisibility(View.VISIBLE);
            btnBook.setVisibility(View.VISIBLE);
        } else {
            clearTimeHighlight();
            selectedStartButton = null;
            selectedEndButton = null;
            selectedStartTimeStr = "";
            selectedEndTimeStr = "";
            tvTotalPrice.setVisibility(View.GONE);
            selectTimeRange(startTime, endTime, btn);
        }
    }

    private void clearTimeHighlight() {
        for (int i = 0; i < timeSlotsGrid.getChildCount(); i++) {
            View child = timeSlotsGrid.getChildAt(i);
            if (child instanceof Button) {
                Button btn = (Button) child;
                if (btn.isEnabled()) {
                    btn.setBackgroundResource(R.drawable.calendar_available);
                }
            }
        }
    }

    private void highlightRange(String startTime, String endTime) {
        int startHour = Integer.parseInt(startTime.split(":")[0]);
        int endHour = Integer.parseInt(endTime.split(":")[0]);

        for (int i = 0; i < timeSlotsGrid.getChildCount(); i++) {
            View child = timeSlotsGrid.getChildAt(i);
            if (child instanceof Button) {
                Button btn = (Button) child;
                String btnStartTime = (String) btn.getTag();

                if (btnStartTime != null) {
                    int btnHour = Integer.parseInt(btnStartTime.split(":")[0]);

                    if (btnHour >= startHour && btnHour <= endHour) {
                        btn.setBackgroundResource(R.drawable.calendar_selected);
                    } else if (!bookedTimeSlots.contains(btnStartTime)) {
                        btn.setBackgroundResource(R.drawable.calendar_available);
                    }
                }
            }
        }
    }

    private void createBooking() {
        if (selectedDateStr.isEmpty()) {
            Toast.makeText(this, R.string.calendar_msg_select_date, Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedStartTimeStr.isEmpty() || selectedEndTimeStr.isEmpty()) {
            Toast.makeText(this, R.string.calendar_msg_select_time, Toast.LENGTH_SHORT).show();
            return;
        }

        btnBook.setEnabled(false);
        btnBook.setText(R.string.calendar_btn_booking);

        new Thread(() -> {
            try {
                SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
                String token = prefs.getString(KEY_TOKEN, "");

                URL url = new URL(API_BOOKING_CREATE);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setConnectTimeout(Config.CONNECT_TIMEOUT);
                conn.setReadTimeout(Config.READ_TIMEOUT);
                conn.setDoOutput(true);

                JSONObject json = new JSONObject();
                json.put("hall_id", hallId);
                json.put("date", selectedDateStr);
                json.put("start_time", selectedStartTimeStr);
                json.put("end_time", selectedEndTimeStr);

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

                mainHandler.post(() -> {
                    btnBook.setEnabled(true);
                    btnBook.setText(R.string.calendar_btn_book);

                    try {
                        JSONObject jsonResponse = new JSONObject(cleanJson(response));

                        if (responseCode == 200 && jsonResponse.optBoolean("success", false)) {
                            Toast.makeText(this, R.string.calendar_msg_success, Toast.LENGTH_LONG).show();
                            resetSelection();
                            updateCalendar();
                        } else {
                            String message = jsonResponse.optString("error",
                                    jsonResponse.optString("message", getString(R.string.calendar_msg_error)));
                            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                        }
                    } catch (Exception e) {
                        Toast.makeText(this, getString(R.string.common_error) + ": " + response, Toast.LENGTH_LONG).show();
                    }
                });

            } catch (Exception e) {
                mainHandler.post(() -> {
                    btnBook.setEnabled(true);
                    btnBook.setText(R.string.calendar_btn_book);
                    Toast.makeText(this, getString(R.string.auth_msg_network_error) + ": " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
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
                URL url = new URL(API_HALL_DETAIL + hallId);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(Config.CONNECT_TIMEOUT);
                conn.setReadTimeout(Config.READ_TIMEOUT);

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
                        Toast.makeText(this, getString(R.string.auth_msg_network_error) + ": " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
            }
        }).start();
    }

    private void parseAndDisplay(String response) {
        try {
            JSONObject json = new JSONObject(cleanJson(response));
            if (!json.optBoolean("success", false)) {
                Toast.makeText(this, json.optString("message", getString(R.string.common_error)), Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            JSONObject hall = json.optJSONObject("hall");
            if (hall == null) return;

            tvName.setText(hall.optString("name", getString(R.string.detail_label_no_name)));
            hourlyPrice = hall.optInt("price_hourly", 0);
            tvPrice.setText(hourlyPrice + " " + getString(R.string.detail_unit_price));
            tvDescription.setText(hall.optString("description", getString(R.string.detail_label_no_description)));

            int capacity = hall.optInt("capacity", 0);
            if (capacity > 0) {
                tvCapacity.setText(String.valueOf(capacity));
            } else {
                tvCapacity.setText("-");
            }

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

            JSONArray photos = hall.optJSONArray("photos");
            photoUrls.clear();

            if (photos != null) {
                for (int i = 0; i < photos.length(); i++) {
                    JSONObject photo = photos.optJSONObject(i);
                    if (photo != null) {
                        String url = photo.optString("url", "");
                        if (!url.isEmpty()) {
                            String fullUrl = url.startsWith("http") ? url : BASE_PHOTO_URL + url;
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
            Toast.makeText(this, getString(R.string.common_error) + ": " + e.getMessage(), Toast.LENGTH_SHORT).show();
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

    private String capitalizeFirst(String str) {
        if (str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase(Locale.getDefault()) + str.substring(1);
    }

    private String formatDateForDisplay(String dateStr) {
        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            SimpleDateFormat outputFormat = new SimpleDateFormat("dd MMMM", new Locale("ru"));
            return capitalizeFirst(outputFormat.format(inputFormat.parse(dateStr)));
        } catch (Exception e) {
            return dateStr;
        }
    }
}