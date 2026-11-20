package com.example.inchekyandroid;

import com.example.inchekyandroid.data.model.LoginRequest;
import com.example.inchekyandroid.data.model.RegisterRequest;
import com.example.inchekyandroid.data.model.FormDateValidator;
import com.example.inchekyandroid.data.model.SaleLineRequestMapper;
import com.example.inchekyandroid.data.model.SaleFormData;
import com.example.inchekyandroid.data.model.SaleEstimate;
import com.example.inchekyandroid.data.model.UsuarioProfileRequest;
import com.example.inchekyandroid.data.model.ApiRequestMapper;
import com.example.inchekyandroid.data.remote.ProductImageUrl;
import com.example.inchekyandroid.ui.home.ApiResource;
import com.example.inchekyandroid.ui.home.PageInfo;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;

import java.util.List;
import java.util.Arrays;
import java.math.BigDecimal;
import java.time.LocalDate;

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
        assertNull(find(resources, "detalle-ventas"));
        assertNull(find(resources, "detalle-abastecimientos"));
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
    public void customerStartsAtCatalogWhileStaffStartsAtDashboard() {
        assertEquals("catalogo", ApiResource.initialResourceForRole("CLIENTE"));
        assertNull(ApiResource.initialResourceForRole("CAJERO"));
        assertNull(ApiResource.initialResourceForRole("ADMINISTRADOR"));
    }

    @Test
    public void productImageFilenameResolvesFromApiOrigin() {
        assertEquals(
                "http://10.0.2.2:8080/uploads/productos/cafe%20molido.jpg",
                ProductImageUrl.fromApiBase(
                        "http://10.0.2.2:8080/api/",
                        "cafe molido.jpg"
                )
        );
    }

    @Test
    public void absoluteProductImageUrlIsPreservedAndMissingImageIsEmpty() {
        assertEquals(
                "https://cdn.example.com/producto.jpg",
                ProductImageUrl.fromApiBase(
                        "http://10.0.2.2:8080/api/",
                        "https://cdn.example.com/producto.jpg"
                )
        );
        assertNull(ProductImageUrl.fromApiBase("http://10.0.2.2:8080/api/", " "));
    }

    @Test
    public void pageInfoUsesSpringPageNumberAndBoundaries() {
        JsonObject response = JsonParser.parseString(
                "{\"content\":[],\"number\":1,\"totalPages\":3}")
                .getAsJsonObject();

        PageInfo info = PageInfo.from(response, 1);

        assertEquals(1, info.number);
        assertEquals(3, info.totalPages);
        assertTrue(info.hasPrevious());
        assertTrue(info.hasNext());
        assertFalse(info.requiresReload);
    }

    @Test
    public void pageInfoClampsOutOfRangeRequestToLastPage() {
        JsonObject response = JsonParser.parseString(
                "{\"content\":[],\"number\":3,\"totalPages\":3}")
                .getAsJsonObject();

        PageInfo info = PageInfo.from(response, 3);

        assertEquals(2, info.number);
        assertEquals(3, info.totalPages);
        assertTrue(info.hasPrevious());
        assertFalse(info.hasNext());
        assertTrue(info.requiresReload);
    }

    @Test
    public void emptyPageResponseRecoversToFirstPage() {
        JsonObject response = JsonParser.parseString(
                "{\"content\":[],\"number\":2,\"totalPages\":0}")
                .getAsJsonObject();

        PageInfo info = PageInfo.from(response, 2);

        assertEquals(0, info.number);
        assertEquals(1, info.totalPages);
        assertTrue(info.requiresReload);
    }

    @Test
    public void adminResourcesExposeCustomerMutations() {
        List<ApiResource> resources = ApiResource.forRole("ADMINISTRADOR");

        ApiResource customers = find(resources, "clientes");
        ApiResource sales = find(resources, "ventas");
        assertNotNull(customers);
        assertTrue(customers.canCreate);
        assertTrue(customers.canUpdate);
        assertTrue(customers.canDelete);
        assertNotNull(sales);
        assertTrue(sales.canCreate);
        assertFalse(sales.canUpdate);
        assertFalse(sales.canDelete);
        assertNotNull(find(resources, "abastecimientos"));
        assertNull(find(resources, "detalle-ventas"));
        assertNull(find(resources, "detalle-abastecimientos"));
    }

    @Test
    public void cashierResourcesAreReadOnlyExceptTicketCreation() {
        List<ApiResource> resources = ApiResource.forRole("CAJERO");

        for (String key : new String[]{"productos", "proveedores", "clientes", "promociones"}) {
            ApiResource resource = find(resources, key);
            assertNotNull(resource);
            assertFalse(resource.canCreate);
            assertFalse(resource.canUpdate);
            assertFalse(resource.canDelete);
        }

        ApiResource sales = find(resources, "ventas");
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
    public void saleLineUsesSelectedProductPriceWithoutPromotionOrEditablePrice() {
        JsonObject product = JsonParser.parseString(
                "{\"id\":21,\"nombre\":\"Café\",\"precio\":49.90}")
                .getAsJsonObject();

        JsonObject line = new SaleLineRequestMapper().fromProduct(product, 2);

        assertEquals(21, line.get("productoId").getAsInt());
        assertEquals(2, line.get("cantidad").getAsInt());
        assertEquals(49.90, line.get("precioUnitario").getAsDouble(), 0.001);
        assertEquals(3, line.size());
        assertFalse(line.has("promocionId"));
    }

    @Test
    public void cashReceivedIsRemovedWhenPaymentIsNotCash() {
        JsonObject request = JsonParser.parseString(
                "{\"metodoPago\":\"TARJETA\",\"efectivoRecibido\":500}")
                .getAsJsonObject();

        SaleFormData.removeCashReceivedForNonCash(request);

        assertFalse(request.has("efectivoRecibido"));
    }

    @Test
    public void cashPaymentKeepsReceivedAmountAndRequiresIt() {
        JsonObject request = JsonParser.parseString(
                "{\"metodoPago\":\"EFECTIVO\",\"efectivoRecibido\":500}")
                .getAsJsonObject();

        SaleFormData.removeCashReceivedForNonCash(request);

        assertTrue(SaleFormData.requiresCashReceived(
                request.get("metodoPago").getAsString()));
        assertEquals(500, request.get("efectivoRecibido").getAsInt());
    }

    @Test
    public void availablePointsComeFromSelectedCustomerAndCanBeUnknown() {
        JsonObject customer = JsonParser.parseString("{\"id\":9,\"puntos\":125}")
                .getAsJsonObject();
        JsonObject customerWithoutPoints = JsonParser.parseString("{\"id\":10}")
                .getAsJsonObject();

        assertEquals(Integer.valueOf(125), SaleFormData.availablePoints(customer));
        assertNull(SaleFormData.availablePoints(customerWithoutPoints));
        assertNull(SaleFormData.availablePoints(null));
    }

    @Test
    public void saleEstimateMatchesPromotionLoyaltyAndPointRules() {
        JsonArray details = JsonParser.parseString("[{"
                + "\"productoId\":3,\"cantidad\":2,\"precioUnitario\":50,"
                + "\"_previewProducto\":{\"promocionIds\":[8]}}]")
                .getAsJsonArray();
        JsonObject customer = JsonParser.parseString(
                "{\"puntos\":200,\"nivelLealtad\":\"PLATA\"}").getAsJsonObject();
        List<JsonObject> promotions = Arrays.asList(JsonParser.parseString("{"
                + "\"id\":8,\"activa\":true,\"porcentajeDescuento\":20,"
                + "\"fechaInicio\":\"2026-01-01\",\"fechaFin\":\"2026-12-31\"}")
                .getAsJsonObject());

        SaleEstimate estimate = SaleEstimate.calculate(
                details, customer, promotions, LocalDate.of(2026, 5, 1), 30);

        assertEquals(new BigDecimal("100.00"), estimate.subtotal);
        assertEquals(new BigDecimal("20.00"), estimate.promotionDiscount);
        assertEquals(new BigDecimal("4.00"), estimate.loyaltyDiscount);
        assertEquals(152, estimate.maxPoints);
        assertEquals(30, estimate.pointsUsed);
        assertEquals(new BigDecimal("15.00"), estimate.pointsDiscount);
        assertEquals(new BigDecimal("61.00"), estimate.total);
        assertEquals(6, estimate.earnedPoints);
    }

    @Test
    public void saleEstimateCapsPointsByRemainingTotalAndIgnoresInactivePromotion() {
        JsonArray details = JsonParser.parseString("[{"
                + "\"productoId\":3,\"cantidad\":1,\"precioUnitario\":5,"
                + "\"_previewProducto\":{\"promocionIds\":[8]}}]")
                .getAsJsonArray();
        JsonObject customer = JsonParser.parseString(
                "{\"puntos\":100,\"nivelLealtad\":\"BRONCE\"}").getAsJsonObject();
        List<JsonObject> promotions = Arrays.asList(JsonParser.parseString("{"
                + "\"id\":8,\"activa\":false,\"porcentajeDescuento\":100,"
                + "\"fechaInicio\":\"2026-01-01\"}").getAsJsonObject());

        SaleEstimate estimate = SaleEstimate.calculate(
                details, customer, promotions, LocalDate.of(2026, 5, 1), 100);

        assertEquals(10, estimate.maxPoints);
        assertEquals(10, estimate.pointsUsed);
        assertEquals(new BigDecimal("0.00"), estimate.total);
        assertEquals(0, estimate.earnedPoints);
    }

    @Test
    public void cashReceivedMustCoverEstimatedTotalButMayBeZeroForFreeSale() {
        assertFalse(SaleFormData.isCashReceivedSufficient(
                new BigDecimal("9.99"), new BigDecimal("10.00")));
        assertTrue(SaleFormData.isCashReceivedSufficient(
                new BigDecimal("10.00"), new BigDecimal("10.00")));
        assertTrue(SaleFormData.isCashReceivedSufficient(
                BigDecimal.ZERO, BigDecimal.ZERO));
        assertFalse(SaleFormData.isCashReceivedSufficient(null, BigDecimal.ZERO));
    }

    @Test
    public void updateFormOmitsBlankPasswordAndTypedSaleDropsPreviewMetadata() {
        JsonObject userRequest = JsonParser.parseString(
                "{\"nombre\":\"Ana\",\"contrasena\":\"  \"}").getAsJsonObject();
        ApiRequestMapper.omitBlankPasswordForUpdate(userRequest, true);
        assertFalse(userRequest.has("contrasena"));

        JsonObject sale = JsonParser.parseString("{"
                + "\"puntosUtilizados\":0,\"metodoPago\":\"TARJETA\","
                + "\"detalles\":[{\"productoId\":3,\"cantidad\":1,"
                + "\"precioUnitario\":5,\"_previewProducto\":{\"promocionIds\":[8]}}]}")
                .getAsJsonObject();
        JsonObject mapped = new ApiRequestMapper().toTypedJson("ventas", sale);
        JsonObject line = mapped.getAsJsonArray("detalles").get(0).getAsJsonObject();
        assertFalse(line.has(SaleEstimate.PREVIEW_PRODUCT_KEY));
        assertEquals(3, line.get("productoId").getAsInt());
    }

    @Test
    public void customerNameAndEmailAreNotValidatedAsDates() {
        FormDateValidator.validateIfDateField(false, false, "nombre", "Ana");
        FormDateValidator.validateIfDateField(false, false, "email", "ana@example.com");
    }

    @Test
    public void dateFieldsStillRejectMalformedValues() {
        try {
            FormDateValidator.validateIfDateField(true, false, "fechaInicio", "no-es-fecha");
            fail("Expected malformed date to be rejected.");
        } catch (IllegalArgumentException error) {
            assertEquals("La fecha no tiene un formato válido.", error.getMessage());
        }
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