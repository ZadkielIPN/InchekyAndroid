package com.example.inchekyandroid.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.inchekyandroid.MainActivity;
import com.example.inchekyandroid.R;
import com.example.inchekyandroid.data.model.UsuarioResponse;
import com.example.inchekyandroid.databinding.ActivityDashboardBinding;
import com.google.android.material.card.MaterialCardView;

import java.util.List;

public class DashboardActivity extends AppCompatActivity {
    private ActivityDashboardBinding binding;
    private DashboardViewModel viewModel;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDashboardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(DashboardViewModel.class);
        binding.btnDashboardProfile.setOnClickListener(view ->
                startActivity(new Intent(this, ProfileActivity.class)));
        binding.btnDashboardLogout.setOnClickListener(view -> logout());
        binding.tvDashboardSubtitle.setText(R.string.home_loading);
        observeState();
        viewModel.loadUser();
    }

    private void observeState() {
        viewModel.getUser().observe(this, user -> {
            binding.tvDashboardSubtitle.setText(getString(
                    R.string.home_welcome, user.getNombre(), user.getRol()));
            renderResources(ApiResource.forRole(user.getRol()));
        });
        viewModel.getError().observe(this, message -> {
            if (message == null || message.isEmpty()) {
                return;
            }
            binding.tvDashboardSubtitle.setText(message);
            new android.os.Handler(getMainLooper()).postDelayed(() -> {
                startActivity(new Intent(DashboardActivity.this, MainActivity.class));
                finish();
            }, 1200);
        });
        viewModel.getLoading().observe(this, loading ->
                binding.btnDashboardLogout.setEnabled(!loading));
    }

    private void renderResources(List<ApiResource> resources) {
        binding.dashboardResources.removeAllViews();
        if (resources.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(R.string.home_no_features);
            empty.setTextColor(getColor(R.color.auth_muted));
            binding.dashboardResources.addView(empty);
            return;
        }
        for (ApiResource resource : resources) {
            MaterialCardView card = new MaterialCardView(this);
            card.setCardBackgroundColor(getColor(R.color.white));
            card.setRadius(getResources().getDimension(R.dimen.auth_card_radius));
            card.setCardElevation(getResources().getDimension(R.dimen.auth_card_elevation));
            LinearLayoutWrapper wrapper = new LinearLayoutWrapper(this);
            TextView title = new TextView(this);
            title.setText(resource.title);
            title.setTextColor(getColor(R.color.auth_ink));
            title.setTextSize(17);
            title.setPadding(16, 16, 16, 16);
            wrapper.addView(title);
            card.addView(wrapper);
            card.setOnClickListener(view -> {
                Intent intent = new Intent(this, ResourceActivity.class);
                intent.putExtra(ResourceActivity.EXTRA_RESOURCE, resource.key);
                startActivity(intent);
            });
            android.widget.LinearLayout.LayoutParams params = new android.widget.LinearLayout
                    .LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.bottomMargin = getResources().getDimensionPixelSize(R.dimen.auth_card_spacing);
            binding.dashboardResources.addView(card, params);
        }
    }

    private void logout() {
        binding.btnDashboardLogout.setEnabled(false);
        viewModel.logout(errorMessage -> {
            if (errorMessage != null) {
                android.widget.Toast.makeText(this, errorMessage, android.widget.Toast.LENGTH_LONG)
                        .show();
            }
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
    }

    private static final class LinearLayoutWrapper extends android.widget.LinearLayout {
        LinearLayoutWrapper(DashboardActivity context) {
            super(context);
            setOrientation(VERTICAL);
        }
    }
}
