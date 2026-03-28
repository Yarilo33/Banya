package com.example.saunapril;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
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

    // константы из конфига
    private static final String PREF_NAME = Config.PREF_NAME;
    private static final String KEY_TOKEN = Config.PREF_KEY_TOKEN;
    private static final String KEY_ROLE = Config.PREF_KEY_ROLE;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

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

            if (id == R.id.nav_main) {
                startActivity(new Intent(this, MainActivity.class));
                drawerLayout.closeDrawer(GravityCompat.END);
                return true;
            }

            if (id == R.id.nav_login) {
                handleLoginClick();
            } else if (id == R.id.nav_edit_halls) {
                if (isAdmin()) {
                    startActivity(new Intent(this, HallsActivity.class));
                } else {
                    Toast.makeText(this, R.string.admin_msg_admin_only, Toast.LENGTH_SHORT).show();
                }
            } else if (id == R.id.nav_edit_bookings) {
                if (isAdmin()) {
                    Toast.makeText(this, R.string.nav_edit_bookings, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, R.string.admin_msg_admin_only, Toast.LENGTH_SHORT).show();
                }
            }

            drawerLayout.closeDrawer(GravityCompat.END);
            return true;
        });

        // Обработка кнопки "Назад"
        getOnBackPressedDispatcher().addCallback(this,
                new androidx.activity.OnBackPressedCallback(true) {
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

    private void handleLoginClick() {
        if (isAuthenticated()) {
            showLogoutDialog();
        } else {
            startActivity(new Intent(this, AuthActivity.class));
        }
    }

    protected boolean isAuthenticated() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        String token = prefs.getString(KEY_TOKEN, null);
        return token != null && !token.isEmpty();
    }

    protected boolean isAdmin() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        String role = prefs.getString(KEY_ROLE, "");
        return "admin".equals(role);
    }

    private void showLogoutDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.nav_logout)
                .setMessage(R.string.nav_msg_logout_confirm)
                .setPositiveButton(R.string.nav_logout, (dialog, which) -> {
                    getSharedPreferences(PREF_NAME, MODE_PRIVATE)
                            .edit()
                            .clear()
                            .apply();
                    updateMenuVisibility();
                    Toast.makeText(this, R.string.nav_msg_logged_out, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.common_no, null)
                .show();
    }
}