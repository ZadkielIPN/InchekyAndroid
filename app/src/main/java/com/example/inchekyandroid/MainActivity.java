package com.example.inchekyandroid;

import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.inchekyandroid.databinding.ActivityMainBinding;
import com.example.inchekyandroid.ui.login.LoginViewModel;

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
        observeLoginState();
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
            binding.btnLogin.setText(loading ? R.string.login_loading : R.string.login_button);
        });

        viewModel.getError().observe(this, error -> {
            if (error == null || error.isEmpty()) {
                return;
            }
            binding.tvFeedback.setText(error);
            binding.tvFeedback.setTextColor(getColor(R.color.login_error));
            binding.tvFeedback.setVisibility(View.VISIBLE);
        });

        viewModel.getUser().observe(this, user -> {
            if (user == null) {
                return;
            }
            binding.tvFeedback.setText(getString(R.string.login_welcome, user.getNombre()));
            binding.tvFeedback.setTextColor(getColor(R.color.login_success));
            binding.tvFeedback.setVisibility(View.VISIBLE);
        });
    }
}