package com.example.inchekyandroid.ui.home;

import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.res.ColorStateList;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Patterns;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.view.LayoutInflater;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.view.inputmethod.EditorInfo;

import androidx.appcompat.widget.PopupMenu;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.example.inchekyandroid.R;
import com.example.inchekyandroid.data.model.UsuarioResponse;
import com.example.inchekyandroid.data.model.ApiRequestMapper;
import com.example.inchekyandroid.data.model.FormDateValidator;
import com.example.inchekyandroid.data.model.SaleFormData;
import com.example.inchekyandroid.data.model.SaleEstimate;
import com.example.inchekyandroid.data.model.SaleLineRequestMapper;
import com.example.inchekyandroid.data.repository.ApiRepository;
import com.example.inchekyandroid.data.repository.SessionRepository;
import com.example.inchekyandroid.data.remote.RetrofitClient;
import com.example.inchekyandroid.data.remote.ProductImageUrl;
import com.example.inchekyandroid.databinding.ActivityResourceBinding;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.bumptech.glide.Glide;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class ResourceFragment extends Fragment {
    private static final int PAGE_SIZE = 20;
    private static final int INPUT_TEXT = 0;
    private static final int INPUT_NUMBER = 1;
    private static final int INPUT_DECIMAL = 2;
    private static final int INPUT_EMAIL = 3;
    private static final int INPUT_PASSWORD = 4;
    private static final int INPUT_DATE = 5;
    private static final int INPUT_DATETIME = 6;
    private static final int INPUT_BOOLEAN = 7;
    private static final int INPUT_ENUM = 8;
    private static final int INPUT_RELATION = 9;
    private static final int INPUT_MULTI_RELATION = 10;

    private ActivityResourceBinding binding;
    private ResourceViewModel viewModel;
    private ApiResource resource;
    private UsuarioResponse user;
    private int page;
    private int lastLoadedPage;
    private int totalPages = 1;
    private File selectedImage;
    private String selectedImageMimeType;
    private boolean productUploadInProgress;
    private ActivityResultLauncher<String> imagePicker;
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchTask;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        imagePicker = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                this::copySelectedImage
        );
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        binding = ActivityResourceBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(this).get(ResourceViewModel.class);
        observeRecords();
        binding.btnResourceCreate.setEnabled(false);
        binding.btnResourceImage.setEnabled(false);
        binding.btnPreviousPage.setEnabled(false);
        binding.btnNextPage.setEnabled(false);
        searchTask = () -> {
            page = 0;
            loadRecords();
        };
        binding.etResourceQuery.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        binding.etResourceQuery.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH
                    || (event != null && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER
                    && event.getAction() == android.view.KeyEvent.ACTION_DOWN)) {
                searchHandler.removeCallbacks(searchTask);
                searchTask.run();
                return true;
            }
            return false;
        });
        binding.etResourceQuery.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence value, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence value, int start, int before, int count) {
                searchHandler.removeCallbacks(searchTask);
                searchHandler.postDelayed(searchTask, 350);
            }

            @Override
            public void afterTextChanged(Editable value) {
            }
        });
        binding.btnResourceCreate.setOnClickListener(ignored -> {
            if (resource != null && "ventas".equals(resource.key)) {
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_resource_to_sale);
            } else {
                showEditor(null, 0);
            }
        });
        binding.btnResourceImage.setOnClickListener(ignored -> imagePicker.launch("image/*"));
        binding.btnPreviousPage.setOnClickListener(ignored -> {
            if (page > 0) {
                page--;
                loadRecords();
            }
        });
        binding.btnNextPage.setOnClickListener(ignored -> {
            if (page + 1 < totalPages) {
                page++;
                loadRecords();
            }
        });
        loadRoleAndResource();
    }

    private void loadRoleAndResource() {
        String key = getArguments() == null ? null : getArguments().getString("resource");
        new SessionRepository().currentUser(new SessionRepository.SessionCallback() {
            @Override
            public void onSuccess(UsuarioResponse currentUser) {
                if (!isAdded() || binding == null) {
                    return;
                }
                user = currentUser;
                resource = findResource(key, ApiResource.forRole(user.getRol()));
                if (resource == null) {
                    Toast.makeText(requireContext(), R.string.home_no_features,
                            Toast.LENGTH_LONG).show();
                    NavHostFragment.findNavController(ResourceFragment.this).popBackStack();
                    return;
                }
                configureScreen();
                loadRecords();
            }

            @Override
            public void onError(String message) {
                if (!isAdded() || binding == null) {
                    return;
                }
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                if (!RetrofitClient.hasSessionCookie()) {
                    NavHostFragment.findNavController(ResourceFragment.this)
                            .navigate(R.id.action_global_login);
                } else {
                    NavHostFragment.findNavController(ResourceFragment.this).popBackStack();
                }
            }
        });
    }

    private void configureScreen() {
        binding.searchControls.setVisibility(resource.searchable ? View.VISIBLE : View.GONE);
        binding.btnResourceCreate.setVisibility(resource.canCreate ? View.VISIBLE : View.GONE);
        boolean productCreate = "productos".equals(resource.key) && resource.canCreate;
        binding.btnResourceImage.setVisibility(productCreate ? View.VISIBLE : View.GONE);
        binding.btnResourceCreate.setEnabled(resource.canCreate);
        binding.btnResourceImage.setEnabled(productCreate);
    }

    private void loadRecords() {
        if (resource == null || binding == null) {
            return;
        }
        String query = binding.etResourceQuery.getText() == null
                ? ""
                : binding.etResourceQuery.getText().toString();
        viewModel.load(resource.path, page, PAGE_SIZE,
                resource.searchable ? query : null);
    }

    private void observeRecords() {
        viewModel.getLoading().observe(getViewLifecycleOwner(), loading -> {
            if (binding == null) {
                return;
            }
            binding.resourceLoading.setVisibility(
                    Boolean.TRUE.equals(loading) ? View.VISIBLE : View.GONE);
            boolean isLoading = Boolean.TRUE.equals(loading);
            binding.resourceRecords.setVisibility(isLoading ? View.GONE : View.VISIBLE);
            if (!isLoading && page == lastLoadedPage) {
                showPageNumber();
            }
            updatePagerControls(isLoading);
        });
        viewModel.getError().observe(getViewLifecycleOwner(), message -> {
            if (message != null && !message.isEmpty()) {
                page = lastLoadedPage;
                showPageNumber();
                updatePagerControls(false);
                showListError(message);
            }
        });
        viewModel.getRecords().observe(getViewLifecycleOwner(), this::renderPage);
    }

    private void renderPage(JsonElement response) {
        if (binding == null || response == null || !response.isJsonObject()) {
            restoreLastLoadedPage();
            showListError(getString(R.string.resource_invalid_response));
            return;
        }
        JsonObject pageResult = response.getAsJsonObject();
        JsonElement content = pageResult.get("content");
        if (content == null || !content.isJsonArray()) {
            restoreLastLoadedPage();
            showListError(getString(R.string.resource_invalid_response));
            return;
        }
        JsonArray records = content.getAsJsonArray();
        PageInfo pageInfo = PageInfo.from(pageResult, page);
        if (pageInfo.requiresReload) {
            page = pageInfo.number;
            loadRecords();
            return;
        }
        page = pageInfo.number;
        lastLoadedPage = page;
        totalPages = pageInfo.totalPages;
        showPageNumber();
        updatePagerControls(false);
        binding.resourceRecords.removeAllViews();
        binding.resourceRecords.setVisibility(View.VISIBLE);
        if (records.size() == 0) {
            showMessage(R.string.resource_empty);
            return;
        }
        for (JsonElement record : records) {
            addRecordCard(record);
        }
    }

    private void restoreLastLoadedPage() {
        page = lastLoadedPage;
        showPageNumber();
        updatePagerControls(false);
    }

    private void showPageNumber() {
        if (binding != null) {
            binding.tvPageNumber.setText(
                    getString(R.string.resource_page, page + 1, totalPages));
        }
    }

    private void updatePagerControls(boolean loading) {
        if (binding == null) {
            return;
        }
        binding.btnPreviousPage.setEnabled(!loading && page > 0);
        binding.btnNextPage.setEnabled(!loading && page + 1 < totalPages);
        binding.btnResourceCreate.setEnabled(
                !loading && resource != null && resource.canCreate);
        binding.btnResourceImage.setEnabled(
                !loading && resource != null && "productos".equals(resource.key)
                        && resource.canCreate);
    }

    private void showDatePicker(TextInputEditText input, boolean includeTime) {
        LocalDate today = LocalDate.now();
        new DatePickerDialog(requireContext(), (picker, year, month, day) -> {
            LocalDate selected = LocalDate.of(year, month + 1, day);
            if (!includeTime) {
                input.setText(selected.toString());
                return;
            }
            java.time.LocalTime now = java.time.LocalTime.now();
            new TimePickerDialog(requireContext(), (timePicker, hour, minute) ->
                    input.setText(selected + "T" + String.format(
                            java.util.Locale.ROOT, "%02d:%02d:00", hour, minute)),
                    now.getHour(), now.getMinute(), true).show();
        }, today.getYear(), today.getMonthValue() - 1, today.getDayOfMonth()).show();
    }

    private void addRecordCard(JsonElement record) {
        MaterialCardView card = new MaterialCardView(requireContext());
        card.setCardBackgroundColor(requireContext().getColor(R.color.surface));
        card.setRadius(getResources().getDimension(R.dimen.card_corner_radius));
        card.setCardElevation(getResources().getDimension(R.dimen.card_elevation));
        card.setStrokeWidth(getResources().getDimensionPixelSize(R.dimen.card_stroke_width));
        card.setStrokeColor(requireContext().getColor(R.color.line));
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        int horizontalPadding = getResources().getDimensionPixelSize(R.dimen.space_4);
        int verticalPadding = getResources().getDimensionPixelSize(R.dimen.space_3);
        content.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding);
        if (("catalogo".equals(resource.key) || "productos".equals(resource.key))
                && record.isJsonObject()) {
            addProductImage(content, record.getAsJsonObject());
        }
        long id = record.isJsonObject() && record.getAsJsonObject().has("id")
                ? record.getAsJsonObject().get("id").getAsLong()
                : -1;
        JsonObject object = record.isJsonObject() ? record.getAsJsonObject() : new JsonObject();
        addRecordHeader(content, object, id, record);
        addRecordMetadata(content, object);
        addRecordStatus(content, object);
        if (id > 0 && resource.canDetail) {
            String name = recordTitle(object, id);
            card.setContentDescription(getString(R.string.resource_open_detail, name));
            card.setClickable(true);
            card.setFocusable(true);
            card.setOnClickListener(ignored -> showDetails(id));
        }
        card.addView(content);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = getResources().getDimensionPixelSize(R.dimen.space_3);
        binding.resourceRecords.addView(card, params);
    }

    private void addRecordHeader(
            LinearLayout parent,
            JsonObject object,
            long id,
            JsonElement record
    ) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);

        TextView title = new TextView(requireContext());
        title.setText(recordTitle(object, id));
        title.setTextColor(requireContext().getColor(R.color.ink));
        title.setTextSize(getResources().getDimension(R.dimen.card_title_text_size)
                / getResources().getDisplayMetrics().scaledDensity);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setMaxLines(2);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        row.addView(title, titleParams);

        if (id > 0) {
            TextView idChip = new TextView(requireContext());
            idChip.setText(getString(R.string.resource_id_chip, id));
            idChip.setTextColor(requireContext().getColor(R.color.chip_text));
            idChip.setTextSize(getResources().getDimension(R.dimen.caption_text_size)
                    / getResources().getDisplayMetrics().scaledDensity);
            idChip.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            idChip.setBackgroundResource(R.drawable.chip_background);
            int horizontal = getResources().getDimensionPixelSize(R.dimen.space_2);
            int vertical = getResources().getDimensionPixelSize(R.dimen.space_1);
            idChip.setPadding(horizontal, vertical, horizontal, vertical);
            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            chipParams.setMarginStart(getResources().getDimensionPixelSize(R.dimen.space_2));
            row.addView(idChip, chipParams);
        }

        if (hasRecordActions(id)) {
            MaterialButton more = createOverflowButton();
            LinearLayout.LayoutParams moreParams = new LinearLayout.LayoutParams(
                    getResources().getDimensionPixelSize(R.dimen.touch_target_min),
                    getResources().getDimensionPixelSize(R.dimen.touch_target_min));
            moreParams.setMarginStart(getResources().getDimensionPixelSize(R.dimen.space_1));
            row.addView(more, moreParams);
            more.setOnClickListener(anchor -> showRecordMenu(more, record, id));
        }
        parent.addView(row);
    }

    private void addRecordMetadata(LinearLayout parent, JsonObject object) {
        int lines = 0;
        for (String field : new String[]{"email", "rol", "numeroEmpleado", "numeroAdministrador",
                "turno", "cajaAsignada", "categoriaNombre", "codigoBarras", "precio", "total",
                "stock", "puntos", "nivelLealtad", "proveedorNombre", "clienteNombre",
                "cajeroNombre", "fecha", "metodoPago"}) {
            String value = primitiveValue(object.get(field));
            if (value.isEmpty()) {
                continue;
            }
            TextView detail = new TextView(requireContext());
            detail.setText(fieldLabel(field) + ": " + value);
            boolean price = "precio".equals(field) || "total".equals(field);
            detail.setTextColor(requireContext().getColor(
                    price ? R.color.accent : R.color.muted));
            detail.setTextSize(getResources().getDimension(R.dimen.body_text_size)
                    / getResources().getDisplayMetrics().scaledDensity);
            if (price) {
                detail.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            }
            detail.setMaxLines(1);
            detail.setEllipsize(android.text.TextUtils.TruncateAt.END);
            parent.addView(detail);
            lines++;
            if (lines == 3) {
                break;
            }
        }
    }

    private void addRecordStatus(LinearLayout parent, JsonObject object) {
        JsonElement state = object.has("activa") ? object.get("activa") : object.get("activo");
        if (state == null || state.isJsonNull()) {
            return;
        }
        boolean active = state.isJsonPrimitive() && state.getAsJsonPrimitive().isBoolean()
                ? state.getAsBoolean()
                : "ACTIVO".equalsIgnoreCase(state.getAsString())
                        || "ACTIVA".equalsIgnoreCase(state.getAsString());
        TextView status = new TextView(requireContext());
        status.setText(active ? R.string.status_active : R.string.status_inactive);
        status.setTextColor(requireContext().getColor(
                active ? R.color.success : R.color.muted_on_line));
        status.setTextSize(getResources().getDimension(R.dimen.caption_text_size)
                / getResources().getDisplayMetrics().scaledDensity);
        status.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        status.setPadding(
                getResources().getDimensionPixelSize(R.dimen.space_2),
                getResources().getDimensionPixelSize(R.dimen.space_1),
                getResources().getDimensionPixelSize(R.dimen.space_2),
                getResources().getDimensionPixelSize(R.dimen.space_1));
        status.setBackgroundResource(active
                ? R.drawable.status_active_background
                : R.drawable.chip_background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = getResources().getDimensionPixelSize(R.dimen.space_1);
        parent.addView(status, params);
    }

    private String recordTitle(JsonObject object, long id) {
        for (String field : new String[]{"nombre", "productoNombre", "clienteNombre",
                "proveedorNombre", "cajeroNombre", "administradorNombre", "descripcion"}) {
            String value = primitiveValue(object.get(field));
            if (!value.isEmpty()) {
                return value;
            }
        }
        String email = primitiveValue(object.get("email"));
        return email.isEmpty() ? getString(R.string.resource_detail_record_number, id) : email;
    }

    private String primitiveValue(JsonElement value) {
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) {
            return "";
        }
        String text = value.getAsString().trim();
        return text;
    }

    private boolean hasRecordActions(long id) {
        return id > 0 && (resource.canDetail || resource.canUpdate || resource.canDelete
                || "ventas".equals(resource.key) || "compras".equals(resource.key));
    }

    private MaterialButton createOverflowButton() {
        MaterialButton button = new MaterialButton(requireContext());
        button.setText(null);
        button.setContentDescription(getString(R.string.resource_more_actions));
        button.setIconResource(R.drawable.ic_more);
        button.setIconSize(getResources().getDimensionPixelSize(R.dimen.space_3));
        button.setIconTint(ColorStateList.valueOf(requireContext().getColor(R.color.muted)));
        button.setBackgroundTintList(ColorStateList.valueOf(
                requireContext().getColor(R.color.surface)));
        button.setStrokeColor(ColorStateList.valueOf(requireContext().getColor(R.color.line)));
        button.setStrokeWidth(getResources().getDimensionPixelSize(R.dimen.card_stroke_width));
        button.setCornerRadius(getResources().getDimensionPixelSize(R.dimen.button_corner_radius));
        button.setInsetTop(0);
        button.setInsetBottom(0);
        return button;
    }

    private void showRecordMenu(View anchor, JsonElement record, long id) {
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        Menu menu = popup.getMenu();
        addMenuAction(menu, 1, R.string.resource_details, R.drawable.ic_info,
                resource.canDetail, R.color.accent);
        addMenuAction(menu, 2, R.string.resource_edit, R.drawable.ic_edit,
                resource.canUpdate, R.color.accent);
        addMenuAction(menu, 3, R.string.resource_delete, R.drawable.ic_delete,
                resource.canDelete, R.color.danger);
        boolean sale = "ventas".equals(resource.key) || "compras".equals(resource.key);
        addMenuAction(menu, 4, R.string.resource_ticket, R.drawable.ic_receipt,
                sale, R.color.accent);
        addMenuAction(menu, 5, R.string.resource_email_ticket, R.drawable.ic_email,
                "ventas".equals(resource.key), R.color.accent);
        popup.setForceShowIcon(true);
        popup.setOnMenuItemClickListener(item -> onRecordAction(item.getItemId(), record, id));
        popup.show();
    }

    private void addMenuAction(
            Menu menu,
            int itemId,
            int title,
            int icon,
            boolean visible,
            int tint
    ) {
        if (!visible) {
            return;
        }
        MenuItem item = menu.add(Menu.NONE, itemId, itemId, title);
        android.graphics.drawable.Drawable drawable =
                androidx.core.content.ContextCompat.getDrawable(requireContext(), icon);
        if (drawable != null) {
            drawable = drawable.mutate();
            drawable.setTintList(ColorStateList.valueOf(requireContext().getColor(tint)));
            item.setIcon(drawable);
        }
    }

    private boolean onRecordAction(int action, JsonElement record, long id) {
        if (action == 1) {
            showDetails(id);
        } else if (action == 2) {
            showEditor(record, id);
        } else if (action == 3) {
            confirmDelete(id);
        } else if (action == 4) {
            downloadTicket(id);
        } else if (action == 5) {
            promptEmailTicket(id);
        } else {
            return false;
        }
        return true;
    }

    private void addProductImage(LinearLayout parent, JsonObject product) {
        ImageView imageView = new ImageView(requireContext());
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageView.setBackgroundColor(requireContext().getColor(R.color.auth_background));
        JsonElement name = product.get("nombre");
        imageView.setContentDescription(name != null && name.isJsonPrimitive()
                ? name.getAsString()
                : getString(R.string.catalog_product_image));
        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                getResources().getDimensionPixelSize(R.dimen.product_image_height)
        );
        imageParams.bottomMargin = getResources()
                .getDimensionPixelSize(R.dimen.auth_card_spacing);
        parent.addView(imageView, imageParams);

        JsonElement image = product.get("imagen");
        String url = image == null || !image.isJsonPrimitive()
                ? null
                : ProductImageUrl.fromApiBase(
                        RetrofitClient.getApiBaseUrl(), image.getAsString());
        Glide.with(this)
                .load(url)
                .placeholder(R.drawable.ic_box_seam)
                .error(R.drawable.ic_image)
                .centerCrop()
                .into(imageView);
    }

    private void showDetails(long id) {
        String path = "catalogo".equals(resource.key) ? "productos" : resource.path;
        viewModel.get(path, id, new ApiRepository.ResultCallback<JsonElement>() {
            @Override
            public void onSuccess(JsonElement result) {
                if (!isAdded() || result == null || !result.isJsonObject()) {
                    if (isAdded()) {
                        showError(getString(R.string.resource_detail_empty));
                    }
                    return;
                }
                JsonObject record = result.getAsJsonObject();
                ScrollView scroll = new ScrollView(requireContext());
                scroll.setFillViewport(true);
                int padding = getResources()
                        .getDimensionPixelSize(R.dimen.form_dialog_padding);
                scroll.setPadding(padding, padding, padding, padding);
                LinearLayout content = new LinearLayout(requireContext());
                content.setOrientation(LinearLayout.VERTICAL);
                addDetailHeader(content, record);
                MaterialCardView overview = createDetailCard();
                LinearLayout overviewContent = new LinearLayout(requireContext());
                overviewContent.setOrientation(LinearLayout.VERTICAL);
                overviewContent.setPadding(padding, padding, padding, padding);
                JsonArray lineItems = null;
                for (String key : record.keySet()) {
                    JsonElement value = record.get(key);
                    if ("detalles".equals(key) && value != null && value.isJsonArray()) {
                        lineItems = value.getAsJsonArray();
                    } else if (!"id".equals(key) && value != null && !value.isJsonNull()) {
                        addDetailValue(overviewContent, key, value);
                    }
                }
                if (overviewContent.getChildCount() > 0) {
                    overview.addView(overviewContent);
                    content.addView(overview, detailLayoutParams());
                }
                if (lineItems != null) {
                    addDetailItems(content, lineItems);
                }
                if (overviewContent.getChildCount() == 0
                        && (lineItems == null || lineItems.size() == 0)) {
                    TextView empty = new TextView(requireContext());
                    empty.setText(R.string.resource_detail_empty);
                    empty.setTextColor(requireContext().getColor(R.color.auth_muted));
                    content.addView(empty, detailLayoutParams());
                }
                scroll.addView(content);
                new AlertDialog.Builder(requireContext())
                        .setTitle(resource.title)
                        .setView(scroll)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            }

            @Override
            public void onError(String message) {
                showError(message);
            }
        });
    }

    private void addDetailHeader(LinearLayout parent, JsonObject record) {
        JsonElement id = record.get("id");
        if (id == null || id.isJsonNull()) {
            return;
        }
        TextView header = new TextView(requireContext());
        header.setText(getString(R.string.resource_detail_record,
                displayDetailValue("id", id)));
        header.setTextColor(requireContext().getColor(R.color.auth_primary));
        header.setTextSize(getResources().getDimension(R.dimen.detail_header_text_size)
                / getResources().getDisplayMetrics().scaledDensity);
        header.setTypeface(null, android.graphics.Typeface.BOLD);
        header.setPadding(0, 0, 0,
                getResources().getDimensionPixelSize(R.dimen.form_field_spacing));
        parent.addView(header);
    }

    private void addDetailValue(LinearLayout parent, String key, JsonElement value) {
        String text = displayDetailValue(key, value);
        if (text.isEmpty()) {
            return;
        }
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.VERTICAL);
        TextView label = new TextView(requireContext());
        label.setText(detailLabel(key));
        label.setTextColor(requireContext().getColor(R.color.auth_muted));
        label.setTextSize(getResources().getDimension(R.dimen.caption_text_size)
                / getResources().getDisplayMetrics().scaledDensity);
        TextView content = new TextView(requireContext());
        content.setText(text);
        content.setTextColor(requireContext().getColor(R.color.auth_ink));
        content.setTextSize(getResources().getDimension(R.dimen.card_title_text_size)
                / getResources().getDisplayMetrics().scaledDensity);
        content.setTypeface(null, android.graphics.Typeface.BOLD);
        row.addView(label);
        row.addView(content);
        LinearLayout.LayoutParams params = detailLayoutParams();
        params.bottomMargin = getResources()
                .getDimensionPixelSize(R.dimen.form_field_spacing);
        parent.addView(row, params);
    }

    private void addDetailItems(LinearLayout parent, JsonArray items) {
        TextView heading = new TextView(requireContext());
        heading.setText("ventas".equals(resource.key)
                ? R.string.resource_detail_sale_items
                : R.string.resource_detail_supply_items);
        heading.setTextColor(requireContext().getColor(R.color.auth_ink));
        heading.setTextSize(getResources().getDimension(R.dimen.card_title_text_size)
                / getResources().getDisplayMetrics().scaledDensity);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams headingParams = detailLayoutParams();
        headingParams.topMargin = getResources()
                .getDimensionPixelSize(R.dimen.form_section_spacing);
        headingParams.bottomMargin = getResources()
                .getDimensionPixelSize(R.dimen.form_field_spacing);
        parent.addView(heading, headingParams);

        if (items.size() == 0) {
            TextView empty = new TextView(requireContext());
            empty.setText(R.string.resource_detail_empty);
            empty.setTextColor(requireContext().getColor(R.color.auth_muted));
            parent.addView(empty, detailLayoutParams());
            return;
        }
        for (JsonElement element : items) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject item = element.getAsJsonObject();
            MaterialCardView card = createDetailCard();
            LinearLayout body = new LinearLayout(requireContext());
            body.setOrientation(LinearLayout.VERTICAL);
            int padding = getResources()
                    .getDimensionPixelSize(R.dimen.form_dialog_padding);
            body.setPadding(padding, padding, padding, padding);

            TextView product = new TextView(requireContext());
            product.setText(detailProductName(item));
            product.setTextColor(requireContext().getColor(R.color.auth_ink));
            product.setTextSize(getResources().getDimension(R.dimen.card_title_text_size)
                    / getResources().getDisplayMetrics().scaledDensity);
            product.setTypeface(null, android.graphics.Typeface.BOLD);
            body.addView(product);

            JsonElement quantity = item.get("cantidad");
            if (quantity != null && !quantity.isJsonNull()) {
                addDetailValue(body, "cantidad", quantity);
            }
            String amountKey = "ventas".equals(resource.key)
                    ? "precioUnitario"
                    : "costoUnitario";
            JsonElement amount = item.get(amountKey);
            if (amount != null && !amount.isJsonNull()) {
                addDetailValue(body, amountKey, amount);
            }
            card.addView(body);
            parent.addView(card, detailLayoutParams());
        }
    }

    private MaterialCardView createDetailCard() {
        MaterialCardView card = new MaterialCardView(requireContext());
        card.setCardBackgroundColor(requireContext().getColor(R.color.white));
        card.setRadius(getResources().getDimension(R.dimen.auth_card_radius));
        card.setCardElevation(getResources().getDimension(R.dimen.auth_card_elevation));
        card.setStrokeWidth(getResources().getDimensionPixelSize(R.dimen.card_stroke_width));
        card.setStrokeColor(requireContext().getColor(R.color.card_outline));
        return card;
    }

    private LinearLayout.LayoutParams detailLayoutParams() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private String detailProductName(JsonObject item) {
        JsonElement product = item.get("producto");
        if (product == null || !product.isJsonObject()) {
            product = item.get(SaleEstimate.PREVIEW_PRODUCT_KEY);
        }
        if (product != null && product.isJsonObject()) {
            JsonElement name = product.getAsJsonObject().get("nombre");
            if (name != null && name.isJsonPrimitive()) {
                return name.getAsString();
            }
        }
        for (String key : new String[]{"productoNombre", "nombreProducto"}) {
            JsonElement name = item.get(key);
            if (name != null && name.isJsonPrimitive() && !name.getAsString().trim().isEmpty()) {
                return name.getAsString();
            }
        }
        JsonElement id = item.get("productoId");
        if (id == null && product != null && product.isJsonObject()) {
            id = product.getAsJsonObject().get("id");
        }
        return id == null || id.isJsonNull()
                ? getString(R.string.catalog_product_image)
                : "Producto #" + id.getAsString();
    }

    private String detailLabel(String key) {
        switch (key) {
            case "nombre": return "Nombre";
            case "apellido": return "Apellido";
            case "email": return "Correo electrónico";
            case "numeroTelefono": return "Teléfono";
            case "descripcion": return "Descripción";
            case "precio": return "Precio";
            case "total": return "Total";
            case "stock": return "Existencias";
            case "fecha": return "Fecha";
            case "activo": return "Estado";
            case "cantidad": return getString(R.string.resource_detail_quantity);
            case "precioUnitario": return getString(R.string.resource_detail_unit_price);
            case "costoUnitario": return getString(R.string.resource_detail_unit_cost);
            case "cliente": return "Cliente";
            case "clienteNombre": return "Cliente";
            case "cajero": return "Cajero";
            case "cajeroNombre": return "Cajero";
            case "proveedor": return "Proveedor";
            case "proveedorNombre": return "Proveedor";
            case "metodoPago": return "Método de pago";
            case "estado": return "Estado";
            case "puntosUtilizados": return "Puntos utilizados";
            case "efectivoRecibido": return "Efectivo recibido";
            default:
                return key.replaceAll("([a-z])([A-Z])", "$1 $2")
                        .replace('_', ' ').trim();
        }
    }

    private String displayDetailValue(String key, JsonElement value) {
        if (value == null || value.isJsonNull()) {
            return "";
        }
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            for (String candidate : new String[]{
                    "nombreCompleto", "nombre", "productoNombre", "email", "descripcion"}) {
                JsonElement name = object.get(candidate);
                if (name != null && name.isJsonPrimitive()
                        && !name.getAsString().trim().isEmpty()) {
                    return name.getAsString();
                }
            }
            JsonElement id = object.get("id");
            return id == null || id.isJsonNull() ? "" : "#" + id.getAsString();
        }
        if (value.isJsonArray()) {
            List<String> values = new ArrayList<>();
            for (JsonElement item : value.getAsJsonArray()) {
                String itemValue = displayDetailValue(key, item);
                if (!itemValue.isEmpty()) {
                    values.add(itemValue);
                }
            }
            return String.join(", ", values);
        }
        if (value.getAsJsonPrimitive().isBoolean()) {
            return value.getAsBoolean() ? "Sí" : "No";
        }
        String text = value.getAsString();
        if ("precio".equals(key) || "total".equals(key) || "precioUnitario".equals(key)
                || "costoUnitario".equals(key) || "efectivoRecibido".equals(key)) {
            try {
                return NumberFormat.getCurrencyInstance(new Locale("es", "MX"))
                        .format(new BigDecimal(text));
            } catch (NumberFormatException ignored) {
                return text;
            }
        }
        if ("fecha".equals(key)) {
            return text.replace('T', ' ');
        }
        return text.replace('_', ' ');
    }

    private void addFormView(LinearLayout parent, View child) {
        addFormView(parent, child, false);
    }

    private void addFormView(LinearLayout parent, View child, boolean section) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = getResources()
                .getDimensionPixelSize(section
                        ? R.dimen.form_section_spacing
                        : R.dimen.form_field_spacing);
        parent.addView(child, params);
    }

    private void showEditor(JsonElement existing, long id) {
        List<FieldSpec> fields = fieldsFor(resource.key);
        if (fields.isEmpty()) {
            showError("No hay un formulario habilitado para este recurso.");
            return;
        }

        LinearLayout form = new LinearLayout(requireContext());
        form.setOrientation(LinearLayout.VERTICAL);
        int spacing = getResources().getDimensionPixelSize(R.dimen.form_dialog_padding);
        form.setPadding(spacing, spacing, spacing, spacing);
        List<FieldInput> inputs = new ArrayList<>();
        List<JsonObject> detailRows = initialDetails(existing);
        final TextView[] saleSummary = new TextView[1];

        for (FieldSpec spec : fields) {
            if ("cajeroId".equals(spec.key) && "CAJERO".equals(user.getRol())) {
                continue;
            }
            FieldSpec configured = spec;
            if (spec.type == INPUT_PASSWORD && existing == null) {
                configured = new FieldSpec(spec.key, spec.label, spec.type, true,
                        spec.defaultValue, spec.options, spec.relationPath);
            }
            FieldInput input = createField(form, configured, existing);
            inputs.add(input);
        }
        final LinearLayout detailsContainer;
        if (hasLineItems(resource.key)) {
            TextView detailTitle = new TextView(requireContext());
            detailTitle.setText(R.string.resource_line_items);
            detailTitle.setTextSize(getResources().getDimension(
                    R.dimen.card_title_text_size)
                    / getResources().getDisplayMetrics().scaledDensity);
            detailTitle.setTextColor(requireContext().getColor(R.color.auth_ink));
            addFormView(form, detailTitle, true);
            detailsContainer = new LinearLayout(requireContext());
            detailsContainer.setOrientation(LinearLayout.VERTICAL);
            addFormView(form, detailsContainer);
            renderDetailRows(detailsContainer, detailRows, null);
            MaterialButton addLine = new MaterialButton(requireContext());
            addLine.setText(R.string.resource_add_line);
            addLine.setAllCaps(false);
            addLine.setIconResource(R.drawable.ic_add);
            addLine.setIconTintResource(R.color.auth_primary);
            addLine.setOnClickListener(ignored ->
                    showLineEditor(detailRows, detailsContainer, saleSummary[0]));
            addFormView(form, addLine);
        } else {
            detailsContainer = null;
        }

        if ("ventas".equals(resource.key)) {
            TextView summaryTitle = new TextView(requireContext());
            summaryTitle.setText(R.string.sale_estimate_title);
            summaryTitle.setTextSize(getResources().getDimension(
                    R.dimen.card_title_text_size)
                    / getResources().getDisplayMetrics().scaledDensity);
            summaryTitle.setTextColor(requireContext().getColor(R.color.auth_ink));
            addFormView(form, summaryTitle, true);
            saleSummary[0] = new TextView(requireContext());
            saleSummary[0].setTextColor(requireContext().getColor(R.color.auth_ink));
            saleSummary[0].setTextSize(getResources().getDimension(R.dimen.body_text_size)
                    / getResources().getDisplayMetrics().scaledDensity);
            saleSummary[0].setPadding(spacing, spacing, spacing, spacing);
            MaterialCardView summaryCard = createDetailCard();
            summaryCard.addView(saleSummary[0]);
            addFormView(form, summaryCard);
            TextView summaryHint = new TextView(requireContext());
            summaryHint.setText(R.string.sale_estimate_disclaimer);
            summaryHint.setTextColor(requireContext().getColor(R.color.auth_muted));
            summaryHint.setTextSize(getResources().getDimension(R.dimen.caption_text_size)
                    / getResources().getDisplayMetrics().scaledDensity);
            addFormView(form, summaryHint);
            configureSaleForm(inputs, detailRows, saleSummary[0]);
        }

        if ("productos".equals(resource.key) && resource.canCreate) {
            TextView imageHint = new TextView(requireContext());
            imageHint.setText(R.string.resource_choose_image);
            imageHint.setTextColor(requireContext().getColor(R.color.auth_muted));
            addFormView(form, imageHint);
        }

        ScrollView scroll = new ScrollView(requireContext());
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(existing == null ? R.string.resource_create : R.string.resource_edit)
                .setView(scroll)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.resource_save, null)
                .create();
        dialog.setOnShowListener(ignored -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setSoftInputMode(
                        WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            }
            configureEditorImeActions(inputs, dialog);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
                    JsonObject request;
                    try {
                        request = buildRequest(inputs);
                        ApiRequestMapper.omitBlankPasswordForUpdate(request, existing != null);
                        if ("CAJERO".equals(user.getRol()) && "ventas".equals(resource.key)) {
                            request.addProperty("cajeroId", user.getId());
                        }
                        if (detailsContainer != null) {
                            if (detailRows.isEmpty()) {
                                throw new IllegalArgumentException("Agrega al menos un detalle.");
                            }
                            request.add("detalles", new GsonBuilder().create().toJsonTree(detailRows));
                        }
                        if ("ventas".equals(resource.key)) {
                            SaleFormData.removeCashReceivedForNonCash(request);
                            validateSale(request, inputs, detailRows);
                        } else if ("promociones".equals(resource.key)) {
                            validatePromotion(request);
                        }
                        request = new ApiRequestMapper().toTypedJson(resource.key, request);
                    } catch (IllegalArgumentException error) {
                        showError(error.getMessage());
                        return;
                    }
                    dialog.dismiss();
                    submitRequest(existing == null, id, request);
            });
        });
        dialog.show();
    }

    private void configureEditorImeActions(List<FieldInput> inputs, AlertDialog dialog) {
        List<FieldInput> keyboardInputs = new ArrayList<>();
        for (FieldInput input : inputs) {
            if (input.editText != null && isKeyboardInput(input.spec.type)) {
                keyboardInputs.add(input);
            }
        }
        for (int i = 0; i < keyboardInputs.size(); i++) {
            FieldInput input = keyboardInputs.get(i);
            boolean hasNext = i + 1 < keyboardInputs.size();
            input.editText.setImeOptions(
                    hasNext ? EditorInfo.IME_ACTION_NEXT : EditorInfo.IME_ACTION_DONE);
            input.editText.setOnEditorActionListener((text, actionId, event) -> {
                boolean enterPressed = event != null
                        && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                        && event.getAction() == KeyEvent.ACTION_DOWN;
                if (actionId == EditorInfo.IME_ACTION_NEXT && hasNext) {
                    keyboardInputs.get(keyboardInputs.indexOf(input) + 1)
                            .editText.requestFocus();
                    return true;
                }
                if (actionId == EditorInfo.IME_ACTION_DONE || enterPressed) {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                    return true;
                }
                return false;
            });
        }
    }

    private boolean isKeyboardInput(int type) {
        return type == INPUT_TEXT || type == INPUT_NUMBER || type == INPUT_DECIMAL
                || type == INPUT_EMAIL || type == INPUT_PASSWORD;
    }

    private FieldInput createField(LinearLayout parent, FieldSpec spec, JsonElement existing) {
        FieldInput input = new FieldInput(spec);
        JsonElement initial = existing != null && existing.isJsonObject()
                ? existing.getAsJsonObject().get(spec.key)
                : null;
        if (spec.type == INPUT_BOOLEAN) {
            SwitchMaterial toggle = new SwitchMaterial(requireContext());
            toggle.setText(spec.label);
            toggle.setChecked(initial == null ? Boolean.parseBoolean(spec.defaultValue)
                    : initial.getAsBoolean());
            addFormView(parent, toggle);
            input.toggle = toggle;
            input.inputView = toggle;
        } else if (spec.type == INPUT_ENUM) {
            TextView label = new TextView(requireContext());
            label.setText(spec.label);
            label.setTextColor(requireContext().getColor(R.color.ink));
            label.setTextSize(getResources().getDimension(R.dimen.body_text_size)
                    / getResources().getDisplayMetrics().scaledDensity);
            addFormView(parent, label);
            Spinner spinner = new Spinner(requireContext());
            addFormView(parent, spinner);
            input.spinner = spinner;
            input.inputView = spinner;
            setSpinnerValues(spinner, spec.options, initial == null ? spec.defaultValue
                    : initial.getAsString());
        } else if (spec.type == INPUT_RELATION) {
            TextView label = new TextView(requireContext());
            label.setText(spec.label);
            label.setTextColor(requireContext().getColor(R.color.ink));
            label.setTextSize(getResources().getDimension(R.dimen.body_text_size)
                    / getResources().getDisplayMetrics().scaledDensity);
            addFormView(parent, label);
            MaterialButton selector = createDropdownButton();
            selector.setText(getString(R.string.entity_select, spec.label));
            selector.setOnClickListener(ignored -> chooseRelationOption(input));
            addFormView(parent, selector);
            input.relationButton = selector;
            input.inputView = selector;
            loadRelationOptions(input, relationId(initial));
        } else if (spec.type == INPUT_MULTI_RELATION) {
            MaterialButton selector = createDropdownButton();
            selector.setText(spec.label);
            addFormView(parent, selector);
            input.multiButton = selector;
            input.inputView = selector;
            input.selectedIds.addAll(readIdSet(initial));
            selector.setOnClickListener(ignored -> chooseMultipleOptions(input));
            loadMultipleOptions(input);
        } else {
            TextInputLayout layout = createOutlinedInput(spec.label);
            TextInputEditText editText = new TextInputEditText(layout.getContext());
            editText.setSingleLine(spec.type != INPUT_DATETIME);
            editText.setInputType(inputType(spec.type));
            editText.setImeOptions(EditorInfo.IME_ACTION_NEXT);
            editText.setTextColor(requireContext().getColor(R.color.auth_ink));
            editText.setMinHeight(getResources()
                    .getDimensionPixelSize(R.dimen.input_min_height));
            editText.setText(initial == null || initial.isJsonNull()
                    ? spec.defaultValue
                    : initial.getAsString());
            if (spec.type == INPUT_DATE || spec.type == INPUT_DATETIME) {
                editText.setFocusable(false);
                editText.setOnClickListener(ignored ->
                        showDatePicker(editText, spec.type == INPUT_DATETIME));
            }
            layout.addView(editText);
            editText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(
                        CharSequence value,
                        int start,
                        int count,
                        int after
                ) {
                }

                @Override
                public void onTextChanged(CharSequence value, int start, int before, int count) {
                    layout.setError(null);
                }

                @Override
                public void afterTextChanged(Editable value) {
                }
            });
            addFormView(parent, layout);
            input.editText = editText;
            input.inputView = layout;
            if ("puntosUtilizados".equals(spec.key)) {
                TextView balance = new TextView(requireContext());
                balance.setText(R.string.sale_points_select_customer);
                balance.setTextColor(requireContext().getColor(R.color.auth_muted));
                balance.setTextSize(getResources().getDimension(R.dimen.body_text_size)
                        / getResources().getDisplayMetrics().scaledDensity);
                input.pointsBalanceView = balance;
                addFormView(parent, balance);
            }
        }
        return input;
    }

    private MaterialButton createDropdownButton() {
        MaterialButton button = new MaterialButton(requireContext());
        button.setAllCaps(false);
        button.setTextColor(requireContext().getColor(R.color.accent));
        button.setTextSize(getResources().getDimension(R.dimen.body_text_size)
                / getResources().getDisplayMetrics().scaledDensity);
        button.setBackgroundTintList(ColorStateList.valueOf(
                requireContext().getColor(R.color.surface)));
        button.setStrokeColor(ColorStateList.valueOf(requireContext().getColor(R.color.accent)));
        button.setStrokeWidth(getResources().getDimensionPixelSize(R.dimen.card_stroke_width));
        button.setCornerRadius(getResources().getDimensionPixelSize(R.dimen.button_corner_radius));
        button.setMinimumHeight(getResources().getDimensionPixelSize(R.dimen.touch_target_min));
        button.setIconResource(R.drawable.ic_expand_more);
        button.setIconTintResource(R.color.accent);
        button.setIconSize(getResources().getDimensionPixelSize(R.dimen.space_3));
        button.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_END);
        button.setMaxLines(2);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        return button;
    }

    private void chooseRelationOption(FieldInput field) {
        if (field.options.isEmpty()) {
            showError(getString(R.string.entity_options_empty, field.spec.label));
            return;
        }

        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        LinearLayout sheet = new LinearLayout(requireContext());
        sheet.setOrientation(LinearLayout.VERTICAL);
        int padding = getResources().getDimensionPixelSize(R.dimen.space_4);
        sheet.setPadding(padding, padding, padding, padding);
        int height = Math.min(
                getResources().getDimensionPixelSize(R.dimen.entity_sheet_max_height),
                getResources().getDisplayMetrics().heightPixels * 2 / 3);
        sheet.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, height));

        TextView title = new TextView(requireContext());
        title.setText(field.spec.label);
        title.setTextColor(requireContext().getColor(R.color.ink));
        title.setTextSize(getResources().getDimension(R.dimen.section_title_text_size)
                / getResources().getDisplayMetrics().scaledDensity);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        addFormView(sheet, title);

        TextInputLayout searchLayout = createOutlinedInput(getString(R.string.resource_search));
        TextInputEditText search = new TextInputEditText(requireContext());
        search.setSingleLine(true);
        search.setInputType(InputType.TYPE_CLASS_TEXT);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchLayout.addView(search);
        addFormView(sheet, searchLayout);

        ScrollView optionsScroll = new ScrollView(requireContext());
        optionsScroll.setFillViewport(false);
        LinearLayout optionsContainer = new LinearLayout(requireContext());
        optionsContainer.setOrientation(LinearLayout.VERTICAL);
        optionsScroll.addView(optionsContainer);
        sheet.addView(optionsScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Runnable renderOptions = () -> {
            optionsContainer.removeAllViews();
            String query = search.getText() == null
                    ? ""
                    : search.getText().toString().trim().toLowerCase(Locale.ROOT);
            int visibleCount = 0;
            for (Option option : field.options) {
                String searchable = (option.label + " " + optionSubtitle(option))
                        .toLowerCase(Locale.ROOT);
                if (!searchable.contains(query)) {
                    continue;
                }
                addRelationOption(optionsContainer, field, option, dialog);
                visibleCount++;
            }
            if (visibleCount == 0) {
                TextView empty = new TextView(requireContext());
                empty.setText(R.string.resource_empty);
                empty.setTextColor(requireContext().getColor(R.color.muted_on_soft));
                empty.setTextSize(getResources().getDimension(R.dimen.body_text_size)
                        / getResources().getDisplayMetrics().scaledDensity);
                empty.setGravity(Gravity.CENTER);
                empty.setPadding(padding, padding, padding, padding);
                optionsContainer.addView(empty);
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
        renderOptions.run();
        dialog.setContentView(sheet);
        dialog.show();
    }

    private void addRelationOption(
            LinearLayout parent,
            FieldInput field,
            Option option,
            BottomSheetDialog dialog
    ) {
        boolean selected = field.selectedOption != null
                && field.selectedOption.id == option.id;
        MaterialCardView card = new MaterialCardView(requireContext());
        card.setCardBackgroundColor(requireContext().getColor(
                selected ? R.color.soft : R.color.surface));
        card.setRadius(getResources().getDimension(R.dimen.input_corner_radius));
        card.setCardElevation(0);
        card.setStrokeWidth(getResources().getDimensionPixelSize(R.dimen.card_stroke_width));
        card.setStrokeColor(requireContext().getColor(
                selected ? R.color.accent : R.color.line));
        LinearLayout row = new LinearLayout(requireContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        int padding = getResources().getDimensionPixelSize(R.dimen.space_3);
        row.setPadding(padding, padding, padding, padding);

        LinearLayout labels = new LinearLayout(requireContext());
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(requireContext());
        name.setText(option.label);
        name.setTextColor(requireContext().getColor(R.color.ink));
        name.setTextSize(getResources().getDimension(R.dimen.body_text_size)
                / getResources().getDisplayMetrics().scaledDensity);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        labels.addView(name);
        String subtitleText = optionSubtitle(option);
        if (!subtitleText.isEmpty()) {
            TextView subtitle = new TextView(requireContext());
            subtitle.setText(subtitleText);
            subtitle.setTextColor(requireContext().getColor(
                    selected ? R.color.muted_on_soft : R.color.muted));
            subtitle.setTextSize(getResources().getDimension(R.dimen.caption_text_size)
                    / getResources().getDisplayMetrics().scaledDensity);
            labels.addView(subtitle);
        }
        row.addView(labels, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        if (selected) {
            TextView check = new TextView(requireContext());
            check.setText("✓");
            check.setTextColor(requireContext().getColor(R.color.accent));
            check.setTextSize(getResources().getDimension(R.dimen.brand_text_size)
                    / getResources().getDisplayMetrics().scaledDensity);
            check.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            check.setContentDescription(getString(R.string.entity_selected));
            row.addView(check);
        }
        card.addView(row);
        card.setMinimumHeight(getResources()
                .getDimensionPixelSize(R.dimen.touch_target_min));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = getResources().getDimensionPixelSize(R.dimen.space_2);
        parent.addView(card, params);
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(ignored -> {
            field.selectedOption = option;
            updateRelationLabel(field);
            updateCustomerPoints(field);
            dialog.dismiss();
        });
    }

    private String optionSubtitle(Option option) {
        if (option.record == null) {
            return "";
        }
        for (String key : new String[]{
                "email", "telefono", "rol", "codigoBarras", "categoriaNombre",
                "nivelLealtad", "numeroEmpleado", "numeroAdministrador", "descripcion"
        }) {
            String value = primitiveValue(option.record.get(key));
            if (!value.isEmpty() && !value.equals(option.label)) {
                return value;
            }
        }
        return "";
    }

    private void updateRelationLabel(FieldInput field) {
        if (field.relationButton == null) {
            return;
        }
        field.relationButton.setTextColor(requireContext().getColor(R.color.accent));
        field.relationButton.setStrokeColor(ColorStateList.valueOf(
                requireContext().getColor(R.color.accent)));
        field.relationButton.setText(field.selectedOption == null
                ? getString(R.string.entity_select, field.spec.label)
                : field.selectedOption.label);
    }

    private void configureSaleForm(
            List<FieldInput> inputs,
            List<JsonObject> details,
            TextView summaryView
    ) {
        FieldInput customer = findInput(inputs, "clienteId");
        FieldInput pointsUsed = findInput(inputs, "puntosUtilizados");
        FieldInput paymentMethod = findInput(inputs, "metodoPago");
        FieldInput cashReceived = findInput(inputs, "efectivoRecibido");
        List<JsonObject> promotions = new ArrayList<>();
        boolean[] promotionsLoaded = {false};
        summaryView.setTag(new SaleSummaryContext(
                inputs, details, promotions, promotionsLoaded));

        if (customer != null && pointsUsed != null) {
            customer.dependentPointsView = pointsUsed.pointsBalanceView;
            customer.saleInputs = inputs;
            customer.saleDetails = details;
            customer.saleSummaryView = summaryView;
            customer.salePromotions = promotions;
            customer.salePromotionsLoaded = promotionsLoaded;
            customer.pointsInput = pointsUsed.editText;
            updateCustomerPoints(customer);
        }

        if (pointsUsed != null && pointsUsed.editText != null) {
            pointsUsed.editText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence value, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence value, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable value) {
                    SaleEstimate estimate = calculateSaleEstimate(
                            inputs, details, promotions);
                    int requested = parseInteger(value.toString());
                    if (requested > estimate.maxPoints) {
                        String cappedValue = String.valueOf(estimate.maxPoints);
                        pointsUsed.editText.setText(cappedValue);
                        pointsUsed.editText.setSelection(cappedValue.length());
                        return;
                    }
                    updateSaleSummary(inputs, details, summaryView, promotions, promotionsLoaded);
                }
            });
        }

        if (cashReceived != null && cashReceived.editText != null) {
            cashReceived.editText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence value, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence value, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable value) {
                    updateSaleSummary(inputs, details, summaryView, promotions, promotionsLoaded);
                }
            });
        }

        if (paymentMethod != null && cashReceived != null) {
            updateCashReceivedVisibility(paymentMethod, cashReceived);
            paymentMethod.spinner.setOnItemSelectedListener(
                    new android.widget.AdapterView.OnItemSelectedListener() {
                        @Override
                        public void onItemSelected(
                                android.widget.AdapterView<?> parent,
                                View view,
                                int position,
                                long id
                        ) {
                            updateCashReceivedVisibility(paymentMethod, cashReceived);
                            updateSaleSummary(
                                    inputs, details, summaryView, promotions, promotionsLoaded);
                        }

                        @Override
                        public void onNothingSelected(android.widget.AdapterView<?> parent) {
                            updateCashReceivedVisibility(paymentMethod, cashReceived);
                            updateSaleSummary(
                                    inputs, details, summaryView, promotions, promotionsLoaded);
                        }
                    });
        }

        updateSaleSummary(inputs, details, summaryView, promotions, promotionsLoaded);
        viewModel.listOptions("promociones", new ApiRepository.ResultCallback<JsonElement>() {
            @Override
            public void onSuccess(JsonElement result) {
                promotions.clear();
                for (JsonElement element : arrayContent(result)) {
                    if (element != null && element.isJsonObject()) {
                        promotions.add(element.getAsJsonObject());
                    }
                }
                promotionsLoaded[0] = true;
                updateSaleSummary(inputs, details, summaryView, promotions, promotionsLoaded);
            }

            @Override
            public void onError(String message) {
                updateSaleSummary(inputs, details, summaryView, promotions, promotionsLoaded);
                showError("No se pudieron cargar las promociones para la estimación: " + message);
            }
        });
    }

    private FieldInput findInput(List<FieldInput> inputs, String key) {
        for (FieldInput input : inputs) {
            if (key.equals(input.spec.key)) {
                return input;
            }
        }
        return null;
    }

    private void updateCashReceivedVisibility(
            FieldInput paymentMethod,
            FieldInput cashReceived
    ) {
        Object selected = paymentMethod.spinner.getSelectedItem();
        boolean cash = selected != null
                && SaleFormData.requiresCashReceived(selected.toString().trim());
        cashReceived.inputView.setVisibility(cash ? View.VISIBLE : View.GONE);
        if (!cash && cashReceived.editText != null) {
            cashReceived.editText.setText("");
        }
    }

    private void updateCustomerPoints(FieldInput customer) {
        if (customer.dependentPointsView == null) {
            return;
        }
        Integer points = customer.selectedOption == null
                ? null
                : SaleFormData.availablePoints(customer.selectedOption.record);
        if (points == null) {
            customer.dependentPointsView.setText(R.string.sale_points_select_customer);
        } else {
            customer.dependentPointsView.setText(
                    getString(R.string.sale_points_available, points));
        }
        if (customer.pointsInput != null) {
            customer.pointsInput.setText("0");
        }
        updateSaleSummary(customer.saleInputs, customer.saleDetails, customer.saleSummaryView,
                customer.salePromotions, customer.salePromotionsLoaded);
    }

    private SaleEstimate calculateSaleEstimate(
            List<FieldInput> inputs,
            List<JsonObject> details,
            List<JsonObject> promotions
    ) {
        JsonArray detailArray = new JsonArray();
        for (JsonObject detail : details) {
            detailArray.add(detail);
        }
        FieldInput customerField = findInput(inputs, "clienteId");
        JsonObject customer = customerField == null || customerField.selectedOption == null
                ? null : customerField.selectedOption.record;
        FieldInput pointsField = findInput(inputs, "puntosUtilizados");
        int requestedPoints = pointsField == null || pointsField.editText == null
                ? 0 : parseInteger(textOf(pointsField.editText));
        return SaleEstimate.calculate(
                detailArray, customer, promotions, LocalDate.now(), requestedPoints);
    }

    private void updateSaleSummary(
            List<FieldInput> inputs,
            List<JsonObject> details,
            TextView summaryView,
            List<JsonObject> promotions,
            boolean[] promotionsLoaded
    ) {
        if (summaryView == null || inputs == null || details == null || promotions == null
                || !isAdded()) {
            return;
        }
        SaleEstimate estimate = calculateSaleEstimate(inputs, details, promotions);
        StringBuilder summary = new StringBuilder();
        appendSummaryLine(summary, getString(R.string.sale_estimate_subtotal,
                money(estimate.subtotal)));
        appendSummaryLine(summary, getString(R.string.sale_estimate_promotions,
                money(estimate.promotionDiscount)));
        appendSummaryLine(summary, getString(R.string.sale_estimate_loyalty,
                estimate.loyaltyLevel, money(estimate.loyaltyDiscount)));
        appendSummaryLine(summary, getString(R.string.sale_estimate_points,
                estimate.pointsUsed, money(estimate.pointsDiscount)));
        appendSummaryLine(summary, getString(R.string.sale_estimate_max_points,
                estimate.availablePoints, estimate.maxPoints));
        appendSummaryLine(summary, getString(R.string.sale_estimate_total,
                money(estimate.total)));
        appendSummaryLine(summary, getString(R.string.sale_estimate_earned,
                estimate.earnedPoints));

        FieldInput payment = findInput(inputs, "metodoPago");
        FieldInput cash = findInput(inputs, "efectivoRecibido");
        if (payment != null && cash != null && cash.editText != null
                && SaleFormData.requiresCashReceived(
                String.valueOf(payment.spinner.getSelectedItem()))) {
            String receivedValue = textOf(cash.editText);
            if (!receivedValue.isEmpty()) {
                try {
                    BigDecimal difference = new BigDecimal(receivedValue).subtract(estimate.total);
                    if (difference.signum() >= 0) {
                        appendSummaryLine(summary, getString(R.string.sale_estimate_change,
                                money(difference)));
                    } else {
                        appendSummaryLine(summary, getString(R.string.sale_estimate_shortage,
                                money(difference.abs())));
                    }
                } catch (NumberFormatException ignored) {
                    // The field's normal validation reports malformed values on submission.
                }
            }
        }
        if (promotionsLoaded != null && promotionsLoaded.length > 0 && !promotionsLoaded[0]) {
            appendSummaryLine(summary, getString(R.string.sale_estimate_promotions_loading));
        }
        summaryView.setText(summary.toString().trim());
    }

    private void appendSummaryLine(StringBuilder summary, String line) {
        if (summary.length() > 0) {
            summary.append('\n');
        }
        summary.append(line);
    }

    private String money(BigDecimal amount) {
        return NumberFormat.getCurrencyInstance(new Locale("es", "MX")).format(amount);
    }

    private int parseInteger(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException error) {
            return 0;
        }
    }

    private JsonObject buildRequest(List<FieldInput> inputs) {
        JsonObject request = new JsonObject();
        for (FieldInput input : inputs) {
            FieldSpec spec = input.spec;
            switch (spec.type) {
                case INPUT_BOOLEAN:
                    request.addProperty(spec.key, input.toggle.isChecked());
                    break;
                case INPUT_ENUM:
                    request.addProperty(spec.key,
                            input.spinner.getSelectedItem().toString().trim());
                    break;
                case INPUT_RELATION:
                    if (input.selectedOption == null) {
                        if (spec.required) {
                            if (input.relationButton != null) {
                                input.relationButton.setTextColor(
                                        requireContext().getColor(R.color.danger));
                                input.relationButton.setStrokeColor(ColorStateList.valueOf(
                                        requireContext().getColor(R.color.danger_border)));
                            }
                            throw new IllegalArgumentException("Selecciona " + spec.label + ".");
                        }
                        request.add(spec.key, com.google.gson.JsonNull.INSTANCE);
                    } else {
                        request.addProperty(spec.key, input.selectedOption.id);
                    }
                    break;
                case INPUT_MULTI_RELATION:
                    JsonArray ids = new JsonArray();
                    for (Long selectedId : input.selectedIds) {
                        ids.add(selectedId);
                    }
                    request.add(spec.key, ids);
                    break;
                default:
                    String value = input.editText.getText() == null
                            ? ""
                            : input.editText.getText().toString().trim();
                    if (value.isEmpty()) {
                        if (spec.required) {
                            setFieldError(input, getString(R.string.form_required));
                            throw new IllegalArgumentException("Completa el campo " + spec.label + ".");
                        }
                        if (spec.type == INPUT_PASSWORD) {
                            request.addProperty(spec.key, "");
                        } else {
                            request.add(spec.key, com.google.gson.JsonNull.INSTANCE);
                        }
                        continue;
                    }
                    if (spec.type == INPUT_EMAIL
                            && !Patterns.EMAIL_ADDRESS.matcher(value).matches()) {
                        setFieldError(input, getString(R.string.form_invalid_email));
                        throw new IllegalArgumentException("Revisa el correo electrónico.");
                    }
                    if (spec.type == INPUT_PASSWORD
                            && (value.length() < 8 || value.length() > 20)) {
                        setFieldError(input, getString(R.string.form_password_length));
                        throw new IllegalArgumentException(
                                "La contraseña debe tener entre 8 y 20 caracteres.");
                    }
                    try {
                        if (spec.type == INPUT_NUMBER) {
                            long number = Long.parseLong(value);
                            validateNumber(spec.key, number);
                            request.addProperty(spec.key, number);
                        } else if (spec.type == INPUT_DECIMAL) {
                            BigDecimal number = new BigDecimal(value);
                            validateDecimal(spec.key, number);
                            request.addProperty(spec.key, number);
                        } else {
                            FormDateValidator.validateIfDateField(
                                    spec.type == INPUT_DATE,
                                    spec.type == INPUT_DATETIME,
                                    spec.key,
                                    value);
                            request.addProperty(spec.key, value);
                        }
                    } catch (NumberFormatException error) {
                        setFieldError(input, getString(R.string.form_invalid_value));
                        throw new IllegalArgumentException("Revisa el campo " + spec.label + ".");
                    } catch (IllegalArgumentException error) {
                        setFieldError(input, error.getMessage());
                        throw error;
                    }
            }
        }
        return request;
    }

    private void setFieldError(FieldInput input, String message) {
        if (input.inputView instanceof TextInputLayout) {
            ((TextInputLayout) input.inputView).setError(message);
        } else if (input.editText != null) {
            input.editText.setError(message);
        }
        if (input.editText != null) {
            input.editText.requestFocus();
        }
    }

    private void loadRelationOptions(FieldInput field, Long selectedId) {
        viewModel.listOptions(field.spec.relationPath,
                new ApiRepository.ResultCallback<JsonElement>() {
                    @Override
                    public void onSuccess(JsonElement result) {
                        if (!isAdded()) {
                            return;
                        }
                        field.options.clear();
                        JsonArray content = arrayContent(result);
                        for (JsonElement element : content) {
                            if (!element.isJsonObject()) {
                                continue;
                            }
                            JsonObject object = element.getAsJsonObject();
                            if (object.has("id")) {
                                field.options.add(new Option(
                                        object.get("id").getAsLong(), optionLabel(object), object));
                            }
                        }
                        field.selectedOption = null;
                        if (selectedId != null) {
                            for (Option option : field.options) {
                                if (option.id == selectedId) {
                                    field.selectedOption = option;
                                    break;
                                }
                            }
                        }
                        updateRelationLabel(field);
                        updateCustomerPoints(field);
                    }

                    @Override
                    public void onError(String message) {
                        showError("No se pudieron cargar opciones de " + field.spec.label
                                + ": " + message);
                    }
                });
    }

    private void loadMultipleOptions(FieldInput field) {
        viewModel.listOptions(field.spec.relationPath,
                new ApiRepository.ResultCallback<JsonElement>() {
                    @Override
                    public void onSuccess(JsonElement result) {
                        if (!isAdded()) {
                            return;
                        }
                        field.options.clear();
                        for (JsonElement element : arrayContent(result)) {
                            if (element.isJsonObject() && element.getAsJsonObject().has("id")) {
                                JsonObject object = element.getAsJsonObject();
                                field.options.add(new Option(object.get("id").getAsLong(),
                                        optionLabel(object)));
                            }
                        }
                        updateMultiLabel(field);
                    }

                    @Override
                    public void onError(String message) {
                        showError("No se pudieron cargar opciones de " + field.spec.label
                                + ": " + message);
                    }
                });
    }

    private void chooseMultipleOptions(FieldInput field) {
        if (field.options.isEmpty()) {
            showError("No hay opciones disponibles para " + field.spec.label + ".");
            return;
        }
        String[] labels = new String[field.options.size()];
        boolean[] checked = new boolean[field.options.size()];
        for (int i = 0; i < field.options.size(); i++) {
            Option option = field.options.get(i);
            labels[i] = option.label;
            checked[i] = field.selectedIds.contains(option.id);
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(field.spec.label)
                .setMultiChoiceItems(labels, checked, (dialog, which, selected) -> {
                    long id = field.options.get(which).id;
                    if (selected) {
                        field.selectedIds.add(id);
                    } else {
                        field.selectedIds.remove(id);
                    }
                    updateMultiLabel(field);
                })
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void updateMultiLabel(FieldInput field) {
        if (field.multiButton == null) {
            return;
        }
        List<String> labels = new ArrayList<>();
        for (Option option : field.options) {
            if (field.selectedIds.contains(option.id)) {
                labels.add(option.label);
            }
        }
        field.multiButton.setText(labels.isEmpty()
                ? field.spec.label
                : field.spec.label + ": " + String.join(", ", labels));
    }

    private void showLineEditor(
            List<JsonObject> details,
            LinearLayout container,
            TextView saleSummaryView
    ) {
        String productPath = "productos";
        viewModel.listOptions(productPath, new ApiRepository.ResultCallback<JsonElement>() {
            @Override
            public void onSuccess(JsonElement result) {
                if (!isAdded()) {
                    return;
                }
                boolean sale = "ventas".equals(resource.key);
                List<Option> products = optionsFrom(result);
                if (sale) {
                    products.removeIf(option -> option.record == null
                            || !option.record.has("activo")
                            || !option.record.get("activo").getAsBoolean()
                            || !option.record.has("stock")
                            || option.record.get("stock").getAsInt() < 1);
                }
                if (products.isEmpty()) {
                    showError("No hay productos disponibles.");
                    return;
                }
                Spinner productSpinner = new Spinner(requireContext());
                configureSpinner(productSpinner, products, null);
                TextInputLayout quantityLayout = createOutlinedInput("Cantidad");
                TextInputEditText quantity = new TextInputEditText(quantityLayout.getContext());
                quantity.setInputType(InputType.TYPE_CLASS_NUMBER);
                quantity.setText("1");
                quantityLayout.addView(quantity);
                LinearLayout editor = new LinearLayout(requireContext());
                int padding = getResources()
                        .getDimensionPixelSize(R.dimen.form_dialog_padding);
                editor.setPadding(padding, padding, padding, padding);
                editor.setOrientation(LinearLayout.VERTICAL);
                addFormView(editor, productSpinner);
                addFormView(editor, quantityLayout);
                TextInputEditText cost = null;
                if (!sale) {
                    TextInputLayout costLayout = createOutlinedInput("Costo unitario");
                    cost = new TextInputEditText(costLayout.getContext());
                    cost.setInputType(InputType.TYPE_CLASS_NUMBER
                            | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                    costLayout.addView(cost);
                    addFormView(editor, costLayout);
                }
                final TextInputEditText costInput = cost;
                AlertDialog dialog = new AlertDialog.Builder(requireContext())
                        .setTitle(R.string.resource_add_line)
                        .setView(editor)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok, null)
                        .create();
                dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setOnClickListener(button -> {
                            String quantityValue = textOf(quantity);
                            try {
                                int count = Integer.parseInt(quantityValue);
                                if (count < 1) {
                                    throw new NumberFormatException();
                                }
                                Option selectedProduct =
                                        products.get(productSpinner.getSelectedItemPosition());
                                if (sale && selectedProduct.record != null
                                        && selectedProduct.record.has("stock")
                                        && count > selectedProduct.record.get("stock").getAsInt()) {
                                    showError("La cantidad supera las existencias disponibles.");
                                    return;
                                }
                                JsonObject detail;
                                if (sale) {
                                    try {
                                        detail = new SaleLineRequestMapper()
                                                .fromProduct(selectedProduct.record, count);
                                        detail.add(SaleEstimate.PREVIEW_PRODUCT_KEY,
                                                selectedProduct.record.deepCopy());
                                    } catch (IllegalArgumentException error) {
                                        showError(error.getMessage());
                                        return;
                                    }
                                } else {
                                    String costValue = textOf(costInput);
                                    if (costValue.isEmpty()) {
                                        showError("Ingresa el costo unitario.");
                                        return;
                                    }
                                    BigDecimal unitCost = new BigDecimal(costValue);
                                    if (unitCost.signum() < 0) {
                                        throw new NumberFormatException();
                                    }
                                    detail = new JsonObject();
                                    detail.addProperty("productoId", selectedProduct.id);
                                    detail.addProperty("cantidad", count);
                                    detail.addProperty("costoUnitario", unitCost);
                                }
                                int mergeResult = sale
                                        ? mergeSaleDetail(details, detail, selectedProduct.record)
                                        : -1;
                                if (mergeResult == 0) {
                                    return;
                                }
                                if (mergeResult > 0) {
                                    renderDetailRows(container, details, saleSummaryView);
                                    dialog.dismiss();
                                    return;
                                }
                                details.add(detail);
                                renderDetailRows(container, details, saleSummaryView);
                                dialog.dismiss();
                            } catch (NumberFormatException error) {
                                showError(sale
                                        ? "Ingresa una cantidad válida."
                                        : "Ingresa cantidad y costo válidos.");
                            }
                        }));
                dialog.show();
            }

            @Override
            public void onError(String message) {
                showError(message);
            }
        });
    }

    private TextInputLayout createOutlinedInput(String hint) {
        TextInputLayout layout = new TextInputLayout(requireContext());
        layout.setHint(hint);
        layout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        layout.setBoxStrokeWidth(getResources()
                .getDimensionPixelSize(R.dimen.card_stroke_width));
        layout.setBoxStrokeWidthFocused(getResources()
                .getDimensionPixelSize(R.dimen.input_focused_stroke_width));
        layout.setBoxBackgroundColor(requireContext().getColor(R.color.white));
        ColorStateList stroke = ContextCompat.getColorStateList(
                requireContext(), R.color.input_stroke);
        if (stroke != null) {
            layout.setBoxStrokeColorStateList(stroke);
        }
        ColorStateList hintColors = ContextCompat.getColorStateList(
                requireContext(), R.color.input_hint);
        if (hintColors != null) {
            layout.setDefaultHintTextColor(hintColors);
        }
        float radius = getResources().getDimension(R.dimen.input_corner_radius);
        layout.setBoxCornerRadii(radius, radius, radius, radius);
        return layout;
    }

    private int mergeSaleDetail(
            List<JsonObject> details,
            JsonObject newDetail,
            JsonObject product
    ) {
        long productId = newDetail.get("productoId").getAsLong();
        int additionalQuantity = newDetail.get("cantidad").getAsInt();
        int stock = product.has("stock") ? product.get("stock").getAsInt() : Integer.MAX_VALUE;
        for (JsonObject existingDetail : details) {
            JsonElement existingProductId = existingDetail.get("productoId");
            if (existingProductId == null && existingDetail.has("producto")
                    && existingDetail.get("producto").isJsonObject()) {
                existingProductId = existingDetail.getAsJsonObject("producto").get("id");
            }
            Long existingId = relationId(existingProductId);
            if (existingId == null || existingId != productId) {
                continue;
            }
            int currentQuantity = existingDetail.get("cantidad").getAsInt();
            if ((long) currentQuantity + additionalQuantity > stock) {
                showError("La cantidad supera las existencias disponibles.");
                return 0;
            }
            existingDetail.addProperty("productoId", productId);
            existingDetail.addProperty("cantidad", currentQuantity + additionalQuantity);
            existingDetail.add("precioUnitario", newDetail.get("precioUnitario").deepCopy());
            existingDetail.add(SaleEstimate.PREVIEW_PRODUCT_KEY, product.deepCopy());
            return 1;
        }
        return -1;
    }

    private void renderDetailRows(
            LinearLayout container,
            List<JsonObject> details,
            TextView saleSummaryView
    ) {
        container.removeAllViews();
        for (int i = 0; i < details.size(); i++) {
            final int index = i;
            JsonObject detail = details.get(i);
            TextView line = new TextView(requireContext());
            JsonElement quantity = detail.get("cantidad");
            JsonElement amount = detail.get(detail.has("precioUnitario")
                    ? "precioUnitario" : "costoUnitario");
            StringBuilder description = new StringBuilder(detailProductName(detail));
            if (quantity != null && !quantity.isJsonNull()) {
                description.append("\n")
                        .append(getString(R.string.resource_detail_quantity))
                        .append(": ").append(quantity.getAsString());
            }
            if (amount != null && !amount.isJsonNull()) {
                String amountKey = detail.has("precioUnitario")
                        ? "precioUnitario" : "costoUnitario";
                description.append("\n").append(detailLabel(amountKey))
                        .append(": ").append(displayDetailValue(amountKey, amount));
            }
            line.setText(description.toString());
            line.setPadding(getResources().getDimensionPixelSize(R.dimen.form_dialog_padding),
                    getResources().getDimensionPixelSize(R.dimen.form_field_spacing),
                    getResources().getDimensionPixelSize(R.dimen.form_dialog_padding),
                    getResources().getDimensionPixelSize(R.dimen.form_field_spacing));
            line.setTextColor(requireContext().getColor(R.color.auth_ink));
            MaterialCardView lineCard = createDetailCard();
            lineCard.addView(line);
            addFormView(container, lineCard);
            MaterialButton remove = new MaterialButton(requireContext());
            remove.setText(R.string.resource_remove_line);
            remove.setAllCaps(false);
            remove.setOnClickListener(ignored -> {
                details.remove(index);
                renderDetailRows(container, details, saleSummaryView);
            });
            addFormView(container, remove);
        }
        if (saleSummaryView != null) {
            updateSaleSummaryForDetails(saleSummaryView, details);
        }
    }

    private void updateSaleSummaryForDetails(TextView summaryView, List<JsonObject> details) {
        if (summaryView.getTag() instanceof SaleSummaryContext) {
            SaleSummaryContext context = (SaleSummaryContext) summaryView.getTag();
            context.details = details;
            updateSaleSummary(context.inputs, details, summaryView, context.promotions,
                    context.promotionsLoaded);
        }
    }

    private void configureSpinner(Spinner spinner, List<Option> options, Long selectedId) {
        List<String> labels = new ArrayList<>();
        for (Option option : options) {
            labels.add(option.label);
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        if (selectedId != null) {
            for (int i = 0; i < options.size(); i++) {
                if (options.get(i).id == selectedId) {
                    spinner.setSelection(i);
                    break;
                }
            }
        }
    }

    private List<Option> optionsFrom(JsonElement result) {
        List<Option> options = new ArrayList<>();
        for (JsonElement element : arrayContent(result)) {
            if (element.isJsonObject() && element.getAsJsonObject().has("id")) {
                JsonObject object = element.getAsJsonObject();
                options.add(new Option(object.get("id").getAsLong(), optionLabel(object), object));
            }
        }
        return options;
    }

    private JsonArray arrayContent(JsonElement result) {
        if (result != null && result.isJsonObject()) {
            JsonElement content = result.getAsJsonObject().get("content");
            if (content != null && content.isJsonArray()) {
                return content.getAsJsonArray();
            }
        }
        return new JsonArray();
    }

    private void submitRequest(
            boolean creating,
            long id,
            JsonObject request
    ) {
        File imageForRequest = creating && "productos".equals(resource.key)
                ? selectedImage
                : null;
        productUploadInProgress = imageForRequest != null;
        binding.btnResourceCreate.setEnabled(false);
        binding.btnResourceImage.setEnabled(false);
        ApiRepository.ResultCallback<JsonElement> callback =
                new ApiRepository.ResultCallback<JsonElement>() {
                    @Override
                    public void onSuccess(JsonElement result) {
                        if (imageForRequest != null) {
                            imageForRequest.delete();
                            productUploadInProgress = false;
                            if (selectedImage == imageForRequest) {
                                selectedImage = null;
                                selectedImageMimeType = null;
                            }
                        }
                        if (!isAdded() || binding == null) {
                            return;
                        }
                        binding.btnResourceImage.setText(R.string.resource_choose_image);
                        binding.btnResourceCreate.setEnabled(resource.canCreate);
                        binding.btnResourceImage.setEnabled(
                                "productos".equals(resource.key) && resource.canCreate);
                        if ("ventas".equals(resource.key) && result != null
                                && result.isJsonObject()
                                && result.getAsJsonObject().has("id")) {
                            long saleId = result.getAsJsonObject().get("id").getAsLong();
                            new AlertDialog.Builder(requireContext())
                                    .setTitle(R.string.sale_created)
                                    .setMessage(summary(result))
                                    .setPositiveButton(R.string.resource_ticket,
                                            (dialog, which) -> downloadTicket(saleId))
                                    .setNegativeButton(android.R.string.ok, null)
                                    .show();
                        } else {
                            Toast.makeText(requireContext(), R.string.resource_save_success,
                                    Toast.LENGTH_SHORT).show();
                        }
                        loadRecords();
                    }

                    @Override
                    public void onError(String message) {
                        productUploadInProgress = false;
                        if (isAdded() && binding != null) {
                            binding.btnResourceCreate.setEnabled(resource.canCreate);
                            binding.btnResourceImage.setEnabled(
                                    "productos".equals(resource.key) && resource.canCreate);
                        }
                        showError(message);
                    }
                };
        if (creating && "productos".equals(resource.key)) {
            viewModel.createProduct(request, selectedImage, selectedImageMimeType, callback);
        } else if (creating) {
            viewModel.create(resource.path, request, callback);
        } else {
            viewModel.update(resource.path, id, request, callback);
        }
    }

    private void confirmDelete(long id) {
        new AlertDialog.Builder(requireContext())
                .setMessage(R.string.resource_delete_confirm)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.resource_delete, (dialog, which) ->
                        viewModel.delete(resource.path, id,
                                new ApiRepository.ResultCallback<Void>() {
                                    @Override
                                    public void onSuccess(Void result) {
                                        loadRecords();
                                    }

                                    @Override
                                    public void onError(String message) {
                                        showError(message);
                                    }
                                }))
                .show();
    }

    private void promptEmailTicket(long id) {
        EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        input.setHint(R.string.resource_email_hint);
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.resource_email_ticket)
                .setView(input)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(button -> {
                    String recipient = input.getText().toString().trim();
                    if (!Patterns.EMAIL_ADDRESS.matcher(recipient).matches()) {
                        input.setError("Ingresa un correo válido.");
                        return;
                    }
                    viewModel.emailTicket(id, recipient, new ApiRepository.ResultCallback<String>() {
                                    @Override
                                    public void onSuccess(String result) {
                                        if (isAdded()) {
                                            Toast.makeText(requireContext(), result,
                                                    Toast.LENGTH_LONG).show();
                                        }
                                    }

                                    @Override
                                    public void onError(String message) {
                                        showError(message);
                                    }
                                });
                    dialog.dismiss();
                }));
        dialog.show();
    }

    private void downloadTicket(long id) {
        File destination = new File(requireContext().getCacheDir(), "ticket_" + id + ".pdf");
        viewModel.downloadTicket(id, destination,
                new ApiRepository.ResultCallback<File>() {
                    @Override
                    public void onSuccess(File file) {
                        if (!isAdded() || binding == null) {
                            return;
                        }
                        Uri uri = FileProvider.getUriForFile(
                                requireContext(),
                                requireContext().getPackageName() + ".files",
                                file
                        );
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
                        showError(message);
                    }
                });
    }

    private void copySelectedImage(Uri uri) {
        if (uri == null || !isAdded()) {
            return;
        }
        String mimeType = requireContext().getContentResolver().getType(uri);
        if (mimeType == null || !mimeType.startsWith("image/")) {
            showError("Selecciona un archivo de imagen válido.");
            return;
        }
        String extension = "." + mimeType.substring("image/".length()).replace("jpeg", "jpg");
        File destination = new File(requireContext().getCacheDir(),
                "product-" + UUID.randomUUID() + extension);
        ContentResolver resolver = requireContext().getContentResolver();
        androidx.fragment.app.FragmentActivity activity = getActivity();
        File previous = selectedImage;
        selectedImage = null;
        selectedImageMimeType = null;
        if (previous != null) {
            previous.delete();
        }
        new Thread(() -> {
            String errorMessage = null;
            try (InputStream input = resolver.openInputStream(uri);
                 FileOutputStream output = new FileOutputStream(destination)) {
                if (input == null) {
                    throw new IOException("No se pudo abrir la imagen seleccionada.");
                }
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                }
            } catch (IOException error) {
                destination.delete();
                errorMessage = "No se pudo leer la imagen seleccionada.";
            }
            String resultError = errorMessage;
            if (activity != null) {
                activity.runOnUiThread(() -> {
                    if (!isAdded() || binding == null) {
                        destination.delete();
                    } else if (resultError != null) {
                        showError(resultError);
                    } else {
                        selectedImage = destination;
                        selectedImageMimeType = mimeType;
                        binding.btnResourceImage.setText(destination.getName());
                    }
                });
            } else {
                destination.delete();
            }
        }, "product-image-copy").start();
    }

    private List<FieldSpec> fieldsFor(String key) {
        List<FieldSpec> fields = new ArrayList<>();
        switch (key) {
            case "clientes":
                addText(fields, "nombre", "Nombre", true);
                addText(fields, "apellido", "Apellido", true);
                addEmail(fields, "email", "Correo electrónico", true);
                addPassword(fields, "contrasena", "Nueva contraseña (opcional)", false);
                addNumber(fields, "puntos", "Puntos", true, "0");
                addEnum(fields, "nivelLealtad", "Nivel de lealtad",
                        new String[]{"BRONCE", "PLATA", "ORO"}, "BRONCE");
                break;
            case "productos":
                addText(fields, "nombre", "Nombre", true);
                addText(fields, "descripcion", "Descripción", true);
                addDecimal(fields, "precio", "Precio", true, "0");
                addNumber(fields, "stock", "Existencias", true, "0");
                addDecimal(fields, "peso", "Peso", false, "0");
                addDecimal(fields, "valoracion", "Valoración", false, "0");
                addBoolean(fields, "activo", "Activo", true);
                addText(fields, "codigoBarras", "Código de barras", true);
                addEnum(fields, "unidadMedida", "Unidad de medida",
                        new String[]{"KILOGRAMO", "GRAMO", "LITRO", "MILILITRO",
                                "METRO", "CENTIMETRO", "PIEZA"}, "PIEZA");
                addDate(fields, "fechaCaducidad", "Caducidad (opcional)", false);
                addText(fields, "imagen", "URL de imagen (opcional)", false);
                addMultiRelation(fields, "promocionIds", "Promociones", "promociones");
                break;
            case "proveedores":
                addText(fields, "nombre", "Nombre", true);
                addText(fields, "telefono", "Teléfono", false);
                addEmail(fields, "email", "Correo electrónico", false);
                addText(fields, "direccion", "Dirección", false);
                addBoolean(fields, "activo", "Activo", true);
                break;
            case "promociones":
                addText(fields, "nombre", "Nombre", true);
                addDecimal(fields, "porcentajeDescuento", "Descuento (0 a 100)", true, "0");
                addDate(fields, "fechaInicio", "Fecha de inicio", true);
                addDate(fields, "fechaFin", "Fecha de fin (opcional)", false);
                addBoolean(fields, "activa", "Activa", true);
                addMultiRelation(fields, "productoIds", "Productos", "productos");
                break;
            case "abastecimientos":
                addDateTime(fields, "fecha", "Fecha", true);
                addRelation(fields, "proveedorId", "Proveedor", "proveedores");
                break;
            case "ventas":
                addDateTime(fields, "fecha", "Fecha", true);
                if (!"CAJERO".equals(user.getRol())) {
                    addRelation(fields, "cajeroId", "Cajero", "cajeros");
                }
                addRelation(fields, "clienteId", "Cliente", "clientes");
                addNumber(fields, "puntosUtilizados", "Puntos utilizados", true, "0");
                addEnum(fields, "metodoPago", "Método de pago",
                        new String[]{"EFECTIVO", "TARJETA", "TRANSFERENCIA", "OTRO"},
                        "EFECTIVO");
                addDecimal(fields, "efectivoRecibido", "Efectivo recibido (si aplica)",
                        false, "");
                break;
            case "cajeros":
                addText(fields, "nombre", "Nombre", true);
                addText(fields, "apellido", "Apellido", true);
                addEmail(fields, "email", "Correo electrónico", true);
                addPassword(fields, "contrasena", "Contraseña", false);
                addEnum(fields, "turno", "Turno",
                        new String[]{"MATUTINO", "VESPERTINO", "NOCTURNO"}, "MATUTINO");
                addNumber(fields, "cajaAsignada", "Caja asignada", true, "1");
                break;
            case "administradores":
                addText(fields, "nombre", "Nombre", true);
                addText(fields, "apellido", "Apellido", true);
                addEmail(fields, "email", "Correo electrónico", true);
                addPassword(fields, "contrasena", "Contraseña", false);
                break;
            case "usuarios":
                addText(fields, "nombre", "Nombre", true);
                addText(fields, "apellido", "Apellido", true);
                addEmail(fields, "email", "Correo electrónico", true);
                addPassword(fields, "contrasena", "Contraseña", true);
                addEnum(fields, "rol", "Rol",
                        new String[]{"CLIENTE", "CAJERO", "ADMINISTRADOR"}, "CLIENTE");
                break;
            default:
                break;
        }
        return fields;
    }

    private void addText(List<FieldSpec> fields, String key, String label, boolean required) {
        fields.add(new FieldSpec(key, label, INPUT_TEXT, required, "", null, null));
    }

    private void validateSale(
            JsonObject request,
            List<FieldInput> inputs,
            List<JsonObject> details
    ) {
        int points = request.get("puntosUtilizados").getAsInt();
        if (points < 0) {
            throw new IllegalArgumentException("Los puntos utilizados no pueden ser negativos.");
        }
        FieldInput customerField = fieldInput(inputs, "clienteId");
        List<JsonObject> promotions = customerField == null || customerField.salePromotions == null
                ? new ArrayList<>() : customerField.salePromotions;
        SaleEstimate estimate = calculateSaleEstimate(inputs, details, promotions);
        if (points > estimate.maxPoints) {
            throw new IllegalArgumentException(getString(
                    R.string.sale_points_exceed_maximum, estimate.maxPoints));
        }
        if ("EFECTIVO".equals(request.get("metodoPago").getAsString())) {
            JsonElement received = request.get("efectivoRecibido");
            BigDecimal receivedAmount = received == null || received.isJsonNull()
                    ? null : received.getAsBigDecimal();
            if (receivedAmount == null || receivedAmount.signum() < 0) {
                throw new IllegalArgumentException(
                        "Ingresa un monto válido de efectivo recibido.");
            }
            if (!SaleFormData.isCashReceivedSufficient(receivedAmount, estimate.total)) {
                throw new IllegalArgumentException(getString(
                        R.string.sale_cash_below_total, money(estimate.total)));
            }
        }
        if (details.isEmpty()) {
            throw new IllegalArgumentException("Agrega al menos un producto a la venta.");
        }
    }

    private void validatePromotion(JsonObject request) {
        JsonElement productIds = request.get("productoIds");
        if (productIds == null || !productIds.isJsonArray()
                || productIds.getAsJsonArray().isEmpty()) {
            throw new IllegalArgumentException("Selecciona al menos un producto para la promoción.");
        }
        LocalDate start = LocalDate.parse(request.get("fechaInicio").getAsString());
        JsonElement endValue = request.get("fechaFin");
        if (endValue != null && !endValue.isJsonNull()
                && !endValue.getAsString().trim().isEmpty()) {
            LocalDate end = LocalDate.parse(endValue.getAsString());
            if (end.isBefore(start)) {
                throw new IllegalArgumentException(
                        "La fecha de fin no puede ser anterior a la fecha de inicio.");
            }
        }
    }

    private void validateNumber(String key, long value) {
        if (value < 0) {
            throw new IllegalArgumentException("El campo " + key + " no puede ser negativo.");
        }
        if (("cantidad".equals(key) || "cajaAsignada".equals(key)) && value < 1) {
            throw new IllegalArgumentException("El campo " + key + " debe ser mayor a cero.");
        }
    }

    private void validateDecimal(String key, BigDecimal value) {
        if (value.signum() < 0) {
            throw new IllegalArgumentException("El campo " + key + " no puede ser negativo.");
        }
        if ("porcentajeDescuento".equals(key)
                && value.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("El descuento no puede superar 100%.");
        }
    }

    private FieldInput fieldInput(List<FieldInput> inputs, String key) {
        for (FieldInput input : inputs) {
            if (key.equals(input.spec.key)) {
                return input;
            }
        }
        return null;
    }

    private void addEmail(List<FieldSpec> fields, String key, String label, boolean required) {
        fields.add(new FieldSpec(key, label, INPUT_EMAIL, required, "", null, null));
    }

    private void addPassword(List<FieldSpec> fields, String key, String label, boolean required) {
        fields.add(new FieldSpec(key, label, INPUT_PASSWORD, required, "", null, null));
    }

    private void addNumber(
            List<FieldSpec> fields,
            String key,
            String label,
            boolean required,
            String defaultValue
    ) {
        fields.add(new FieldSpec(key, label, INPUT_NUMBER, required, defaultValue, null, null));
    }

    private void addDecimal(
            List<FieldSpec> fields,
            String key,
            String label,
            boolean required,
            String defaultValue
    ) {
        fields.add(new FieldSpec(key, label, INPUT_DECIMAL, required, defaultValue, null, null));
    }

    private void addDate(List<FieldSpec> fields, String key, String label, boolean required) {
        fields.add(new FieldSpec(key, label, INPUT_DATE, required, "", null, null));
    }

    private void addDateTime(List<FieldSpec> fields, String key, String label, boolean required) {
        fields.add(new FieldSpec(key, label, INPUT_DATETIME, required,
                LocalDateTime.now().withNano(0).toString(), null, null));
    }

    private void addBoolean(List<FieldSpec> fields, String key, String label, boolean defaultValue) {
        fields.add(new FieldSpec(key, label, INPUT_BOOLEAN, false,
                String.valueOf(defaultValue), null, null));
    }

    private void addEnum(
            List<FieldSpec> fields,
            String key,
            String label,
            String[] options,
            String defaultValue
    ) {
        fields.add(new FieldSpec(key, label, INPUT_ENUM, true, defaultValue, options, null));
    }

    private void addRelation(
            List<FieldSpec> fields,
            String key,
            String label,
            String path
    ) {
        fields.add(new FieldSpec(key, label, INPUT_RELATION, true, "", null, path));
    }

    private void addMultiRelation(
            List<FieldSpec> fields,
            String key,
            String label,
            String path
    ) {
        fields.add(new FieldSpec(key, label, INPUT_MULTI_RELATION, false, "", null, path));
    }

    private int inputType(int type) {
        switch (type) {
            case INPUT_NUMBER:
                return InputType.TYPE_CLASS_NUMBER;
            case INPUT_DECIMAL:
                return InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                        | InputType.TYPE_NUMBER_FLAG_SIGNED;
            case INPUT_EMAIL:
                return InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS;
            case INPUT_PASSWORD:
                return InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD;
            case INPUT_DATE:
                return InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_DATE;
            case INPUT_DATETIME:
                return InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_NORMAL;
            default:
                return InputType.TYPE_CLASS_TEXT;
        }
    }

    private void setSpinnerValues(Spinner spinner, String[] values, String selected) {
        List<String> items = Arrays.asList(values);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        int position = items.indexOf(selected);
        spinner.setSelection(Math.max(0, position));
    }

    private Set<Long> readIdSet(JsonElement element) {
        Set<Long> ids = new LinkedHashSet<>();
        if (element != null && element.isJsonArray()) {
            for (JsonElement id : element.getAsJsonArray()) {
                if (id.isJsonPrimitive()) {
                    ids.add(id.getAsLong());
                } else if (id.isJsonObject() && id.getAsJsonObject().has("id")) {
                    ids.add(id.getAsJsonObject().get("id").getAsLong());
                }
            }
        }
        return ids;
    }

    private Long relationId(JsonElement value) {
        if (value == null || value.isJsonNull()) {
            return null;
        }
        if (value.isJsonPrimitive()) {
            return value.getAsLong();
        }
        if (value.isJsonObject() && value.getAsJsonObject().has("id")) {
            return value.getAsJsonObject().get("id").getAsLong();
        }
        return null;
    }

    private List<JsonObject> initialDetails(JsonElement existing) {
        List<JsonObject> details = new ArrayList<>();
        if (!hasLineItems(resource.key) || existing == null || !existing.isJsonObject()) {
            return details;
        }
        JsonElement value = existing.getAsJsonObject().get("detalles");
        if (value != null && value.isJsonArray()) {
            for (JsonElement item : value.getAsJsonArray()) {
                if (item.isJsonObject()) {
                    details.add(item.getAsJsonObject().deepCopy());
                }
            }
        }
        return details;
    }

    private boolean hasLineItems(String key) {
        return "ventas".equals(key) || "abastecimientos".equals(key);
    }

    private String optionLabel(JsonObject object) {
        StringBuilder label = new StringBuilder();
        for (String key : new String[]{"nombre", "apellido", "nombreCompleto", "email"}) {
            JsonElement value = object.get(key);
            if (value != null && value.isJsonPrimitive()
                    && !value.getAsString().trim().isEmpty()) {
                if (label.length() > 0) {
                    label.append(' ');
                }
                label.append(value.getAsString());
            }
        }
        if (label.length() == 0) {
            label.append("Registro");
        }
        label.append(" (#").append(object.get("id").getAsString()).append(')');
        return label.toString();
    }

    private String textOf(EditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    private void showError(String message) {
        if (isAdded()) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
            if (!RetrofitClient.hasSessionCookie()) {
                NavHostFragment.findNavController(this).navigate(R.id.action_global_login);
            }
        }
    }

    private ApiResource findResource(String key, List<ApiResource> resources) {
        if (key == null) {
            return null;
        }
        for (ApiResource item : resources) {
            if (item.key.equals(key)) {
                return item;
            }
        }
        return null;
    }

    private String pretty(JsonElement element) {
        return new GsonBuilder().setPrettyPrinting().create().toJson(element);
    }

    private void showMessage(int message) {
        if (binding == null) {
            return;
        }
        binding.resourceRecords.removeAllViews();
        LinearLayout empty = new LinearLayout(requireContext());
        empty.setOrientation(LinearLayout.VERTICAL);
        empty.setGravity(Gravity.CENTER);
        empty.setMinimumHeight(
                getResources().getDimensionPixelSize(R.dimen.empty_state_min_height));
        ImageView icon = new ImageView(requireContext());
        icon.setImageResource(R.drawable.ic_search);
        icon.setColorFilter(requireContext().getColor(R.color.muted_on_soft));
        icon.setContentDescription(null);
        int iconSize = getResources().getDimensionPixelSize(R.dimen.empty_icon_size);
        empty.addView(icon, new LinearLayout.LayoutParams(iconSize, iconSize));
        TextView label = new TextView(requireContext());
        label.setText(message);
        label.setTextColor(requireContext().getColor(R.color.muted_on_soft));
        label.setTextSize(getResources().getDimension(R.dimen.body_text_size)
                / getResources().getDisplayMetrics().scaledDensity);
        label.setGravity(Gravity.CENTER);
        label.setPadding(0, getResources().getDimensionPixelSize(R.dimen.space_2),
                0, 0);
        empty.addView(label);
        binding.resourceRecords.addView(empty);
        binding.resourceRecords.setVisibility(View.VISIBLE);
    }

    private void showListError(String message) {
        if (binding == null) {
            return;
        }
        binding.resourceRecords.removeAllViews();
        MaterialCardView notice = new MaterialCardView(requireContext());
        notice.setCardBackgroundColor(requireContext().getColor(R.color.error_notice_bg));
        notice.setRadius(getResources().getDimension(R.dimen.card_corner_radius));
        notice.setCardElevation(getResources().getDimension(R.dimen.card_elevation));
        notice.setStrokeWidth(getResources().getDimensionPixelSize(R.dimen.card_stroke_width));
        notice.setStrokeColor(requireContext().getColor(R.color.danger_border));
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = getResources().getDimensionPixelSize(R.dimen.space_4);
        content.setPadding(padding, padding, padding, padding);
        TextView error = new TextView(requireContext());
        error.setText(message == null || message.trim().isEmpty()
                ? getString(R.string.resource_load_error)
                : message);
        error.setTextColor(requireContext().getColor(R.color.danger));
        error.setTextSize(getResources().getDimension(R.dimen.body_text_size)
                / getResources().getDisplayMetrics().scaledDensity);
        content.addView(error);
        MaterialButton retry = new MaterialButton(requireContext());
        retry.setText(R.string.resource_retry);
        retry.setTextColor(requireContext().getColor(R.color.accent));
        retry.setStrokeColor(ColorStateList.valueOf(requireContext().getColor(R.color.accent)));
        retry.setBackgroundTintList(ColorStateList.valueOf(
                requireContext().getColor(R.color.surface)));
        retry.setCornerRadius(getResources().getDimensionPixelSize(
                R.dimen.button_corner_radius));
        retry.setOnClickListener(ignored -> loadRecords());
        LinearLayout.LayoutParams retryParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                getResources().getDimensionPixelSize(R.dimen.touch_target_min));
        retryParams.topMargin = getResources().getDimensionPixelSize(R.dimen.space_2);
        content.addView(retry, retryParams);
        notice.addView(content);
        binding.resourceRecords.addView(notice);
        binding.resourceRecords.setVisibility(View.VISIBLE);
    }

    private String summary(JsonElement record) {
        if (!record.isJsonObject()) {
            return pretty(record);
        }
        JsonObject object = record.getAsJsonObject();
        StringBuilder text = new StringBuilder();
        for (String field : new String[]{"nombre", "productoNombre", "clienteNombre",
                "proveedorNombre", "cajeroNombre", "email", "descripcion", "precio",
                "total", "stock", "fecha"}) {
            appendField(text, object, field);
        }
        if (text.length() == 0) {
            appendField(text, object, "id");
            appendField(text, object, "rol");
        } else if (object.has("id")) {
            text.append("\n#").append(object.get("id").getAsString());
        }
        return text.toString();
    }

    private void appendField(StringBuilder text, JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || value.isJsonNull() || value.isJsonArray()
                || value.isJsonObject() || value.getAsString().trim().isEmpty()) {
            return;
        }
        if (text.length() > 0) {
            text.append('\n');
        }
        text.append(fieldLabel(field)).append(": ").append(value.getAsString());
    }

    private String fieldLabel(String field) {
        switch (field) {
            case "nombre":
                return "Nombre";
            case "productoNombre":
                return "Producto";
            case "clienteNombre":
                return "Cliente";
            case "proveedorNombre":
                return "Proveedor";
            case "cajeroNombre":
                return "Cajero";
            case "descripcion":
                return "Descripción";
            case "precio":
                return "Precio";
            case "total":
                return "Total";
            case "stock":
                return "Existencias";
            case "fecha":
                return "Fecha";
            case "id":
                return "ID";
            case "rol":
                return "Rol";
            case "numeroEmpleado":
                return "Número de empleado";
            case "numeroAdministrador":
                return "Número de administrador";
            case "turno":
                return "Turno";
            case "cajaAsignada":
                return "Caja asignada";
            case "categoriaNombre":
                return "Categoría";
            case "codigoBarras":
                return "Código de barras";
            case "puntos":
                return "Puntos";
            case "nivelLealtad":
                return "Nivel";
            case "metodoPago":
                return "Método de pago";
            default:
                return "Correo";
        }
    }

    @Override
    public void onDestroyView() {
        if (searchTask != null) {
            searchHandler.removeCallbacks(searchTask);
        }
        if (selectedImage != null && !productUploadInProgress) {
            selectedImage.delete();
            selectedImage = null;
        }
        binding = null;
        super.onDestroyView();
    }

    private static final class FieldSpec {
        final String key;
        final String label;
        final int type;
        final boolean required;
        final String defaultValue;
        final String[] options;
        final String relationPath;

        FieldSpec(
                String key,
                String label,
                int type,
                boolean required,
                String defaultValue,
                String[] options,
                String relationPath
        ) {
            this.key = key;
            this.label = label;
            this.type = type;
            this.required = required;
            this.defaultValue = defaultValue;
            this.options = options;
            this.relationPath = relationPath;
        }
    }

    private static final class FieldInput {
        final FieldSpec spec;
        final List<Option> options = new ArrayList<>();
        final Set<Long> selectedIds = new LinkedHashSet<>();
        TextInputEditText editText;
        SwitchMaterial toggle;
        Spinner spinner;
        MaterialButton multiButton;
        MaterialButton relationButton;
        View inputView;
        TextView pointsBalanceView;
        TextView dependentPointsView;
        TextInputEditText pointsInput;
        TextView saleSummaryView;
        List<FieldInput> saleInputs;
        List<JsonObject> saleDetails;
        List<JsonObject> salePromotions;
        boolean[] salePromotionsLoaded;
        Option selectedOption;

        FieldInput(FieldSpec spec) {
            this.spec = spec;
        }
    }

    private static final class SaleSummaryContext {
        final List<FieldInput> inputs;
        List<JsonObject> details;
        final List<JsonObject> promotions;
        final boolean[] promotionsLoaded;

        SaleSummaryContext(
                List<FieldInput> inputs,
                List<JsonObject> details,
                List<JsonObject> promotions,
                boolean[] promotionsLoaded
        ) {
            this.inputs = inputs;
            this.details = details;
            this.promotions = promotions;
            this.promotionsLoaded = promotionsLoaded;
        }
    }

    private static final class Option {
        final long id;
        final String label;
        final JsonObject record;

        Option(long id, String label) {
            this(id, label, null);
        }

        Option(long id, String label, JsonObject record) {
            this.id = id;
            this.label = label;
            this.record = record;
        }

        @NonNull
        @Override
        public String toString() {
            return label;
        }
    }
}
