package com.example.inchekyandroid.data.model.request;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UsuarioRequest {
    private String nombre;
    private String apellido;
    private String email;
    private String contrasena;
    private String rol;
}
