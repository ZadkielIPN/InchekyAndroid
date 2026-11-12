package com.example.inchekyandroid.ui.register;

import android.os.Bundle;
import android.content.Intent;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.inchekyandroid.R;
import com.example.inchekyandroid.databinding.ActivityRegisterBinding;
import com.example.inchekyandroid.ui.home.DashboardActivity;

public class RegisterActivity extends AppCompatActivity {
    private ActivityRegisterBinding binding;
    private RegisterViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        viewModel = new ViewModelProvider(this).get(RegisterViewModel.class);
        binding.btnRegister.setOnClickListener(view -> submitRegistration());
        binding.btnLogin.setOnClickListener(view -> finish());
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
        viewModel.getLoading().observe(this, loading -> {
            binding.btnRegister.setEnabled(!loading);
            binding.btnLogin.setEnabled(!loading);
            binding.btnRegister.setText(
                    loading ? R.string.register_loading : R.string.register_button);
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
            startActivity(new Intent(this, DashboardActivity.class));
            finishAffinity();
        });
    }

    private String textOf(CharSequence text) {
        return text == null ? "" : text.toString();
    }
}
