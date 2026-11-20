package com.example.inchekyandroid.ui.home;

import android.os.Bundle;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
        binding.dashboardResources.setColumnCount(2);
        ApiResource sales = findCreatableSalesResource(resources);
        if (sales != null) {
            addDashboardCard(sales, true);
        }
        if (resources.isEmpty()) {
            TextView empty = new TextView(requireContext());
            empty.setText(R.string.home_no_features);
            empty.setTextColor(requireContext().getColor(R.color.muted_on_soft));
            empty.setTextSize(getResources().getDimension(R.dimen.body_text_size)
                    / getResources().getDisplayMetrics().scaledDensity);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED),
                    GridLayout.spec(GridLayout.UNDEFINED, 2, 1f));
            params.width = 0;
            binding.dashboardResources.addView(empty, params);
            return;
        }
        for (ApiResource resource : resources) {
            addDashboardCard(resource, false);
        }
    }

    private ApiResource findCreatableSalesResource(List<ApiResource> resources) {
        for (ApiResource resource : resources) {
            if ("ventas".equals(resource.key) && resource.canCreate) {
                return resource;
            }
        }
        return null;
    }

    private void addDashboardCard(ApiResource resource, boolean featured) {
        MaterialCardView card = new MaterialCardView(requireContext());
        int cardColor = requireContext().getColor(
                featured ? R.color.featured_start : R.color.surface);
        card.setCardBackgroundColor(cardColor);
        card.setRadius(getResources().getDimension(R.dimen.card_corner_radius));
        card.setCardElevation(getResources().getDimension(R.dimen.card_elevation));
        card.setStrokeWidth(getResources().getDimensionPixelSize(R.dimen.card_stroke_width));
        card.setStrokeColor(requireContext().getColor(
                featured ? R.color.featured_border : R.color.line));
        card.setMinimumHeight(getResources().getDimensionPixelSize(
                featured ? R.dimen.dashboard_featured_min_height
                        : R.dimen.dashboard_card_min_height));

        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(featured ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        content.setGravity(featured ? Gravity.CENTER_VERTICAL : Gravity.TOP);
        int padding = getResources().getDimensionPixelSize(R.dimen.space_4);
        content.setPadding(padding, padding, padding, padding);
        if (featured) {
            content.setBackgroundResource(R.drawable.featured_card_background);
            addFeaturedIcon(content, resource);
        } else {
            addResourceIcon(content, resource);
        }

        LinearLayout labels = new LinearLayout(requireContext());
        labels.setOrientation(LinearLayout.VERTICAL);
        if (featured) {
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            labelParams.setMarginStart(getResources()
                    .getDimensionPixelSize(R.dimen.space_3));
            labels.setLayoutParams(labelParams);
        } else {
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            labelParams.topMargin = getResources()
                    .getDimensionPixelSize(R.dimen.space_2);
            labels.setLayoutParams(labelParams);
        }

        TextView title = new TextView(requireContext());
        if (featured) {
            title.setText(R.string.home_create_ticket);
        } else {
            title.setText(resource.title);
        }
        title.setTextColor(requireContext().getColor(R.color.ink));
        title.setTextSize(getResources().getDimension(R.dimen.card_title_text_size)
                / getResources().getDisplayMetrics().scaledDensity);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setMaxLines(2);
        labels.addView(title);

        TextView description = new TextView(requireContext());
        description.setText(featured
                ? R.string.home_create_ticket_description
                : resourceDescription(resource.key));
        description.setTextColor(requireContext().getColor(
                featured ? R.color.muted_on_line : R.color.muted));
        description.setTextSize(getResources().getDimension(R.dimen.body_text_size)
                / getResources().getDisplayMetrics().scaledDensity);
        description.setMaxLines(3);
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        descriptionParams.topMargin = getResources()
                .getDimensionPixelSize(R.dimen.space_1);
        labels.addView(description, descriptionParams);
        content.addView(labels);
        card.addView(content);
        card.setContentDescription(featured
                ? getString(R.string.home_create_ticket)
                : resource.title);
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(ignored -> openResource(resource.key, featured));

        GridLayout.Spec rowSpec = GridLayout.spec(GridLayout.UNDEFINED);
        GridLayout.Spec columnSpec = featured
                ? GridLayout.spec(GridLayout.UNDEFINED, 2, 1f)
                : GridLayout.spec(GridLayout.UNDEFINED, 1, 1f);
        GridLayout.LayoutParams params = new GridLayout.LayoutParams(rowSpec, columnSpec);
        params.width = 0;
        params.height = LinearLayout.LayoutParams.WRAP_CONTENT;
        int gap = getResources().getDimensionPixelSize(R.dimen.space_1);
        params.setMargins(gap, gap, gap, gap);
        binding.dashboardResources.addView(card, params);
    }

    private void addFeaturedIcon(LinearLayout parent, ApiResource resource) {
        int size = getResources().getDimensionPixelSize(R.dimen.dashboard_feature_icon_box_size);
        android.widget.FrameLayout iconBox = new android.widget.FrameLayout(requireContext());
        iconBox.setBackgroundResource(R.drawable.featured_icon_background);
        ImageView icon = new ImageView(requireContext());
        icon.setImageResource(resourceIcon(resource.key));
        icon.setColorFilter(requireContext().getColor(R.color.success));
        icon.setContentDescription(getString(R.string.home_create_ticket));
        int iconSize = getResources().getDimensionPixelSize(R.dimen.dashboard_icon_size);
        android.widget.FrameLayout.LayoutParams iconParams =
                new android.widget.FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER);
        iconBox.addView(icon, iconParams);
        parent.addView(iconBox, new LinearLayout.LayoutParams(size, size));
    }

    private void addResourceIcon(LinearLayout parent, ApiResource resource) {
        int iconSize = getResources().getDimensionPixelSize(R.dimen.dashboard_icon_size);
        ImageView icon = new ImageView(requireContext());
        icon.setImageResource(resourceIcon(resource.key));
        icon.setColorFilter(requireContext().getColor(R.color.accent));
        icon.setContentDescription(resource.title);
        parent.addView(icon, new LinearLayout.LayoutParams(iconSize, iconSize));
    }

    private int resourceDescription(String key) {
        switch (key) {
            case "catalogo":
                return R.string.dashboard_catalog_description;
            case "productos":
                return R.string.dashboard_products_description;
            case "clientes":
                return R.string.dashboard_clients_description;
            case "proveedores":
                return R.string.dashboard_providers_description;
            case "abastecimientos":
                return R.string.dashboard_supplies_description;
            case "promociones":
                return R.string.dashboard_promotions_description;
            case "ventas":
                return R.string.dashboard_sales_description;
            case "compras":
                return R.string.dashboard_purchases_description;
            case "cajeros":
                return R.string.dashboard_cashiers_description;
            case "administradores":
                return R.string.dashboard_admins_description;
            case "usuarios":
                return R.string.dashboard_users_description;
            default:
                return R.string.home_no_features;
        }
    }

    private void openResource(String resourceKey, boolean createOnOpen) {
        Bundle args = new Bundle();
        args.putString("resource", resourceKey);
        args.putBoolean("createOnOpen", createOnOpen);
        NavHostFragment.findNavController(this)
                .navigate(R.id.action_dashboard_to_resource, args);
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
