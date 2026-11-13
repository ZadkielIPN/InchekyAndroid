package com.example.inchekyandroid.data.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

public final class SaleEstimate {
    public static final String PREVIEW_PRODUCT_KEY = "_previewProducto";

    public final BigDecimal subtotal;
    public final BigDecimal promotionDiscount;
    public final BigDecimal subtotalAfterPromotions;
    public final BigDecimal loyaltyDiscount;
    public final BigDecimal pointsDiscount;
    public final BigDecimal total;
    public final int availablePoints;
    public final int maxPoints;
    public final int pointsUsed;
    public final int earnedPoints;
    public final String loyaltyLevel;

    private SaleEstimate(
            BigDecimal subtotal,
            BigDecimal promotionDiscount,
            BigDecimal subtotalAfterPromotions,
            BigDecimal loyaltyDiscount,
            BigDecimal pointsDiscount,
            BigDecimal total,
            int availablePoints,
            int maxPoints,
            int pointsUsed,
            int earnedPoints,
            String loyaltyLevel
    ) {
        this.subtotal = subtotal;
        this.promotionDiscount = promotionDiscount;
        this.subtotalAfterPromotions = subtotalAfterPromotions;
        this.loyaltyDiscount = loyaltyDiscount;
        this.pointsDiscount = pointsDiscount;
        this.total = total;
        this.availablePoints = availablePoints;
        this.maxPoints = maxPoints;
        this.pointsUsed = pointsUsed;
        this.earnedPoints = earnedPoints;
        this.loyaltyLevel = loyaltyLevel;
    }

    public static SaleEstimate calculate(
            JsonArray details,
            JsonObject customer,
            List<JsonObject> promotions,
            LocalDate today,
            int requestedPoints
    ) {
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal promotionDiscount = BigDecimal.ZERO;
        if (details != null) {
            for (JsonElement element : details) {
                if (element == null || !element.isJsonObject()) {
                    continue;
                }
                JsonObject detail = element.getAsJsonObject();
                BigDecimal unitPrice = decimal(detail.get("precioUnitario"));
                BigDecimal quantity = decimal(detail.get("cantidad"));
                BigDecimal lineSubtotal = unitPrice.multiply(quantity);
                subtotal = subtotal.add(lineSubtotal);
                BigDecimal discountPercent = activePromotionPercent(
                        previewProduct(detail), promotions, today);
                promotionDiscount = promotionDiscount.add(lineSubtotal
                        .multiply(discountPercent)
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
            }
        }

        BigDecimal afterPromotions = subtotal.subtract(promotionDiscount)
                .max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        String loyaltyLevel = value(customer, "nivelLealtad", "BRONCE");
        BigDecimal loyaltyPercent = loyaltyPercent(loyaltyLevel);
        BigDecimal loyaltyDiscount = afterPromotions.multiply(loyaltyPercent)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal beforePoints = afterPromotions.subtract(loyaltyDiscount)
                .max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

        Integer customerPoints = SaleFormData.availablePoints(customer);
        int availablePoints = Math.max(customerPoints == null ? 0 : customerPoints, 0);
        int pointsAffordable = beforePoints
                .divide(new BigDecimal("0.50"), 0, RoundingMode.FLOOR)
                .intValue();
        int maxPoints = Math.min(availablePoints, pointsAffordable);
        int pointsUsed = Math.min(Math.max(requestedPoints, 0), maxPoints);
        BigDecimal pointsDiscount = BigDecimal.valueOf(pointsUsed)
                .multiply(new BigDecimal("0.50"))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = beforePoints.subtract(pointsDiscount)
                .max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        int earnedPoints = total.setScale(0, RoundingMode.FLOOR)
                .divideToIntegralValue(BigDecimal.TEN)
                .intValue();

        return new SaleEstimate(
                subtotal.setScale(2, RoundingMode.HALF_UP),
                promotionDiscount.setScale(2, RoundingMode.HALF_UP),
                afterPromotions,
                loyaltyDiscount,
                pointsDiscount,
                total,
                availablePoints,
                maxPoints,
                pointsUsed,
                earnedPoints,
                loyaltyLevel
        );
    }

    private static BigDecimal activePromotionPercent(
            JsonObject product,
            List<JsonObject> promotions,
            LocalDate today
    ) {
        if (product == null || promotions == null || today == null) {
            return BigDecimal.ZERO;
        }
        JsonArray productPromotionIds = array(product.get("promocionIds"));
        BigDecimal bestPercent = BigDecimal.ZERO;
        for (JsonObject promotion : promotions) {
            if (promotion == null
                    || !booleanValue(promotion.get("activa"))
                    || !hasMatchingId(productPromotionIds, promotion.get("id"))
                    || !isActiveOn(promotion, today)) {
                continue;
            }
            bestPercent = bestPercent.max(decimal(promotion.get("porcentajeDescuento")));
        }
        return bestPercent;
    }

    private static boolean isActiveOn(JsonObject promotion, LocalDate today) {
        try {
            LocalDate start = LocalDate.parse(value(promotion, "fechaInicio", ""));
            if (start.isAfter(today)) {
                return false;
            }
            String endValue = value(promotion, "fechaFin", "");
            return endValue.isEmpty() || !LocalDate.parse(endValue).isBefore(today);
        } catch (RuntimeException error) {
            return false;
        }
    }

    private static JsonObject previewProduct(JsonObject detail) {
        JsonElement preview = detail.get(PREVIEW_PRODUCT_KEY);
        if (preview != null && preview.isJsonObject()) {
            return preview.getAsJsonObject();
        }
        JsonElement product = detail.get("producto");
        return product != null && product.isJsonObject() ? product.getAsJsonObject() : null;
    }

    private static boolean hasMatchingId(JsonArray ids, JsonElement target) {
        if (ids == null || target == null || target.isJsonNull()) {
            return false;
        }
        String targetId = target.getAsString();
        for (JsonElement item : ids) {
            JsonElement id = item != null && item.isJsonObject()
                    ? item.getAsJsonObject().get("id") : item;
            if (id != null && !id.isJsonNull() && targetId.equals(id.getAsString())) {
                return true;
            }
        }
        return false;
    }

    private static JsonArray array(JsonElement element) {
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }

    private static BigDecimal loyaltyPercent(String level) {
        switch (level) {
            case "PLATA":
                return BigDecimal.valueOf(5);
            case "ORO":
                return BigDecimal.TEN;
            default:
                return BigDecimal.ZERO;
        }
    }

    private static BigDecimal decimal(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return BigDecimal.ZERO;
        }
        try {
            return element.getAsBigDecimal();
        } catch (NumberFormatException error) {
            return BigDecimal.ZERO;
        }
    }

    private static boolean booleanValue(JsonElement element) {
        return element != null && element.isJsonPrimitive()
                && element.getAsJsonPrimitive().isBoolean() && element.getAsBoolean();
    }

    private static String value(JsonObject object, String key, String fallback) {
        if (object == null) {
            return fallback;
        }
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? fallback : element.getAsString();
    }
}
