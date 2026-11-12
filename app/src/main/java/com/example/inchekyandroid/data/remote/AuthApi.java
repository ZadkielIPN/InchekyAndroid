package com.example.inchekyandroid.data.remote;

import com.example.inchekyandroid.data.model.LoginRequest;
import com.example.inchekyandroid.data.model.RegisterRequest;
import com.example.inchekyandroid.data.model.UsuarioSession;
import com.example.inchekyandroid.data.model.UsuarioProfileRequest;
import com.example.inchekyandroid.data.model.UsuarioResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;

public interface AuthApi {
    @POST("auth/login")
    Call<UsuarioSession> login(@Body LoginRequest request);

    @POST("auth/register")
    Call<UsuarioSession> register(@Body RegisterRequest request);

    @GET("auth/me")
    Call<UsuarioResponse> currentUser();

    @PUT("auth/me")
    Call<UsuarioResponse> updateProfile(@Body UsuarioProfileRequest request);

    @POST("auth/logout")
    Call<Void> logout();
}
