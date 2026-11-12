package com.example.inchekyandroid.data.repository;

import com.example.inchekyandroid.data.remote.BackendApi;
import com.example.inchekyandroid.data.remote.RetrofitClient;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ApiRepository {
    public interface ResultCallback<T> {
        void onSuccess(T result);

        void onError(String message);
    }

    private final BackendApi api;

    public ApiRepository() {
        this(RetrofitClient.getBackendApi());
    }

    ApiRepository(BackendApi api) {
        this.api = api;
    }

    public void list(
            String path,
            int page,
            int size,
            String query,
            ResultCallback<JsonElement> callback
    ) {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("page", String.valueOf(page));
        parameters.put("size", String.valueOf(size));
        if (query != null && !query.trim().isEmpty()) {
            parameters.put("q", query.trim());
        }
        execute(api.list(path, parameters), callback);
    }

    public void get(String path, long id, ResultCallback<JsonElement> callback) {
        execute(api.get(path, id), callback);
    }

    public void create(String path, JsonElement request, ResultCallback<JsonElement> callback) {
        execute(api.create(path, request), callback);
    }

    public void update(
            String path,
            long id,
            JsonElement request,
            ResultCallback<JsonElement> callback
    ) {
        execute(api.update(path, id, request), callback);
    }

    public void delete(String path, long id, ResultCallback<Void> callback) {
        execute(api.delete(path, id), callback);
    }

    public void createProduct(
            JsonElement request,
            File image,
            String imageMimeType,
            ResultCallback<JsonElement> callback
    ) {
        RequestBody productBody = RequestBody.create(
                MediaType.parse("application/json; charset=utf-8"),
                request.toString()
        );
        MultipartBody.Part filePart = null;
        if (image != null) {
            String mimeType = imageMimeType == null
                    ? URLConnection.guessContentTypeFromName(image.getName())
                    : imageMimeType;
            if (mimeType == null || !mimeType.startsWith("image/")) {
                callback.onError("Selecciona un archivo de imagen válido.");
                return;
            }
            RequestBody imageBody = RequestBody.create(
                    MediaType.parse(mimeType),
                    image
            );
            filePart = MultipartBody.Part.createFormData("file", image.getName(), imageBody);
        }
        execute(api.createProduct(productBody, filePart), callback);
    }

    public void emailTicket(
            long saleId,
            String recipient,
            ResultCallback<String> callback
    ) {
        execute(api.emailTicket(saleId, recipient), new ResultCallback<ResponseBody>() {
            @Override
            public void onSuccess(ResponseBody response) {
                try {
                    callback.onSuccess(response.string());
                } catch (IOException error) {
                    callback.onError("No se pudo leer la respuesta del envío de correo.");
                } finally {
                    response.close();
                }
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    public void downloadTicket(
            long saleId,
            File destination,
            ResultCallback<File> callback
    ) {
        api.downloadTicket(saleId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (!response.isSuccessful()) {
                    callback.onError(errorMessage(response));
                    return;
                }
                ResponseBody body = response.body();
                if (body == null) {
                    callback.onError("El servidor no devolvió el archivo del ticket.");
                    return;
                }
                new Thread(() -> saveTicket(body, destination, callback), "ticket-download").start();
            }

            private void saveTicket(
                    ResponseBody body,
                    File destination,
                    ResultCallback<File> callback
            ) {
                String errorMessage = null;
                try (ResponseBody closeableBody = body;
                     InputStream input = closeableBody.byteStream();
                     FileOutputStream output = new FileOutputStream(destination)) {
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        output.write(buffer, 0, count);
                    }
                } catch (IOException error) {
                    destination.delete();
                    errorMessage = "No se pudo guardar el ticket descargado.";
                }
                String resultError = errorMessage;
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (resultError == null) {
                        callback.onSuccess(destination);
                    } else {
                        callback.onError(resultError);
                    }
                });
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable error) {
                callback.onError(connectionError(error));
            }
        });
    }

    private <T> void execute(Call<T> call, ResultCallback<T> callback) {
        call.enqueue(new Callback<T>() {
            @Override
            public void onResponse(Call<T> call, Response<T> response) {
                if (response.isSuccessful()) {
                    if (response.code() == 204) {
                        callback.onSuccess(null);
                    } else if (response.body() != null) {
                        callback.onSuccess(response.body());
                    } else {
                        callback.onError("El servidor respondió sin contenido.");
                    }
                    return;
                }
                callback.onError(errorMessage(response));
            }

            @Override
            public void onFailure(Call<T> call, Throwable error) {
                callback.onError(connectionError(error));
            }
        });
    }

    private String errorMessage(Response<?> response) {
        if (response.code() == 401) {
            RetrofitClient.clearSession();
        }
        String fallback = "La solicitud no pudo completarse (HTTP " + response.code() + ").";
        ResponseBody errorBody = response.errorBody();
        if (errorBody == null) {
            return fallback;
        }
        try (ResponseBody closeableBody = errorBody) {
            JsonElement body = JsonParser.parseString(closeableBody.string());
            if (body.isJsonObject()) {
                JsonObject object = body.getAsJsonObject();
                JsonElement message = object.get("message");
                if (message != null && message.isJsonPrimitive()
                        && message.getAsJsonPrimitive().isString()) {
                    String value = message.getAsString().trim();
                    if (!value.isEmpty()) {
                        return value;
                    }
                }
            }
        } catch (IOException | JsonParseException ignored) {
            return fallback;
        }
        return fallback;
    }

    private String connectionError(Throwable error) {
        if (error instanceof IOException) {
            return "No se pudo conectar con Spring Boot. Verifica la red y que el backend esté activo.";
        }
        return "Ocurrió un error al procesar la solicitud.";
    }
}
