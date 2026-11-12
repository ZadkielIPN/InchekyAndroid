package com.example.inchekyandroid.data.model.request;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ProveedorRequest {
    private String nombre;
    private String telefono;
    private String email;
    private String direccion;
    private boolean activo;
}
