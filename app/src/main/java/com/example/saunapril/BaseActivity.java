package com.example.saunapril;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import com.google.android.material.navigation.NavigationView;

public abstract class BaseActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private static final String PREF_NAME = "auth_prefs";
    private static final String KEY_TOKEN = "jwt_token";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    /**
     * Инициализация меню (вызывать из onCreate после setContentView)
     */
    protected void initMenu() {
        drawerLayout = findViewById(R.id.drawerLayout);
        navigationView = findViewById(R.id.navigationView);

        if (drawerLayout == null || navigationView == null) {
            return;
        }

        // Кнопка меню
        ImageButton btnMenu = findViewById(R.id.btnMenu);
        if (btnMenu != null) {
            btnMenu.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.END));
        }

        // Обработчик пунктов меню
        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_login) {
                handleLoginClick();
            } else if (id == R.id.nav_edit_halls) {
                if (isAdmin()) {
                    startActivity(new Intent(this, HallsActivity.class));
                } else {
                    Toast.makeText(this, "Только для администраторов", Toast.LENGTH_SHORT).show();
                }
            } else if (id == R.id.nav_edit_bookings) {
                if (isAdmin()) {
                    Toast.makeText(this, "Редактирование броней", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Только для администраторов", Toast.LENGTH_SHORT).show();
                }
            }

            drawerLayout.closeDrawer(GravityCompat.END);
            return true;
        });

        // Обработка кнопки "Назад"
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

    @Override
    protected void onResume() {
        super.onResume();
        updateMenuVisibility();
    }

    /**
     * Обновляет видимость пунктов меню
     */
    private void updateMenuVisibility() {
        if (navigationView == null) return;

        Menu menu = navigationView.getMenu();
        MenuItem editHallsItem = menu.findItem(R.id.nav_edit_halls);
        MenuItem editBookingsItem = menu.findItem(R.id.nav_edit_bookings);

        boolean isAdmin = isAdmin();
        if (editHallsItem != null) {
            editHallsItem.setVisible(isAdmin);
        }
        if (editBookingsItem != null) {
            editBookingsItem.setVisible(isAdmin);
        }
    }

    /**
     * Обработка клика на "Войти/Выйти"
     */
    private void handleLoginClick() {
        if (isAuthenticated()) {
            showLogoutDialog();
        } else {
            startActivity(new Intent(this, AuthActivity.class));
        }
    }

    /**
     * Проверка авторизации
     */
    protected boolean isAuthenticated() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        String token = prefs.getString(KEY_TOKEN, null);
        return token != null && !token.isEmpty();
    }

    /**
     * Проверка на админа
     */
    protected boolean isAdmin() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        String role = prefs.getString("user_role", "");
        return "admin".equals(role);
    }

    private void showLogoutDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Выход")
                .setMessage("Вы действительно хотите выйти из аккаунта?")
                .setPositiveButton("Выйти", (dialog, which) -> {
                    getSharedPreferences(PREF_NAME, MODE_PRIVATE)
                            .edit()
                            .clear()
                            .apply();
                    updateMenuVisibility();
                    Toast.makeText(this, "Вы вышли из аккаунта", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }
}