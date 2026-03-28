package com.example.saunapril;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class RegistrationActivity extends AppCompatActivity {

    private EditText etPhone, etPassword, etPasswordConfirm;
    private Button btnRegister;
    private TextView tvBack;
    private ProgressBar progressBar;

    // Константы из Config
    private static final String API_URL = Config.API_REGISTER;
    private static final String PREF_NAME = Config.PREF_NAME;
    private static final String KEY_TOKEN = Config.PREF_KEY_TOKEN;
    private static final String KEY_USER = Config.PREF_KEY_USER;
    private static final String KEY_ROLE = Config.PREF_KEY_ROLE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_registration);

        etPhone = findViewById(R.id.et_phone);
        etPassword = findViewById(R.id.et_password);
        etPasswordConfirm = findViewById(R.id.et_password_confirm);
        btnRegister = findViewById(R.id.btn_register);
        tvBack = findViewById(R.id.tv_back);
        progressBar = findViewById(R.id.progress_bar);

        tvBack.setOnClickListener(v -> finish());

        btnRegister.setOnClickListener(v -> {
            String phone = etPhone.getText().toString().trim();
            String password = etPassword.getText().toString().trim();
            String passwordConfirm = etPasswordConfirm.getText().toString().trim();

            if (phone.isEmpty() || password.isEmpty() || passwordConfirm.isEmpty()) {
                Toast.makeText(this, R.string.auth_msg_fill_fields, Toast.LENGTH_SHORT).show();
                return;
            }

            if (!password.equals(passwordConfirm)) {
                Toast.makeText(this, R.string.reg_msg_passwords_mismatch, Toast.LENGTH_SHORT).show();
                return;
            }

            if (password.length() < 6) {
                Toast.makeText(this, R.string.reg_msg_password_short, Toast.LENGTH_SHORT).show();
                return;
            }

            register(phone, password);
        });
    }

    private void register(final String phone, final String password) {
        progressBar.setVisibility(View.VISIBLE);
        btnRegister.setEnabled(false);

        new Thread(() -> {
            try {
                URL url = new URL(API_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setConnectTimeout(Config.CONNECT_TIMEOUT);
                conn.setReadTimeout(Config.READ_TIMEOUT);
                conn.setDoOutput(true);

                JSONObject json = new JSONObject();
                json.put("phone", phone);
                json.put("password", password);
                String jsonInputString = json.toString();

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = jsonInputString.getBytes("UTF-8");
                    os.write(input, 0, input.length);
                }

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

                final String response = result.toString();

                new Handler(Looper.getMainLooper()).post(() -> {
                    progressBar.setVisibility(View.GONE);
                    btnRegister.setEnabled(true);

                    try {
                        JSONObject jsonResult = new JSONObject(response);
                        boolean success = jsonResult.getBoolean("success");

                        if (success) {
                            String token = jsonResult.getString("token");
                            JSONObject user = jsonResult.getJSONObject("user");
                            String userData = user.toString();
                            String userRole = user.optString("role", "");

                            SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
                            prefs.edit()
                                    .putString(KEY_TOKEN, token)
                                    .putString(KEY_USER, userData)
                                    .putString(KEY_ROLE, userRole)
                                    .apply();

                            Toast.makeText(RegistrationActivity.this, R.string.reg_msg_success, Toast.LENGTH_SHORT).show();
                            finish();
                        } else {
                            String message = jsonResult.optString("message", getString(R.string.reg_msg_error));
                            Toast.makeText(RegistrationActivity.this, message, Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        Toast.makeText(RegistrationActivity.this,
                                getString(R.string.common_error) + ": " + e.getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });

            } catch (final Exception e) {
                e.printStackTrace();
                new Handler(Looper.getMainLooper()).post(() -> {
                    progressBar.setVisibility(View.GONE);
                    btnRegister.setEnabled(true);
                    Toast.makeText(RegistrationActivity.this,
                            getString(R.string.auth_msg_network_error) + ": " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }
}