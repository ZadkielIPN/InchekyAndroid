package com.example.inchekyandroid.data.remote;

import com.google.gson.JsonElement;

import java.util.Map;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.Query;
import retrofit2.http.QueryMap;
import retrofit2.http.Streaming;

public interface BackendApi {
    @GET("{path}")
    Call<JsonElement> list(
            @Path(value = "path", encoded = true) String path,
            @QueryMap Map<String, String> query
    );

    @GET("{path}/{id}")
    Call<JsonElement> get(
            @Path(value = "path", encoded = true) String path,
            @Path("id") long id
    );

    @POST("{path}")
    Call<JsonElement> create(
            @Path(value = "path", encoded = true) String path,
            @Body JsonElement request
    );

    @PUT("{path}/{id}")
    Call<JsonElement> update(
            @Path(value = "path", encoded = true) String path,
            @Path("id") long id,
            @Body JsonElement request
    );

    @DELETE("{path}/{id}")
    Call<Void> delete(
            @Path(value = "path", encoded = true) String path,
            @Path("id") long id
    );

    @Multipart
    @POST("productos")
    Call<JsonElement> createProduct(
            @Part("producto") RequestBody product,
            @Part MultipartBody.Part file
    );

    @Streaming
    @GET("ventas/{id}/ticket")
    Call<ResponseBody> downloadTicket(@Path("id") long id);

    @POST("emails/ticket/{id}")
    Call<ResponseBody> emailTicket(
            @Path("id") long saleId,
            @Query("emailDestino") String recipient
    );
}
