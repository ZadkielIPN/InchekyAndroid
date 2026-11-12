package com.example.inchekyandroid.data.remote;

import com.example.inchekyandroid.data.model.LoginRequest;
import com.example.inchekyandroid.data.model.UsuarioSession;
import com.google.gson.JsonElement;

import java.util.Map;

import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.QueryMap;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface AuthApi {
    @POST("auth/login")
    Call<UsuarioSession> login(@Body LoginRequest request);

    @POST("auth/register")
    Call<UsuarioSession> register(@Body JsonElement request);

    @GET("auth/me")
    Call<JsonElement> profile();

    @PUT("auth/me")
    Call<JsonElement> updateProfile(@Body JsonElement request);

    @POST("auth/logout")
    Call<JsonElement> logout(@Body JsonElement request);

    @GET("{path}")
    Call<JsonElement> get(@Path(value = "path", encoded = true) String path);

    @GET("{path}")
    Call<JsonElement> list(
            @Path(value = "path", encoded = true) String path,
            @QueryMap Map<String, String> query
    );

    @POST("{path}")
    Call<JsonElement> create(
            @Path(value = "path", encoded = true) String path,
            @Body JsonElement request
    );

    @PUT("{path}")
    Call<JsonElement> update(
            @Path(value = "path", encoded = true) String path,
            @Body JsonElement request
    );

    @DELETE("{path}")
    Call<JsonElement> delete(@Path(value = "path", encoded = true) String path);
}
