package com.example.inchekyandroid.ui.home;

import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.util.Patterns;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
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
import com.example.inchekyandroid.data.repository.ApiRepository;
import com.example.inchekyandroid.data.repository.SessionRepository;
import com.example.inchekyandroid.data.remote.RetrofitClient;
import com.example.inchekyandroid.databinding.ActivityResourceBinding;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
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
                binding.resourceRecords.removeAllViews();
                binding.tvPageNumber.setText(R.string.home_loading);
            }
        });
        viewModel.getError().observe(getViewLifecycleOwner(), message -> {
            if (message != null && !message.isEmpty()) {
                binding.tvPageNumber.setText("");
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
            }
        });
        viewModel.getRecords().observe(getViewLifecycleOwner(), this::renderPage);
    }

    private void renderPage(JsonElement response) {
        if (binding == null || response == null || !response.isJsonObject()) {
            showMessage(R.string.resource_empty);
            return;
        }
        JsonObject pageResult = response.getAsJsonObject();
        JsonElement content = pageResult.get("content");
        if (content == null || !content.isJsonArray()) {
            showMessage(R.string.resource_empty);
            return;
        }
        JsonArray records = content.getAsJsonArray();
        totalPages = pageResult.has("totalPages")
                ? Math.max(1, pageResult.get("totalPages").getAsInt())
                : 1;
        page = pageResult.has("number") ? pageResult.get("number").getAsInt() : page;
        binding.tvPageNumber.setText(getString(R.string.resource_page, page + 1, totalPages));
        binding.btnPreviousPage.setEnabled(page > 0);
        binding.btnNextPage.setEnabled(page + 1 < totalPages);
        binding.resourceRecords.removeAllViews();
        if (records.size() == 0) {
            showMessage(R.string.resource_empty);
            return;
        }
        for (JsonElement record : records) {
            addRecordCard(record);
        }
    }

    private void loadActivePromotions(Spinner spinner, long productId) {
        viewModel.listOptions("promociones", new ApiRepository.ResultCallback<JsonElement>() {
            @Override
            public void onSuccess(JsonElement result) {
                if (!isAdded() || binding == null) {
                    return;
                }
                List<Option> promotions = new ArrayList<>();
                promotions.add(new Option(0, "Sin promoción"));
                LocalDate today = LocalDate.now();
                for (Option option : optionsFrom(result)) {
                    JsonObject promotion = option.record;
                    if (promotion == null
                            || !promotion.has("activa")
                            || !promotion.get("activa").getAsBoolean()) {
                        continue;
                    }
                    try {
                        JsonElement products = promotion.get("productoIds");
                        if (products != null && products.isJsonArray()
                                && products.getAsJsonArray().size() > 0) {
                            boolean appliesToProduct = false;
                            for (JsonElement id : products.getAsJsonArray()) {
                                appliesToProduct |= id.getAsLong() == productId;
                            }
                            if (!appliesToProduct) {
                                continue;
                            }
                        }
                        LocalDate start = LocalDate.parse(
                                promotion.get("fechaInicio").getAsString());
                        LocalDate end = LocalDate.parse(
                                promotion.get("fechaFin").getAsString());
                        if (!today.isBefore(start) && !today.isAfter(end)) {
                            promotions.add(option);
                        }
                    } catch (RuntimeException error) {
                        showError("Una promoción tiene fechas inválidas y no se puede ofrecer.");
                    }
                }
                ArrayAdapter<Option> adapter = new ArrayAdapter<>(requireContext(),
                        android.R.layout.simple_spinner_item, promotions);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinner.setAdapter(adapter);
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) {
                    return;
                }
                showError("No se pudieron consultar promociones: " + message);
                ArrayAdapter<Option> adapter = new ArrayAdapter<>(requireContext(),
                        android.R.layout.simple_spinner_item,
                        Arrays.asList(new Option(0, "Sin promoción")));
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinner.setAdapter(adapter);
            }
        });
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
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(16, 12, 16, 12);
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

    private void addAction(
            LinearLayout parent,
            int label,
            int icon,
            View.OnClickListener listener
    ) {
        MaterialButton button = new MaterialButton(requireContext());
        button.setText(label);
        button.setAllCaps(false);
        button.setIconResource(icon);
        button.setIconTintResource(R.color.auth_primary);
        button.setOnClickListener(listener);
        parent.addView(button);
    }

    private void showDetails(long id) {
        String path = "catalogo".equals(resource.key) ? "productos" : resource.path;
        viewModel.get(path, id, new ApiRepository.ResultCallback<JsonElement>() {
            @Override
            public void onSuccess(JsonElement result) {
                if (!isAdded()) {
                    return;
                }
                new AlertDialog.Builder(requireContext())
                        .setTitle(resource.title)
                        .setMessage(pretty(result))
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            }

            @Override
            public void onError(String message) {
                showError(message);
            }
        });
    }

    private void showEditor(JsonElement existing, long id) {
        List<FieldSpec> fields = fieldsFor(resource.key);
        if (fields.isEmpty()) {
            showError("No hay un formulario habilitado para este recurso.");
            return;
        }

        LinearLayout form = new LinearLayout(requireContext());
        form.setOrientation(LinearLayout.VERTICAL);
        int spacing = getResources().getDimensionPixelSize(R.dimen.auth_card_spacing);
        form.setPadding(spacing, spacing, spacing, spacing);
        List<FieldInput> inputs = new ArrayList<>();

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

        List<JsonObject> detailRows = initialDetails(existing);
        final LinearLayout detailsContainer;
        if (hasLineItems(resource.key)) {
            TextView detailTitle = new TextView(requireContext());
            detailTitle.setText(R.string.resource_line_items);
            detailTitle.setTextSize(16);
            detailTitle.setTextColor(requireContext().getColor(R.color.auth_ink));
            form.addView(detailTitle);
            detailsContainer = new LinearLayout(requireContext());
            detailsContainer.setOrientation(LinearLayout.VERTICAL);
            form.addView(detailsContainer);
            renderDetailRows(detailsContainer, detailRows);
            MaterialButton addLine = new MaterialButton(requireContext());
            addLine.setText(R.string.resource_add_line);
            addLine.setAllCaps(false);
            addLine.setIconResource(R.drawable.ic_add);
            addLine.setIconTintResource(R.color.auth_primary);
            addLine.setOnClickListener(ignored -> showLineEditor(detailRows, detailsContainer));
            form.addView(addLine);
        } else {
            detailsContainer = null;
        }

        if ("productos".equals(resource.key) && resource.canCreate) {
            TextView imageHint = new TextView(requireContext());
            imageHint.setText(R.string.resource_choose_image);
            imageHint.setTextColor(requireContext().getColor(R.color.auth_muted));
            form.addView(imageHint);
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
                            validateSale(request, inputs, detailRows);
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
            parent.addView(toggle);
            input.toggle = toggle;
        } else if (spec.type == INPUT_ENUM || spec.type == INPUT_RELATION) {
            TextView label = new TextView(requireContext());
            label.setText(spec.label);
            label.setTextColor(requireContext().getColor(R.color.auth_ink));
            parent.addView(label);
            Spinner spinner = new Spinner(requireContext());
            parent.addView(spinner);
            input.spinner = spinner;
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
            parent.addView(selector);
            input.multiButton = selector;
            input.selectedIds.addAll(readIdSet(initial));
            selector.setOnClickListener(ignored -> chooseMultipleOptions(input));
            loadMultipleOptions(input);
        } else {
            TextInputLayout layout = new TextInputLayout(requireContext());
            layout.setHint(spec.label);
            layout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
            TextInputEditText editText = new TextInputEditText(layout.getContext());
            editText.setSingleLine(spec.type != INPUT_DATETIME);
            editText.setInputType(inputType(spec.type));
            editText.setText(initial == null || initial.isJsonNull()
                    ? spec.defaultValue
                    : initial.getAsString());
            if (spec.type == INPUT_DATE || spec.type == INPUT_DATETIME) {
                editText.setFocusable(false);
                editText.setOnClickListener(ignored ->
                        showDatePicker(editText, spec.type == INPUT_DATETIME));
            }
            layout.addView(editText);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.bottomMargin = getResources().getDimensionPixelSize(R.dimen.auth_card_spacing);
            parent.addView(layout, params);
            input.editText = editText;
        }
        return input;
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
                                    }

                                    @Override
                                    public void onNothingSelected(
                                            android.widget.AdapterView<?> parent
                                    ) {
                                        field.selectedOption = null;
                                    }
                                });
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

    private void showLineEditor(List<JsonObject> details, LinearLayout container) {
        String productPath = "productos";
        viewModel.listOptions(productPath, new ApiRepository.ResultCallback<JsonElement>() {
            @Override
            public void onSuccess(JsonElement result) {
                if (!isAdded()) {
                    return;
                }
                boolean sale = "ventas".equals(resource.key)
                        || "detalle-ventas".equals(resource.key);
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
                TextInputEditText quantity = new TextInputEditText(requireContext());
                quantity.setHint("Cantidad");
                quantity.setInputType(InputType.TYPE_CLASS_NUMBER);
                quantity.setText("1");
                TextInputEditText price = new TextInputEditText(requireContext());
                price.setHint(sale ? "Precio unitario" : "Costo unitario");
                price.setInputType(InputType.TYPE_CLASS_NUMBER
                        | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                LinearLayout editor = new LinearLayout(requireContext());
                editor.setPadding(24, 12, 24, 0);
                editor.setOrientation(LinearLayout.VERTICAL);
                editor.addView(productSpinner);
                editor.addView(quantity);
                editor.addView(price);
                final Spinner promotionSpinner;
                if (sale) {
                    promotionSpinner = new Spinner(requireContext());
                    TextView promotionLabel = new TextView(requireContext());
                    promotionLabel.setText("Promoción (opcional)");
                    editor.addView(promotionLabel);
                    editor.addView(promotionSpinner);
                    productSpinner.setOnItemSelectedListener(
                            new android.widget.AdapterView.OnItemSelectedListener() {
                                @Override
                                public void onItemSelected(
                                        android.widget.AdapterView<?> parent,
                                        View view,
                                        int position,
                                        long id
                                ) {
                                    JsonObject product = products.get(position).record;
                                    if (product != null && product.has("precio")) {
                                        price.setText(product.get("precio").getAsString());
                                    }
                                    if (product != null) {
                                        loadActivePromotions(promotionSpinner,
                                                products.get(position).id);
                                    }
                                }

                                @Override
                                public void onNothingSelected(
                                        android.widget.AdapterView<?> parent
                                ) { }
                            });
                } else {
                    promotionSpinner = null;
                }
                AlertDialog dialog = new AlertDialog.Builder(requireContext())
                        .setTitle(R.string.resource_add_line)
                        .setView(editor)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok, null)
                        .create();
                dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setOnClickListener(button -> {
                            String quantityValue = textOf(quantity);
                            String priceValue = textOf(price);
                            try {
                                int count = Integer.parseInt(quantityValue);
                                BigDecimal amount = new BigDecimal(priceValue);
                                if (count < 1 || amount.signum() < 0) {
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
                                JsonObject detail = new JsonObject();
                                detail.addProperty("productoId",
                                        selectedProduct.id);
                                detail.addProperty("cantidad", count);
                                detail.addProperty(sale ? "precioUnitario" : "costoUnitario",
                                        amount);
                                if (promotionSpinner != null
                                        && promotionSpinner.getSelectedItem() instanceof Option) {
                                    Option promotion = (Option) promotionSpinner.getSelectedItem();
                                    if (promotion.id > 0) {
                                        detail.addProperty("promocionId", promotion.id);
                                    }
                                }
                                details.add(detail);
                                renderDetailRows(container, details);
                                dialog.dismiss();
                            } catch (NumberFormatException error) {
                                showError("Ingresa cantidad y precio válidos.");
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

    private void renderDetailRows(LinearLayout container, List<JsonObject> details) {
        container.removeAllViews();
        for (int i = 0; i < details.size(); i++) {
            final int index = i;
            JsonObject detail = details.get(i);
            TextView line = new TextView(requireContext());
            line.setText("Producto #" + detail.get("productoId").getAsString()
                    + " · " + detail.get("cantidad").getAsString() + " · "
                    + detail.get(detail.has("precioUnitario")
                    ? "precioUnitario" : "costoUnitario").getAsString());
            line.setPadding(4, 8, 4, 0);
            container.addView(line);
            MaterialButton remove = new MaterialButton(requireContext());
            remove.setText(R.string.resource_remove_line);
            remove.setAllCaps(false);
            remove.setOnClickListener(ignored -> {
                details.remove(index);
                renderDetailRows(container, details);
            });
            container.addView(remove);
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
                addDecimal(fields, "peso", "Peso", true, "0");
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
                addDate(fields, "fechaFin", "Fecha de fin", true);
                addBoolean(fields, "activa", "Activa", true);
                addMultiRelation(fields, "productoIds", "Productos", "productos");
                break;
            case "abastecimientos":
                addDateTime(fields, "fecha", "Fecha", true);
                addRelation(fields, "proveedorId", "Proveedor", "proveedores");
                break;
            case "detalle-ventas":
                addRelation(fields, "productoId", "Producto", "productos");
                addNumber(fields, "cantidad", "Cantidad", true, "1");
                addDecimal(fields, "precioUnitario", "Precio unitario", true, "0");
                addRelationOptional(fields, "promocionId", "Promoción", "promociones");
                break;
            case "detalle-abastecimientos":
                addRelation(fields, "productoId", "Producto", "productos");
                addNumber(fields, "cantidad", "Cantidad", true, "1");
                addDecimal(fields, "costoUnitario", "Costo unitario", true, "0");
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
        if (customerField != null && customerField.selectedOption != null
                && customerField.selectedOption.record != null
                && customerField.selectedOption.record.has("puntos")) {
            int availablePoints = customerField.selectedOption.record
                    .get("puntos").getAsInt();
            if (points > availablePoints) {
                throw new IllegalArgumentException(
                        "El cliente no cuenta con tantos puntos disponibles.");
            }
        }
        if ("EFECTIVO".equals(request.get("metodoPago").getAsString())) {
            JsonElement received = request.get("efectivoRecibido");
            if (received == null || received.isJsonNull()
                    || received.getAsBigDecimal().signum() <= 0) {
                throw new IllegalArgumentException(
                        "Ingresa el efectivo recibido para pagos en efectivo.");
            }
        }
        if (details.isEmpty()) {
            throw new IllegalArgumentException("Agrega al menos un producto a la venta.");
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

    private void addRelationOptional(
            List<FieldSpec> fields,
            String key,
            String label,
            String path
    ) {
        fields.add(new FieldSpec(key, label, INPUT_RELATION, false, "", null, path));
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
        Option selectedOption;

        FieldInput(FieldSpec spec) {
            this.spec = spec;
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
