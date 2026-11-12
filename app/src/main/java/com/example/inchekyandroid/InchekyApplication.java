package com.example.inchekyandroid;

import android.app.Application;

import com.example.inchekyandroid.data.remote.RetrofitClient;

public class InchekyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        RetrofitClient.initialize(this);
    }
}
