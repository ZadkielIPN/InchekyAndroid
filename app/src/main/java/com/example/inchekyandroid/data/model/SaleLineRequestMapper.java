package com.example.inchekyandroid.data.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.math.BigDecimal;

public final class SaleLineRequestMapper {
    public JsonObject fromProduct(JsonObject product, int quantity) {
        if (product == null || !product.has("id") || product.get("id").isJsonNull()) {
            throw new IllegalArgumentException("El producto seleccionado no tiene un identificador válido.");
        }
        if (quantity < 1) {
            throw new IllegalArgumentException("La cantidad debe ser mayor a cero.");
        }
        JsonElement productPrice = product.get("precio");
        if (productPrice == null || productPrice.isJsonNull()) {
            throw new IllegalArgumentException("El producto seleccionado no tiene un precio válido.");
        }
        BigDecimal price;
        try {
            price = productPrice.getAsBigDecimal();
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(
                    "El producto seleccionado no tiene un precio válido.", error);
        }
        if (price.signum() < 0) {
            throw new IllegalArgumentException("El producto seleccionado no tiene un precio válido.");
        }

        JsonObject detail = new JsonObject();
        detail.addProperty("productoId", product.get("id").getAsLong());
        detail.addProperty("cantidad", quantity);
        detail.addProperty("precioUnitario", price);
        return detail;
    }
}
