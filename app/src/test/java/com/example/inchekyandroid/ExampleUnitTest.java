package com.example.inchekyandroid;

import com.example.inchekyandroid.data.model.LoginRequest;
import com.example.inchekyandroid.data.model.RegisterRequest;
import com.example.inchekyandroid.data.model.UsuarioProfileRequest;
import com.example.inchekyandroid.data.model.ApiRequestMapper;
import com.example.inchekyandroid.ui.home.ApiResource;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class ExampleUnitTest {
    private final Gson gson = new Gson();

    @Test
    public void loginRequestUsesSpringBootFieldNames() {
        LoginRequest request = new LoginRequest("ana@example.com", "password123");

        JsonObject json = JsonParser.parseString(gson.toJson(request)).getAsJsonObject();

        assertEquals("ana@example.com", json.get("email").getAsString());
        assertEquals("password123", json.get("contrasena").getAsString());
        assertFalse(json.has("password"));
    }

    @Test
    public void registerRequestIncludesInitialLoyaltyValues() {
        RegisterRequest request = new RegisterRequest(
                "Ana", "Pérez", "ana@example.com", "password123", 0, "BRONCE");

        JsonObject json = JsonParser.parseString(gson.toJson(request)).getAsJsonObject();

        assertEquals("Ana", json.get("nombre").getAsString());
        assertEquals("Pérez", json.get("apellido").getAsString());
        assertEquals("ana@example.com", json.get("email").getAsString());
        assertEquals("password123", json.get("contrasena").getAsString());
        assertEquals(0, json.get("puntos").getAsInt());
        assertEquals("BRONCE", json.get("nivelLealtad").getAsString());
    }

    @Test
    public void profileRequestMatchesBackendNames() {
        UsuarioProfileRequest request =
                new UsuarioProfileRequest("Ana", "Pérez", "ana@example.com", "");

        JsonObject json = JsonParser.parseString(gson.toJson(request)).getAsJsonObject();

        assertEquals("Ana", json.get("nombre").getAsString());
        assertEquals("Pérez", json.get("apellido").getAsString());
        assertEquals("ana@example.com", json.get("email").getAsString());
        assertEquals("", json.get("contrasena").getAsString());
    }

    @Test
    public void cashierCannotEditOrDeleteSalesOrPromotions() {
        List<ApiResource> resources = ApiResource.forRole("CAJERO");

        ApiResource sales = find(resources, "ventas");
        ApiResource promotions = find(resources, "promociones");
        assertNotNull(sales);
        assertTrue(sales.canCreate);
        assertFalse(sales.canUpdate);
        assertFalse(sales.canDelete);
        assertNotNull(promotions);
        assertFalse(promotions.canCreate);
        assertFalse(promotions.canUpdate);
        assertFalse(promotions.canDelete);
        assertNull(find(resources, "administradores"));
    }

    @Test
    public void customerOnlySeesCatalogAndOwnPurchases() {
        List<ApiResource> resources = ApiResource.forRole("CLIENTE");

        assertNotNull(find(resources, "catalogo"));
        assertNotNull(find(resources, "compras"));
        assertNull(find(resources, "ventas"));
        assertNull(find(resources, "clientes"));
    }

    @Test
    public void adminResourcesDoNotExposeDisabledMutations() {
        List<ApiResource> resources = ApiResource.forRole("ADMINISTRADOR");

        ApiResource customers = find(resources, "clientes");
        ApiResource sales = find(resources, "ventas");
        assertNotNull(customers);
        assertFalse(customers.canCreate);
        assertTrue(customers.canUpdate);
        assertTrue(customers.canDelete);
        assertNotNull(sales);
        assertTrue(sales.canCreate);
        assertFalse(sales.canUpdate);
        assertFalse(sales.canDelete);
    }

    @Test
    public void saleRequestUsesBackendNamesAndTypedLineItems() {
        JsonObject input = JsonParser.parseString("{"
                + "\"fecha\":\"2026-11-12T10:30:00\","
                + "\"cajeroId\":7,\"clienteId\":12,\"puntosUtilizados\":0,"
                + "\"metodoPago\":\"EFECTIVO\",\"efectivoRecibido\":250,"
                + "\"detalles\":[{\"productoId\":3,\"cantidad\":2,"
                + "\"precioUnitario\":25.50,\"promocionId\":4}]}")
                .getAsJsonObject();

        JsonObject mapped = new ApiRequestMapper().toTypedJson("ventas", input);

        assertEquals(7, mapped.get("cajeroId").getAsInt());
        assertEquals(12, mapped.get("clienteId").getAsInt());
        assertEquals("EFECTIVO", mapped.get("metodoPago").getAsString());
        assertEquals(1, mapped.getAsJsonArray("detalles").size());
        JsonObject line = mapped.getAsJsonArray("detalles").get(0).getAsJsonObject();
        assertEquals(3, line.get("productoId").getAsInt());
        assertEquals(4, line.get("promocionId").getAsInt());
        assertFalse(mapped.has("id"));
    }

    @Test
    public void productRequestPreservesBackendFieldsAndNullableExpiry() {
        JsonObject input = JsonParser.parseString("{"
                + "\"nombre\":\"Café\",\"descripcion\":\"Molido\","
                + "\"precio\":99.5,\"stock\":4,\"peso\":0.25,"
                + "\"valoracion\":4.5,\"activo\":true,\"codigoBarras\":\"123\","
                + "\"unidadMedida\":\"PIEZA\",\"fechaCaducidad\":null,"
                + "\"promocionIds\":[2]}")
                .getAsJsonObject();

        JsonObject mapped = new ApiRequestMapper().toTypedJson("productos", input);

        assertEquals("Café", mapped.get("nombre").getAsString());
        assertEquals("PIEZA", mapped.get("unidadMedida").getAsString());
        assertEquals(1, mapped.getAsJsonArray("promocionIds").size());
        assertFalse(mapped.has("fechaCaducidad"));
    }

    @Test
    public void customerCatalogHasProductDetailsButNoPurchaseMutation() {
        ApiResource catalog = find(ApiResource.forRole("CLIENTE"), "catalogo");
        ApiResource purchases = find(ApiResource.forRole("CLIENTE"), "compras");

        assertNotNull(catalog);
        assertTrue(catalog.canDetail);
        assertNotNull(purchases);
        assertFalse(purchases.canDetail);
        assertFalse(purchases.canCreate);
        assertFalse(purchases.canUpdate);
        assertFalse(purchases.canDelete);
    }

    private ApiResource find(List<ApiResource> resources, String key) {
        for (ApiResource resource : resources) {
            if (resource.key.equals(key)) {
                return resource;
            }
        }
        return null;
    }
}