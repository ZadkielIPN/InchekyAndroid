package com.example.inchekyandroid.data.model.request;

import java.math.BigDecimal;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class DetalleVentaRequest {
    private Long productoId;
    private Integer cantidad;
    private BigDecimal precioUnitario;
    private Long promocionId;
}
