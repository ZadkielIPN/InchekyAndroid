package com.example.inchekyandroid.data.model.request;

import java.math.BigDecimal;
import java.util.Set;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class PromocionRequest {
    private String nombre;
    private BigDecimal porcentajeDescuento;
    private String fechaInicio;
    private String fechaFin;
    private boolean activa;
    private Set<Long> productoIds;
}
