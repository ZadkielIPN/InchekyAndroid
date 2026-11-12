package com.example.inchekyandroid.data.model.request;

import java.math.BigDecimal;
import java.util.Set;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ProductoRequest {
    private String nombre;
    private String descripcion;
    private BigDecimal precio;
    private Integer stock;
    private BigDecimal peso;
    private BigDecimal valoracion;
    private boolean activo;
    private String codigoBarras;
    private String unidadMedida;
    private String fechaCaducidad;
    private String imagen;
    private Set<Long> promocionIds;
}
