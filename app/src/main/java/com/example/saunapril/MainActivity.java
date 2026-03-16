package com.example.saunapril;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;

public class MainActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        initMenu();


        EditText etSearch = findViewById(R.id.etSearch);
        if (etSearch != null) {
            etSearch.setOnFocusChangeListener((v, hasFocus) -> {
                if (!hasFocus) {
                    String query = etSearch.getText().toString().trim();
                    if (!query.isEmpty()) {
                        Toast.makeText(this, "Поиск: " + query, Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
    }
}