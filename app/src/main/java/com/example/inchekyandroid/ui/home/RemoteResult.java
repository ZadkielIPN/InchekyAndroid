package com.example.inchekyandroid.ui.home;

import com.google.gson.JsonElement;

public final class RemoteResult {
    public final String operation;
    public final String endpoint;
    public final int page;
    public final JsonElement data;
    public final String error;
    public final int statusCode;

    public RemoteResult(String operation, String endpoint, int page, JsonElement data,
                        String error, int statusCode) {
        this.operation = operation;
        this.endpoint = endpoint;
        this.page = page;
        this.data = data;
        this.error = error;
        this.statusCode = statusCode;
    }
}
