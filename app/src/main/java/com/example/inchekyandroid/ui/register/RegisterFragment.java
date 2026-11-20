package com.example.inchekyandroid.ui.register;

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
import com.example.inchekyandroid.databinding.ActivityRegisterBinding;
import com.google.android.material.textfield.TextInputLayout;

public class RegisterFragment extends Fragment {
    private ActivityRegisterBinding binding;
    private RegisterViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        binding = ActivityRegisterBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(this).get(RegisterViewModel.class);
        binding.btnRegister.setOnClickListener(ignored -> submitRegistration());
        binding.btnRegisterBack.setOnClickListener(ignored -> navigateBack());
        binding.btnLogin.setOnClickListener(ignored -> navigateBack());
        binding.etName.setOnEditorActionListener((text, actionId, event) ->
                focusNext(binding.etSurname, actionId));
        binding.etSurname.setOnEditorActionListener((text, actionId, event) ->
                focusNext(binding.etEmail, actionId));
        binding.etEmail.setOnEditorActionListener((text, actionId, event) ->
                focusNext(binding.etPassword, actionId));
        binding.etPassword.setOnEditorActionListener((text, actionId, event) ->
                focusNext(binding.etConfirmPassword, actionId));
        binding.etConfirmPassword.setOnEditorActionListener((text, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE
                    || event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                submitRegistration();
                return true;
            }
            return false;
        });
        binding.etName.addTextChangedListener(clearErrorOnChange(binding.tilName));
        binding.etSurname.addTextChangedListener(clearErrorOnChange(binding.tilSurname));
        binding.etEmail.addTextChangedListener(clearErrorOnChange(binding.tilEmail));
        binding.etPassword.addTextChangedListener(clearErrorOnChange(binding.tilPassword));
        binding.etConfirmPassword.addTextChangedListener(clearErrorOnChange(binding.tilConfirmPassword));
        applyKeyboardInsets();
        observeRegistrationState();
    }

    private void submitRegistration() {
        clearFieldErrors();
        binding.tvFeedback.setVisibility(View.GONE);
        String name = textOf(binding.etName.getText()).trim();
        String surname = textOf(binding.etSurname.getText()).trim();
        String email = textOf(binding.etEmail.getText()).trim();
        String password = textOf(binding.etPassword.getText());
        String confirmation = textOf(binding.etConfirmPassword.getText());

        if (name.isEmpty()) {
            showFieldError(binding.tilName, binding.etName, R.string.register_name_required);
            return;
        }
        if (surname.isEmpty()) {
            showFieldError(binding.tilSurname, binding.etSurname, R.string.register_surname_required);
            return;
        }
        if (email.isEmpty()) {
            showFieldError(binding.tilEmail, binding.etEmail, R.string.login_email_required);
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showFieldError(binding.tilEmail, binding.etEmail, R.string.auth_invalid_email);
            return;
        }
        if (password.isEmpty()) {
            showFieldError(binding.tilPassword, binding.etPassword, R.string.register_password_required);
            return;
        }
        if (password.length() < 8 || password.length() > 20) {
            showFieldError(binding.tilPassword, binding.etPassword, R.string.auth_password_length);
            return;
        }
        if (confirmation.isEmpty()) {
            showFieldError(
                    binding.tilConfirmPassword,
                    binding.etConfirmPassword,
                    R.string.register_confirmation_required
            );
            return;
        }
        if (!password.equals(confirmation)) {
            showFieldError(
                    binding.tilConfirmPassword,
                    binding.etConfirmPassword,
                    R.string.register_password_mismatch
            );
            return;
        }
        viewModel.register(
                name,
                surname,
                email,
                password,
                confirmation
        );
    }

    private boolean focusNext(View next, int actionId) {
        if (actionId != EditorInfo.IME_ACTION_NEXT) {
            return false;
        }
        next.requestFocus();
        return true;
    }

    private void clearFieldErrors() {
        binding.tilName.setError(null);
        binding.tilSurname.setError(null);
        binding.tilEmail.setError(null);
        binding.tilPassword.setError(null);
        binding.tilConfirmPassword.setError(null);
    }

    private void showFieldError(TextInputLayout input, View field, int message) {
        input.setError(getString(message));
        field.requestFocus();
    }

    private TextWatcher clearErrorOnChange(TextInputLayout input) {
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

    private void observeRegistrationState() {
        viewModel.getLoading().observe(getViewLifecycleOwner(), loading -> {
            binding.btnRegister.setEnabled(!loading);
            binding.btnLogin.setEnabled(!loading);
            binding.btnRegisterBack.setEnabled(!loading);
            binding.btnRegister.setText(
                    loading ? R.string.register_loading : R.string.register_button);
            binding.registerProgress.setVisibility(loading ? View.VISIBLE : View.GONE);
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
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_register_to_dashboard);
            }
        });
    }

    private void navigateBack() {
        NavHostFragment.findNavController(this).popBackStack();
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
