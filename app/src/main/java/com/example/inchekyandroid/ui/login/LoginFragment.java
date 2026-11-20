package com.example.inchekyandroid.ui.login;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.example.inchekyandroid.R;
import com.example.inchekyandroid.data.model.UsuarioResponse;
import com.example.inchekyandroid.data.remote.RetrofitClient;
import com.example.inchekyandroid.data.repository.SessionRepository;
import com.example.inchekyandroid.databinding.ActivityMainBinding;

public class LoginFragment extends Fragment {
    private ActivityMainBinding binding;
    private LoginViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        binding = ActivityMainBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(this).get(LoginViewModel.class);
        binding.btnLogin.setOnClickListener(ignored -> submitLogin());
        binding.btnRegister.setOnClickListener(ignored ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_login_to_register));
        binding.etEmail.setOnEditorActionListener((text, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                binding.etPassword.requestFocus();
                return true;
            }
            return false;
        });
        binding.etPassword.setOnEditorActionListener((text, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE
                    || event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                submitLogin();
                return true;
            }
            return false;
        });
        binding.etEmail.addTextChangedListener(clearErrorOnChange(binding.tilEmail));
        binding.etPassword.addTextChangedListener(clearErrorOnChange(binding.tilPassword));
        applyKeyboardInsets();
        observeLoginState();
        restoreAuthenticatedSession();
    }

    private void applyKeyboardInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.authScroll, (scroll, insets) -> {
            int imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            int systemBottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            int keyboardPadding = Math.max(0, imeBottom - systemBottom);
            scroll.setPadding(
                    scroll.getPaddingLeft(),
                    scroll.getPaddingTop(),
                    scroll.getPaddingRight(),
                    keyboardPadding
            );
            return insets;
        });
        ViewCompat.requestApplyInsets(binding.authScroll);
    }

    private TextWatcher clearErrorOnChange(com.google.android.material.textfield.TextInputLayout input) {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence value, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence value, int start, int before, int count) {
                input.setError(null);
            }

            @Override
            public void afterTextChanged(Editable value) {
            }
        };
    }

    private void restoreAuthenticatedSession() {
        if (!RetrofitClient.hasSessionCookie()) {
            return;
        }
        binding.btnLogin.setEnabled(false);
        binding.btnRegister.setEnabled(false);
        binding.loginProgress.setVisibility(View.VISIBLE);
        binding.btnLogin.setText(R.string.login_loading);
        new SessionRepository().currentUser(new SessionRepository.SessionCallback() {
            @Override
            public void onSuccess(UsuarioResponse user) {
                if (isAdded()) {
                    openDashboard();
                }
            }

            @Override
            public void onError(String message) {
                if (isAdded() && binding != null) {
                    binding.btnLogin.setEnabled(true);
                    binding.btnRegister.setEnabled(true);
                    binding.loginProgress.setVisibility(View.GONE);
                    binding.btnLogin.setText(R.string.login_button);
                }
            }
        });
    }

    private void submitLogin() {
        binding.tilEmail.setError(null);
        binding.tilPassword.setError(null);
        binding.tvFeedback.setVisibility(View.GONE);
        String email = textOf(binding.etEmail.getText()).trim();
        String password = textOf(binding.etPassword.getText());
        if (email.isEmpty()) {
            binding.tilEmail.setError(getString(R.string.login_email_required));
            binding.etEmail.requestFocus();
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.setError(getString(R.string.auth_invalid_email));
            binding.etEmail.requestFocus();
            return;
        }
        if (password.isEmpty()) {
            binding.tilPassword.setError(getString(R.string.login_password_required));
            binding.etPassword.requestFocus();
            return;
        }
        if (password.length() < 8 || password.length() > 20) {
            binding.tilPassword.setError(getString(R.string.auth_password_length));
            binding.etPassword.requestFocus();
            return;
        }
        viewModel.login(
                email,
                password
        );
    }

    private void observeLoginState() {
        viewModel.getLoading().observe(getViewLifecycleOwner(), loading -> {
            binding.btnLogin.setEnabled(!loading);
            binding.btnRegister.setEnabled(!loading);
            binding.btnLogin.setText(loading ? R.string.login_loading : R.string.login_button);
            binding.loginProgress.setVisibility(loading ? View.VISIBLE : View.GONE);
        });
        viewModel.getError().observe(getViewLifecycleOwner(), error -> {
            if (error == null || error.isEmpty()) {
                return;
            }
            binding.tvFeedback.setText(error);
            binding.tvFeedback.setVisibility(View.VISIBLE);
        });
        viewModel.getUser().observe(getViewLifecycleOwner(), user -> {
            if (user != null) {
                openDashboard();
            }
        });
    }

    private void openDashboard() {
        NavHostFragment.findNavController(this)
                .navigate(R.id.action_login_to_dashboard);
    }

    private String textOf(CharSequence text) {
        return text == null ? "" : text.toString();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
