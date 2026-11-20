package com.example.inchekyandroid.data.model;

import static org.junit.Assert.assertEquals;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;

public class SaleEstimateTest {
    @Test
    public void appliesPromotionLoyaltyAndPointsInOrder() {
        JsonObject product = new JsonObject();
        product.addProperty("id", 7);
        JsonArray promotionIds = new JsonArray();
        promotionIds.add(3);
        product.add("promocionIds", promotionIds);

        JsonObject detail = new SaleLineRequestMapper().fromProduct(productWithPrice(product), 2);
        detail.add(SaleEstimate.PREVIEW_PRODUCT_KEY, product);
        JsonArray details = new JsonArray();
        details.add(detail);

        JsonObject customer = new JsonObject();
        customer.addProperty("nivelLealtad", "ORO");
        customer.addProperty("puntos", 10);
        JsonObject promotion = new JsonObject();
        promotion.addProperty("id", 3);
        promotion.addProperty("activa", true);
        promotion.addProperty("porcentajeDescuento", 10);
        promotion.addProperty("fechaInicio", "2025-01-01");
        promotion.addProperty("fechaFin", "2025-12-31");

        SaleEstimate estimate = SaleEstimate.calculate(
                details, customer, Collections.singletonList(promotion),
                LocalDate.of(2025, 6, 1), 10);

        assertEquals(new BigDecimal("200.00"), estimate.subtotal);
        assertEquals(new BigDecimal("20.00"), estimate.promotionDiscount);
        assertEquals(new BigDecimal("18.00"), estimate.loyaltyDiscount);
        assertEquals(new BigDecimal("5.00"), estimate.pointsDiscount);
        assertEquals(new BigDecimal("157.00"), estimate.total);
        assertEquals(10, estimate.pointsUsed);
    }

    private JsonObject productWithPrice(JsonObject product) {
        product.addProperty("precio", "100.00");
        return product;
    }
}
