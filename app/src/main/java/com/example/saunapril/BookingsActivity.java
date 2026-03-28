package com.example.saunapril;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class BookingsActivity extends BaseActivity {

    private LinearLayout bookingsContainer;
    private TextView tvBookingsCount;
    private ImageButton btnMenu;

    // Константы из Config
    private static final String API_BOOKING_LIST = Config.API_BOOKING_LIST;
    private static final String API_BOOKING_DELETE_BASE = Config.API_BOOKING_DELETE;
    private static final String PREF_NAME = Config.PREF_NAME;
    private static final String KEY_TOKEN = Config.PREF_KEY_TOKEN;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_bookings);

        initMenu();
        initViews();
        loadBookings();
    }

    private void initViews() {
        bookingsContainer = findViewById(R.id.bookingsContainer);
        tvBookingsCount = findViewById(R.id.tvBookingsCount);
        btnMenu = findViewById(R.id.btnMenu);

        if (btnMenu != null) {
            btnMenu.setOnClickListener(v -> {
                if (drawerLayout != null) {
                    drawerLayout.openDrawer(androidx.core.view.GravityCompat.END);
                }
            });
        }
    }

    private void loadBookings() {
        new Thread(() -> {
            try {
                SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
                String token = prefs.getString(KEY_TOKEN, "");

                if (token.isEmpty()) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, R.string.admin_msg_auth_required, Toast.LENGTH_SHORT).show();
                        finish();
                    });
                    return;
                }

                URL url = new URL(API_BOOKING_LIST);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setConnectTimeout(Config.CONNECT_TIMEOUT);
                conn.setReadTimeout(Config.READ_TIMEOUT);

                int responseCode = conn.getResponseCode();
                BufferedReader reader = (responseCode >= 200 && responseCode < 300)
                        ? new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"))
                        : new BufferedReader(new InputStreamReader(conn.getErrorStream(), "UTF-8"));

                StringBuilder result = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    result.append(line);
                }
                reader.close();

                String response = result.toString();
                int finalCode = responseCode;

                runOnUiThread(() -> {
                    if (finalCode != 200) {
                        showError(getString(R.string.common_error) + ": " + finalCode);
                        return;
                    }
                    try {
                        parseAndDisplay(response);
                    } catch (Exception e) {
                        showError(getString(R.string.common_error) + ": " + e.getMessage());
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(this, getString(R.string.auth_msg_network_error) + ": " + e.getMessage(),
                                Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void parseAndDisplay(String response) throws Exception {
        String cleanResponse = cleanJson(response);
        JSONObject json = new JSONObject(cleanResponse);

        if (!json.optBoolean("success", false)) {
            showError(json.optString("message", getString(R.string.common_error)));
            return;
        }

        JSONArray bookings = json.optJSONArray("bookings");
        if (bookings == null || bookings.length() == 0) {
            showError(getString(R.string.booking_msg_no_bookings));
            return;
        }

        tvBookingsCount.setText(getString(R.string.bookings_title) + " (" + bookings.length() + ")");

        bookingsContainer.removeAllViews();
        for (int i = 0; i < bookings.length(); i++) {
            try {
                createBookingCard(bookings.getJSONObject(i));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void createBookingCard(JSONObject booking) throws Exception {
        int bookingId = booking.optInt("id", 0);

        String hallName = getString(R.string.detail_label_no_name);
        JSONObject hall = booking.optJSONObject("hall");
        if (hall != null) {
            hallName = hall.optString("name", getString(R.string.detail_label_no_name));
        }


        String customerPhone = booking.optString("customer_phone", "");



        if (customerPhone.isEmpty()) {
            // Проверяем вложенный объект user
            JSONObject user = booking.optJSONObject("user");
            if (user != null) {
                customerPhone = user.optString("phone", "");
            }
        }
        if (customerPhone.isEmpty()) {
            customerPhone = getString(R.string.detail_label_no_name);
        }

        String date = formatDate(booking.optString("date", ""));
        String startTime = booking.optString("start_time", "");
        String endTime = booking.optString("end_time", "");

        // Инфлейтим карточку из XML
        View cardView = getLayoutInflater().inflate(R.layout.card_booking, bookingsContainer, false);

        TextView tvHallName = cardView.findViewById(R.id.tvHallName);
        TextView tvCustomerPhone = cardView.findViewById(R.id.tvCustomerPhone);
        TextView tvBookingDate = cardView.findViewById(R.id.tvBookingDate);
        TextView tvBookingTime = cardView.findViewById(R.id.tvBookingTime);
        Button btnDelete = cardView.findViewById(R.id.btnDelete);

        tvHallName.setText(hallName);
        tvCustomerPhone.setText(customerPhone);
        tvBookingDate.setText(date);
        tvBookingTime.setText(startTime + "-" + endTime);

        btnDelete.setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle(R.string.booking_btn_delete)
                        .setMessage(R.string.booking_msg_delete_confirm)
                        .setPositiveButton(R.string.booking_btn_delete, (dialog, which) ->
                                deleteBooking(bookingId))
                        .setNegativeButton(R.string.common_no, null)
                        .show()
        );

        bookingsContainer.addView(cardView);
    }

    private void deleteBooking(int bookingId) {
        new Thread(() -> {
            try {
                SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
                String token = prefs.getString(KEY_TOKEN, "");

                if (token.isEmpty()) {
                    runOnUiThread(() ->
                            Toast.makeText(this, R.string.admin_msg_auth_required, Toast.LENGTH_SHORT).show());
                    return;
                }

                URL url = new URL(API_BOOKING_DELETE_BASE + bookingId);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("DELETE");
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setConnectTimeout(Config.CONNECT_TIMEOUT);
                conn.setReadTimeout(Config.READ_TIMEOUT);

                int responseCode = conn.getResponseCode();
                BufferedReader reader = (responseCode >= 200 && responseCode < 300)
                        ? new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"))
                        : new BufferedReader(new InputStreamReader(conn.getErrorStream(), "UTF-8"));

                StringBuilder result = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    result.append(line);
                }
                reader.close();

                String response = result.toString();
                int finalCode = responseCode;

                runOnUiThread(() -> {
                    if (finalCode == 200) {
                        Toast.makeText(this, R.string.booking_msg_deleted, Toast.LENGTH_SHORT).show();
                        loadBookings();
                    } else {
                        try {
                            String cleanResponse = cleanJson(response);
                            JSONObject errorJson = new JSONObject(cleanResponse);
                            String message = errorJson.optString("error",
                                    errorJson.optString("message", getString(R.string.common_error)));
                            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                        } catch (Exception e) {
                            Toast.makeText(this, getString(R.string.common_error) + ": " + finalCode,
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(this, getString(R.string.auth_msg_network_error) + ": " + e.getMessage(),
                                Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void showError(String msg) {
        TextView tv = new TextView(this);
        tv.setText("⚠️ " + msg);
        tv.setPadding(16, 16, 16, 16);
        tv.setTextColor(0xFFD32F2F);
        tv.setTextSize(15);
        bookingsContainer.addView(tv);
    }

    private String formatDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return "";
        try {
            String[] parts = dateStr.split("-");
            if (parts.length == 3) {
                return parts[2] + "." + parts[1] + "." + parts[0];
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return dateStr;
    }

    private String cleanJson(String response) {
        if (response == null) return "{}";
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        return (start >= 0 && end > start) ? response.substring(start, end + 1) : response;
    }
}