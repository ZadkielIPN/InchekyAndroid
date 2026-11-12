package com.example.inchekyandroid;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.example.inchekyandroid.ui.AppController;

public class MainActivity extends AppCompatActivity {
    private AppController appController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        appController = new AppController(this);
        appController.initialize();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (appController == null || !appController.handleBackPressed()) {
            super.onBackPressed();
        }
    }
}
