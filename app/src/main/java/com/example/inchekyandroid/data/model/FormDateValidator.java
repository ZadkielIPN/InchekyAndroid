package com.example.inchekyandroid.data.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.DateTimeException;

public final class FormDateValidator {
    private FormDateValidator() {
    }

    public static void validateIfDateField(
            boolean dateField,
            boolean dateTimeField,
            String key,
            String value
    ) {
        if (!dateField && !dateTimeField) {
            return;
        }
        try {
            if (dateTimeField && "fecha".equals(key)) {
                LocalDateTime.parse(value);
            } else {
                LocalDate date = LocalDate.parse(value);
                if (("fechaFin".equals(key) || "fechaCaducidad".equals(key))
                        && !date.isAfter(LocalDate.now())) {
                    throw new IllegalArgumentException(
                            "La fecha de fin/caducidad debe ser futura.");
                }
            }
        } catch (DateTimeException error) {
            throw new IllegalArgumentException("La fecha no tiene un formato válido.");
        }
    }
}
