package com.example.inchekyandroid.ui.login;

import android.util.Patterns;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.inchekyandroid.data.model.UsuarioSession;
import com.example.inchekyandroid.data.repository.AuthRepository;

public class LoginViewModel extends ViewModel {
    private final AuthRepository authRepository;
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>("");
    private final MutableLiveData<UsuarioSession> user = new MutableLiveData<>();

    public LoginViewModel() {
        this(new AuthRepository());
    }

    LoginViewModel(AuthRepository authRepository) {
        this.authRepository = authRepository;
    }

    public LiveData<Boolean> getLoading() {
        return loading;
    }

    public LiveData<String> getError() {
        return error;
    }

    public LiveData<UsuarioSession> getUser() {
        return user;
    }

    public void login(String email, String password) {
        String normalizedEmail = email.trim();
        if (normalizedEmail.isEmpty() || password.isEmpty()) {
            error.setValue("Ingresa tu correo y contraseña.");
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(normalizedEmail).matches()) {
            error.setValue("Ingresa un correo electrónico válido.");
            return;
        }

        error.setValue("");
        user.setValue(null);
        loading.setValue(true);
        authRepository.login(normalizedEmail, password, new AuthRepository.LoginCallback() {
            @Override
            public void onSuccess(UsuarioSession loggedInUser) {
                loading.postValue(false);
                user.postValue(loggedInUser);
            }

            @Override
            public void onError(String message) {
                loading.postValue(false);
                error.postValue(message);
            }
        });
    }
}
