package com.example.inchekyandroid.data.repository;

import com.example.inchekyandroid.data.model.UsuarioProfileRequest;
import com.example.inchekyandroid.data.model.UsuarioResponse;
import com.example.inchekyandroid.data.remote.RetrofitClient;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SessionRepository {
    public interface SessionCallback {
        void onSuccess(UsuarioResponse user);

        void onError(String message);
    }

    public interface LogoutCallback {
        void onComplete(String errorMessage);
    }

    public void currentUser(SessionCallback callback) {
        RetrofitClient.getAuthApi().currentUser().enqueue(new Callback<UsuarioResponse>() {
            @Override
            public void onResponse(
                    Call<UsuarioResponse> call,
                    Response<UsuarioResponse> response
            ) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    if (response.code() == 401 || response.code() == 403) {
                        RetrofitClient.clearSession();
                        callback.onError("La sesión expiró. Inicia sesión nuevamente.");
                    } else {
                        callback.onError("No se pudo validar la sesión (HTTP "
                                + response.code() + ").");
                    }
                }
            }

            @Override
            public void onFailure(Call<UsuarioResponse> call, Throwable error) {
                callback.onError("No se pudo validar la sesión con el backend.");
            }
        });
    }

    public void updateProfile(
            UsuarioProfileRequest request,
            SessionCallback callback
    ) {
        RetrofitClient.getAuthApi().updateProfile(request).enqueue(new Callback<UsuarioResponse>() {
            @Override
            public void onResponse(
                    Call<UsuarioResponse> call,
                    Response<UsuarioResponse> response
            ) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    if (response.code() == 401 || response.code() == 403) {
                        RetrofitClient.clearSession();
                    }
                    callback.onError("No se pudo actualizar el perfil (HTTP " + response.code()
                            + ").");
                }
            }

            @Override
            public void onFailure(Call<UsuarioResponse> call, Throwable error) {
                callback.onError("No se pudo conectar para actualizar el perfil.");
            }
        });
    }

    public void logout(LogoutCallback callback) {
        RetrofitClient.getAuthApi().logout().enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                RetrofitClient.clearSession();
                callback.onComplete(response.isSuccessful()
                        ? null
                        : "La sesión local se cerró, pero el backend respondió HTTP "
                                + response.code() + ".");
            }

            @Override
            public void onFailure(Call<Void> call, Throwable error) {
                RetrofitClient.clearSession();
                callback.onComplete(
                        "La sesión local se cerró, pero no se pudo contactar al backend.");
            }
        });
    }
}
