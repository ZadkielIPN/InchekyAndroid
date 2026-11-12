package com.example.inchekyandroid.ui.home;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.lifecycle.ViewModelProvider;

import com.example.inchekyandroid.R;
import com.example.inchekyandroid.data.repository.ApiRepository;
import com.example.inchekyandroid.data.repository.SessionRepository;
import com.example.inchekyandroid.databinding.ActivityResourceBinding;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class ResourceActivity extends AppCompatActivity {
    public static final String EXTRA_RESOURCE = "resource";
    private static final int PAGE_SIZE = 20;

    private ActivityResourceBinding binding;
    private ResourceViewModel viewModel;
    private ApiResource resource;
    private int page;
    private int totalPages = 1;
    private File selectedImage;
    private String selectedImageMimeType;
    private ActivityResultLauncher<String> imagePicker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityResourceBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        viewModel = new ViewModelProvider(this).get(ResourceViewModel.class);
        observeRecords();
        binding.btnResourceSearch.setEnabled(false);
        binding.btnResourceCreate.setEnabled(false);
        binding.btnResourceImage.setEnabled(false);
        binding.btnPreviousPage.setEnabled(false);
        binding.btnNextPage.setEnabled(false);
        imagePicker = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                this::copySelectedImage
        );

        binding.btnResourceSearch.setOnClickListener(view -> {
            page = 0;
            loadRecords();
        });
        binding.btnResourceCreate.setOnClickListener(view -> showEditor(null, 0));
        binding.btnResourceImage.setOnClickListener(view -> imagePicker.launch("image/*"));
        binding.btnPreviousPage.setOnClickListener(view -> {
            if (page > 0) {
                page--;
                loadRecords();
            }
        });
        binding.btnNextPage.setOnClickListener(view -> {
            if (page + 1 < totalPages) {
                page++;
                loadRecords();
            }
        });

        String key = getIntent().getStringExtra(EXTRA_RESOURCE);
        new SessionRepository().currentUser(new SessionRepository.SessionCallback() {
            @Override
            public void onSuccess(com.example.inchekyandroid.data.model.UsuarioResponse user) {
                resource = findResource(key, ApiResource.forRole(user.getRol()));
                if (resource == null) {
                    Toast.makeText(ResourceActivity.this, R.string.home_no_features,
                            Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
                configureScreen();
                loadRecords();
            }

            @Override
            public void onError(String message) {
                Toast.makeText(ResourceActivity.this, message, Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }

    private void configureScreen() {
        binding.tvResourceTitle.setText(resource.title);
        binding.btnResourceSearch.setEnabled(resource.searchable);
        binding.searchControls.setVisibility(resource.searchable ? View.VISIBLE : View.GONE);
        binding.btnResourceCreate.setVisibility(resource.canCreate ? View.VISIBLE : View.GONE);
        binding.btnResourceImage.setVisibility("productos".equals(resource.key)
                && resource.canCreate ? View.VISIBLE : View.GONE);
        binding.btnResourceCreate.setEnabled(resource.canCreate);
        binding.btnResourceImage.setEnabled("productos".equals(resource.key)
                && resource.canCreate);
        binding.btnPreviousPage.setEnabled(false);
    }

    private void loadRecords() {
        String query = binding.etResourceQuery.getText() == null
                ? ""
                : binding.etResourceQuery.getText().toString();
        viewModel.load(resource.path, page, PAGE_SIZE,
                resource.searchable ? query : null);
    }

    private void observeRecords() {
        viewModel.getLoading().observe(this, loading -> {
            if (loading) {
                binding.resourceRecords.removeAllViews();
                binding.tvPageNumber.setText(R.string.home_loading);
            }
        });
        viewModel.getError().observe(this, message -> {
            if (message != null && !message.isEmpty()) {
                binding.tvPageNumber.setText("");
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            }
        });
        viewModel.getRecords().observe(this, this::renderPage);
    }

    private void renderPage(JsonElement response) {
        if (response == null || !response.isJsonObject()) {
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
        page = pageResult.has("number")
                ? pageResult.get("number").getAsInt()
                : page;
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

    private void addRecordCard(JsonElement record) {
        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(getColor(R.color.white));
        card.setRadius(getResources().getDimension(R.dimen.auth_card_radius));
        card.setCardElevation(getResources().getDimension(R.dimen.auth_card_elevation));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(16, 12, 16, 12);

        TextView json = new TextView(this);
        json.setText(summary(record));
        json.setTextColor(getColor(R.color.auth_ink));
        json.setTextSize(16);
        content.addView(json);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        long id = record.isJsonObject() && record.getAsJsonObject().has("id")
                ? record.getAsJsonObject().get("id").getAsLong()
                : -1;
        if (id > 0) {
            if (resource.canDetail) {
                addAction(actions, R.string.resource_details, view -> showDetails(id));
            }
            if (resource.canUpdate) {
                addAction(actions, R.string.resource_edit, view -> showEditor(record, id));
            }
            if (resource.canDelete) {
                addAction(actions, R.string.resource_delete, view -> confirmDelete(id));
            }
            if ("ventas".equals(resource.key) || "compras".equals(resource.key)) {
                addAction(actions, R.string.resource_ticket, view -> downloadTicket(id));
                if ("ventas".equals(resource.key)) {
                    addAction(actions, R.string.resource_email_ticket,
                            button -> promptEmailTicket(id));
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

    private void addAction(LinearLayout parent, int label, View.OnClickListener listener) {
        MaterialButton button = new MaterialButton(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setOnClickListener(listener);
        parent.addView(button);
    }

    private void showDetails(long id) {
        viewModel.get(resource.path, id, new ApiRepository.ResultCallback<JsonElement>() {
            @Override
            public void onSuccess(JsonElement result) {
                new AlertDialog.Builder(ResourceActivity.this)
                        .setTitle(resource.title)
                        .setMessage(pretty(result))
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            }

            @Override
            public void onError(String message) {
                Toast.makeText(ResourceActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showEditor(JsonElement existing, long id) {
        EditText input = new EditText(this);
        input.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        input.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setMinLines(10);
        input.setText(existing == null ? requestTemplate() : pretty(existing));
        int padding = getResources().getDimensionPixelSize(R.dimen.auth_card_spacing);
        input.setPadding(padding, padding, padding, padding);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(existing == null ? R.string.resource_create : R.string.resource_edit)
                .setMessage(R.string.resource_request_json)
                .setView(input)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    JsonElement request;
                    try {
                        request = JsonParser.parseString(input.getText().toString());
                    } catch (JsonParseException error) {
                        Toast.makeText(this, R.string.resource_invalid_json, Toast.LENGTH_LONG)
                                .show();
                        return;
                    }
                    if (!request.isJsonObject()) {
                        Toast.makeText(this, R.string.resource_invalid_json, Toast.LENGTH_LONG)
                                .show();
                        return;
                    }
                    dialog.dismiss();
                    submitJson(existing == null, id, request);
                }));
        dialog.show();
    }

    private void submitJson(boolean creating, long id, JsonElement request) {
        ApiRepository.ResultCallback<JsonElement> callback =
                new ApiRepository.ResultCallback<JsonElement>() {
                    @Override
                    public void onSuccess(JsonElement result) {
                        if (selectedImage != null) {
                            selectedImage.delete();
                        }
                        selectedImage = null;
                        selectedImageMimeType = null;
                        binding.btnResourceImage.setText(R.string.resource_choose_image);
                        Toast.makeText(ResourceActivity.this, R.string.profile_saved,
                                Toast.LENGTH_SHORT).show();
                        loadRecords();
                    }

                    @Override
                    public void onError(String message) {
                        Toast.makeText(ResourceActivity.this, message, Toast.LENGTH_LONG).show();
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
        new AlertDialog.Builder(this)
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
                                        Toast.makeText(ResourceActivity.this, message,
                                                Toast.LENGTH_LONG).show();
                                    }
                                }))
                .show();
    }

    private void promptEmailTicket(long id) {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        input.setHint(R.string.resource_email_hint);
        new AlertDialog.Builder(this)
                .setTitle(R.string.resource_email_ticket)
                .setView(input)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) ->
                        viewModel.emailTicket(id, input.getText().toString().trim(),
                                new ApiRepository.ResultCallback<String>() {
                                    @Override
                                    public void onSuccess(String result) {
                                        Toast.makeText(ResourceActivity.this, result,
                                                Toast.LENGTH_LONG).show();
                                    }

                                    @Override
                                    public void onError(String message) {
                                        Toast.makeText(ResourceActivity.this, message,
                                                Toast.LENGTH_LONG).show();
                                    }
                                }))
                .show();
    }

    private void downloadTicket(long id) {
        File destination = new File(getCacheDir(), "ticket_" + id + ".pdf");
        viewModel.downloadTicket(id, destination, new ApiRepository.ResultCallback<File>() {
            @Override
            public void onSuccess(File file) {
                Uri uri = FileProvider.getUriForFile(
                        ResourceActivity.this,
                        getPackageName() + ".files",
                        file
                );
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(uri, "application/pdf");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                try {
                    startActivity(intent);
                } catch (ActivityNotFoundException error) {
                    Toast.makeText(ResourceActivity.this,
                            "Ticket descargado en " + file.getAbsolutePath(),
                            Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onError(String message) {
                Toast.makeText(ResourceActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void copySelectedImage(Uri uri) {
        if (uri == null) {
            return;
        }
        String mimeType = getContentResolver().getType(uri);
        String extension = mimeType == null || !mimeType.startsWith("image/")
                ? ".image"
                : "." + mimeType.substring("image/".length()).replace("jpeg", "jpg");
        File destination = new File(getCacheDir(), "product-" + UUID.randomUUID() + extension);
        File previous = selectedImage;
        selectedImage = null;
        selectedImageMimeType = null;
        if (previous != null) {
            previous.delete();
        }
        new Thread(() -> {
            String errorMessage = null;
            try (InputStream input = getContentResolver().openInputStream(uri);
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
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    destination.delete();
                    return;
                }
                if (resultError != null) {
                    Toast.makeText(this, resultError, Toast.LENGTH_LONG).show();
                    return;
                }
                selectedImage = destination;
                selectedImageMimeType = mimeType;
                binding.btnResourceImage.setText(destination.getName());
            });
        }, "product-image-copy").start();
    }

    private String requestTemplate() {
        String now = LocalDateTime.now().withNano(0).toString();
        switch (resource.key) {
            case "productos":
                return "{\n  \"nombre\": \"\",\n  \"descripcion\": \"\",\n  \"precio\": 0,\n"
                        + "  \"stock\": 0,\n  \"peso\": 0,\n  \"valoracion\": 0,\n"
                        + "  \"activo\": true,\n  \"codigoBarras\": \"\",\n"
                        + "  \"unidadMedida\": \"PIEZA\",\n  \"fechaCaducidad\": null,\n"
                        + "  \"imagen\": null,\n  \"promocionIds\": []\n}";
            case "proveedores":
                return "{\n  \"nombre\": \"\",\n  \"telefono\": \"\",\n"
                        + "  \"email\": \"\",\n  \"direccion\": \"\",\n  \"activo\": true\n}";
            case "promociones":
                return "{\n  \"nombre\": \"\",\n  \"porcentajeDescuento\": 0,\n"
                        + "  \"fechaInicio\": \"" + LocalDate.now() + "\",\n"
                        + "  \"fechaFin\": \"" + LocalDate.now().plusDays(1) + "\",\n"
                        + "  \"activa\": true,\n  \"productoIds\": []\n}";
            case "abastecimientos":
                return "{\n  \"fecha\": \"" + now + "\",\n  \"proveedorId\": 1,\n"
                        + "  \"detalles\": []\n}";
            case "detalle-ventas":
                return "{\n  \"productoId\": 1,\n  \"cantidad\": 1,\n"
                        + "  \"precioUnitario\": 0,\n  \"promocionId\": null\n}";
            case "detalle-abastecimientos":
                return "{\n  \"productoId\": 1,\n  \"cantidad\": 1,\n"
                        + "  \"costoUnitario\": 0\n}";
            case "ventas":
                return "{\n  \"fecha\": \"" + now + "\",\n  \"cajeroId\": 1,\n"
                        + "  \"clienteId\": 1,\n  \"detalles\": [],\n"
                        + "  \"puntosUtilizados\": 0,\n  \"metodoPago\": \"EFECTIVO\",\n"
                        + "  \"efectivoRecibido\": 0,\n  \"cambio\": 0\n}";
            case "cajeros":
                return "{\n  \"nombre\": \"\",\n  \"apellido\": \"\",\n"
                        + "  \"email\": \"\",\n  \"contrasena\": \"\",\n"
                        + "  \"turno\": \"MATUTINO\",\n  \"cajaAsignada\": 1\n}";
            case "administradores":
                return "{\n  \"nombre\": \"\",\n  \"apellido\": \"\",\n"
                        + "  \"email\": \"\",\n  \"contrasena\": \"\"\n}";
            default:
                return "{}";
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
        binding.resourceRecords.removeAllViews();
        TextView empty = new TextView(this);
        empty.setText(message);
        empty.setTextColor(getColor(R.color.auth_muted));
        binding.resourceRecords.addView(empty);
    }

    private String summary(JsonElement record) {
        if (!record.isJsonObject()) {
            return pretty(record);
        }
        JsonObject object = record.getAsJsonObject();
        StringBuilder text = new StringBuilder();
        appendField(text, object, "nombre");
        appendField(text, object, "productoNombre");
        appendField(text, object, "clienteNombre");
        appendField(text, object, "proveedorNombre");
        appendField(text, object, "cajeroNombre");
        appendField(text, object, "email");
        appendField(text, object, "descripcion");
        appendField(text, object, "precio");
        appendField(text, object, "total");
        appendField(text, object, "stock");
        appendField(text, object, "fecha");
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
}
