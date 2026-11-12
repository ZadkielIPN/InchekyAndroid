package com.example.inchekyandroid.ui.home;

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
import com.example.inchekyandroid.data.remote.RetrofitClient;
import com.example.inchekyandroid.databinding.ActivityProfileBinding;

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
        observeState();
        viewModel.loadProfile();
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
        viewModel.getLoading().observe(getViewLifecycleOwner(), loading ->
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
        binding.tvProfileFeedback.setTextColor(requireContext().getColor(
                success ? R.color.auth_success : R.color.auth_error));
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
