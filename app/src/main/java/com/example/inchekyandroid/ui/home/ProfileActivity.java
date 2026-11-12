package com.example.inchekyandroid.ui.home;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.inchekyandroid.R;
import com.example.inchekyandroid.databinding.ActivityProfileBinding;

public class ProfileActivity extends AppCompatActivity {
    private ActivityProfileBinding binding;
    private ProfileViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        viewModel = new ViewModelProvider(this).get(ProfileViewModel.class);
        binding.btnProfileSave.setOnClickListener(view -> submitProfile());
        observeState();
        viewModel.loadProfile();
    }

    private void observeState() {
        viewModel.getUser().observe(this, user -> {
            binding.etProfileName.setText(user.getNombre());
            binding.etProfileSurname.setText(user.getApellido());
            binding.etProfileEmail.setText(user.getEmail());
        });
        viewModel.getError().observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                showFeedback(error, false);
            }
        });
        viewModel.getMessage().observe(this, message -> {
            if (message != null && !message.isEmpty()) {
                binding.etProfilePassword.setText("");
                showFeedback(message, true);
            }
        });
        viewModel.getLoading().observe(this, loading ->
                binding.btnProfileSave.setEnabled(!loading));
    }

    private void submitProfile() {
        viewModel.updateProfile(
                textOf(binding.etProfileName.getText()),
                textOf(binding.etProfileSurname.getText()),
                textOf(binding.etProfileEmail.getText()),
                textOf(binding.etProfilePassword.getText())
        );
    }

    private void showFeedback(String message, boolean success) {
        binding.tvProfileFeedback.setText(message);
        binding.tvProfileFeedback.setTextColor(getColor(
                success ? R.color.auth_success : R.color.auth_error));
        binding.tvProfileFeedback.setVisibility(View.VISIBLE);
    }

    private String textOf(CharSequence text) {
        return text == null ? "" : text.toString();
    }
}
