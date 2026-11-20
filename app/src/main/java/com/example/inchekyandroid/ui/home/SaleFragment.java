package com.example.inchekyandroid.ui.home;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Patterns;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.example.inchekyandroid.R;
import com.example.inchekyandroid.data.model.SaleEstimate;
import com.example.inchekyandroid.data.model.SaleFormData;
import com.example.inchekyandroid.data.model.SaleLineRequestMapper;
import com.example.inchekyandroid.data.model.UsuarioResponse;
import com.example.inchekyandroid.data.remote.RetrofitClient;
import com.example.inchekyandroid.data.repository.ApiRepository;
import com.example.inchekyandroid.data.repository.SessionRepository;
import com.example.inchekyandroid.databinding.ActivitySaleBinding;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SaleFragment extends Fragment {
    private static final String STATE_CART = "sale.cart";
    private static final String STATE_CUSTOMER = "sale.customer";
    private static final String STATE_CASHIER = "sale.cashier";
    private static final String STATE_POINTS = "sale.points";
    private static final String STATE_PAYMENT = "sale.payment";
    private static final String STATE_CASH = "sale.cash";
    private static final String STATE_QUERY = "sale.query";
    private static final int OPTIONS_PAGE_SIZE = 100;

    private ActivitySaleBinding binding;
    private ResourceViewModel viewModel;
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private final List<JsonObject> products = new ArrayList<>();
    private final List<JsonObject> customers = new ArrayList<>();
    private final List<JsonObject> cashiers = new ArrayList<>();
    private final List<JsonObject> promotions = new ArrayList<>();
    private final Map<Long, CartItem> cart = new LinkedHashMap<>();
    private final Map<String, TextView> summaryValues = new LinkedHashMap<>();
    private final Map<Long, TicketLineViews> ticketLineViews = new LinkedHashMap<>();
    private final SaleLineRequestMapper lineMapper = new SaleLineRequestMapper();

    private JsonObject selectedCustomer;
    private JsonObject selectedCashier;
    private UsuarioResponse user;
    private BottomSheetDialog summaryDialog;
    private Runnable pendingSearch;
    private TextInputLayout pointsInputLayout;
    private TextInputLayout cashInputLayout;
    private TextInputEditText pointsInput;
    private TextInputEditText cashInput;
    private MaterialButton customerButton;
    private MaterialButton cashierButton;
    private MaterialButton confirmButton;
    private ProgressBar submitProgress;
    private Spinner paymentSpinner;
    private TextView pointsHint;
    private boolean productsLoaded;
    private boolean productsLoading;
    private boolean customersLoaded;
    private boolean customersLoading;
    private boolean cashiersLoaded;
    private boolean cashiersLoading;
    private boolean submitting;
    private String customerLoadError;
    private String cashierLoadError;
    private String productQuery = "";
    private String restoredPoints = "0";
    private String restoredCash = "";
    private int restoredPayment;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        binding = ActivitySaleBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(this).get(ResourceViewModel.class);
        restoreState(savedInstanceState);
        binding.btnViewTicket.setEnabled(false);
        binding.btnViewTicket.setOnClickListener(ignored -> showTicketSummary());
        binding.btnRetryCatalog.setOnClickListener(ignored -> loadProducts());
        binding.etProductSearch.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        binding.etProductSearch.setText(productQuery);
        binding.etProductSearch.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                if (pendingSearch != null) {
                    searchHandler.removeCallbacks(pendingSearch);
                }
                productQuery = textView.getText().toString().trim();
                renderCatalog();
                return true;
            }
            return false;
        });
        binding.etProductSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence value, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence value, int start, int before, int count) {
                productQuery = value.toString().trim();
                searchHandler.removeCallbacks(pendingSearch);
                pendingSearch = SaleFragment.this::renderCatalog;
                searchHandler.postDelayed(pendingSearch, 300);
            }

            @Override
            public void afterTextChanged(Editable value) {
            }
        });

        new SessionRepository().currentUser(new SessionRepository.SessionCallback() {
            @Override
            public void onSuccess(UsuarioResponse currentUser) {
                if (!isAdded() || binding == null) {
                    return;
                }
                user = currentUser;
                if (findCreatableSalesResource(ApiResource.forRole(user.getRol())) == null) {
                    Toast.makeText(requireContext(), R.string.home_no_features,
                            Toast.LENGTH_LONG).show();
                    NavHostFragment.findNavController(SaleFragment.this).popBackStack();
                    return;
                }
                if ("CAJERO".equals(user.getRol())) {
                    selectedCashier = new JsonObject();
                    selectedCashier.addProperty("id", user.getId());
                    selectedCashier.addProperty("nombre", user.getNombreCompleto());
                    cashiersLoaded = true;
                } else {
                    loadCashiers();
                }
                loadCustomers();
                loadProducts();
                loadPromotions();
            }

            @Override
            public void onError(String message) {
                if (!isAdded() || binding == null) {
                    return;
                }
                if (!RetrofitClient.hasSessionCookie()) {
                    NavHostFragment.findNavController(SaleFragment.this)
                            .navigate(R.id.action_global_login);
                } else {
                    showNotice(message);
                    Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private ApiResource findCreatableSalesResource(List<ApiResource> resources) {
        for (ApiResource resource : resources) {
            if ("ventas".equals(resource.key) && resource.canCreate) {
                return resource;
            }
        }
        return null;
    }

    private void restoreState(Bundle state) {
        if (state == null) {
            return;
        }
        productQuery = state.getString(STATE_QUERY, "");
        restoredPoints = state.getString(STATE_POINTS, "0");
        restoredCash = state.getString(STATE_CASH, "");
        restoredPayment = state.getInt(STATE_PAYMENT, 0);
        selectedCustomer = parseObject(state.getString(STATE_CUSTOMER));
        selectedCashier = parseObject(state.getString(STATE_CASHIER));
        String encodedCart = state.getString(STATE_CART);
        if (encodedCart == null) {
            return;
        }
        try {
            JsonElement parsed = JsonParser.parseString(encodedCart);
            if (!parsed.isJsonArray()) {
                return;
            }
            for (JsonElement element : parsed.getAsJsonArray()) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject item = element.getAsJsonObject();
                JsonElement product = item.get("product");
                JsonElement quantity = item.get("quantity");
                if (product != null && product.isJsonObject()
                        && quantity != null && quantity.isJsonPrimitive()) {
                    long id = idOf(product.getAsJsonObject());
                    int count = quantity.getAsInt();
                    if (id > 0 && count > 0) {
                        cart.put(id, new CartItem(product.getAsJsonObject(), count));
                    }
                }
            }
        } catch (RuntimeException ignored) {
            cart.clear();
        }
    }

    private JsonObject parseObject(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            JsonElement parsed = JsonParser.parseString(value);
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        JsonArray savedCart = new JsonArray();
        for (CartItem item : cart.values()) {
            JsonObject savedItem = new JsonObject();
            savedItem.add("product", item.product);
            savedItem.addProperty("quantity", item.quantity);
            savedCart.add(savedItem);
        }
        outState.putString(STATE_CART, savedCart.toString());
        outState.putString(STATE_CUSTOMER,
                selectedCustomer == null ? null : selectedCustomer.toString());
        outState.putString(STATE_CASHIER,
                selectedCashier == null ? null : selectedCashier.toString());
        outState.putString(STATE_POINTS,
                pointsInput == null ? restoredPoints : textOf(pointsInput));
        outState.putString(STATE_CASH,
                cashInput == null ? restoredCash : textOf(cashInput));
        outState.putInt(STATE_PAYMENT,
                paymentSpinner == null ? restoredPayment : paymentSpinner.getSelectedItemPosition());
        outState.putString(STATE_QUERY, productQuery);
    }

    private void loadProducts() {
        if (!isAdded() || binding == null || productsLoading) {
            return;
        }
        productsLoading = true;
        productsLoaded = false;
        products.clear();
        binding.saleLoading.setVisibility(View.VISIBLE);
        binding.saleProductScroll.setVisibility(View.GONE);
        binding.saleEmptyContainer.setVisibility(View.GONE);
        binding.saleNoticeContainer.setVisibility(View.GONE);
        loadAllPages("productos", 0, new ArrayList<>(), new JsonListCallback() {
            @Override
            public void onSuccess(List<JsonObject> records) {
                if (!isAdded() || binding == null) {
                    return;
                }
                productsLoading = false;
                productsLoaded = true;
                for (JsonObject product : records) {
                    if (isSellable(product)) {
                        products.add(product);
                    }
                }
                renderCatalog();
            }

            @Override
            public void onError(String message) {
                if (!isAdded() || binding == null) {
                    return;
                }
                productsLoading = false;
                productsLoaded = false;
                showCatalogError(message);
            }
        });
    }

    private void loadCustomers() {
        if (customersLoaded || customersLoading) {
            return;
        }
        customersLoading = true;
        loadAllPages("clientes", 0, new ArrayList<>(), new JsonListCallback() {
            @Override
            public void onSuccess(List<JsonObject> records) {
                if (!isAdded()) {
                    return;
                }
                customersLoading = false;
                customersLoaded = true;
                customerLoadError = null;
                customers.clear();
                customers.addAll(records);
            }

            @Override
            public void onError(String message) {
                customersLoading = false;
                customerLoadError = message;
                if (isAdded()) {
                    Toast.makeText(requireContext(),
                            getString(R.string.sale_customer_options_error) + " " + message,
                            Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void loadCashiers() {
        if (cashiersLoaded || cashiersLoading) {
            return;
        }
        cashiersLoading = true;
        loadAllPages("cajeros", 0, new ArrayList<>(), new JsonListCallback() {
            @Override
            public void onSuccess(List<JsonObject> records) {
                if (!isAdded()) {
                    return;
                }
                cashiersLoading = false;
                cashiersLoaded = true;
                cashierLoadError = null;
                cashiers.clear();
                cashiers.addAll(records);
            }

            @Override
            public void onError(String message) {
                cashiersLoading = false;
                cashierLoadError = message;
                if (isAdded()) {
                    Toast.makeText(requireContext(),
                            getString(R.string.sale_cashier_options_error) + " " + message,
                            Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void loadPromotions() {
        loadAllPages("promociones", 0, new ArrayList<>(), new JsonListCallback() {
            @Override
            public void onSuccess(List<JsonObject> records) {
                promotions.clear();
                promotions.addAll(records);
                updateTicketButton();
                updateSummaryEstimate();
            }

            @Override
            public void onError(String message) {
                if (isAdded()) {
                    Toast.makeText(requireContext(),
                            "No se pudieron cargar las promociones: " + message,
                            Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void loadAllPages(
            String path,
            int page,
            List<JsonObject> collected,
            JsonListCallback callback
    ) {
        if (page >= 100) {
            callback.onError("La lista supera el límite de páginas permitido.");
            return;
        }
        viewModel.listOptions(path, page, OPTIONS_PAGE_SIZE,
                new ApiRepository.ResultCallback<JsonElement>() {
                    @Override
                    public void onSuccess(JsonElement result) {
                        if (!isAdded()) {
                            return;
                        }
                        if (result == null) {
                            callback.onError(getString(R.string.resource_invalid_response));
                            return;
                        }
                        JsonArray content = pageContent(result);
                        if (content == null) {
                            callback.onError(getString(R.string.resource_invalid_response));
                            return;
                        }
                        for (JsonElement element : content) {
                            if (element != null && element.isJsonObject()) {
                                collected.add(element.getAsJsonObject());
                            }
                        }
                        int totalPages = totalPages(result);
                        boolean hasNext = totalPages > 0
                                ? page + 1 < totalPages
                                : content.size() >= OPTIONS_PAGE_SIZE;
                        if (hasNext) {
                            loadAllPages(path, page + 1, collected, callback);
                        } else {
                            callback.onSuccess(collected);
                        }
                    }

                    @Override
                    public void onError(String message) {
                        callback.onError(message);
                    }
                });
    }

    private JsonArray pageContent(JsonElement result) {
        if (result.isJsonArray()) {
            return result.getAsJsonArray();
        }
        if (result.isJsonObject()) {
            JsonElement content = result.getAsJsonObject().get("content");
            return content != null && content.isJsonArray() ? content.getAsJsonArray() : null;
        }
        return null;
    }

    private int totalPages(JsonElement result) {
        if (result == null || !result.isJsonObject()) {
            return 0;
        }
        JsonElement totalPages = result.getAsJsonObject().get("totalPages");
        try {
            return totalPages == null || totalPages.isJsonNull()
                    ? 0
                    : totalPages.getAsInt();
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private boolean isSellable(JsonObject product) {
        if (idOf(product) <= 0 || !isActive(product)) {
            return false;
        }
        int stock = integerValue(firstValue(product, "stock", "existencias", "stockDisponible"));
        JsonElement priceElement = product.get("precio");
        if (priceElement == null || priceElement.isJsonNull()) {
            return false;
        }
        try {
            return stock > 0 && priceElement.getAsBigDecimal().signum() >= 0;
        } catch (RuntimeException error) {
            return false;
        }
    }

    private boolean isActive(JsonObject product) {
        JsonElement active = firstValue(product, "activo", "activa");
        if (active == null || active.isJsonNull()) {
            return true;
        }
        if (active.isJsonPrimitive() && active.getAsJsonPrimitive().isBoolean()) {
            return active.getAsBoolean();
        }
        String value = active.getAsString();
        return !"false".equalsIgnoreCase(value)
                && !"inactivo".equalsIgnoreCase(value)
                && !"inactiva".equalsIgnoreCase(value);
    }

    private void renderCatalog() {
        if (binding == null || !productsLoaded) {
            return;
        }
        binding.saleLoading.setVisibility(View.GONE);
        binding.saleNoticeContainer.setVisibility(View.GONE);
        binding.saleProductScroll.setVisibility(View.VISIBLE);
        binding.saleProducts.removeAllViews();
        String query = productQuery.toLowerCase(Locale.ROOT);
        int count = 0;
        for (JsonObject product : products) {
            String name = productName(product);
            String category = productCategory(product);
            if (!query.isEmpty()
                    && !name.toLowerCase(Locale.ROOT).contains(query)
                    && !category.toLowerCase(Locale.ROOT).contains(query)) {
                continue;
            }
            addProductCard(product, count++);
        }
        binding.tvSaleEmpty.setText(products.isEmpty()
                ? R.string.sale_no_products
                : R.string.sale_catalog_no_results);
        binding.saleEmptyContainer.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
    }

    private void showCatalogError(String message) {
        binding.saleLoading.setVisibility(View.GONE);
        binding.saleProductScroll.setVisibility(View.GONE);
        binding.saleEmptyContainer.setVisibility(View.GONE);
        binding.saleNoticeContainer.setVisibility(View.VISIBLE);
        binding.tvSaleNotice.setText(TextUtils.isEmpty(message)
                ? getString(R.string.sale_catalog_error)
                : getString(R.string.sale_catalog_error) + " " + message);
        binding.btnRetryCatalog.setVisibility(View.VISIBLE);
    }

    private void showNotice(String message) {
        if (binding == null) {
            return;
        }
        binding.saleNoticeContainer.setVisibility(View.VISIBLE);
        binding.tvSaleNotice.setText(message);
        binding.btnRetryCatalog.setVisibility(View.GONE);
    }

    private void addProductCard(JsonObject product, int position) {
        MaterialCardView card = new MaterialCardView(requireContext());
        card.setCardBackgroundColor(requireContext().getColor(R.color.surface));
        card.setRadius(getResources().getDimension(R.dimen.card_corner_radius));
        card.setCardElevation(getResources().getDimension(R.dimen.card_elevation));
        card.setStrokeWidth(getResources().getDimensionPixelSize(R.dimen.card_stroke_width));
        card.setStrokeColor(requireContext().getColor(R.color.line));
        card.setMinimumHeight(getResources()
                .getDimensionPixelSize(R.dimen.pos_product_card_min_height));
        card.setClickable(true);
        card.setFocusable(true);
        card.setContentDescription(getString(R.string.sale_add_product,
                productName(product), money(decimalValue(product.get("precio")))));
        card.setOnClickListener(ignored -> addToCart(product));

        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = getResources().getDimensionPixelSize(R.dimen.space_3);
        content.setPadding(padding, padding, padding, padding);

        FrameLayout iconBox = new FrameLayout(requireContext());
        iconBox.setBackgroundResource(R.drawable.featured_icon_background);
        int boxSize = getResources().getDimensionPixelSize(R.dimen.pos_catalog_icon_box_size);
        int iconSize = getResources().getDimensionPixelSize(R.dimen.dashboard_icon_size);
        ImageView icon = new ImageView(requireContext());
        icon.setImageResource(R.drawable.ic_box_seam);
        icon.setColorFilter(requireContext().getColor(R.color.success));
        icon.setContentDescription(getString(R.string.sale_add_product,
                productName(product), money(decimalValue(product.get("precio")))));
        iconBox.addView(icon, new FrameLayout.LayoutParams(
                iconSize, iconSize, Gravity.CENTER));
        content.addView(iconBox, new LinearLayout.LayoutParams(boxSize, boxSize));

        TextView name = createText(productName(product), R.color.ink, R.dimen.card_title_text_size);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nameParams = wrapParams();
        nameParams.topMargin = getResources().getDimensionPixelSize(R.dimen.space_2);
        content.addView(name, nameParams);

        TextView category = createText(productCategory(product), R.color.muted, R.dimen.caption_text_size);
        category.setMaxLines(1);
        category.setEllipsize(TextUtils.TruncateAt.END);
        content.addView(category, wrapParams());

        TextView price = createText(
                money(decimalValue(product.get("precio"))), R.color.pos_price,
                R.dimen.card_title_text_size);
        price.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams priceParams = wrapParams();
        priceParams.topMargin = getResources().getDimensionPixelSize(R.dimen.space_1);
        content.addView(price, priceParams);
        card.addView(content);

        int gap = getResources().getDimensionPixelSize(R.dimen.space_1);
        GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED, 1, 1f),
                GridLayout.spec(GridLayout.UNDEFINED, 1, 1f));
        params.width = 0;
        params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        params.setMargins(gap, gap, gap, gap);
        binding.saleProducts.addView(card, params);
    }

    private void addToCart(JsonObject product) {
        long id = idOf(product);
        CartItem item = cart.get(id);
        int stock = integerValue(firstValue(product, "stock", "existencias", "stockDisponible"));
        if (item == null) {
            cart.put(id, new CartItem(product, 1));
        } else if (item.quantity < stock) {
            item.quantity++;
        } else {
            Snackbar.make(binding.getRoot(),
                    getString(R.string.sale_stock_limit, stock), Snackbar.LENGTH_SHORT).show();
            return;
        }
        updateTicketButton();
        if (summaryDialog != null && summaryDialog.isShowing()) {
            summaryDialog.dismiss();
            showTicketSummary();
        } else {
            Snackbar.make(binding.getRoot(),
                    getString(R.string.sale_add_product, productName(product),
                            money(decimalValue(product.get("precio")))),
                    Snackbar.LENGTH_SHORT).show();
        }
    }

    private void updateTicketButton() {
        if (binding == null) {
            return;
        }
        SaleEstimate estimate = estimate(0);
        binding.btnViewTicket.setText(getString(
                R.string.sale_view_ticket, cartCount(), money(estimate.total)));
        binding.btnViewTicket.setEnabled(!cart.isEmpty());
    }

    private int cartCount() {
        int count = 0;
        for (CartItem item : cart.values()) {
            count += item.quantity;
        }
        return count;
    }

    private void showTicketSummary() {
        if (cart.isEmpty() || !isAdded()) {
            return;
        }
        if (summaryDialog != null && summaryDialog.isShowing()) {
            summaryDialog.dismiss();
        }
        summaryValues.clear();
        ticketLineViews.clear();
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(requireContext().getColor(R.color.surface));
        int horizontal = getResources().getDimensionPixelSize(R.dimen.space_4);
        root.setPadding(horizontal, getResources().getDimensionPixelSize(R.dimen.space_3),
                horizontal, getResources().getDimensionPixelSize(R.dimen.space_3));

        TextView title = createText(
                getString(R.string.sale_ticket_summary), R.color.ink, R.dimen.section_title_text_size);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title, wrapParams());

        ScrollView scroll = new ScrollView(requireContext());
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        scrollParams.topMargin = getResources().getDimensionPixelSize(R.dimen.space_2);
        root.addView(scroll, scrollParams);
        scroll.addView(content);

        addSectionLabel(content, getString(R.string.menu_clients));
        customerButton = createDropdownButton(
                selectedCustomer == null
                        ? getString(R.string.sale_select_customer)
                        : entityName(selectedCustomer));
        customerButton.setOnClickListener(ignored -> chooseCustomer());
        addFormView(content, customerButton);

        addSectionLabel(content, getString(R.string.menu_cashiers));
        if ("CAJERO".equals(user.getRol())) {
            TextView cashierName = createText(
                    getString(R.string.sale_cashier_self, user.getNombreCompleto()),
                    R.color.muted, R.dimen.body_text_size);
            addFormView(content, cashierName);
        } else {
            cashierButton = createDropdownButton(
                    selectedCashier == null
                            ? getString(R.string.sale_select_cashier)
                            : entityName(selectedCashier));
            cashierButton.setOnClickListener(ignored -> chooseCashier());
            addFormView(content, cashierButton);
        }

        addSectionLabel(content, getString(R.string.resource_line_items));
        renderTicketLines(content);
        addSectionLabel(content, getString(R.string.sale_estimate_title));
        addEstimateCard(content);

        pointsInputLayout = createInputLayout(getString(R.string.sale_points_label));
        pointsInput = createEditText(InputType.TYPE_CLASS_NUMBER, restoredPoints);
        pointsInput.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        pointsInputLayout.addView(pointsInput);
        addFormView(content, pointsInputLayout);
        pointsHint = createText("", R.color.muted, R.dimen.caption_text_size);
        addFormView(content, pointsHint);
        pointsInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence value, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence value, int start, int before, int count) {
                pointsInputLayout.setError(null);
                updateSummaryEstimate();
            }

            @Override
            public void afterTextChanged(Editable value) {
            }
        });

        addSectionLabel(content, getString(R.string.sale_payment_method));
        paymentSpinner = new Spinner(requireContext());
        ArrayAdapter<CharSequence> paymentAdapter = ArrayAdapter.createFromResource(
                requireContext(), R.array.sale_payment_methods,
                android.R.layout.simple_spinner_item);
        paymentAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        paymentSpinner.setAdapter(paymentAdapter);
        paymentSpinner.setSelection(Math.min(restoredPayment, paymentAdapter.getCount() - 1));
        addFormView(content, paymentSpinner);
        cashInputLayout = createInputLayout(getString(R.string.sale_cash_received));
        cashInput = createEditText(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL, restoredCash);
        cashInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        cashInputLayout.addView(cashInput);
        addFormView(content, cashInputLayout);
        cashInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence value, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence value, int start, int before, int count) {
                cashInputLayout.setError(null);
                updateSummaryEstimate();
            }

            @Override
            public void afterTextChanged(Editable value) {
            }
        });
        paymentSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(
                    android.widget.AdapterView<?> parent,
                    View view,
                    int position,
                    long id
            ) {
                boolean cash = position == 0;
                cashInputLayout.setVisibility(cash ? View.VISIBLE : View.GONE);
                if (!cash) {
                    cashInput.setText("");
                }
                updateSummaryEstimate();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });

        submitProgress = new ProgressBar(requireContext());
        submitProgress.setIndeterminateTintList(
                ColorStateList.valueOf(requireContext().getColor(R.color.accent)));
        submitProgress.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                getResources().getDimensionPixelSize(R.dimen.space_5),
                getResources().getDimensionPixelSize(R.dimen.space_5),
                Gravity.CENTER_HORIZONTAL);
        progressParams.topMargin = getResources().getDimensionPixelSize(R.dimen.space_2);
        root.addView(submitProgress, progressParams);

        confirmButton = new MaterialButton(requireContext());
        confirmButton.setText(R.string.sale_confirm);
        confirmButton.setTextColor(requireContext().getColor(R.color.white));
        confirmButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        confirmButton.setBackgroundTintList(
                ColorStateList.valueOf(requireContext().getColor(R.color.accent)));
        confirmButton.setCornerRadius(
                getResources().getDimensionPixelSize(R.dimen.button_corner_radius));
        confirmButton.setMinHeight(getResources().getDimensionPixelSize(R.dimen.touch_target_min));
        confirmButton.setAllCaps(false);
        LinearLayout.LayoutParams confirmParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                getResources().getDimensionPixelSize(R.dimen.touch_target_min));
        confirmParams.topMargin = getResources().getDimensionPixelSize(R.dimen.space_2);
        root.addView(confirmButton, confirmParams);
        confirmButton.setOnClickListener(ignored -> confirmSale());

        summaryDialog = new BottomSheetDialog(requireContext());
        summaryDialog.setContentView(root);
        summaryDialog.setOnShowListener(ignored -> {
            View sheet = summaryDialog.findViewById(
                    com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) {
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(sheet);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
                int screenHeight = getResources().getDisplayMetrics().heightPixels;
                int maxHeight = getResources()
                        .getDimensionPixelSize(R.dimen.pos_sheet_max_height);
                ViewGroup.LayoutParams params = sheet.getLayoutParams();
                params.height = Math.min(maxHeight, screenHeight);
                sheet.setLayoutParams(params);
            }
            if (summaryDialog.getWindow() != null) {
                summaryDialog.getWindow().setSoftInputMode(
                        WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            }
        });
        summaryDialog.setOnDismissListener(ignored -> {
            restoredPoints = pointsInput == null ? restoredPoints : textOf(pointsInput);
            restoredCash = cashInput == null ? restoredCash : textOf(cashInput);
            restoredPayment = paymentSpinner == null
                    ? restoredPayment : paymentSpinner.getSelectedItemPosition();
            summaryValues.clear();
            ticketLineViews.clear();
            pointsInputLayout = null;
            cashInputLayout = null;
            pointsInput = null;
            cashInput = null;
            customerButton = null;
            cashierButton = null;
            confirmButton = null;
            submitProgress = null;
            paymentSpinner = null;
            pointsHint = null;
            if (summaryDialog != null) {
                summaryDialog = null;
            }
        });
        summaryDialog.show();
        updateSummaryEstimate();
    }

    private void renderTicketLines(LinearLayout content) {
        if (cart.isEmpty()) {
            TextView empty = createText(getString(R.string.sale_empty_ticket),
                    R.color.muted, R.dimen.body_text_size);
            addFormView(content, empty);
            return;
        }
        for (Map.Entry<Long, CartItem> entry : cart.entrySet()) {
            content.addView(createTicketLine(entry.getKey(), entry.getValue()), wrapParams());
        }
    }

    private View createTicketLine(long id, CartItem item) {
        MaterialCardView card = createCard();
        LinearLayout body = new LinearLayout(requireContext());
        body.setOrientation(LinearLayout.VERTICAL);
        int padding = getResources().getDimensionPixelSize(R.dimen.space_3);
        body.setPadding(padding, padding, padding, padding);

        TextView name = createText(productName(item.product), R.color.ink,
                R.dimen.card_title_text_size);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        body.addView(name, wrapParams());

        LinearLayout controls = new LinearLayout(requireContext());
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        TextView subtotal = createText("", R.color.accent, R.dimen.body_text_size);
        subtotal.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        subtotal.setText(money(decimalValue(item.product.get("precio"))
                .multiply(BigDecimal.valueOf(item.quantity))));
        LinearLayout.LayoutParams subtotalParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        controls.addView(subtotal, subtotalParams);

        MaterialButton decrease = createSmallButton("-");
        decrease.setContentDescription(getString(R.string.sale_decrease_quantity));
        controls.addView(decrease, new LinearLayout.LayoutParams(
                getResources().getDimensionPixelSize(R.dimen.touch_target_min),
                getResources().getDimensionPixelSize(R.dimen.touch_target_min)));

        TextInputLayout quantityLayout = createInputLayout(getString(R.string.sale_quantity));
        quantityLayout.setBoxBackgroundColor(requireContext().getColor(R.color.surface));
        TextInputEditText quantity = createEditText(InputType.TYPE_CLASS_NUMBER,
                String.valueOf(item.quantity));
        quantity.setGravity(Gravity.CENTER);
        quantityLayout.addView(quantity);
        LinearLayout.LayoutParams quantityParams = new LinearLayout.LayoutParams(
                getResources().getDimensionPixelSize(R.dimen.pos_quantity_input_width),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        controls.addView(quantityLayout, quantityParams);

        MaterialButton increase = createSmallButton("+");
        increase.setContentDescription(getString(R.string.sale_increase_quantity));
        controls.addView(increase, new LinearLayout.LayoutParams(
                getResources().getDimensionPixelSize(R.dimen.touch_target_min),
                getResources().getDimensionPixelSize(R.dimen.touch_target_min)));
        body.addView(controls, wrapParams());

        MaterialButton remove = new MaterialButton(requireContext());
        remove.setText(R.string.sale_remove_line);
        remove.setAllCaps(false);
        remove.setTextColor(requireContext().getColor(R.color.danger));
        remove.setStrokeColor(ColorStateList.valueOf(
                requireContext().getColor(R.color.danger_border)));
        remove.setStrokeWidth(getResources().getDimensionPixelSize(R.dimen.card_stroke_width));
        remove.setBackgroundTintList(
                ColorStateList.valueOf(requireContext().getColor(R.color.surface)));
        remove.setCornerRadius(
                getResources().getDimensionPixelSize(R.dimen.button_corner_radius));
        LinearLayout.LayoutParams removeParams = wrapParams();
        removeParams.topMargin = getResources().getDimensionPixelSize(R.dimen.space_1);
        body.addView(remove, removeParams);
        card.addView(body);

        TicketLineViews lineViews = new TicketLineViews(quantity, subtotal, item);
        ticketLineViews.put(id, lineViews);
        decrease.setOnClickListener(ignored -> {
            if (item.quantity > 1) {
                item.quantity--;
                quantity.setText(String.valueOf(item.quantity));
            }
        });
        increase.setOnClickListener(ignored -> {
            int stock = integerValue(firstValue(item.product, "stock", "existencias",
                    "stockDisponible"));
            if (item.quantity >= stock) {
                quantityLayout.setError(getString(R.string.sale_stock_limit, stock));
                return;
            }
            item.quantity++;
            quantity.setText(String.valueOf(item.quantity));
        });
        remove.setOnClickListener(ignored -> {
            cart.remove(id);
            updateTicketButton();
            if (cart.isEmpty()) {
                summaryDialog.dismiss();
            } else {
                summaryDialog.dismiss();
                showTicketSummary();
            }
        });
        quantity.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence value, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence value, int start, int before, int count) {
                String entered = value.toString().trim();
                if (entered.isEmpty()) {
                    return;
                }
                int requested = integerValue(value);
                int stock = integerValue(firstValue(item.product, "stock", "existencias",
                        "stockDisponible"));
                if (requested < 1) {
                    quantityLayout.setError(getString(R.string.sale_quantity_invalid));
                } else if (requested > stock) {
                    quantityLayout.setError(getString(R.string.sale_stock_limit, stock));
                } else {
                    quantityLayout.setError(null);
                    item.quantity = requested;
                    updateTicketLine(id);
                    updateSummaryEstimate();
                    updateTicketButton();
                }
            }

            @Override
            public void afterTextChanged(Editable value) {
            }
        });
        return card;
    }

    private void updateTicketLine(long id) {
        TicketLineViews line = ticketLineViews.get(id);
        if (line != null) {
            BigDecimal subtotal = decimalValue(line.item.product.get("precio"))
                    .multiply(BigDecimal.valueOf(line.item.quantity));
            line.subtotal.setText(money(subtotal));
        }
    }

    private void addEstimateCard(LinearLayout parent) {
        MaterialCardView card = createCard();
        LinearLayout rows = new LinearLayout(requireContext());
        rows.setOrientation(LinearLayout.VERTICAL);
        int padding = getResources().getDimensionPixelSize(R.dimen.space_3);
        rows.setPadding(padding, padding, padding, padding);
        addAmountRow(rows, R.string.sale_label_subtotal, "subtotal", false);
        addAmountRow(rows, R.string.sale_label_promotions, "promotions", false);
        addAmountRow(rows, R.string.sale_label_loyalty, "loyalty", false);
        addAmountRow(rows, R.string.sale_label_points, "points", false);
        TextView totalLabel = createText(getString(R.string.sale_label_total),
                R.color.ink, R.dimen.body_text_size);
        totalLabel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        TextView totalValue = createText("", R.color.accent, R.dimen.pos_total_text_size);
        totalValue.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        summaryValues.put("total", totalValue);
        LinearLayout totalRow = new LinearLayout(requireContext());
        totalRow.setGravity(Gravity.CENTER_VERTICAL);
        totalRow.addView(totalLabel, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        totalRow.addView(totalValue, wrapParams());
        LinearLayout.LayoutParams totalParams = wrapParams();
        totalParams.topMargin = getResources().getDimensionPixelSize(R.dimen.space_2);
        rows.addView(totalRow, totalParams);
        card.addView(rows);
        addFormView(parent, card);
    }

    private void addAmountRow(
            LinearLayout parent,
            int labelResource,
            String key,
            boolean emphasized
    ) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = createText(getString(labelResource), R.color.muted,
                R.dimen.body_text_size);
        TextView value = createText("", emphasized ? R.color.accent : R.color.ink,
                R.dimen.body_text_size);
        if (emphasized) {
            value.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        row.addView(label, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(value, wrapParams());
        LinearLayout.LayoutParams params = wrapParams();
        params.bottomMargin = getResources().getDimensionPixelSize(R.dimen.space_1);
        parent.addView(row, params);
        summaryValues.put(key + "_label", label);
        summaryValues.put(key, value);
    }

    private void updateSummaryEstimate() {
        if (summaryValues.isEmpty()) {
            updateTicketButton();
            return;
        }
        int requestedPoints = parsePositiveInteger(pointsInput == null ? "0" : textOf(pointsInput));
        SaleEstimate estimate = estimate(requestedPoints);
        setSummaryValue("subtotal", money(estimate.subtotal));
        setSummaryValue("promotions", money(estimate.promotionDiscount.negate()));
        setSummaryValue("loyalty", money(estimate.loyaltyDiscount.negate()));
        setSummaryValue("points", money(estimate.pointsDiscount.negate()));
        setSummaryValue("total", money(estimate.total));
        if (pointsHint != null) {
            pointsHint.setText(getString(R.string.sale_estimate_max_points,
                    estimate.availablePoints, estimate.maxPoints));
        }
        updateTicketButton();
    }

    private void setSummaryValue(String key, String value) {
        TextView valueView = summaryValues.get(key);
        if (valueView != null) {
            valueView.setText(value);
        }
    }

    private SaleEstimate estimate(int requestedPoints) {
        JsonArray details = new JsonArray();
        for (CartItem item : cart.values()) {
            JsonObject detail = lineMapper.fromProduct(item.product, item.quantity);
            detail.add(SaleEstimate.PREVIEW_PRODUCT_KEY, item.product);
            details.add(detail);
        }
        return SaleEstimate.calculate(
                details, selectedCustomer, promotions, LocalDate.now(), requestedPoints);
    }

    private void chooseCustomer() {
        if (!customersLoaded) {
            if (!customersLoading) {
                loadCustomers();
            }
            String message = customersLoading
                    ? getString(R.string.sale_options_loading)
                    : getString(R.string.sale_customer_options_error) + " "
                            + String.valueOf(customerLoadError);
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
            return;
        }
        showEntityPicker(getString(R.string.menu_clients), customers, selectedCustomer,
                option -> {
                    selectedCustomer = option;
                    customerButton.setText(entityName(option));
                    updateSummaryEstimate();
                });
    }

    private void chooseCashier() {
        if (!cashiersLoaded) {
            if (!cashiersLoading) {
                loadCashiers();
            }
            String message = cashiersLoading
                    ? getString(R.string.sale_options_loading)
                    : getString(R.string.sale_cashier_options_error) + " "
                            + String.valueOf(cashierLoadError);
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
            return;
        }
        showEntityPicker(getString(R.string.menu_cashiers), cashiers, selectedCashier,
                option -> {
                    selectedCashier = option;
                    cashierButton.setText(entityName(option));
                });
    }

    private void showEntityPicker(
            String title,
            List<JsonObject> options,
            JsonObject selected,
            JsonObjectCallback callback
    ) {
        BottomSheetDialog picker = new BottomSheetDialog(requireContext());
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        int padding = getResources().getDimensionPixelSize(R.dimen.space_4);
        root.setPadding(padding, padding, padding, padding);
        TextView heading = createText(title, R.color.ink, R.dimen.section_title_text_size);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(heading, wrapParams());

        TextInputLayout searchLayout = createInputLayout(getString(R.string.resource_search));
        TextInputEditText search = createEditText(InputType.TYPE_CLASS_TEXT, "");
        searchLayout.addView(search);
        LinearLayout.LayoutParams searchParams = wrapParams();
        searchParams.topMargin = getResources().getDimensionPixelSize(R.dimen.space_2);
        root.addView(searchLayout, searchParams);

        ScrollView scroll = new ScrollView(requireContext());
        LinearLayout rows = new LinearLayout(requireContext());
        rows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rows);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                getResources().getDimensionPixelSize(R.dimen.entity_sheet_max_height));
        scrollParams.topMargin = getResources().getDimensionPixelSize(R.dimen.space_2);
        root.addView(scroll, scrollParams);

        Runnable renderOptions = () -> {
            if (!isAdded()) {
                return;
            }
            rows.removeAllViews();
            String query = textOf(search).toLowerCase(Locale.ROOT);
            int shown = 0;
            for (JsonObject option : options) {
                String name = entityName(option);
                String email = stringValue(option.get("email"));
                String secondary = email.isEmpty() ? entitySubtitle(option) : email;
                if (!query.isEmpty()
                        && !name.toLowerCase(Locale.ROOT).contains(query)
                        && !secondary.toLowerCase(Locale.ROOT).contains(query)) {
                    continue;
                }
                rows.addView(createEntityOption(option, name, secondary,
                        selected != null && idOf(selected) == idOf(option), () -> {
                            callback.onSelect(option);
                            picker.dismiss();
                        }), wrapParams());
                shown++;
            }
            if (shown == 0) {
                TextView empty = createText(getString(R.string.resource_empty),
                        R.color.muted, R.dimen.body_text_size);
                empty.setGravity(Gravity.CENTER);
                rows.addView(empty, wrapParams());
            }
        };
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence value, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence value, int start, int before, int count) {
                renderOptions.run();
            }

            @Override
            public void afterTextChanged(Editable value) {
            }
        });
        picker.setContentView(root);
        picker.show();
        renderOptions.run();
    }

    private View createEntityOption(
            JsonObject option,
            String name,
            String subtitle,
            boolean selected,
            Runnable onClick
    ) {
        MaterialCardView card = createCard();
        card.setCardBackgroundColor(requireContext().getColor(
                selected ? R.color.soft : R.color.surface));
        LinearLayout row = new LinearLayout(requireContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        int padding = getResources().getDimensionPixelSize(R.dimen.space_3);
        row.setPadding(padding, padding, padding, padding);
        LinearLayout labels = new LinearLayout(requireContext());
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView nameView = createText(name, R.color.ink, R.dimen.body_text_size);
        nameView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        labels.addView(nameView, wrapParams());
        if (!subtitle.isEmpty()) {
            TextView subtitleView = createText(subtitle, R.color.muted, R.dimen.caption_text_size);
            labels.addView(subtitleView, wrapParams());
        }
        row.addView(labels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (selected) {
            TextView check = createText("✓", R.color.accent, R.dimen.card_title_text_size);
            check.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            row.addView(check, wrapParams());
        }
        card.addView(row);
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(ignored -> onClick.run());
        return card;
    }

    private void confirmSale() {
        if (submitting) {
            return;
        }
        if (cart.isEmpty()) {
            showValidationError(getString(R.string.sale_empty_ticket));
            return;
        }
        if (selectedCustomer == null || idOf(selectedCustomer) <= 0) {
            showValidationError(getString(R.string.sale_customer_required));
            return;
        }
        long cashierId = "CAJERO".equals(user.getRol())
                ? user.getId()
                : idOf(selectedCashier);
        if (cashierId <= 0) {
            showValidationError(getString(R.string.sale_cashier_required));
            return;
        }
        String pointsText = pointsInput == null ? "0" : textOf(pointsInput);
        int points;
        try {
            points = pointsText.isEmpty() ? 0 : Integer.parseInt(pointsText);
        } catch (NumberFormatException error) {
            pointsInputLayout.setError(getString(R.string.sale_quantity_invalid));
            return;
        }
        SaleEstimate estimate = estimate(points);
        if (points < 0 || points > estimate.maxPoints) {
            pointsInputLayout.setError(getString(
                    R.string.sale_points_invalid, estimate.maxPoints));
            return;
        }
        pointsInputLayout.setError(null);

        String paymentMethod = paymentMethodAt(paymentSpinner.getSelectedItemPosition());
        BigDecimal received = BigDecimal.ZERO;
        if (SaleFormData.requiresCashReceived(paymentMethod)) {
            try {
                received = new BigDecimal(textOf(cashInput));
            } catch (NumberFormatException error) {
                cashInputLayout.setError(getString(R.string.sale_cash_invalid));
                return;
            }
            if (!SaleFormData.isCashReceivedSufficient(received, estimate.total)) {
                cashInputLayout.setError(getString(
                        R.string.sale_cash_short, money(estimate.total)));
                return;
            }
            cashInputLayout.setError(null);
        }

        JsonObject request = new JsonObject();
        request.addProperty("fecha", LocalDateTime.now().withNano(0).toString());
        request.addProperty("cajeroId", cashierId);
        request.addProperty("clienteId", idOf(selectedCustomer));
        request.addProperty("puntosUtilizados", points);
        request.addProperty("metodoPago", paymentMethod);
        if (SaleFormData.requiresCashReceived(paymentMethod)) {
            request.addProperty("efectivoRecibido", received);
            request.addProperty("cambio", received.subtract(estimate.total)
                    .max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
        }
        JsonArray details = new JsonArray();
        for (CartItem item : cart.values()) {
            details.add(lineMapper.fromProduct(item.product, item.quantity));
        }
        request.add("detalles", details);

        submitting = true;
        confirmButton.setEnabled(false);
        confirmButton.setText(R.string.sale_submitting);
        submitProgress.setVisibility(View.VISIBLE);
        viewModel.create("ventas", request, new ApiRepository.ResultCallback<JsonElement>() {
            @Override
            public void onSuccess(JsonElement result) {
                if (!isAdded() || binding == null) {
                    return;
                }
                submitting = false;
                long saleId = idOfResult(result);
                BigDecimal total = responseTotal(result);
                cart.clear();
                selectedCustomer = null;
                restoredPoints = "0";
                restoredCash = "";
                restoredPayment = 0;
                if (pointsInput != null) {
                    pointsInput.setText("0");
                }
                if (cashInput != null) {
                    cashInput.setText("");
                }
                if (paymentSpinner != null) {
                    paymentSpinner.setSelection(0);
                }
                summaryDialog.dismiss();
                updateTicketButton();
                if (saleId > 0) {
                    showRegisteredSale(saleId, total);
                } else {
                    new AlertDialog.Builder(requireContext())
                            .setTitle(R.string.sale_ticket_actions_title)
                            .setMessage(R.string.sale_missing_id)
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                }
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) {
                    return;
                }
                submitting = false;
                submitProgress.setVisibility(View.GONE);
                confirmButton.setEnabled(true);
                confirmButton.setText(R.string.sale_confirm);
                showValidationError(message);
            }
        });
    }

    private void showValidationError(String message) {
        if (!isAdded()) {
            return;
        }
        View target = summaryDialog == null ? null
                : summaryDialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        Snackbar.make(target == null ? binding.getRoot() : target,
                message, Snackbar.LENGTH_LONG).show();
    }

    private String paymentMethodAt(int position) {
        switch (position) {
            case 1:
                return "TARJETA";
            case 2:
                return "TRANSFERENCIA";
            case 3:
                return "OTRO";
            default:
                return "EFECTIVO";
        }
    }

    private long idOfResult(JsonElement result) {
        if (result == null || !result.isJsonObject()) {
            return 0;
        }
        JsonObject object = result.getAsJsonObject();
        JsonElement id = object.get("id");
        if (id == null || id.isJsonNull()) {
            JsonElement data = object.get("data");
            if (data != null && data.isJsonObject()) {
                id = data.getAsJsonObject().get("id");
            }
        }
        try {
            return id == null || id.isJsonNull() ? 0 : id.getAsLong();
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private BigDecimal responseTotal(JsonElement result) {
        if (result != null && result.isJsonObject()) {
            JsonElement total = result.getAsJsonObject().get("total");
            if (total == null && result.getAsJsonObject().has("data")
                    && result.getAsJsonObject().get("data").isJsonObject()) {
                total = result.getAsJsonObject().getAsJsonObject("data").get("total");
            }
            if (total != null && !total.isJsonNull()) {
                return decimalValue(total);
            }
        }
        return estimate(parsePositiveInteger(restoredPoints)).total;
    }

    private void showRegisteredSale(long saleId, BigDecimal total) {
        String[] actions = {
                getString(R.string.resource_ticket),
                getString(R.string.resource_email_ticket),
                getString(R.string.sale_print),
                getString(R.string.sale_action_done)
        };
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.sale_ticket_actions_title)
                .setMessage(getString(R.string.sale_created_message, money(total)))
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        downloadTicket(saleId);
                    } else if (which == 1) {
                        promptEmailTicket(saleId);
                    } else if (which == 2) {
                        Snackbar.make(binding.getRoot(), R.string.sale_print_unavailable,
                                Snackbar.LENGTH_LONG).show();
                    }
                })
                .show();
    }

    private void downloadTicket(long saleId) {
        File destination = new File(requireContext().getCacheDir(), "ticket_" + saleId + ".pdf");
        viewModel.downloadTicket(saleId, destination,
                new ApiRepository.ResultCallback<File>() {
                    @Override
                    public void onSuccess(File file) {
                        if (!isAdded()) {
                            return;
                        }
                        Uri uri = FileProvider.getUriForFile(
                                requireContext(),
                                requireContext().getPackageName() + ".files",
                                file);
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setDataAndType(uri, "application/pdf");
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        try {
                            startActivity(intent);
                        } catch (ActivityNotFoundException error) {
                            Toast.makeText(requireContext(),
                                    "Ticket descargado en " + file.getAbsolutePath(),
                                    Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onError(String message) {
                        showTicketError(message);
                    }
                });
    }

    private void promptEmailTicket(long saleId) {
        TextInputLayout inputLayout = createInputLayout(getString(R.string.resource_email_hint));
        EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        input.setSingleLine(true);
        inputLayout.addView(input);
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.resource_email_ticket)
                .setView(inputLayout)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(button -> {
                    String recipient = input.getText().toString().trim();
                    if (!Patterns.EMAIL_ADDRESS.matcher(recipient).matches()) {
                        input.setError(getString(R.string.sale_email_invalid));
                        return;
                    }
                    viewModel.emailTicket(saleId, recipient,
                            new ApiRepository.ResultCallback<String>() {
                                @Override
                                public void onSuccess(String result) {
                                    if (isAdded()) {
                                        Snackbar.make(binding.getRoot(),
                                                TextUtils.isEmpty(result)
                                                        ? getString(R.string.sale_email_sent)
                                                        : result,
                                                Snackbar.LENGTH_LONG).show();
                                    }
                                }

                                @Override
                                public void onError(String message) {
                                    showTicketError(message);
                                }
                            });
                    dialog.dismiss();
                }));
        dialog.show();
    }

    private void showTicketError(String message) {
        if (isAdded() && binding != null) {
            Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
        }
    }

    private MaterialButton createDropdownButton(String text) {
        MaterialButton button = new MaterialButton(requireContext());
        button.setText(text);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        button.setTextColor(requireContext().getColor(R.color.ink));
        button.setIconResource(R.drawable.ic_expand_more);
        button.setIconTint(ColorStateList.valueOf(requireContext().getColor(R.color.muted)));
        button.setIconGravity(MaterialButton.ICON_GRAVITY_END);
        button.setIconSize(getResources().getDimensionPixelSize(R.dimen.space_5));
        button.setStrokeColor(ColorStateList.valueOf(requireContext().getColor(R.color.line)));
        button.setStrokeWidth(getResources().getDimensionPixelSize(R.dimen.card_stroke_width));
        button.setBackgroundTintList(
                ColorStateList.valueOf(requireContext().getColor(R.color.surface)));
        button.setCornerRadius(
                getResources().getDimensionPixelSize(R.dimen.button_corner_radius));
        button.setMinHeight(getResources().getDimensionPixelSize(R.dimen.touch_target_min));
        return button;
    }

    private MaterialButton createSmallButton(String text) {
        MaterialButton button = new MaterialButton(requireContext());
        button.setText(text);
        button.setTextColor(requireContext().getColor(R.color.accent));
        button.setBackgroundTintList(
                ColorStateList.valueOf(requireContext().getColor(R.color.surface)));
        button.setStrokeColor(ColorStateList.valueOf(requireContext().getColor(R.color.line)));
        button.setStrokeWidth(getResources().getDimensionPixelSize(R.dimen.card_stroke_width));
        button.setCornerRadius(
                getResources().getDimensionPixelSize(R.dimen.button_corner_radius));
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setMinWidth(getResources().getDimensionPixelSize(R.dimen.touch_target_min));
        button.setMinHeight(getResources().getDimensionPixelSize(R.dimen.touch_target_min));
        return button;
    }

    private TextInputLayout createInputLayout(String hint) {
        TextInputLayout layout = new TextInputLayout(requireContext());
        layout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        layout.setBoxBackgroundColor(requireContext().getColor(R.color.surface));
        layout.setBoxStrokeColor(requireContext().getColor(R.color.input_border));
        layout.setBoxStrokeWidth(getResources()
                .getDimensionPixelSize(R.dimen.card_stroke_width));
        layout.setBoxStrokeWidthFocused(getResources()
                .getDimensionPixelSize(R.dimen.input_focused_stroke_width));
        layout.setHint(hint);
        layout.setHintTextColor(ColorStateList.valueOf(requireContext().getColor(R.color.muted)));
        layout.setBoxCornerRadii(
                getResources().getDimension(R.dimen.input_corner_radius),
                getResources().getDimension(R.dimen.input_corner_radius),
                getResources().getDimension(R.dimen.input_corner_radius),
                getResources().getDimension(R.dimen.input_corner_radius));
        return layout;
    }

    private TextInputEditText createEditText(int inputType, String text) {
        TextInputEditText input = new TextInputEditText(requireContext());
        input.setInputType(inputType);
        input.setTextColor(requireContext().getColor(R.color.ink));
        input.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimension(R.dimen.body_text_size));
        input.setMinHeight(getResources().getDimensionPixelSize(R.dimen.input_min_height));
        input.setSingleLine(true);
        input.setText(text);
        return input;
    }

    private TextView createText(String value, int color, int size) {
        TextView text = new TextView(requireContext());
        text.setText(value);
        text.setTextColor(requireContext().getColor(color));
        text.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(size));
        return text;
    }

    private void addSectionLabel(LinearLayout parent, String text) {
        TextView label = createText(text.toUpperCase(Locale.ROOT),
                R.color.muted, R.dimen.caption_text_size);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams params = wrapParams();
        params.topMargin = getResources().getDimensionPixelSize(R.dimen.space_3);
        params.bottomMargin = getResources().getDimensionPixelSize(R.dimen.space_1);
        parent.addView(label, params);
    }

    private void addFormView(LinearLayout parent, View child) {
        LinearLayout.LayoutParams params = wrapParams();
        params.bottomMargin = getResources().getDimensionPixelSize(R.dimen.space_2);
        parent.addView(child, params);
    }

    private LinearLayout.LayoutParams wrapParams() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private MaterialCardView createCard() {
        MaterialCardView card = new MaterialCardView(requireContext());
        card.setCardBackgroundColor(requireContext().getColor(R.color.surface));
        card.setRadius(getResources().getDimension(R.dimen.card_corner_radius));
        card.setCardElevation(getResources().getDimension(R.dimen.card_elevation));
        card.setStrokeWidth(getResources().getDimensionPixelSize(R.dimen.card_stroke_width));
        card.setStrokeColor(requireContext().getColor(R.color.line));
        return card;
    }

    private String productName(JsonObject product) {
        String name = stringValue(product.get("nombre"));
        return name.isEmpty() ? getString(R.string.catalog_product_image) : name;
    }

    private String productCategory(JsonObject product) {
        JsonElement category = firstValue(product, "categoriaNombre", "categoria");
        if (category != null && category.isJsonObject()) {
            category = firstValue(category.getAsJsonObject(), "nombre", "descripcion");
        }
        String name = stringValue(category);
        return name.isEmpty() ? getString(R.string.sale_product_category_fallback) : name;
    }

    private String entityName(JsonObject entity) {
        String full = stringValue(entity.get("nombreCompleto"));
        if (!full.isEmpty()) {
            return full;
        }
        String first = stringValue(entity.get("nombre"));
        String last = stringValue(entity.get("apellido"));
        String name = (first + " " + last).trim();
        if (!name.isEmpty()) {
            return name;
        }
        String email = stringValue(entity.get("email"));
        return email.isEmpty() ? getString(R.string.resource_detail_record_number, idOf(entity)) : email;
    }

    private String entitySubtitle(JsonObject entity) {
        for (String key : new String[]{"numeroTelefono", "turno", "nivelLealtad"}) {
            String value = stringValue(entity.get(key));
            if (!value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    private long idOf(JsonObject entity) {
        if (entity == null) {
            return 0;
        }
        try {
            JsonElement id = entity.get("id");
            return id == null || id.isJsonNull() ? 0 : id.getAsLong();
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private JsonElement firstValue(JsonObject object, String... keys) {
        for (String key : keys) {
            JsonElement value = object.get(key);
            if (value != null && !value.isJsonNull()) {
                return value;
            }
        }
        return null;
    }

    private String stringValue(JsonElement value) {
        if (value == null || value.isJsonNull()) {
            return "";
        }
        if (value.isJsonPrimitive()) {
            return value.getAsString().trim();
        }
        return "";
    }

    private int integerValue(JsonElement value) {
        try {
            return value == null || value.isJsonNull() ? 0 : value.getAsInt();
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private int integerValue(CharSequence value) {
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private int parsePositiveInteger(String value) {
        try {
            return Math.max(Integer.parseInt(value), 0);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private BigDecimal decimalValue(JsonElement value) {
        try {
            return value == null || value.isJsonNull()
                    ? BigDecimal.ZERO
                    : value.getAsBigDecimal();
        } catch (RuntimeException ignored) {
            return BigDecimal.ZERO;
        }
    }

    private String money(BigDecimal value) {
        return NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-MX")).format(value);
    }

    private String textOf(EditText input) {
        return input == null || input.getText() == null
                ? ""
                : input.getText().toString().trim();
    }

    @Override
    public void onDestroyView() {
        searchHandler.removeCallbacksAndMessages(null);
        if (summaryDialog != null) {
            summaryDialog.dismiss();
            summaryDialog = null;
        }
        binding = null;
        super.onDestroyView();
    }

    private interface JsonListCallback {
        void onSuccess(List<JsonObject> records);

        void onError(String message);
    }

    private interface JsonObjectCallback {
        void onSelect(JsonObject option);
    }

    private static final class CartItem {
        final JsonObject product;
        int quantity;

        CartItem(JsonObject product, int quantity) {
            this.product = product;
            this.quantity = quantity;
        }
    }

    private static final class TicketLineViews {
        final TextInputEditText quantity;
        final TextView subtotal;
        final CartItem item;

        TicketLineViews(TextInputEditText quantity, TextView subtotal, CartItem item) {
            this.quantity = quantity;
            this.subtotal = subtotal;
            this.item = item;
        }
    }
}
