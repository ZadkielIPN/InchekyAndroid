package com.example.inchekyandroid.ui.home;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.inchekyandroid.data.repository.ApiRepository;
import com.google.gson.JsonElement;

import java.io.File;

public class ResourceViewModel extends ViewModel {
    private final ApiRepository repository = new ApiRepository();
    private final MutableLiveData<JsonElement> records = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>("");

    public LiveData<JsonElement> getRecords() {
        return records;
    }

    public LiveData<Boolean> getLoading() {
        return loading;
    }

    public LiveData<String> getError() {
        return error;
    }

    public void load(String path, int page, int size, String query) {
        loading.setValue(true);
        error.setValue("");
        repository.list(path, page, size, query, new ApiRepository.ResultCallback<JsonElement>() {
            @Override
            public void onSuccess(JsonElement result) {
                loading.postValue(false);
                records.postValue(result);
            }

            @Override
            public void onError(String message) {
                loading.postValue(false);
                error.postValue(message);
            }
        });
    }

    public void get(String path, long id, ApiRepository.ResultCallback<JsonElement> callback) {
        repository.get(path, id, callback);
    }

    public void listOptions(
            String path,
            ApiRepository.ResultCallback<JsonElement> callback
    ) {
        repository.list(path, 0, 200, null, callback);
    }

    public void listOptions(
            String path,
            int page,
            int size,
            ApiRepository.ResultCallback<JsonElement> callback
    ) {
        repository.list(path, page, size, null, callback);
    }

    public void create(
            String path,
            JsonElement request,
            ApiRepository.ResultCallback<JsonElement> callback
    ) {
        repository.create(path, request, callback);
    }

    public void createProduct(
            JsonElement request,
            File image,
            String imageMimeType,
            ApiRepository.ResultCallback<JsonElement> callback
    ) {
        repository.createProduct(request, image, imageMimeType, callback);
    }

    public void update(
            String path,
            long id,
            JsonElement request,
            ApiRepository.ResultCallback<JsonElement> callback
    ) {
        repository.update(path, id, request, callback);
    }

    public void delete(String path, long id, ApiRepository.ResultCallback<Void> callback) {
        repository.delete(path, id, callback);
    }

    public void emailTicket(
            long saleId,
            String recipient,
            ApiRepository.ResultCallback<String> callback
    ) {
        repository.emailTicket(saleId, recipient, callback);
    }

    public void downloadTicket(
            long saleId,
            File destination,
            ApiRepository.ResultCallback<File> callback
    ) {
        repository.downloadTicket(saleId, destination, callback);
    }
}
