package com.example.inchekyandroid.data.model.request;

import java.math.BigDecimal;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class AbastecimientoDetalleRequest {
    private Long productoId;
    private Integer cantidad;
    private BigDecimal costoUnitario;
}
