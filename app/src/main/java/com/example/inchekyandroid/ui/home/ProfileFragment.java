package com.example.inchekyandroid.ui.home;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.example.inchekyandroid.R;
import com.example.inchekyandroid.data.remote.RetrofitClient;
import com.example.inchekyandroid.databinding.ActivityProfileBinding;
import com.google.android.material.textfield.TextInputLayout;

public class ProfileFragment extends Fragment {
    private ActivityProfileBinding binding;
    private ProfileViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        binding = ActivityProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(this).get(ProfileViewModel.class);
        binding.btnProfileBack.setOnClickListener(ignored ->
                NavHostFragment.findNavController(this).popBackStack());
        binding.btnProfileSave.setOnClickListener(ignored -> submitProfile());
        binding.etProfileName.setOnEditorActionListener((text, actionId, event) ->
                focusNext(binding.etProfileSurname, actionId));
        binding.etProfileSurname.setOnEditorActionListener((text, actionId, event) ->
                focusNext(binding.etProfileEmail, actionId));
        binding.etProfileEmail.setOnEditorActionListener((text, actionId, event) ->
                focusNext(binding.etProfilePassword, actionId));
        binding.etProfilePassword.setOnEditorActionListener((text, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submitProfile();
                return true;
            }
            return false;
        });
        binding.etProfileName.addTextChangedListener(clearErrorOnChange(binding.tilProfileName));
        binding.etProfileSurname.addTextChangedListener(
                clearErrorOnChange(binding.tilProfileSurname));
        binding.etProfileEmail.addTextChangedListener(clearErrorOnChange(binding.tilProfileEmail));
        binding.etProfilePassword.addTextChangedListener(
                clearErrorOnChange(binding.tilProfilePassword));
        observeState();
        viewModel.loadProfile();
    }

    private boolean focusNext(View next, int actionId) {
        if (actionId != EditorInfo.IME_ACTION_NEXT) {
            return false;
        }
        next.requestFocus();
        return true;
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

    private void observeState() {
        viewModel.getUser().observe(getViewLifecycleOwner(), user -> {
            binding.etProfileName.setText(user.getNombre());
            binding.etProfileSurname.setText(user.getApellido());
            binding.etProfileEmail.setText(user.getEmail());
        });
        viewModel.getError().observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                showFeedback(error, false);
                if (!RetrofitClient.hasSessionCookie()) {
                    androidx.navigation.fragment.NavHostFragment.findNavController(this)
                            .navigate(R.id.action_global_login);
                }
            }
        });
        viewModel.getMessage().observe(getViewLifecycleOwner(), message -> {
            if (message != null && !message.isEmpty()) {
                binding.etProfilePassword.setText("");
                showFeedback(message, true);
            }
        });
        viewModel.getLoading().observe(getViewLifecycleOwner(), loading -> {
            boolean isLoading = Boolean.TRUE.equals(loading);
            binding.btnProfileSave.setEnabled(!isLoading);
            binding.btnProfileSave.setText(
                    isLoading ? R.string.profile_saving : R.string.profile_save);
            binding.profileProgress.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        });
    }

    private void submitProfile() {
        binding.tilProfileName.setError(null);
        binding.tilProfileSurname.setError(null);
        binding.tilProfileEmail.setError(null);
        binding.tilProfilePassword.setError(null);
        binding.tvProfileFeedback.setVisibility(View.GONE);
        String name = textOf(binding.etProfileName.getText()).trim();
        String surname = textOf(binding.etProfileSurname.getText()).trim();
        String email = textOf(binding.etProfileEmail.getText()).trim();
        String password = textOf(binding.etProfilePassword.getText());
        if (name.isEmpty()) {
            showFieldError(binding.tilProfileName, binding.etProfileName,
                    R.string.profile_name_required);
            return;
        }
        if (surname.isEmpty()) {
            showFieldError(binding.tilProfileSurname, binding.etProfileSurname,
                    R.string.profile_surname_required);
            return;
        }
        if (email.isEmpty()) {
            showFieldError(binding.tilProfileEmail, binding.etProfileEmail,
                    R.string.profile_email_required);
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showFieldError(binding.tilProfileEmail, binding.etProfileEmail,
                    R.string.profile_email_invalid);
            return;
        }
        if (!password.isEmpty() && (password.length() < 8 || password.length() > 20)) {
            showFieldError(binding.tilProfilePassword, binding.etProfilePassword,
                    R.string.profile_password_length);
            return;
        }
        viewModel.updateProfile(
                name,
                surname,
                email,
                password
        );
    }

    private void showFieldError(TextInputLayout input, View field, int message) {
        input.setError(getString(message));
        field.requestFocus();
    }

    private void showFeedback(String message, boolean success) {
        binding.tvProfileFeedback.setText(message);
        binding.tvProfileFeedback.setTextColor(requireContext().getColor(
                success ? R.color.success_notice_text : R.color.danger));
        binding.tvProfileFeedback.setBackgroundResource(success
                ? R.drawable.success_notice_background
                : R.drawable.error_notice_background);
        binding.tvProfileFeedback.setVisibility(View.VISIBLE);
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
