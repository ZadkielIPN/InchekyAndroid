package com.example.inchekyandroid.data.model.request;

import java.math.BigDecimal;
import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class VentaRequest {
    private String fecha;
    private Long cajeroId;
    private Long clienteId;
    private List<VentaDetalleRequest> detalles;
    private Integer puntosUtilizados;
    private String metodoPago;
    private BigDecimal efectivoRecibido;
    private BigDecimal cambio;
}
