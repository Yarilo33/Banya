package com.example.saunapril;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import com.google.android.material.navigation.NavigationView;

public class MainActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout;
    private static final String PREF_NAME = "auth_prefs";
    private static final String KEY_TOKEN = "jwt_token";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        drawerLayout = findViewById(R.id.drawerLayout);
        NavigationView navigationView = findViewById(R.id.navigationView);

        // Открытие меню по кнопке
        findViewById(R.id.btnMenu).setOnClickListener(v ->
                drawerLayout.openDrawer(GravityCompat.END)
        );

        // Обработчик пунктов меню
        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_login) {
                handleLoginClick();
            } else if (id == R.id.nav_edit_halls) {
                // Проверка авторизации перед доступом к админке
                if (isAuthenticated()) {
                    Toast.makeText(this, "Редактирование залов", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Сначала авторизуйтесь", Toast.LENGTH_SHORT).show();
                    handleLoginClick();
                }
            } else if (id == R.id.nav_edit_bookings) {
                if (isAuthenticated()) {
                    Toast.makeText(this, "Редактирование броней", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Сначала авторизуйтесь", Toast.LENGTH_SHORT).show();
                    handleLoginClick();
                }
            }

            drawerLayout.closeDrawer(GravityCompat.END);
            return true;
        });


        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.END)) {
                    drawerLayout.closeDrawer(GravityCompat.END);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    private void handleLoginClick() {
        if (isAuthenticated()) {
            // Пользователь уже авторизован — показываем выход
            showLogoutDialog();
        } else {
            // Открываем экран авторизации
            startActivity(new Intent(this, AuthActivity.class));
        }
    }


    private boolean isAuthenticated() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        String token = prefs.getString(KEY_TOKEN, null);
        return token != null && !token.isEmpty();
    }


    private void showLogoutDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Выход")
                .setMessage("Вы действительно хотите выйти из аккаунта?")
                .setPositiveButton("Выйти", (dialog, which) -> {
                    // Очищаем сохранённые данные
                    getSharedPreferences(PREF_NAME, MODE_PRIVATE)
                            .edit()
                            .clear()
                            .apply();
                    Toast.makeText(this, "Вы вышли из аккаунта", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }


    @Override
    protected void onResume() {
        super.onResume();

    }
}