package com.example.inchekyandroid.data.repository;

import com.example.inchekyandroid.data.remote.AuthApi;
import com.example.inchekyandroid.data.remote.RetrofitClient;
import com.google.gson.JsonElement;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ApiRepository {
    public interface ResultCallback {
        void onSuccess(JsonElement value);
        void onError(String message, int statusCode);
    }

    private final AuthApi api;

    public ApiRepository() {
        this(RetrofitClient.getAuthApi());
    }

    ApiRepository(AuthApi api) {
        this.api = api;
    }

    public void list(String endpoint, int page, int size, String query, ResultCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("page", String.valueOf(page));
        params.put("size", String.valueOf(size));
        if (query != null && !query.trim().isEmpty()) params.put("q", query.trim());
        enqueue(api.list(endpoint, params), callback);
    }

    public void get(String path, ResultCallback callback) {
        enqueue(api.get(path), callback);
    }

    public void create(String endpoint, JsonElement body, ResultCallback callback) {
        enqueue(api.create(endpoint, body), callback);
    }

    public void update(String path, JsonElement body, ResultCallback callback) {
        enqueue(api.update(path, body), callback);
    }

    public void delete(String path, ResultCallback callback) {
        enqueue(api.delete(path), callback);
    }

    public void register(JsonElement body, ResultCallback callback) {
        enqueue(api.register(body), callback);
    }

    public void profile(ResultCallback callback) {
        enqueue(api.profile(), callback);
    }

    public void updateProfile(JsonElement body, ResultCallback callback) {
        enqueue(api.updateProfile(body), callback);
    }

    public void logout(ResultCallback callback) {
        enqueue(api.logout(new com.google.gson.JsonObject()), callback);
    }

    private static void enqueue(Call<JsonElement> call, ResultCallback callback) {
        call.enqueue(new Callback<JsonElement>() {
            @Override
            public void onResponse(Call<JsonElement> ignored, Response<JsonElement> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError(readError(response.errorBody(), response.code()), response.code());
                }
            }

            @Override
            public void onFailure(Call<JsonElement> ignored, Throwable error) {
                String message = error instanceof IOException
                        ? "No se pudo conectar con Spring Boot. Verifica que esté activo en el puerto 8080."
                        : "Ocurrió un error al procesar la solicitud.";
                callback.onError(message, 0);
            }
        });
    }

    private static String readError(ResponseBody body, int statusCode) {
        if (body != null) {
            try {
                String text = body.string();
                if (!text.trim().isEmpty()) {
                    JsonElement parsed = com.google.gson.JsonParser.parseString(text);
                    if (parsed.isJsonObject()) {
                        JsonElement message = parsed.getAsJsonObject().get("message");
                        if (message != null && !message.isJsonNull()) return message.getAsString();
                    }
                    return text;
                }
            } catch (IOException | IllegalStateException ignored) {
                // Use the status-specific message below when the response body is not readable JSON.
            }
        }
        if (statusCode == 401 || statusCode == 403) return "No tienes permiso para realizar esta acción.";
        if (statusCode == 404) return "No se encontró el recurso solicitado.";
        return "La solicitud falló (HTTP " + statusCode + ").";
    }
}
