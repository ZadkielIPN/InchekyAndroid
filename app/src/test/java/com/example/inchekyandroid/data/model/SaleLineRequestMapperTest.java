package com.example.inchekyandroid.data.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import com.google.gson.JsonObject;

import org.junit.Test;

import java.math.BigDecimal;

public class SaleLineRequestMapperTest {
    @Test
    public void mapsProductToSaleLineRequest() {
        JsonObject product = new JsonObject();
        product.addProperty("id", 7);
        product.addProperty("precio", "2.50");

        JsonObject detail = new SaleLineRequestMapper().fromProduct(product, 3);

        assertEquals(7, detail.get("productoId").getAsLong());
        assertEquals(3, detail.get("cantidad").getAsInt());
        assertEquals(new BigDecimal("2.50"), detail.get("precioUnitario").getAsBigDecimal());
    }

    @Test
    public void rejectsZeroQuantity() {
        JsonObject product = new JsonObject();
        product.addProperty("id", 7);
        product.addProperty("precio", 2.50);

        assertThrows(IllegalArgumentException.class,
                () -> new SaleLineRequestMapper().fromProduct(product, 0));
    }
}
