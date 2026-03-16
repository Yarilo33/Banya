package com.example.saunapril;

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

public class AuthActivity extends AppCompatActivity {

    private EditText etPhone, etPassword;
    private Button btnLogin, btnRegister;
    private TextView tvBack;
    private ProgressBar progressBar;

    private static final String API_URL = "http://10.51.185.164/api/login.php";
    private static final String PREF_NAME = "auth_prefs";
    private static final String KEY_TOKEN = "jwt_token";
    private static final String KEY_USER = "user_data";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth);

        // Привязываем view по ID из layout
        etPhone = findViewById(R.id.et_phone);
        etPassword = findViewById(R.id.et_password);
        btnLogin = findViewById(R.id.btn_login);
        btnRegister = findViewById(R.id.btn_register);
        tvBack = findViewById(R.id.tv_back);
        progressBar = findViewById(R.id.progress_bar);

        tvBack.setOnClickListener(v -> finish());

        btnRegister.setOnClickListener(v ->
                Toast.makeText(this, "Регистрация пока недоступна", Toast.LENGTH_SHORT).show()
        );

        btnLogin.setOnClickListener(v -> {
            String phone = etPhone.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            if (phone.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Заполните все поля", Toast.LENGTH_SHORT).show();
                return;
            }

            login(phone, password);
        });
    }

    private void login(final String phone, final String password) {
        progressBar.setVisibility(View.VISIBLE);
        btnLogin.setEnabled(false);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    URL url = new URL(API_URL);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(10000);
                    conn.setDoOutput(true);


                    JSONObject json = new JSONObject();
                    json.put("phone", phone);
                    json.put("password", password);
                    String jsonInputString = json.toString();


                    OutputStream os = conn.getOutputStream();
                    byte[] input = jsonInputString.getBytes("UTF-8");
                    os.write(input, 0, input.length);
                    os.flush();
                    os.close();


                    int responseCode = conn.getResponseCode();
                    BufferedReader reader;
                    if (responseCode >= 200 && responseCode < 300) {
                        reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                    } else {
                        reader = new BufferedReader(new InputStreamReader(conn.getErrorStream(), "UTF-8"));
                    }

                    StringBuilder result = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        result.append(line);
                    }
                    reader.close();

                    final String response = result.toString();


                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            progressBar.setVisibility(View.GONE);
                            btnLogin.setEnabled(true);

                            try {
                                JSONObject jsonResult = new JSONObject(response);
                                boolean success = jsonResult.getBoolean("success");

                                if (success) {
                                    String token = jsonResult.getString("token");
                                    String userData = jsonResult.getJSONObject("user").toString();

                                    // Сохранение токена
                                    SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
                                    prefs.edit()
                                            .putString(KEY_TOKEN, token)
                                            .putString(KEY_USER, userData)
                                            .apply();

                                    Toast.makeText(AuthActivity.this, "Вход выполнен успешно", Toast.LENGTH_SHORT).show();
                                    finish();
                                } else {
                                    String message = jsonResult.optString("message", "Ошибка авторизации");
                                    Toast.makeText(AuthActivity.this, message, Toast.LENGTH_SHORT).show();
                                }
                            } catch (Exception e) {
                                Toast.makeText(AuthActivity.this, "Ошибка ответа: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            }
                        }
                    });

                } catch (final Exception e) {
                    e.printStackTrace();
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            progressBar.setVisibility(View.GONE);
                            btnLogin.setEnabled(true);
                            Toast.makeText(AuthActivity.this, "Ошибка сети: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        }).start();
    }
}