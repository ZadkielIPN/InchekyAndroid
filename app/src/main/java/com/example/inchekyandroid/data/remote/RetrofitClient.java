package com.example.inchekyandroid.data.remote;

import android.content.Context;

import com.example.inchekyandroid.BuildConfig;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import okhttp3.logging.HttpLoggingInterceptor;

public final class RetrofitClient {
    private static final String BASE_URL = BuildConfig.API_BASE_URL;
    private static AuthApi authApi;
    private static BackendApi backendApi;
    private static SessionCookieJar cookieJar;

    private RetrofitClient() {
    }

    public static synchronized void initialize(Context context) {
        if (authApi != null) {
            return;
        }

        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);
        cookieJar = new SessionCookieJar(context.getApplicationContext()
                .getSharedPreferences("incheky-session", Context.MODE_PRIVATE));
        OkHttpClient client = new OkHttpClient.Builder()
                .cookieJar(cookieJar)
                .addInterceptor(logging)
                .build();
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        authApi = retrofit.create(AuthApi.class);
        backendApi = retrofit.create(BackendApi.class);
    }

    public static synchronized AuthApi getAuthApi() {
        if (authApi == null) {
            throw new IllegalStateException("RetrofitClient.initialize must be called first.");
        }
        return authApi;
    }

    public static synchronized BackendApi getBackendApi() {
        if (backendApi == null) {
            throw new IllegalStateException("RetrofitClient.initialize must be called first.");
        }
        return backendApi;
    }

    public static String getApiBaseUrl() {
        return BASE_URL;
    }

    public static synchronized boolean hasSessionCookie() {
        return cookieJar != null && cookieJar.hasSessionCookie();
    }

    public static synchronized void clearSession() {
        if (cookieJar != null) {
            cookieJar.clear();
        }
    }
}
