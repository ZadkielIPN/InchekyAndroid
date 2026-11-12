package com.example.inchekyandroid.ui.login;

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
        observeLoginState();
        restoreAuthenticatedSession();
    }

    private void restoreAuthenticatedSession() {
        if (!RetrofitClient.hasSessionCookie()) {
            return;
        }
        binding.btnLogin.setEnabled(false);
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
                }
            }
        });
    }

    private void submitLogin() {
        binding.tilEmail.setError(null);
        binding.tilPassword.setError(null);
        binding.tvFeedback.setVisibility(View.GONE);
        viewModel.login(
                textOf(binding.etEmail.getText()),
                textOf(binding.etPassword.getText())
        );
    }

    private void observeLoginState() {
        viewModel.getLoading().observe(getViewLifecycleOwner(), loading -> {
            binding.btnLogin.setEnabled(!loading);
            binding.btnRegister.setEnabled(!loading);
            binding.btnLogin.setText(loading ? R.string.login_loading : R.string.login_button);
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
