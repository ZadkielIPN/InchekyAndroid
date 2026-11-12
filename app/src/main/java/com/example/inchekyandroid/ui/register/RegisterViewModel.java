package com.example.inchekyandroid.ui.register;

import android.util.Patterns;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.inchekyandroid.data.model.RegisterRequest;
import com.example.inchekyandroid.data.model.UsuarioSession;
import com.example.inchekyandroid.data.repository.AuthRepository;

public class RegisterViewModel extends ViewModel {
    private final AuthRepository authRepository;
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>("");
    private final MutableLiveData<UsuarioSession> user = new MutableLiveData<>();

    public RegisterViewModel() {
        this(new AuthRepository());
    }

    RegisterViewModel(AuthRepository authRepository) {
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

    public void register(
            String nombre,
            String apellido,
            String email,
            String password,
            String confirmation
    ) {
        String normalizedName = nombre.trim();
        String normalizedSurname = apellido.trim();
        String normalizedEmail = email.trim();
        user.setValue(null);

        if (normalizedName.isEmpty() || normalizedSurname.isEmpty()
                || normalizedEmail.isEmpty() || password.isEmpty() || confirmation.isEmpty()) {
            error.setValue("Completa todos los campos.");
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(normalizedEmail).matches()) {
            error.setValue("Ingresa un correo electrónico válido.");
            return;
        }
        if (password.length() < 8 || password.length() > 20) {
            error.setValue("La contraseña debe tener entre 8 y 20 caracteres.");
            return;
        }
        if (!password.equals(confirmation)) {
            error.setValue("Las contraseñas no coinciden.");
            return;
        }

        error.setValue("");
        loading.setValue(true);
        RegisterRequest request = new RegisterRequest(
                normalizedName,
                normalizedSurname,
                normalizedEmail,
                password,
                0,
                "BRONCE"
        );
        authRepository.register(request, new AuthRepository.AuthCallback() {
            @Override
            public void onSuccess(UsuarioSession registeredUser) {
                loading.postValue(false);
                user.postValue(registeredUser);
            }

            @Override
            public void onError(String message) {
                loading.postValue(false);
                error.postValue(message);
            }
        });
    }
}
