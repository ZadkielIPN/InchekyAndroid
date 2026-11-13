package com.example.inchekyandroid.ui.home;

import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Patterns;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.example.inchekyandroid.R;
import com.example.inchekyandroid.data.model.UsuarioResponse;
import com.example.inchekyandroid.data.model.ApiRequestMapper;
import com.example.inchekyandroid.data.model.SaleFormData;
import com.example.inchekyandroid.data.model.SaleEstimate;
import com.example.inchekyandroid.data.model.SaleLineRequestMapper;
import com.example.inchekyandroid.data.repository.ApiRepository;
import com.example.inchekyandroid.data.repository.SessionRepository;
import com.example.inchekyandroid.data.remote.RetrofitClient;
import com.example.inchekyandroid.data.remote.ProductImageUrl;
import com.example.inchekyandroid.databinding.ActivityResourceBinding;
import com.google.android.material.button.MaterialButton;
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
        binding.btnResourceSearch.setEnabled(false);
        binding.btnResourceCreate.setEnabled(false);
        binding.btnResourceImage.setEnabled(false);
        binding.btnPreviousPage.setEnabled(false);
        binding.btnNextPage.setEnabled(false);
        binding.btnResourceBack.setOnClickListener(ignored ->
                NavHostFragment.findNavController(this).popBackStack());
        binding.btnResourceSearch.setOnClickListener(ignored -> {
            page = 0;
            loadRecords();
        });
        binding.btnResourceCreate.setOnClickListener(ignored -> showEditor(null, 0));
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
        binding.tvResourceTitle.setText(resource.title);
        binding.btnResourceSearch.setEnabled(resource.searchable);
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
            if (loading) {
                binding.tvPageNumber.setText(R.string.home_loading);
            } else if (binding != null && page == lastLoadedPage) {
                showPageNumber();
            }
            updatePagerControls(Boolean.TRUE.equals(loading));
        });
        viewModel.getError().observe(getViewLifecycleOwner(), message -> {
            if (message != null && !message.isEmpty()) {
                page = lastLoadedPage;
                showPageNumber();
                updatePagerControls(false);
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
            }
        });
        viewModel.getRecords().observe(getViewLifecycleOwner(), this::renderPage);
    }

    private void renderPage(JsonElement response) {
        if (binding == null || response == null || !response.isJsonObject()) {
            restoreLastLoadedPage();
            showMessage(R.string.resource_empty);
            return;
        }
        JsonObject pageResult = response.getAsJsonObject();
        JsonElement content = pageResult.get("content");
        if (content == null || !content.isJsonArray()) {
            restoreLastLoadedPage();
            showMessage(R.string.resource_empty);
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
        binding.btnResourceSearch.setEnabled(
                !loading && resource != null && resource.searchable);
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
        card.setCardBackgroundColor(requireContext().getColor(R.color.white));
        card.setRadius(getResources().getDimension(R.dimen.auth_card_radius));
        card.setCardElevation(getResources().getDimension(R.dimen.auth_card_elevation));
        card.setStrokeWidth(getResources().getDimensionPixelSize(R.dimen.card_stroke_width));
        card.setStrokeColor(requireContext().getColor(R.color.card_outline));
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(16, 12, 16, 12);
        if (("catalogo".equals(resource.key) || "productos".equals(resource.key))
                && record.isJsonObject()) {
            addProductImage(content, record.getAsJsonObject());
        }
        TextView summary = new TextView(requireContext());
        summary.setText(summary(record));
        summary.setTextColor(requireContext().getColor(R.color.auth_ink));
        summary.setTextSize(16);
        content.addView(summary);
        LinearLayout actions = new LinearLayout(requireContext());
        actions.setOrientation(LinearLayout.HORIZONTAL);
        long id = record.isJsonObject() && record.getAsJsonObject().has("id")
                ? record.getAsJsonObject().get("id").getAsLong()
                : -1;
        if (id > 0) {
            if (resource.canDetail) {
                addAction(actions, R.string.resource_details, R.drawable.ic_info,
                        ignored -> showDetails(id));
            }
            if (resource.canUpdate) {
                addAction(actions, R.string.resource_edit, R.drawable.ic_edit,
                        ignored -> showEditor(record, id));
            }
            if (resource.canDelete) {
                addAction(actions, R.string.resource_delete, R.drawable.ic_delete,
                        ignored -> confirmDelete(id));
            }
            if ("ventas".equals(resource.key) || "compras".equals(resource.key)) {
                addAction(actions, R.string.resource_ticket, R.drawable.ic_receipt,
                        ignored -> downloadTicket(id));
                if ("ventas".equals(resource.key)) {
                    addAction(actions, R.string.resource_email_ticket,
                            R.drawable.ic_email,
                            ignored -> promptEmailTicket(id));
                }
            }
        }
        content.addView(actions);
        card.addView(content);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = getResources().getDimensionPixelSize(R.dimen.auth_card_spacing);
        binding.resourceRecords.addView(card, params);
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

    private void addAction(
            LinearLayout parent,
            int label,
            int icon,
            View.OnClickListener listener
    ) {
        MaterialButton button = new MaterialButton(requireContext());
        button.setText(label);
        button.setAllCaps(false);
        button.setTextColor(requireContext().getColor(R.color.white));
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                requireContext().getColor(R.color.auth_secondary)));
        button.setIconResource(icon);
        button.setIconTintResource(R.color.white);
        button.setCornerRadius(getResources()
                .getDimensionPixelSize(R.dimen.input_corner_radius));
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        params.setMarginEnd(getResources().getDimensionPixelSize(R.dimen.action_button_spacing));
        parent.addView(button, params);
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
        header.setTextSize(18);
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
        label.setTextSize(12);
        TextView content = new TextView(requireContext());
        content.setText(text);
        content.setTextColor(requireContext().getColor(R.color.auth_ink));
        content.setTextSize(16);
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
        heading.setTextSize(17);
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
            product.setTextSize(16);
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
            detailTitle.setTextSize(16);
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
            summaryTitle.setTextSize(16);
            summaryTitle.setTextColor(requireContext().getColor(R.color.auth_ink));
            addFormView(form, summaryTitle, true);
            saleSummary[0] = new TextView(requireContext());
            saleSummary[0].setTextColor(requireContext().getColor(R.color.auth_ink));
            saleSummary[0].setTextSize(14);
            saleSummary[0].setPadding(spacing, spacing, spacing, spacing);
            MaterialCardView summaryCard = createDetailCard();
            summaryCard.addView(saleSummary[0]);
            addFormView(form, summaryCard);
            TextView summaryHint = new TextView(requireContext());
            summaryHint.setText(R.string.sale_estimate_disclaimer);
            summaryHint.setTextColor(requireContext().getColor(R.color.auth_muted));
            summaryHint.setTextSize(12);
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
        scroll.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(existing == null ? R.string.resource_create : R.string.resource_edit)
                .setView(scroll)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(button -> {
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
                }));
        dialog.show();
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
        } else if (spec.type == INPUT_ENUM || spec.type == INPUT_RELATION) {
            TextView label = new TextView(requireContext());
            label.setText(spec.label);
            label.setTextColor(requireContext().getColor(R.color.auth_ink));
            addFormView(parent, label);
            Spinner spinner = new Spinner(requireContext());
            addFormView(parent, spinner);
            input.spinner = spinner;
            input.inputView = spinner;
            if (spec.type == INPUT_ENUM) {
                setSpinnerValues(spinner, spec.options, initial == null ? spec.defaultValue
                        : initial.getAsString());
            } else {
                loadRelationOptions(input, relationId(initial));
            }
        } else if (spec.type == INPUT_MULTI_RELATION) {
            MaterialButton selector = new MaterialButton(requireContext());
            selector.setText(spec.label);
            selector.setAllCaps(false);
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
            addFormView(parent, layout);
            input.editText = editText;
            input.inputView = layout;
            if ("puntosUtilizados".equals(spec.key)) {
                TextView balance = new TextView(requireContext());
                balance.setText(R.string.sale_points_select_customer);
                balance.setTextColor(requireContext().getColor(R.color.auth_muted));
                balance.setTextSize(13);
                input.pointsBalanceView = balance;
                addFormView(parent, balance);
            }
        }
        return input;
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
                            input.editText.setError("Campo requerido");
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
                        input.editText.setError("Ingresa un correo válido");
                        throw new IllegalArgumentException("Revisa el correo electrónico.");
                    }
                    if (spec.type == INPUT_PASSWORD
                            && (value.length() < 8 || value.length() > 20)) {
                        input.editText.setError("Usa de 8 a 20 caracteres");
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
                            validateDate(spec.key, value);
                            request.addProperty(spec.key, value);
                        }
                    } catch (NumberFormatException error) {
                        input.editText.setError("Ingresa un valor válido");
                        throw new IllegalArgumentException("Revisa el campo " + spec.label + ".");
                    }
            }
        }
        return request;
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
                        List<String> labels = new ArrayList<>();
                        labels.add("Selecciona " + field.spec.label);
                        for (Option option : field.options) {
                            labels.add(option.label);
                        }
                        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                                android.R.layout.simple_spinner_item, labels);
                        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                        field.spinner.setAdapter(adapter);
                        field.selectedOption = null;
                        if (selectedId != null) {
                            for (int i = 0; i < field.options.size(); i++) {
                                if (field.options.get(i).id == selectedId) {
                                    field.spinner.setSelection(i + 1);
                                    field.selectedOption = field.options.get(i);
                                    break;
                                }
                            }
                        }
                        field.spinner.setOnItemSelectedListener(
                                new android.widget.AdapterView.OnItemSelectedListener() {
                                    @Override
                                    public void onItemSelected(
                                            android.widget.AdapterView<?> parent,
                                            View view,
                                            int position,
                                            long id
                                    ) {
                                        field.selectedOption = position == 0
                                                ? null : field.options.get(position - 1);
                                        updateCustomerPoints(field);
                                    }

                                    @Override
                                    public void onNothingSelected(
                                            android.widget.AdapterView<?> parent
                                    ) {
                                        field.selectedOption = null;
                                        updateCustomerPoints(field);
                                    }
                                });
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
        layout.setBoxStrokeWidthFocused(getResources()
                .getDimensionPixelSize(R.dimen.input_focused_stroke_width));
        layout.setBoxBackgroundColor(requireContext().getColor(R.color.white));
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

    private void validateDate(String key, String value) {
        try {
            if ("fecha".equals(key)) {
                LocalDateTime.parse(value);
            } else {
                LocalDate date = LocalDate.parse(value);
                if (("fechaFin".equals(key) || "fechaCaducidad".equals(key))
                        && !date.isAfter(LocalDate.now())) {
                    throw new IllegalArgumentException(
                            "La fecha de fin/caducidad debe ser futura.");
                }
            }
        } catch (java.time.DateTimeException error) {
            throw new IllegalArgumentException("La fecha no tiene un formato válido.");
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
        TextView empty = new TextView(requireContext());
        empty.setText(message);
        empty.setTextColor(requireContext().getColor(R.color.auth_muted));
        binding.resourceRecords.addView(empty);
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
            default:
                return "Correo";
        }
    }

    @Override
    public void onDestroyView() {
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
