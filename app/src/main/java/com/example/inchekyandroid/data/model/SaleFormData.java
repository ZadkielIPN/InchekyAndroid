package com.example.inchekyandroid.data.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.math.BigDecimal;

public final class SaleFormData {
    private SaleFormData() {
    }

    public static boolean requiresCashReceived(String paymentMethod) {
        return "EFECTIVO".equals(paymentMethod);
    }

    public static void removeCashReceivedForNonCash(JsonObject request) {
        if (request == null) {
            throw new IllegalArgumentException("La solicitud de venta es obligatoria.");
        }
        JsonElement paymentMethod = request.get("metodoPago");
        if (paymentMethod == null || !paymentMethod.isJsonPrimitive()
                || !requiresCashReceived(paymentMethod.getAsString())) {
            request.remove("efectivoRecibido");
        }
    }

    public static Integer availablePoints(JsonObject customer) {
        if (customer == null) {
            return null;
        }
        JsonElement points = customer.get("puntos");
        if (points == null || points.isJsonNull() || !points.isJsonPrimitive()) {
            return null;
        }
        if (!points.getAsJsonPrimitive().isNumber()) {
            return null;
        }
        try {
            return points.getAsInt();
        } catch (NumberFormatException error) {
            return null;
        }
    }

    public static boolean isCashReceivedSufficient(BigDecimal received, BigDecimal total) {
        return received != null && received.signum() >= 0
                && total != null && received.compareTo(total) >= 0;
    }
}
