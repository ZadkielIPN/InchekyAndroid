package com.example.inchekyandroid.data.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioProfileRequest {
    private String nombre;
    private String apellido;
    private String email;
    private String contrasena;
}
