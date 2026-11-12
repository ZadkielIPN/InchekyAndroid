package com.example.inchekyandroid;

import com.example.inchekyandroid.data.model.LoginRequest;
import com.example.inchekyandroid.data.model.RegisterRequest;
import com.example.inchekyandroid.data.model.UsuarioProfileRequest;
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

    private ApiResource find(List<ApiResource> resources, String key) {
        for (ApiResource resource : resources) {
            if (resource.key.equals(key)) {
                return resource;
            }
        }
        return null;
    }
}