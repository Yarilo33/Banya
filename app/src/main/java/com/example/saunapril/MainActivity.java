package com.example.saunapril;
import android.os.Bundle;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;

public class MainActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        drawerLayout = findViewById(R.id.drawerLayout);
        NavigationView navigationView = findViewById(R.id.navigationView);

        findViewById(R.id.btnMenu).setOnClickListener(v ->
                drawerLayout.openDrawer(GravityCompat.END)
        );

        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_login) {
                Toast.makeText(this, "Войти/Выйти", Toast.LENGTH_SHORT).show();
            } else if (id == R.id.nav_edit_halls) {
                Toast.makeText(this, "Редактирование залов", Toast.LENGTH_SHORT).show();
            } else if (id == R.id.nav_edit_bookings) {
                Toast.makeText(this, "Редактирование броней", Toast.LENGTH_SHORT).show();
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
}