package com.example.inchekyandroid.data.model;

import com.example.inchekyandroid.data.model.request.AbastecimientoRequest;
import com.example.inchekyandroid.data.model.request.AdministradorRequest;
import com.example.inchekyandroid.data.model.request.CajeroRequest;
import com.example.inchekyandroid.data.model.request.ClienteUpdateRequest;
import com.example.inchekyandroid.data.model.request.DetalleAbastecimientoRequest;
import com.example.inchekyandroid.data.model.request.DetalleVentaRequest;
import com.example.inchekyandroid.data.model.request.ProductoRequest;
import com.example.inchekyandroid.data.model.request.PromocionRequest;
import com.example.inchekyandroid.data.model.request.ProveedorRequest;
import com.example.inchekyandroid.data.model.request.UsuarioRequest;
import com.example.inchekyandroid.data.model.request.VentaRequest;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

public final class ApiRequestMapper {
    private final Gson gson;

    public ApiRequestMapper() {
        this(new Gson());
    }

    ApiRequestMapper(Gson gson) {
        this.gson = gson;
    }

    public JsonObject toTypedJson(String resourceKey, JsonObject request) {
        Object typed;
        switch (resourceKey) {
            case "clientes":
                typed = gson.fromJson(request, ClienteUpdateRequest.class);
                break;
            case "productos":
                typed = gson.fromJson(request, ProductoRequest.class);
                break;
            case "proveedores":
                typed = gson.fromJson(request, ProveedorRequest.class);
                break;
            case "promociones":
                typed = gson.fromJson(request, PromocionRequest.class);
                break;
            case "abastecimientos":
                typed = gson.fromJson(request, AbastecimientoRequest.class);
                break;
            case "detalle-ventas":
                typed = gson.fromJson(request, DetalleVentaRequest.class);
                break;
            case "detalle-abastecimientos":
                typed = gson.fromJson(request, DetalleAbastecimientoRequest.class);
                break;
            case "ventas":
                typed = gson.fromJson(request, VentaRequest.class);
                break;
            case "cajeros":
                typed = gson.fromJson(request, CajeroRequest.class);
                break;
            case "administradores":
                typed = gson.fromJson(request, AdministradorRequest.class);
                break;
            case "usuarios":
                typed = gson.fromJson(request, UsuarioRequest.class);
                break;
            default:
                return request;
        }
        return gson.toJsonTree(typed).getAsJsonObject();
    }
}
