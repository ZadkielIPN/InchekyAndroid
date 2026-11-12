package com.example.inchekyandroid.data.model.request;

import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class AbastecimientoRequest {
    private String fecha;
    private Long proveedorId;
    private List<AbastecimientoDetalleRequest> detalles;
}
