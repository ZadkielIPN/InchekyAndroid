package com.example.inchekyandroid.data.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioResponse {
    private long id;
    private String nombre;
    private String apellido;
    private String email;
    private String rol;
}
