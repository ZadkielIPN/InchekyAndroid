package com.example.inchekyandroid.data.model.request;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ClienteUpdateRequest {
    private String nombre;
    private String apellido;
    private String email;
    private String contrasena;
    private Integer puntos;
    private String nivelLealtad;
}
