package com.example.inchekyandroid.data.repository;

import com.example.inchekyandroid.data.model.LoginRequest;
import com.example.inchekyandroid.data.model.UsuarioSession;
import com.example.inchekyandroid.data.remote.AuthApi;
import com.example.inchekyandroid.data.remote.RetrofitClient;

import java.io.IOException;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AuthRepository {
    public interface LoginCallback {
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
                    callback.onError("No se pudo iniciar sesión (HTTP " + response.code() + ").");
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
}
