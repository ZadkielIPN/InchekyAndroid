package com.example.inchekyandroid.ui.home;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.inchekyandroid.data.repository.ApiRepository;
import com.example.inchekyandroid.data.repository.AuthRepository;
import com.google.gson.JsonElement;

public class AppViewModel extends ViewModel {
    private final ApiRepository apiRepository;
    private final AuthRepository authRepository;
    private final MutableLiveData<RemoteResult> result = new MutableLiveData<>();

    public AppViewModel() {
        this(new ApiRepository(), new AuthRepository());
    }

    AppViewModel(ApiRepository apiRepository, AuthRepository authRepository) {
        this.apiRepository = apiRepository;
        this.authRepository = authRepository;
    }

    public LiveData<RemoteResult> getResult() {
        return result;
    }

    public void list(String endpoint, int page, String query) {
        request("list", endpoint, page, callback -> apiRepository.list(endpoint, page, 10, query, callback));
    }

    public void detail(String endpoint, long id) {
        String path = endpoint + "/" + id;
        request("detail", endpoint, 0, callback -> apiRepository.get(path, callback));
    }

    public void save(String endpoint, long id, JsonElement body) {
        if (id > 0) {
            request("save", endpoint, 0, callback -> apiRepository.update(endpoint + "/" + id, body, callback));
        } else {
            request("save", endpoint, 0, callback -> apiRepository.create(endpoint, body, callback));
        }
    }

    public void delete(String endpoint, long id) {
        request("delete", endpoint, 0, callback -> apiRepository.delete(endpoint + "/" + id, callback));
    }

    public void register(JsonElement body) {
        request("register", "auth/register", 0, callback -> authRepository.register(body,
                new AuthRepository.DataCallback() {
                    @Override
                    public void onSuccess(JsonElement value) {
                        result.postValue(new RemoteResult("register", "auth/register", 0, value, null, 200));
                    }

                    @Override
                    public void onError(String message, int statusCode) {
                        result.postValue(new RemoteResult("register", "auth/register", 0, null, message, statusCode));
                    }
                }));
    }

    public void profile() {
        request("profile", "auth/me", 0, callback -> authRepository.profile(new AuthRepository.DataCallback() {
            @Override
            public void onSuccess(JsonElement value) {
                result.postValue(new RemoteResult("profile", "auth/me", 0, value, null, 200));
            }

            @Override
            public void onError(String message, int statusCode) {
                result.postValue(new RemoteResult("profile", "auth/me", 0, null, message, statusCode));
            }
        }));
    }

    public void updateProfile(JsonElement body) {
        authRepository.updateProfile(body, new AuthRepository.DataCallback() {
            @Override
            public void onSuccess(JsonElement value) {
                result.postValue(new RemoteResult("updateProfile", "auth/me", 0, value, null, 200));
            }

            @Override
            public void onError(String message, int statusCode) {
                result.postValue(new RemoteResult("updateProfile", "auth/me", 0, null, message, statusCode));
            }
        });
    }

    public void logout() {
        authRepository.logout(new AuthRepository.DataCallback() {
            @Override
            public void onSuccess(JsonElement value) {
                result.postValue(new RemoteResult("logout", "auth/logout", 0, value, null, 200));
            }

            @Override
            public void onError(String message, int statusCode) {
                result.postValue(new RemoteResult("logout", "auth/logout", 0, null, message, statusCode));
            }
        });
    }

    private void request(String operation, String endpoint, int page, Request request) {
        request.execute(new ApiRepository.ResultCallback() {
            @Override
            public void onSuccess(JsonElement value) {
                result.postValue(new RemoteResult(operation, endpoint, page, value, null, 200));
            }

            @Override
            public void onError(String message, int statusCode) {
                result.postValue(new RemoteResult(operation, endpoint, page, null, message, statusCode));
            }
        });
    }

    private interface Request {
        void execute(ApiRepository.ResultCallback callback);
    }
}
