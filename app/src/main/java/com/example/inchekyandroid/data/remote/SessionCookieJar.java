package com.example.inchekyandroid.data.remote;

import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;

public final class SessionCookieJar implements CookieJar {
    private static final String COOKIE_KEY = "cookies";

    private final SharedPreferences preferences;
    private final Gson gson = new Gson();
    private final Map<String, Cookie> cookies = new HashMap<>();

    public SessionCookieJar(SharedPreferences preferences) {
        this.preferences = preferences;
        String stored = preferences.getString(COOKIE_KEY, null);
        if (stored == null) {
            return;
        }

        Type type = new TypeToken<List<StoredCookie>>() { }.getType();
        List<StoredCookie> saved = gson.fromJson(stored, type);
        if (saved == null) {
            return;
        }
        for (StoredCookie item : saved) {
            Cookie cookie = item.toCookie();
            if (cookie != null && cookie.expiresAt() > System.currentTimeMillis()) {
                cookies.put(key(cookie), cookie);
            }
        }
    }

    @Override
    public synchronized void saveFromResponse(HttpUrl url, List<Cookie> received) {
        for (Cookie cookie : received) {
            String cookieKey = key(cookie);
            if (cookie.expiresAt() <= System.currentTimeMillis()) {
                cookies.remove(cookieKey);
            } else {
                cookies.put(cookieKey, cookie);
            }
        }
        persist();
    }

    @Override
    public synchronized List<Cookie> loadForRequest(HttpUrl url) {
        List<Cookie> matching = new ArrayList<>();
        long now = System.currentTimeMillis();
        cookies.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
        for (Cookie cookie : cookies.values()) {
            if (cookie.matches(url)) {
                matching.add(cookie);
            }
        }
        return matching;
    }

    public synchronized void clear() {
        cookies.clear();
        preferences.edit().remove(COOKIE_KEY).apply();
    }

    public synchronized boolean hasSessionCookie() {
        long now = System.currentTimeMillis();
        cookies.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
        for (Cookie cookie : cookies.values()) {
            if ("JSESSIONID".equalsIgnoreCase(cookie.name())) {
                return true;
            }
        }
        return false;
    }

    private void persist() {
        List<StoredCookie> stored = new ArrayList<>();
        for (Cookie cookie : cookies.values()) {
            stored.add(new StoredCookie(cookie));
        }
        preferences.edit().putString(COOKIE_KEY, gson.toJson(stored)).apply();
    }

    private static String key(Cookie cookie) {
        return cookie.name() + "|" + cookie.domain() + "|" + cookie.path();
    }

    private static final class StoredCookie {
        String name;
        String value;
        String domain;
        String path;
        long expiresAt;
        boolean secure;
        boolean httpOnly;
        boolean hostOnly;

        StoredCookie(Cookie cookie) {
            name = cookie.name();
            value = cookie.value();
            domain = cookie.domain();
            path = cookie.path();
            expiresAt = cookie.expiresAt();
            secure = cookie.secure();
            httpOnly = cookie.httpOnly();
            hostOnly = cookie.hostOnly();
        }

        Cookie toCookie() {
            Cookie.Builder builder = new Cookie.Builder()
                    .name(name)
                    .value(value)
                    .path(path)
                    .expiresAt(expiresAt);
            if (hostOnly) {
                builder.hostOnlyDomain(domain);
            } else {
                builder.domain(domain);
            }
            if (secure) {
                builder.secure();
            }
            if (httpOnly) {
                builder.httpOnly();
            }
            return builder.build();
        }
    }
}
