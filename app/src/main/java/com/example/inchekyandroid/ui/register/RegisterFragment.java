package com.example.inchekyandroid.ui.register;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.example.inchekyandroid.R;
import com.example.inchekyandroid.databinding.ActivityRegisterBinding;

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
        observeRegistrationState();
    }

    private void submitRegistration() {
        binding.tvFeedback.setVisibility(View.GONE);
        viewModel.register(
                textOf(binding.etName.getText()),
                textOf(binding.etSurname.getText()),
                textOf(binding.etEmail.getText()),
                textOf(binding.etPassword.getText()),
                textOf(binding.etConfirmPassword.getText())
        );
    }

    private void observeRegistrationState() {
        viewModel.getLoading().observe(getViewLifecycleOwner(), loading -> {
            binding.btnRegister.setEnabled(!loading);
            binding.btnLogin.setEnabled(!loading);
            binding.btnRegisterBack.setEnabled(!loading);
            binding.btnRegister.setText(
                    loading ? R.string.register_loading : R.string.register_button);
        });
        viewModel.getError().observe(getViewLifecycleOwner(), error -> {
            if (error == null || error.isEmpty()) {
                return;
            }
            binding.tvFeedback.setText(error);
            binding.tvFeedback.setTextColor(requireContext().getColor(R.color.auth_error));
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
