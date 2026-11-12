package com.example.inchekyandroid.data.repository;

import com.example.inchekyandroid.data.model.LoginRequest;
import com.example.inchekyandroid.data.model.RegisterRequest;
import com.example.inchekyandroid.data.model.UsuarioSession;
import com.example.inchekyandroid.data.remote.AuthApi;
import com.example.inchekyandroid.data.remote.RetrofitClient;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonParseException;

import java.io.IOException;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AuthRepository {
    public interface AuthCallback {
        void onSuccess(UsuarioSession user);

        void onError(String message);
    }

    private final AuthApi authApi;

    public AuthRepository() {
        this(RetrofitClient.getAuthApi());
    }

    AuthRepository(AuthApi authApi) {
        this.authApi = authApi;
    }

    public void login(String email, String password, AuthCallback callback) {
        execute(authApi.login(new LoginRequest(email, password)), callback, false);
    }

    public void register(RegisterRequest request, AuthCallback callback) {
        execute(authApi.register(request), callback, true);
    }

    private void execute(
            Call<UsuarioSession> call,
            AuthCallback callback,
            boolean registration
    ) {
        call.enqueue(new Callback<UsuarioSession>() {
            @Override
            public void onResponse(Call<UsuarioSession> call, Response<UsuarioSession> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                    return;
                }

                if (response.isSuccessful()) {
                    if (response.body() == null) {
                        callback.onError("El servidor no devolvió los datos de la sesión.");
                    }
                    return;
                }

                String serverMessage = readServerMessage(response);
                if (serverMessage != null) {
                    callback.onError(serverMessage);
                } else if (response.code() == 401 && !registration) {
                    callback.onError("Correo o contraseña incorrectos.");
                } else if (response.code() == 400 && registration) {
                    callback.onError("No se pudo crear la cuenta. Revisa los datos ingresados.");
                } else if (response.code() == 409) {
                    callback.onError("Ya existe una cuenta con ese correo electrónico.");
                } else if (response.code() == 404) {
                    callback.onError("No se encontró el endpoint de autenticación en el backend.");
                } else {
                    String action = registration ? "crear la cuenta" : "iniciar sesión";
                    callback.onError("No se pudo " + action + " (HTTP " + response.code() + ").");
                }
            }

            @Override
            public void onFailure(Call<UsuarioSession> call, Throwable error) {
                if (error instanceof IOException) {
                    callback.onError("No se pudo conectar con Spring Boot. Verifica que esté activo en el puerto 8080.");
                } else {
                    callback.onError("Ocurrió un error al procesar la solicitud de autenticación.");
                }
            }
        });
    }

    private String readServerMessage(Response<?> response) {
        if (response.errorBody() == null) {
            return null;
        }
        try {
            JsonElement body = JsonParser.parseString(response.errorBody().string());
            if (!body.isJsonObject()) {
                return null;
            }
            JsonObject object = body.getAsJsonObject();
            JsonElement message = object.get("message");
            if (message == null || !message.isJsonPrimitive()
                    || !message.getAsJsonPrimitive().isString()) {
                return null;
            }
            String value = message.getAsString().trim();
            return value.isEmpty() ? null : value;
        } catch (IOException | JsonParseException error) {
            return null;
        }
    }
}
