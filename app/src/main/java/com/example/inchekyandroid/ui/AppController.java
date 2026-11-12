package com.example.inchekyandroid.ui;

import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.inchekyandroid.MainActivity;
import com.example.inchekyandroid.R;
import com.example.inchekyandroid.data.model.UsuarioSession;
import com.example.inchekyandroid.data.remote.RetrofitClient;
import com.example.inchekyandroid.data.repository.SessionStore;
import com.example.inchekyandroid.databinding.ActivityMainBinding;
import com.example.inchekyandroid.databinding.ActivityMainBinding;
import com.example.inchekyandroid.ui.home.AppViewModel;
import com.example.inchekyandroid.ui.home.RemoteResult;
import com.example.inchekyandroid.ui.login.LoginViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AppController {
    private final MainActivity activity;
    private ActivityMainBinding binding;
    private LoginViewModel loginViewModel;
    private AppViewModel appViewModel;
    private UsuarioSession currentUser;
    private String screen = "login";
    private String endpoint = "";
    private String title = "";
    private String query = "";
    private int page = 0;
    private long recordId;
    private boolean openFormAfterDetail;
    private boolean loginObserversBound;
    private JsonObject profileRecord;
    private LinearLayout pageContent;
    private final Map<String, EditText> textFields = new LinkedHashMap<>();
    private final Map<String, CheckBox> booleanFields = new LinkedHashMap<>();
    private final Map<String, String> labels = new LinkedHashMap<>();

    public AppController(MainActivity activity) {
        this.activity = activity;
    }

    public void initialize() {
        RetrofitClient.initialize(activity.getApplicationContext());
        appViewModel = new ViewModelProvider(activity).get(AppViewModel.class);
        appViewModel.getResult().observe(activity, this::onRemoteResult);
        loginViewModel = new ViewModelProvider(activity).get(LoginViewModel.class);

        binding = ActivityMainBinding.inflate(activity.getLayoutInflater());
        activity.setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        binding.btnLogin.setOnClickListener(view -> submitLogin());
        binding.btnRegister.setOnClickListener(view -> showRegistration());
        observeLoginState();

        currentUser = SessionStore.load(activity);
        if (currentUser != null) {
            appViewModel.profile();
        }
    }

    private void submitLogin() {
        binding.tilEmail.setError(null);
        binding.tilPassword.setError(null);
        binding.tvFeedback.setVisibility(View.GONE);
        loginViewModel.login(
                binding.etEmail.getText() == null ? "" : binding.etEmail.getText().toString(),
                binding.etPassword.getText() == null ? "" : binding.etPassword.getText().toString()
        );
    }

    private void observeLoginState() {
        if (loginObserversBound) return;
        loginObserversBound = true;
        loginViewModel.getLoading().observe(activity, loading -> {
            binding.btnLogin.setEnabled(!loading);
            binding.btnLogin.setText(loading ? R.string.login_loading : R.string.login_button);
        });
        loginViewModel.getError().observe(activity, error -> {
            if (error == null || error.isEmpty()) return;
            binding.tvFeedback.setText(error);
            binding.tvFeedback.setTextColor(activity.getColor(R.color.login_error));
            binding.tvFeedback.setVisibility(View.VISIBLE);
        });
        loginViewModel.getUser().observe(activity, user -> {
            if (user == null) return;
            currentUser = user;
            SessionStore.save(activity, user);
            showHome();
        });
    }

    private void showRegistration() {
        screen = "register";
        LinearLayout form = createPage("Crear cuenta", true);
        EditText firstName = addInput(form, "Nombre", null, InputType.TYPE_CLASS_TEXT);
        EditText lastName = addInput(form, "Apellido", null, InputType.TYPE_CLASS_TEXT);
        EditText email = addInput(form, "Correo electrónico", null,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText password = addInput(form, "Contraseña (8 a 20 caracteres)", null,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText confirmation = addInput(form, "Confirmar contraseña", null,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        addButton(form, "Crear cuenta", view -> {
            String pass = password.getText().toString();
            if (!pass.equals(confirmation.getText().toString())) {
                showMessage("Las contraseñas no coinciden.");
                return;
            }
            if (pass.length() < 8 || pass.length() > 20) {
                showMessage("La contraseña debe tener entre 8 y 20 caracteres.");
                return;
            }
            JsonObject body = new JsonObject();
            body.addProperty("nombre", firstName.getText().toString().trim());
            body.addProperty("apellido", lastName.getText().toString().trim());
            body.addProperty("email", email.getText().toString().trim());
            body.addProperty("contrasena", pass);
            body.addProperty("puntos", 0);
            body.addProperty("nivelLealtad", "BRONCE");
            appViewModel.register(body);
        });
        addButton(form, "Volver al inicio de sesión", view -> showLogin());
    }

    private void showLogin() {
        screen = "login";
        binding = ActivityMainBinding.inflate(activity.getLayoutInflater());
        activity.setContentView(binding.getRoot());
        binding.btnLogin.setOnClickListener(view -> submitLogin());
        binding.btnRegister.setOnClickListener(view -> showRegistration());
        observeLoginState();
    }

    private void showHome() {
        screen = "home";
        endpoint = "";
        LinearLayout content = createPage(
                currentUser == null ? "Incheky" : "Hola, " + currentUser.getNombre(), false);
        addText(content, "Panel de " + (currentUser == null ? "" : roleName(currentUser.getRol())),
                16, R.color.login_secondary_text);

        if (hasRole("CLIENTE")) {
            addMenu(content, "Catálogo de productos", "productos/catalogo", "productos");
            addMenu(content, "Mi perfil", "profile", "perfil");
            addMenu(content, "Mis compras", "ventas/mis-compras", "compras");
        } else {
            addMenu(content, "Dashboard", "dashboard", "dashboard");
            addMenu(content, "Productos", "productos", "productos");
            addMenu(content, "Proveedores", "proveedores", "proveedores");
            addMenu(content, "Clientes", "clientes", "clientes");
            if (hasRole("ADMINISTRADOR")) {
                addMenu(content, "Usuarios", "usuarios", "usuarios");
                addMenu(content, "Cajeros", "cajeros", "cajeros");
                addMenu(content, "Administradores", "administradores", "administradores");
            }
            addMenu(content, "Promociones", "promociones", "promociones");
            addMenu(content, "Ventas", "ventas", "ventas");
            addMenu(content, "Registrar venta", "sale", "ventas");
            if (hasRole("ADMINISTRADOR")) {
                addMenu(content, "Abastecimientos", "abastecimientos", "abastecimientos");
                addMenu(content, "Registrar abastecimiento", "supply", "abastecimientos");
            }
        }
        addButton(content, "Cerrar sesión", view -> appViewModel.logout());
    }

    private void addMenu(LinearLayout content, String label, String action, String route) {
        addButton(content, label, view -> {
            if ("profile".equals(action)) {
                screen = "profile";
                appViewModel.profile();
            } else if ("compras".equals(action)) {
                screen = "compras";
                endpoint = "ventas/mis-compras";
                title = "Mis compras";
                page = 0;
                loadList();
            } else if ("sale".equals(action) || "supply".equals(action)) {
                showTransactionForm(action);
            } else if ("dashboard".equals(action)) {
                screen = "dashboard";
                showDashboardData();
            } else {
                screen = "list";
                endpoint = action;
                title = label;
                page = 0;
                query = "";
                loadList();
            }
        });
    }

    private void showDashboardData() {
        LinearLayout content = createPage("Dashboard", true);
        addText(content, "Resumen de gestión", 18, R.color.login_text);
        for (String resource : Arrays.asList("productos", "ventas", "clientes")) {
            addButton(content, "Ver " + resource, view -> {
                endpoint = resource;
                title = capitalize(resource);
                screen = "list";
                page = 0;
                loadList();
            });
        }
    }

    private void loadList() {
        appViewModel.list(endpoint, page, query);
        renderLoading("Cargando " + title.toLowerCase(Locale.ROOT) + "...");
    }

    private void renderList(JsonElement response) {
        JsonArray rows = response != null && response.isJsonObject()
                && response.getAsJsonObject().has("content")
                && response.getAsJsonObject().get("content").isJsonArray()
                ? response.getAsJsonObject().getAsJsonArray("content")
                : response != null && response.isJsonArray() ? response.getAsJsonArray() : new JsonArray();
        LinearLayout content = createPage(title, true);
        EditText search = addInput(content, "Buscar", query, InputType.TYPE_CLASS_TEXT);
        addButton(content, "Buscar", view -> {
            query = search.getText().toString().trim();
            page = 0;
            loadList();
        });
        if (canCreate(endpoint)) addButton(content, "＋ Nuevo", view -> showForm(endpoint, null));
        if (rows.size() == 0) addText(content, "No hay registros para mostrar.", 16, R.color.login_secondary_text);

        List<String> columns = columnsFor(endpoint);
        for (JsonElement element : rows) {
            if (!element.isJsonObject()) continue;
            JsonObject item = element.getAsJsonObject();
            long id = number(item, "id", -1);
            StringBuilder summary = new StringBuilder();
            for (String column : columns) {
                JsonElement value = item.get(column);
                if ((value == null || value.isJsonNull()) && column.endsWith("Nombre")) {
                    value = item.get(column.substring(0, column.length() - 6) + "Id");
                }
                if (value != null && !value.isJsonNull()) {
                    if (summary.length() > 0) summary.append('\n');
                    summary.append(displayLabel(column)).append(": ").append(value.isJsonPrimitive()
                            ? value.getAsString() : value.toString());
                }
            }
            MaterialCardView card = new MaterialCardView(activity);
            card.setRadius(dp(12));
            card.setCardElevation(dp(2));
            card.setUseCompatPadding(true);
            LinearLayout inside = new LinearLayout(activity);
            inside.setOrientation(LinearLayout.VERTICAL);
            inside.setPadding(dp(14), dp(12), dp(14), dp(8));
            TextView value = new TextView(activity);
            value.setText(summary.length() == 0 ? item.toString() : summary.toString());
            value.setTextSize(15);
            inside.addView(value);
            LinearLayout actions = new LinearLayout(activity);
            actions.setGravity(Gravity.END);
            addSmallButton(actions, "Ver", v -> openDetail(id));
            if (canEdit(endpoint)) addSmallButton(actions, "Editar", v -> {
                recordId = id;
                openFormAfterDetail = true;
                appViewModel.detail(endpoint, id);
                renderLoading("Cargando registro...");
            });
            if (canDelete(endpoint)) addSmallButton(actions, "Eliminar", v -> confirmDelete(id));
            inside.addView(actions);
            card.addView(inside);
            content.addView(card);
        }
        if (response != null && response.isJsonObject()) {
            JsonObject pageData = response.getAsJsonObject();
            int totalPages = pageData.has("totalPages") ? pageData.get("totalPages").getAsInt() : 1;
            LinearLayout pager = new LinearLayout(activity);
            pager.setGravity(Gravity.CENTER);
            Button previous = new Button(activity);
            previous.setText("Anterior");
            previous.setEnabled(page > 0);
            previous.setOnClickListener(v -> {
                page--;
                loadList();
            });
            Button next = new Button(activity);
            next.setText("Siguiente");
            next.setEnabled(page + 1 < totalPages);
            next.setOnClickListener(v -> {
                page++;
                loadList();
            });
            pager.addView(previous);
            pager.addView(next);
            content.addView(pager);
        }
    }

    private void openDetail(long id) {
        recordId = id;
        screen = "detail";
        openFormAfterDetail = false;
        appViewModel.detail(endpoint, id);
        renderLoading("Cargando detalle...");
    }

    private void renderDetail(JsonObject record) {
        LinearLayout content = createPage(title + " #" + recordId, true);
        for (Map.Entry<String, JsonElement> field : record.entrySet()) {
            JsonElement value = field.getValue();
            if (value == null || value.isJsonNull()) continue;
            addText(content, displayLabel(field.getKey()) + ": " + value, 15, R.color.login_text);
        }
        if ("ventas".equals(endpoint) || ("compras".equals(screen))) {
            addButton(content, "Descargar ticket PDF", view ->
                    showMessage("La descarga de tickets requiere habilitar almacenamiento compartido."));
        }
        if (canEdit(endpoint)) addButton(content, "Editar", view -> {
            openFormAfterDetail = true;
            showForm(endpoint, record);
        });
    }

    private void showForm(String entity, JsonObject existing) {
        endpoint = entity;
        screen = "form";
        recordId = existing == null ? 0 : number(existing, "id", 0);
        String formTitle = recordId == 0 ? "Nuevo " + singular(entity) : "Editar " + singular(entity);
        LinearLayout content = createPage(formTitle, true);
        textFields.clear();
        booleanFields.clear();
        labels.clear();
        for (FieldSpec field : fieldsFor(entity)) {
            if (field.booleanField) {
                CheckBox checkBox = new CheckBox(activity);
                checkBox.setText(field.label);
                checkBox.setChecked(existing == null ? field.defaultBoolean
                        : existing.has(field.key) && existing.get(field.key).getAsBoolean());
                content.addView(checkBox);
                booleanFields.put(field.key, checkBox);
            } else {
                String initial = existing != null && existing.has(field.key) && !existing.get(field.key).isJsonNull()
                        ? existing.get(field.key).getAsString() : field.defaultValue;
                int inputType = field.number
                        ? InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                        : InputType.TYPE_CLASS_TEXT;
                if ("email".equals(field.key)) inputType |= InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS;
                if ("contrasena".equals(field.key)) inputType |= InputType.TYPE_TEXT_VARIATION_PASSWORD;
                EditText input = addInput(content, field.label, initial, inputType);
                if (field.required && recordId == 0) input.setHint(field.label + " (obligatorio)");
                textFields.put(field.key, input);
                labels.put(field.key, field.label);
            }
        }
        if ("promociones".equals(entity)) {
            addText(content, "En productoIds, escribe los IDs separados por coma.", 13, R.color.login_secondary_text);
        }
        addButton(content, "Guardar", view -> saveForm(entity));
    }

    private void saveForm(String entity) {
        JsonObject payload = new JsonObject();
        for (FieldSpec spec : fieldsFor(entity)) {
            if (spec.booleanField) {
                CheckBox check = booleanFields.get(spec.key);
                payload.addProperty(spec.key, check != null && check.isChecked());
                continue;
            }
            EditText input = textFields.get(spec.key);
            if (input == null) continue;
            String value = input.getText().toString().trim();
            if (spec.required && value.isEmpty() && !"contrasena".equals(spec.key)) {
                input.setError("Campo obligatorio");
                return;
            }
            if ("contrasena".equals(spec.key) && value.isEmpty() && recordId > 0) continue;
            if (value.isEmpty() && ("fechaFin".equals(spec.key) || "fechaCaducidad".equals(spec.key))) {
                payload.add(spec.key, com.google.gson.JsonNull.INSTANCE);
            } else if ("productoIds".equals(spec.key)) {
                JsonArray ids = new JsonArray();
                for (String part : value.split(",")) {
                    try {
                        if (!part.trim().isEmpty()) ids.add(Long.parseLong(part.trim()));
                    } catch (NumberFormatException exception) {
                        input.setError("Usa IDs numéricos separados por coma");
                        return;
                    }
                }
                if (ids.size() == 0) {
                    input.setError("Selecciona al menos un producto");
                    return;
                }
                payload.add(spec.key, ids);
            } else if (spec.number) {
                try {
                    payload.addProperty(spec.key, Double.parseDouble(value));
                } catch (NumberFormatException exception) {
                    input.setError("Ingresa un número válido");
                    return;
                }
            } else {
                payload.addProperty(spec.key, value);
            }
        }
        if ("promociones".equals(entity)) {
            double discount = payload.has("porcentajeDescuento")
                    ? payload.get("porcentajeDescuento").getAsDouble() : -1;
            if (discount < 0 || discount > 100) {
                showMessage("El descuento debe estar entre 0% y 100%.");
                return;
            }
            String start = payload.has("fechaInicio") ? payload.get("fechaInicio").getAsString() : "";
            String end = payload.has("fechaFin") && !payload.get("fechaFin").isJsonNull()
                    ? payload.get("fechaFin").getAsString() : "";
            if (start.isEmpty() || (!end.isEmpty() && end.compareTo(start) < 0)) {
                showMessage("Revisa las fechas de inicio y fin.");
                return;
            }
        }
        appViewModel.save(entity, recordId, payload);
        renderLoading("Guardando...");
    }

    private void showTransactionForm(String transaction) {
        screen = transaction;
        endpoint = "sale".equals(transaction) ? "ventas" : "abastecimientos";
        title = "sale".equals(transaction) ? "Registrar venta" : "Registrar abastecimiento";
        LinearLayout content = createPage(title, true);
        textFields.clear();
        labels.clear();
        List<FieldSpec> fields = "sale".equals(transaction)
                ? Arrays.asList(new FieldSpec("clienteId", "ID del cliente", true, true),
                    new FieldSpec("productoId", "ID del producto", true, true),
                    new FieldSpec("cantidad", "Cantidad", true, true),
                    new FieldSpec("precioUnitario", "Precio unitario", true, true),
                    new FieldSpec("puntosUtilizados", "Puntos a utilizar", false, true),
                    new FieldSpec("efectivoRecibido", "Efectivo recibido", false, true))
                : Arrays.asList(new FieldSpec("proveedorId", "ID del proveedor", true, true),
                    new FieldSpec("productoId", "ID del producto", true, true),
                    new FieldSpec("cantidad", "Cantidad", true, true),
                    new FieldSpec("costoUnitario", "Costo unitario", true, true));
        if ("sale".equals(transaction) && hasRole("ADMINISTRADOR")) {
            fields = new ArrayList<>(fields);
            fields.add(0, new FieldSpec("cajeroId", "ID del cajero", true, true));
        }
        addText(content, "Captura los identificadores y datos del ticket conforme al catálogo del sistema.", 14,
                R.color.login_secondary_text);
        for (FieldSpec field : fields) {
            EditText input = addInput(content, field.label, null,
                    InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            textFields.put(field.key, input);
        }
        addButton(content, "Confirmar", view -> saveTransaction(transaction));
    }

    private void saveTransaction(String transaction) {
        JsonObject payload = new JsonObject();
        String now = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(new Date());
        payload.addProperty("fecha", now);
        JsonObject detail = new JsonObject();
        for (Map.Entry<String, EditText> field : textFields.entrySet()) {
            String value = field.getValue().getText().toString().trim();
            if (value.isEmpty() && "efectivoRecibido".equals(field.getKey())) {
                payload.add(field.getKey(), com.google.gson.JsonNull.INSTANCE);
                continue;
            }
            if (value.isEmpty() && "puntosUtilizados".equals(field.getKey())) value = "0";
            if (value.isEmpty()) {
                field.getValue().setError("Campo obligatorio");
                return;
            }
            try {
                double numeric = Double.parseDouble(value);
                if ("productoId".equals(field.getKey()) || "cantidad".equals(field.getKey())
                        || "clienteId".equals(field.getKey()) || "cajeroId".equals(field.getKey())
                        || "proveedorId".equals(field.getKey()) || "puntosUtilizados".equals(field.getKey())) {
                    if ("productoId".equals(field.getKey()) || "cantidad".equals(field.getKey())) {
                        detail.addProperty(field.getKey(), (long) numeric);
                    } else {
                        payload.addProperty(field.getKey(), (long) numeric);
                    }
                } else if ("precioUnitario".equals(field.getKey()) || "costoUnitario".equals(field.getKey())) {
                    detail.addProperty(field.getKey(), numeric);
                } else {
                    payload.addProperty(field.getKey(), numeric);
                }
            } catch (NumberFormatException exception) {
                field.getValue().setError("Ingresa un número válido");
                return;
            }
        }
        if ("sale".equals(transaction)) {
            if (hasRole("CAJERO")) payload.addProperty("cajeroId", currentUser.getId());
            payload.addProperty("metodoPago", "EFECTIVO");
            JsonArray details = new JsonArray();
            details.add(detail);
            payload.add("detalles", details);
        } else {
            JsonArray details = new JsonArray();
            details.add(detail);
            payload.add("detalles", details);
        }
        appViewModel.save(endpoint, 0, payload);
        renderLoading("Enviando...");
    }

    private void onRemoteResult(RemoteResult result) {
        if (result == null) return;
        if (result.error != null) {
            if ("profile".equals(result.operation) && result.statusCode == 401) {
                SessionStore.clear(activity);
                RetrofitClient.clearSession();
                currentUser = null;
                showLogin();
                return;
            }
            if ("logout".equals(result.operation)) {
                SessionStore.clear(activity);
                currentUser = null;
                showLogin();
                return;
            }
            if (screen.equals("list")) {
                renderError(result.error);
            } else {
                showMessage(result.error);
            }
            return;
        }
        switch (result.operation) {
            case "register":
                UsuarioSession registered = parseUser(result.data);
                if (registered != null) {
                    currentUser = registered;
                    SessionStore.save(activity, registered);
                    showHome();
                } else {
                    showMessage("Registro completado, pero no se recibió la sesión.");
                }
                break;
            case "profile":
                if (result.data != null && result.data.isJsonObject()) {
                    profileRecord = result.data.getAsJsonObject();
                    if (currentUser == null) currentUser = parseUser(result.data);
                    if (currentUser != null) SessionStore.save(activity, currentUser);
                    if ("profile".equals(screen)) renderProfile();
                    else showHome();
                } else {
                    SessionStore.clear(activity);
                    RetrofitClient.clearSession();
                    currentUser = null;
                    showLogin();
                }
                break;
            case "updateProfile":
                profileRecord = result.data == null ? profileRecord : result.data.getAsJsonObject();
                showMessage("Tu perfil se actualizó correctamente.");
                renderProfile();
                break;
            case "list":
                if ("compras".equals(screen)) renderPurchaseHistory(result.data);
                else renderList(result.data);
                break;
            case "detail":
                if (result.data != null && result.data.isJsonObject()) {
                    JsonObject record = result.data.getAsJsonObject();
                    if (openFormAfterDetail) {
                        openFormAfterDetail = false;
                        showForm(endpoint, record);
                    } else {
                        renderDetail(record);
                    }
                }
                break;
            case "save":
                showMessage("Cambios guardados correctamente.");
                if ("sale".equals(screen) || "supply".equals(screen)) {
                    showHome();
                } else {
                    screen = "list";
                    page = 0;
                    loadList();
                }
                break;
            case "delete":
                showMessage("Registro eliminado.");
                loadList();
                break;
            case "logout":
                SessionStore.clear(activity);
                currentUser = null;
                showLogin();
                break;
            default:
                break;
        }
    }

    private void renderProfile() {
        JsonObject profile = profileRecord == null ? new JsonObject() : profileRecord;
        LinearLayout content = createPage("Mi perfil", true);
        textFields.clear();
        EditText name = addInput(content, "Nombre", string(profile, "nombre"), InputType.TYPE_CLASS_TEXT);
        EditText surname = addInput(content, "Apellido", string(profile, "apellido"), InputType.TYPE_CLASS_TEXT);
        EditText email = addInput(content, "Correo", string(profile, "email"),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText password = addInput(content, "Nueva contraseña (opcional)", null,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        addText(content, "Puntos: " + string(profile, "puntos"), 15, R.color.login_secondary_text);
        addText(content, "Nivel de lealtad: " + string(profile, "nivelLealtad"), 15, R.color.login_secondary_text);
        addButton(content, "Guardar perfil", view -> {
            JsonObject body = new JsonObject();
            body.addProperty("nombre", name.getText().toString().trim());
            body.addProperty("apellido", surname.getText().toString().trim());
            body.addProperty("email", email.getText().toString().trim());
            if (!password.getText().toString().isEmpty()) body.addProperty("contrasena", password.getText().toString());
            appViewModel.updateProfile(body);
        });
    }

    private void renderPurchaseHistory(JsonElement response) {
        JsonArray rows = response != null && response.isJsonObject() && response.getAsJsonObject().has("content")
                ? response.getAsJsonObject().getAsJsonArray("content")
                : response != null && response.isJsonArray() ? response.getAsJsonArray() : new JsonArray();
        LinearLayout content = createPage("Mis compras", true);
        if (rows.size() == 0) addText(content, "Aún no tienes compras.", 16, R.color.login_secondary_text);
        for (JsonElement entry : rows) {
            if (!entry.isJsonObject()) continue;
            JsonObject sale = entry.getAsJsonObject();
            addButton(content, "Compra #" + string(sale, "id") + " · $" + string(sale, "total")
                    + " · " + string(sale, "fecha"), view -> {
                endpoint = "ventas";
                title = "Compra";
                screen = "detail";
                recordId = number(sale, "id", 0);
                renderDetail(sale);
            });
        }
    }

    private void confirmDelete(long id) {
        new androidx.appcompat.app.AlertDialog.Builder(activity)
                .setMessage("¿Deseas eliminar este registro?")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Eliminar", (dialog, which) -> appViewModel.delete(endpoint, id))
                .show();
    }

    private LinearLayout createPage(String heading, boolean showBack) {
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(activity.getColor(R.color.login_background));
        root.setPadding(dp(16), dp(12), dp(16), dp(20));
        LinearLayout toolbar = new LinearLayout(activity);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        if (showBack) {
            Button back = new Button(activity);
            back.setText("‹");
            back.setOnClickListener(view -> activity.onBackPressed());
            toolbar.addView(back, new LinearLayout.LayoutParams(dp(54), dp(48)));
        }
        TextView headingView = new TextView(activity);
        headingView.setText(heading);
        headingView.setTextSize(22);
        headingView.setTextColor(activity.getColor(R.color.login_primary));
        headingView.setTypeface(null, android.graphics.Typeface.BOLD);
        toolbar.addView(headingView, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(toolbar);
        ScrollView scroll = new ScrollView(activity);
        pageContent = new LinearLayout(activity);
        pageContent.setOrientation(LinearLayout.VERTICAL);
        pageContent.setPadding(0, dp(8), 0, dp(24));
        scroll.addView(pageContent);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        activity.setContentView(root);
        return pageContent;
    }

    private void renderLoading(String message) {
        LinearLayout content = createPage(title.isEmpty() ? "Incheky" : title, true);
        addText(content, message, 16, R.color.login_secondary_text);
    }

    private void renderError(String error) {
        LinearLayout content = createPage(title, true);
        addText(content, error, 16, R.color.login_error);
        addButton(content, "Reintentar", view -> loadList());
    }

    private EditText addInput(LinearLayout parent, String label, String value, int inputType) {
        TextView caption = new TextView(activity);
        caption.setText(label);
        caption.setTextColor(activity.getColor(R.color.login_text));
        caption.setTextSize(14);
        parent.addView(caption);
        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setInputType(inputType);
        input.setText(value == null ? "" : value);
        input.setPadding(dp(12), dp(8), dp(12), dp(8));
        parent.addView(input, new LinearLayout.LayoutParams(-1, dp(54)));
        return input;
    }

    private void addText(LinearLayout parent, String value, int size, int color) {
        TextView text = new TextView(activity);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(activity.getColor(color));
        text.setPadding(0, dp(8), 0, dp(8));
        parent.addView(text);
    }

    private void addButton(LinearLayout parent, String text, View.OnClickListener listener) {
        MaterialButton button = new MaterialButton(activity);
        button.setText(text);
        button.setAllCaps(false);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(48));
        params.topMargin = dp(6);
        params.bottomMargin = dp(4);
        parent.addView(button, params);
    }

    private void addSmallButton(LinearLayout parent, String text, View.OnClickListener listener) {
        Button button = new Button(activity);
        button.setText(text);
        button.setTextSize(12);
        button.setOnClickListener(listener);
        parent.addView(button);
    }

    private void showMessage(String message) {
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private boolean hasRole(String role) {
        return currentUser != null && role.equals(currentUser.getRol());
    }

    private boolean canCreate(String entity) {
        if ("productos/catalogo".equals(entity)) return false;
        if ("ventas".equals(entity)) return hasRole("ADMINISTRADOR") || hasRole("CAJERO");
        if ("abastecimientos".equals(entity)) return hasRole("ADMINISTRADOR");
        return hasRole("ADMINISTRADOR") && !"usuarios".equals(entity);
    }

    private boolean canEdit(String entity) {
        return hasRole("ADMINISTRADOR") && !"ventas".equals(entity)
                && !"abastecimientos".equals(entity) && !"usuarios".equals(entity)
                && !"productos/catalogo".equals(entity);
    }

    private boolean canDelete(String entity) {
        return hasRole("ADMINISTRADOR") && !"ventas".equals(entity)
                && !"usuarios".equals(entity) && !"productos/catalogo".equals(entity);
    }

    private List<String> columnsFor(String entity) {
        switch (entity) {
            case "productos":
            case "productos/catalogo": return Arrays.asList("id", "nombre", "precio", "stock", "activo");
            case "proveedores": return Arrays.asList("id", "nombre", "telefono", "email", "activo");
            case "clientes": return Arrays.asList("id", "nombre", "apellido", "email", "puntos", "nivelLealtad");
            case "usuarios": return Arrays.asList("id", "nombre", "apellido", "email", "rol");
            case "cajeros": return Arrays.asList("id", "nombre", "apellido", "email", "numeroEmpleado", "turno", "cajaAsignada");
            case "administradores": return Arrays.asList("id", "nombre", "apellido", "email", "numeroAdministrador");
            case "promociones": return Arrays.asList("id", "nombre", "porcentajeDescuento", "fechaInicio", "fechaFin", "activa");
            case "ventas": return Arrays.asList("id", "fecha", "cajeroNombre", "clienteNombre", "total", "descuento", "puntosGanados", "metodoPago");
            case "abastecimientos": return Arrays.asList("id", "fecha", "proveedorNombre", "total");
            default: return Arrays.asList("id", "nombre");
        }
    }

    private List<FieldSpec> fieldsFor(String entity) {
        List<FieldSpec> fields = new ArrayList<>();
        switch (entity) {
            case "productos":
                fields.add(new FieldSpec("nombre", "Nombre", true, false));
                fields.add(new FieldSpec("descripcion", "Descripción", true, false));
                fields.add(new FieldSpec("precio", "Precio", true, true));
                fields.add(new FieldSpec("stock", "Stock", true, true));
                fields.add(new FieldSpec("peso", "Peso", false, true));
                fields.add(new FieldSpec("valoracion", "Valoración", false, true));
                fields.add(new FieldSpec("codigoBarras", "Código de barras", true, false));
                fields.add(new FieldSpec("unidadMedida", "Unidad de medida", true, false, "PIEZA"));
                fields.add(new FieldSpec("fechaCaducidad", "Fecha de caducidad (AAAA-MM-DD)", false, false));
                fields.add(new FieldSpec("activo", "Activo", false, false, true));
                break;
            case "proveedores":
                fields.add(new FieldSpec("nombre", "Nombre", true, false));
                fields.add(new FieldSpec("telefono", "Teléfono", false, false));
                fields.add(new FieldSpec("email", "Correo", false, false));
                fields.add(new FieldSpec("direccion", "Dirección", false, false));
                fields.add(new FieldSpec("activo", "Activo", false, false, true));
                break;
            case "clientes":
                userFields(fields, true, true);
                fields.add(new FieldSpec("puntos", "Puntos", false, true, "0"));
                fields.add(new FieldSpec("nivelLealtad", "Nivel de lealtad (BRONCE/PLATA/ORO)", false, false, "BRONCE"));
                break;
            case "cajeros":
                userFields(fields, true, true);
                fields.add(new FieldSpec("turno", "Turno (MATUTINO/VESPERTINO/NOCTURNO)", false, false, "MATUTINO"));
                fields.add(new FieldSpec("cajaAsignada", "Caja asignada", true, true, "1"));
                break;
            case "administradores":
                userFields(fields, true, true);
                break;
            case "promociones":
                fields.add(new FieldSpec("nombre", "Nombre", true, false));
                fields.add(new FieldSpec("porcentajeDescuento", "Descuento (0-100)", true, true));
                fields.add(new FieldSpec("fechaInicio", "Fecha de inicio (AAAA-MM-DD)", true, false));
                fields.add(new FieldSpec("fechaFin", "Fecha de fin (AAAA-MM-DD)", false, false));
                fields.add(new FieldSpec("activa", "Activa", false, false, true));
                fields.add(new FieldSpec("productoIds", "IDs de productos", true, false));
                break;
            default: break;
        }
        return fields;
    }

    private void userFields(List<FieldSpec> fields, boolean withSurname, boolean withPassword) {
        fields.add(new FieldSpec("nombre", "Nombre", true, false));
        if (withSurname) fields.add(new FieldSpec("apellido", "Apellido", true, false));
        fields.add(new FieldSpec("email", "Correo", true, false));
        if (withPassword) fields.add(new FieldSpec("contrasena", "Contraseña (8-20)", false, false));
    }

    private String displayLabel(String field) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("cajeroNombre", "Cajero");
        map.put("clienteNombre", "Cliente");
        map.put("proveedorNombre", "Proveedor");
        if (map.containsKey(field)) return map.get(field);
        return field.replaceAll("([A-Z])", " $1").replaceFirst("^.", field.substring(0, 1).toUpperCase(Locale.ROOT));
    }

    private String singular(String entity) {
        switch (entity) {
            case "productos": return "producto";
            case "proveedores": return "proveedor";
            case "clientes": return "cliente";
            case "cajeros": return "cajero";
            case "administradores": return "administrador";
            case "promociones": return "promoción";
            default: return entity;
        }
    }

    private String roleName(String role) {
        if ("ADMINISTRADOR".equals(role)) return "administrador";
        if ("CAJERO".equals(role)) return "cajero";
        return "cliente";
    }

    private String capitalize(String value) {
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
    }

    private String string(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
    }

    private long number(JsonObject object, String key, long fallback) {
        try {
            return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsLong() : fallback;
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private UsuarioSession parseUser(JsonElement value) {
        if (value == null || !value.isJsonObject()) return null;
        try {
            return new com.google.gson.Gson().fromJson(value, UsuarioSession.class);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    @SuppressWarnings("deprecation")
    public boolean handleBackPressed() {
        if ("form".equals(screen) || "detail".equals(screen)) {
            screen = "list";
            loadList();
        } else if ("list".equals(screen) || "profile".equals(screen) || "compras".equals(screen)
                || "dashboard".equals(screen) || "sale".equals(screen) || "supply".equals(screen)
                || "register".equals(screen)) {
            showHome();
        } else {
            return false;
        }
        return true;
    }

    private static final class FieldSpec {
        final String key;
        final String label;
        final boolean required;
        final boolean number;
        final String defaultValue;
        final boolean booleanField;
        final boolean defaultBoolean;

        FieldSpec(String key, String label, boolean required, boolean number) {
            this(key, label, required, number, "");
        }

        FieldSpec(String key, String label, boolean required, boolean number, String defaultValue) {
            this.key = key;
            this.label = label;
            this.required = required;
            this.number = number;
            this.defaultValue = defaultValue;
            this.booleanField = false;
            this.defaultBoolean = false;
        }

        FieldSpec(String key, String label, boolean required, boolean number, boolean defaultBoolean) {
            this.key = key;
            this.label = label;
            this.required = required;
            this.number = number;
            this.defaultValue = "";
            this.booleanField = true;
            this.defaultBoolean = defaultBoolean;
        }
    }
}
