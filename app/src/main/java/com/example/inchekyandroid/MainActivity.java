package com.example.inchekyandroid;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.inchekyandroid.databinding.ActivityMainBinding;
import com.example.inchekyandroid.data.model.UsuarioResponse;
import com.example.inchekyandroid.data.remote.RetrofitClient;
import com.example.inchekyandroid.data.repository.SessionRepository;
import com.example.inchekyandroid.ui.home.DashboardActivity;
import com.example.inchekyandroid.ui.login.LoginViewModel;
import com.example.inchekyandroid.ui.register.RegisterActivity;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private LoginViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        viewModel = new ViewModelProvider(this).get(LoginViewModel.class);
        binding.btnLogin.setOnClickListener(view -> submitLogin());
        binding.btnRegister.setOnClickListener(view ->
                startActivity(new Intent(this, RegisterActivity.class)));
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
                openDashboard();
            }

            @Override
            public void onError(String message) {
                binding.btnLogin.setEnabled(true);
            }
        });
    }

    private void openDashboard() {
        startActivity(new Intent(this, DashboardActivity.class));
        finish();
    }

    private void submitLogin() {
        binding.tilEmail.setError(null);
        binding.tilPassword.setError(null);
        binding.tvFeedback.setVisibility(View.GONE);
        viewModel.login(
                binding.etEmail.getText() == null ? "" : binding.etEmail.getText().toString(),
                binding.etPassword.getText() == null ? "" : binding.etPassword.getText().toString()
        );
    }

    private void observeLoginState() {
        viewModel.getLoading().observe(this, loading -> {
            binding.btnLogin.setEnabled(!loading);
            binding.btnRegister.setEnabled(!loading);
            binding.btnLogin.setText(loading ? R.string.login_loading : R.string.login_button);
        });

        viewModel.getError().observe(this, error -> {
            if (error == null || error.isEmpty()) {
                return;
            }
            binding.tvFeedback.setText(error);
            binding.tvFeedback.setTextColor(getColor(R.color.auth_error));
            binding.tvFeedback.setVisibility(View.VISIBLE);
        });

        viewModel.getUser().observe(this, user -> {
            if (user == null) {
                return;
            }
            openDashboard();
        });
    }
}