package com.example.inchekyandroid;

import android.os.Bundle;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;

import com.example.inchekyandroid.data.model.UsuarioResponse;
import com.example.inchekyandroid.data.remote.RetrofitClient;
import com.example.inchekyandroid.data.repository.SessionRepository;
import com.example.inchekyandroid.databinding.ActivityHostBinding;
import com.example.inchekyandroid.ui.home.ApiResource;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.navigation.NavigationView;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private static final Map<Integer, String> RESOURCE_MENU_KEYS = new HashMap<>();

    static {
        RESOURCE_MENU_KEYS.put(R.id.menuProducts, "productos");
        RESOURCE_MENU_KEYS.put(R.id.menuProviders, "proveedores");
        RESOURCE_MENU_KEYS.put(R.id.menuClients, "clientes");
        RESOURCE_MENU_KEYS.put(R.id.menuUsers, "usuarios");
        RESOURCE_MENU_KEYS.put(R.id.menuCashiers, "cajeros");
        RESOURCE_MENU_KEYS.put(R.id.menuAdmins, "administradores");
        RESOURCE_MENU_KEYS.put(R.id.menuPromotions, "promociones");
        RESOURCE_MENU_KEYS.put(R.id.menuSales, "ventas");
        RESOURCE_MENU_KEYS.put(R.id.menuSupplies, "abastecimientos");
    }

    private ActivityHostBinding binding;
    private NavController navController;
    private final SessionRepository sessionRepository = new SessionRepository();
    private UsuarioResponse currentUser;
    private boolean userRequestInFlight;
    private TextView userPill;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityHostBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host);
        navController = navHostFragment.getNavController();
        configureToolbar();
        binding.drawerNavigation.setNavigationItemSelectedListener(this::onMenuItemSelected);
        binding.btnDrawerLogout.setOnClickListener(ignored -> logout());
        navController.addOnDestinationChangedListener(this::onDestinationChanged);
        ViewCompat.setOnApplyWindowInsetsListener(binding.drawerLayout, (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            binding.mainContent.setPadding(
                    systemBars.left, systemBars.top, systemBars.right,
                    Math.max(systemBars.bottom, ime.bottom));
            binding.drawerPanel.setPadding(0, systemBars.top, 0, systemBars.bottom);
            return insets;
        });
    }

    private void configureToolbar() {
        MaterialToolbar toolbar = binding.topbar;
        toolbar.setNavigationIcon(R.drawable.ic_menu);
        toolbar.setNavigationContentDescription(R.string.menu_open);
        toolbar.setNavigationOnClickListener(ignored -> {
            if (binding.drawerLayout.getDrawerLockMode(GravityCompat.START)
                    == DrawerLayout.LOCK_MODE_UNLOCKED) {
                binding.drawerLayout.openDrawer(GravityCompat.START);
            }
        });
        userPill = new TextView(this);
        userPill.setSingleLine(true);
        userPill.setEllipsize(android.text.TextUtils.TruncateAt.END);
        userPill.setMaxWidth(getResources().getDimensionPixelSize(R.dimen.topbar_user_max_width));
        userPill.setTextColor(getColor(R.color.ink));
        userPill.setTextSize(getResources().getDimension(R.dimen.body_text_size)
                / getResources().getDisplayMetrics().scaledDensity);
        userPill.setTypeface(userPill.getTypeface(), android.graphics.Typeface.BOLD);
        userPill.setPadding(
                getResources().getDimensionPixelSize(R.dimen.space_3),
                getResources().getDimensionPixelSize(R.dimen.space_2),
                getResources().getDimensionPixelSize(R.dimen.space_3),
                getResources().getDimensionPixelSize(R.dimen.space_2));
        userPill.setBackgroundResource(R.drawable.topbar_user_background);
        MaterialToolbar.LayoutParams params = new MaterialToolbar.LayoutParams(
                MaterialToolbar.LayoutParams.WRAP_CONTENT,
                getResources().getDimensionPixelSize(R.dimen.touch_target_min),
                Gravity.END | Gravity.CENTER_VERTICAL);
        params.setMarginEnd(getResources().getDimensionPixelSize(R.dimen.space_2));
        toolbar.addView(userPill, params);
    }

    private void onDestinationChanged(
            @NonNull NavController controller,
            @NonNull NavDestination destination,
            Bundle arguments
    ) {
        boolean authenticatedArea = destination.getId() != R.id.loginFragment
                && destination.getId() != R.id.registerFragment;
        binding.topbar.setVisibility(authenticatedArea ? View.VISIBLE : View.GONE);
        binding.drawerLayout.setDrawerLockMode(
                authenticatedArea && currentUser != null
                        ? DrawerLayout.LOCK_MODE_UNLOCKED
                        : DrawerLayout.LOCK_MODE_LOCKED_CLOSED,
                GravityCompat.START);

        if (!authenticatedArea) {
            currentUser = null;
            return;
        }

        binding.topbar.setTitle(titleFor(destination, arguments));
        if (currentUser == null) {
            loadCurrentUser();
        } else {
            renderUser();
        }
        updateCheckedItem(destination, arguments);
    }

    private String titleFor(NavDestination destination, Bundle arguments) {
        if (destination.getId() == R.id.dashboardFragment) {
            return getString(R.string.menu_dashboard);
        }
        if (destination.getId() == R.id.profileFragment) {
            return getString(R.string.home_profile);
        }
        if (destination.getId() == R.id.saleFragment) {
            return getString(R.string.home_create_ticket);
        }
        if (destination.getId() == R.id.resourceFragment && arguments != null) {
            ApiResource resource = findResource(
                    arguments.getString("resource"),
                    currentUser == null ? null : currentUser.getRol());
            if (resource != null) {
                return resource.title;
            }
            String key = arguments.getString("resource");
            if ("compras".equals(key)) {
                return getString(R.string.menu_purchases);
            }
            if ("catalogo".equals(key)) {
                return getString(R.string.catalog_title);
            }
        }
        return getString(R.string.home_title);
    }

    private void loadCurrentUser() {
        if (userRequestInFlight) {
            return;
        }
        userRequestInFlight = true;
        sessionRepository.currentUser(new SessionRepository.SessionCallback() {
            @Override
            public void onSuccess(UsuarioResponse user) {
                userRequestInFlight = false;
                currentUser = user;
                if (binding == null) {
                    return;
                }
                renderUser();
                configureMenuForRole(user.getRol());
                binding.drawerLayout.setDrawerLockMode(
                        DrawerLayout.LOCK_MODE_UNLOCKED, GravityCompat.START);
                NavDestination destination = navController.getCurrentDestination();
                Bundle arguments = navController.getCurrentBackStackEntry() == null
                        ? null
                        : navController.getCurrentBackStackEntry().getArguments();
                updateCheckedItem(destination, arguments);
                if (destination != null) {
                    binding.topbar.setTitle(titleFor(destination, arguments));
                }
            }

            @Override
            public void onError(String message) {
                userRequestInFlight = false;
                if (!isFinishing() && !RetrofitClient.hasSessionCookie()) {
                    navController.navigate(R.id.action_global_login);
                }
            }
        });
    }

    private void renderUser() {
        if (currentUser == null) {
            return;
        }
        String displayName = currentUser.getNombreCompleto();
        userPill.setText(displayName);
        binding.drawerUserName.setText(displayName);
        String role = currentUser.getRol() == null ? "" : currentUser.getRol();
        binding.drawerUserRole.setText(role.toUpperCase(Locale.ROOT));
    }

    private void configureMenuForRole(String role) {
        Menu menu = binding.drawerNavigation.getMenu();
        boolean customer = "CLIENTE".equals(role);
        menu.findItem(R.id.menuDashboard).setVisible(true);
        menu.findItem(R.id.menuDashboard).setTitle(
                customer ? R.string.menu_customer_home : R.string.menu_dashboard);
        menu.findItem(R.id.menuProfile).setVisible(customer);
        menu.findItem(R.id.menuPurchases).setVisible(customer);
        for (Map.Entry<Integer, String> entry : RESOURCE_MENU_KEYS.entrySet()) {
            menu.findItem(entry.getKey()).setVisible(
                    !customer && findResource(entry.getValue(), role) != null);
        }
        binding.drawerNavigation.invalidate();
    }

    private void updateCheckedItem(NavDestination destination, Bundle arguments) {
        if (destination == null || currentUser == null) {
            return;
        }
        int menuId = R.id.menuDashboard;
        if (destination.getId() == R.id.profileFragment) {
            menuId = R.id.menuProfile;
        } else if (destination.getId() == R.id.saleFragment) {
            menuId = R.id.menuSales;
        } else if (destination.getId() == R.id.resourceFragment && arguments != null) {
            String key = arguments.getString("resource");
            if ("compras".equals(key)) {
                menuId = R.id.menuPurchases;
            } else {
                for (Map.Entry<Integer, String> entry : RESOURCE_MENU_KEYS.entrySet()) {
                    if (entry.getValue().equals(key)) {
                        menuId = entry.getKey();
                        break;
                    }
                }
            }
        }
        binding.drawerNavigation.setCheckedItem(menuId);
    }

    private boolean onMenuItemSelected(@NonNull MenuItem item) {
        binding.drawerLayout.closeDrawer(GravityCompat.START);
        if (item.getItemId() == R.id.menuDashboard) {
            navController.popBackStack(R.id.dashboardFragment, false);
            return true;
        }
        if (item.getItemId() == R.id.menuProfile) {
            navController.popBackStack(R.id.dashboardFragment, false);
            navController.navigate(R.id.action_global_profile);
            return true;
        }
        String resourceKey = item.getItemId() == R.id.menuPurchases
                ? "compras"
                : RESOURCE_MENU_KEYS.get(item.getItemId());
        if (resourceKey == null) {
            return false;
        }
        Bundle arguments = new Bundle();
        arguments.putString("resource", resourceKey);
        navController.popBackStack(R.id.dashboardFragment, false);
        navController.navigate(R.id.action_global_resource, arguments);
        return true;
    }

    private ApiResource findResource(String key, String role) {
        if (key == null || role == null) {
            return null;
        }
        for (ApiResource item : ApiResource.forRole(role)) {
            if (item.key.equals(key)) {
                return item;
            }
        }
        return null;
    }

    private void logout() {
        binding.drawerLayout.closeDrawer(GravityCompat.START);
        binding.btnDrawerLogout.setEnabled(false);
        sessionRepository.logout(errorMessage -> {
            if (binding == null) {
                return;
            }
            binding.btnDrawerLogout.setEnabled(true);
            if (errorMessage != null) {
                Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show();
            }
            currentUser = null;
            navController.navigate(R.id.action_global_login);
        });
    }

    @Override
    public void onBackPressed() {
        if (binding != null && binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START);
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        binding = null;
        super.onDestroy();
    }
}
