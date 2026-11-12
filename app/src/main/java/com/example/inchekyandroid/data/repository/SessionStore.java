package com.example.inchekyandroid.data.repository;

import android.content.Context;

import com.example.inchekyandroid.data.model.UsuarioSession;
import com.google.gson.Gson;

public final class SessionStore {
    private static final String PREFERENCES = "incheky-user";
    private static final String USER_KEY = "user";
    private static final Gson GSON = new Gson();

    private SessionStore() {
    }

    public static void save(Context context, UsuarioSession user) {
        context.getApplicationContext().getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit().putString(USER_KEY, GSON.toJson(user)).apply();
    }

    public static UsuarioSession load(Context context) {
        String value = context.getApplicationContext().getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getString(USER_KEY, null);
        return value == null ? null : GSON.fromJson(value, UsuarioSession.class);
    }

    public static void clear(Context context) {
        context.getApplicationContext().getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit().remove(USER_KEY).apply();
    }
}
