package com.example.inchekyandroid.data.remote;

import com.example.inchekyandroid.data.model.LoginRequest;
import com.example.inchekyandroid.data.model.UsuarioSession;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface AuthApi {
    @POST("auth/login")
    Call<UsuarioSession> login(@Body LoginRequest request);
}
