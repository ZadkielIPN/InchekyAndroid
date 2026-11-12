package com.example.inchekyandroid.ui.home;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.inchekyandroid.data.model.UsuarioResponse;
import com.example.inchekyandroid.data.repository.SessionRepository;

public class DashboardViewModel extends ViewModel {
    private final SessionRepository repository = new SessionRepository();
    private final MutableLiveData<UsuarioResponse> user = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>("");
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);

    public LiveData<UsuarioResponse> getUser() {
        return user;
    }

    public LiveData<String> getError() {
        return error;
    }

    public LiveData<Boolean> getLoading() {
        return loading;
    }

    public void loadUser() {
        loading.setValue(true);
        repository.currentUser(new SessionRepository.SessionCallback() {
            @Override
            public void onSuccess(UsuarioResponse currentUser) {
                loading.postValue(false);
                user.postValue(currentUser);
            }

            @Override
            public void onError(String message) {
                loading.postValue(false);
                error.postValue(message);
            }
        });
    }

    public void logout(SessionRepository.LogoutCallback callback) {
        loading.setValue(true);
        repository.logout(message -> {
            loading.postValue(false);
            callback.onComplete(message);
        });
    }
}
