package com.example.inchekyandroid.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.example.inchekyandroid.R;
import com.example.inchekyandroid.data.repository.SessionRepository;
import com.example.inchekyandroid.databinding.ActivityDashboardBinding;
import com.google.android.material.card.MaterialCardView;

import java.util.List;

public class DashboardFragment extends Fragment {
    private ActivityDashboardBinding binding;
    private DashboardViewModel viewModel;
    private boolean clientCatalogOpened;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        binding = ActivityDashboardBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(this).get(DashboardViewModel.class);
        binding.btnDashboardProfile.setOnClickListener(ignored ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_dashboard_to_profile));
        binding.btnDashboardLogout.setOnClickListener(ignored -> logout());
        binding.tvDashboardSubtitle.setText(R.string.home_loading);
        observeState();
        viewModel.loadUser();
    }

    private void observeState() {
        viewModel.getUser().observe(getViewLifecycleOwner(), user -> {
            binding.tvDashboardSubtitle.setText(getString(
                    R.string.home_welcome, user.getNombre(), user.getRol()));
            renderResources(ApiResource.forRole(user.getRol()));
            String initialResource = ApiResource.initialResourceForRole(user.getRol());
            if (initialResource != null && !clientCatalogOpened) {
                clientCatalogOpened = true;
                openCustomerCatalog(initialResource);
            }
        });
        viewModel.getError().observe(getViewLifecycleOwner(), message -> {
            if (message == null || message.isEmpty()) {
                return;
            }
            binding.tvDashboardSubtitle.setText(message);
            binding.getRoot().postDelayed(() -> {
                if (isAdded()) {
                    NavHostFragment.findNavController(this)
                            .navigate(R.id.action_dashboard_to_login);
                }
            }, 1200);
        });
        viewModel.getLoading().observe(getViewLifecycleOwner(), loading ->
                binding.btnDashboardLogout.setEnabled(!loading));
    }

    private void openCustomerCatalog(String resourceKey) {
        Bundle args = new Bundle();
        args.putString("resource", resourceKey);
        NavHostFragment.findNavController(this)
                .navigate(R.id.action_dashboard_to_resource, args);
    }

    private void renderResources(List<ApiResource> resources) {
        binding.dashboardResources.removeAllViews();
        if (resources.isEmpty()) {
            TextView empty = new TextView(requireContext());
            empty.setText(R.string.home_no_features);
            empty.setTextColor(requireContext().getColor(R.color.auth_muted));
            binding.dashboardResources.addView(empty);
            return;
        }
        for (ApiResource resource : resources) {
            MaterialCardView card = new MaterialCardView(requireContext());
            card.setCardBackgroundColor(requireContext().getColor(R.color.white));
            card.setRadius(getResources().getDimension(R.dimen.auth_card_radius));
            card.setCardElevation(getResources().getDimension(R.dimen.auth_card_elevation));
            card.setStrokeWidth(getResources().getDimensionPixelSize(R.dimen.card_stroke_width));
            card.setStrokeColor(requireContext().getColor(R.color.card_outline));
            android.widget.LinearLayout wrapper = new android.widget.LinearLayout(requireContext());
            wrapper.setGravity(android.view.Gravity.CENTER_VERTICAL);
            wrapper.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            wrapper.setPadding(16, 8, 16, 8);
            ImageView icon = new ImageView(requireContext());
            icon.setImageResource(resourceIcon(resource.key));
            icon.setColorFilter(requireContext().getColor(R.color.auth_primary));
            icon.setContentDescription(resource.title);
            int iconSize = getResources().getDimensionPixelSize(R.dimen.dashboard_icon_size);
            android.widget.LinearLayout.LayoutParams iconParams =
                    new android.widget.LinearLayout.LayoutParams(iconSize, iconSize);
            iconParams.setMarginEnd(getResources()
                    .getDimensionPixelSize(R.dimen.auth_card_spacing));
            wrapper.addView(icon, iconParams);
            TextView title = new TextView(requireContext());
            title.setText(resource.title);
            title.setTextColor(requireContext().getColor(R.color.auth_ink));
            title.setTextSize(17);
            title.setPadding(0, 16, 8, 16);
            title.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                    0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            wrapper.addView(title);
            card.addView(wrapper);
            card.setOnClickListener(ignored -> {
                Bundle args = new Bundle();
                args.putString("resource", resource.key);
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_dashboard_to_resource, args);
            });
            android.widget.LinearLayout.LayoutParams params =
                    new android.widget.LinearLayout.LayoutParams(
                            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    );
            params.bottomMargin = getResources()
                    .getDimensionPixelSize(R.dimen.auth_card_spacing);
            binding.dashboardResources.addView(card, params);
        }
    }

    private int resourceIcon(String key) {
        switch (key) {
            case "catalogo":
            case "productos":
                return R.drawable.ic_box_seam;
            case "compras":
            case "ventas":
                return R.drawable.ic_receipt;
            case "clientes":
                return R.drawable.ic_people;
            case "proveedores":
                return R.drawable.ic_truck;
            case "abastecimientos":
                return R.drawable.ic_boxes;
            case "promociones":
                return R.drawable.ic_percent;
            case "cajeros":
                return R.drawable.ic_person_badge;
            case "administradores":
                return R.drawable.ic_person_gear;
            case "usuarios":
                return R.drawable.ic_person_circle;
            default:
                return R.drawable.ic_dashboard;
        }
    }

    private void logout() {
        binding.btnDashboardLogout.setEnabled(false);
        viewModel.logout(errorMessage -> {
            if (!isAdded()) {
                return;
            }
            if (errorMessage != null) {
                Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show();
            }
            NavHostFragment.findNavController(this)
                    .navigate(R.id.action_dashboard_to_login);
        });
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
