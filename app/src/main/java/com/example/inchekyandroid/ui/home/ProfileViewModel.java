package com.example.inchekyandroid.ui.home;

import android.util.Patterns;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.inchekyandroid.data.model.UsuarioProfileRequest;
import com.example.inchekyandroid.data.model.UsuarioResponse;
import com.example.inchekyandroid.data.repository.SessionRepository;

public class ProfileViewModel extends ViewModel {
    private final SessionRepository repository = new SessionRepository();
    private final MutableLiveData<UsuarioResponse> user = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>("");
    private final MutableLiveData<String> message = new MutableLiveData<>("");
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);

    public LiveData<UsuarioResponse> getUser() {
        return user;
    }

    public LiveData<String> getError() {
        return error;
    }

    public LiveData<String> getMessage() {
        return message;
    }

    public LiveData<Boolean> getLoading() {
        return loading;
    }

    public void loadProfile() {
        loading.setValue(true);
        repository.currentUser(new SessionRepository.SessionCallback() {
            @Override
            public void onSuccess(UsuarioResponse result) {
                loading.postValue(false);
                user.postValue(result);
            }

            @Override
            public void onError(String errorMessage) {
                loading.postValue(false);
                error.postValue(errorMessage);
            }
        });
    }

    public void updateProfile(String name, String surname, String email, String password) {
        String normalizedName = name.trim();
        String normalizedSurname = surname.trim();
        String normalizedEmail = email.trim();
        if (normalizedName.isEmpty() || normalizedSurname.isEmpty()
                || normalizedEmail.isEmpty()) {
            error.setValue("Completa nombre, apellido y correo.");
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(normalizedEmail).matches()) {
            error.setValue("Ingresa un correo electrónico válido.");
            return;
        }
        if (!password.isEmpty() && (password.length() < 8 || password.length() > 20)) {
            error.setValue("La nueva contraseña debe tener entre 8 y 20 caracteres.");
            return;
        }

        loading.setValue(true);
        error.setValue("");
        message.setValue("");
        UsuarioProfileRequest request =
                new UsuarioProfileRequest(normalizedName, normalizedSurname, normalizedEmail,
                        password);
        repository.updateProfile(request, new SessionRepository.SessionCallback() {
            @Override
            public void onSuccess(UsuarioResponse result) {
                loading.postValue(false);
                user.postValue(result);
                message.postValue("Perfil actualizado.");
            }

            @Override
            public void onError(String errorMessage) {
                loading.postValue(false);
                error.postValue(errorMessage);
            }
        });
    }
}
