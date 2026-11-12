package com.example.inchekyandroid.data.repository;

import com.example.inchekyandroid.data.model.LoginRequest;
import com.example.inchekyandroid.data.model.UsuarioSession;
import com.example.inchekyandroid.data.remote.AuthApi;
import com.example.inchekyandroid.data.remote.RetrofitClient;
import com.google.gson.JsonElement;

import java.io.IOException;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AuthRepository {
    public interface LoginCallback {
        void onSuccess(UsuarioSession user);

        void onError(String message);
    }

    public interface DataCallback {
        void onSuccess(JsonElement value);
        void onError(String message, int statusCode);
    }

    private final AuthApi authApi;

    public AuthRepository() {
        this(RetrofitClient.getAuthApi());
    }

    AuthRepository(AuthApi authApi) {
        this.authApi = authApi;
    }

    public void register(JsonElement request, DataCallback callback) {
        enqueueData(authApi.register(request), callback, false);
    }

    public void profile(DataCallback callback) {
        enqueueData(authApi.profile(), callback, false);
    }

    public void updateProfile(JsonElement request, DataCallback callback) {
        enqueueData(authApi.updateProfile(request), callback, false);
    }

    public void logout(DataCallback callback) {
        enqueueData(authApi.logout(new com.google.gson.JsonObject()), callback, true);
    }

    public void login(String email, String password, LoginCallback callback) {
        authApi.login(new LoginRequest(email, password)).enqueue(new Callback<UsuarioSession>() {
            @Override
            public void onResponse(Call<UsuarioSession> call, Response<UsuarioSession> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                    return;
                }

                if (response.code() == 401 || response.code() == 400) {
                    callback.onError("Correo o contraseña incorrectos.");
                } else if (response.code() == 404) {
                    callback.onError("No se encontró /api/auth/login. Verifica el backend.");
                } else {
                    callback.onError(readError(response.errorBody(), response.code()));
                }
            }

            @Override
            public void onFailure(Call<UsuarioSession> call, Throwable error) {
                if (error instanceof IOException) {
                    callback.onError("No se pudo conectar con Spring Boot. Verifica que esté activo en el puerto 8080.");
                } else {
                    callback.onError("Ocurrió un error al procesar el inicio de sesión.");
                }
            }
        });
    }

    private static void enqueueData(Call<JsonElement> call, DataCallback callback, boolean clearOnFailure) {
        call.enqueue(new Callback<JsonElement>() {
            @Override
            public void onResponse(Call<JsonElement> call, Response<JsonElement> response) {
                if (clearOnFailure) RetrofitClient.clearSession();
                if (response.isSuccessful()) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError(readError(response.errorBody(), response.code()), response.code());
                }
            }

            @Override
            public void onFailure(Call<JsonElement> call, Throwable error) {
                if (clearOnFailure) RetrofitClient.clearSession();
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
                    com.google.gson.JsonElement parsed = com.google.gson.JsonParser.parseString(text);
                    if (parsed.isJsonObject()) {
                        com.google.gson.JsonElement message = parsed.getAsJsonObject().get("message");
                        if (message != null && !message.isJsonNull()) return message.getAsString();
                    }
                    return text;
                }
            } catch (IOException | IllegalStateException ignored) {
                // Keep the explicit HTTP fallback for an unreadable error response.
            }
        }
        return "No se pudo iniciar sesión (HTTP " + statusCode + ").";
    }
}
