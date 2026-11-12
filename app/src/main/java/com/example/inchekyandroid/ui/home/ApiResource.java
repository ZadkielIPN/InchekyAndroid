package com.example.inchekyandroid.ui.home;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class ApiResource {
    public final String key;
    public final String title;
    public final String path;
    public final boolean searchable;
    public final boolean canDetail;
    public final boolean canCreate;
    public final boolean canUpdate;
    public final boolean canDelete;

    private ApiResource(
            String key,
            String title,
            String path,
            boolean searchable,
            boolean canCreate,
            boolean canUpdate,
            boolean canDelete
    ) {
        this.key = key;
        this.title = title;
        this.path = path;
        this.searchable = searchable;
        this.canDetail = !"catalogo".equals(key) && !"compras".equals(key);
        this.canCreate = canCreate;
        this.canUpdate = canUpdate;
        this.canDelete = canDelete;
    }

    public static List<ApiResource> forRole(String role) {
        if ("CLIENTE".equals(role)) {
            return Arrays.asList(
                    resource("catalogo", "Catálogo de productos", "productos/catalogo", true, false, false, false),
                    resource("compras", "Mis compras", "ventas/mis-compras", false, false, false, false)
            );
        }
        if ("CAJERO".equals(role)) {
            return Arrays.asList(
                    resource("catalogo", "Catálogo de productos", "productos/catalogo", true, false, false, false),
                    resource("productos", "Productos", "productos", true, false, false, false),
                    resource("clientes", "Clientes", "clientes", true, false, false, false),
                    resource("proveedores", "Proveedores", "proveedores", true, false, false, false),
                    resource("abastecimientos", "Abastecimientos", "abastecimientos", false, false, false, false),
                    resource("promociones", "Promociones", "promociones", true, false, false, false),
                    resource("detalle-ventas", "Detalles de ventas", "detalle-ventas",
                            false, false, false, false),
                    resource("detalle-abastecimientos", "Detalles de abastecimientos",
                            "detalle-abastecimientos", false, false, false, false),
                    resource("ventas", "Ventas", "ventas", false, true, false, false)
            );
        }
        if ("ADMINISTRADOR".equals(role)) {
            return Arrays.asList(
                    resource("clientes", "Clientes", "clientes", true, false, true, true),
                    resource("productos", "Productos", "productos", true, true, true, true),
                    resource("proveedores", "Proveedores", "proveedores", true, true, true, true),
                    resource("abastecimientos", "Abastecimientos", "abastecimientos", false, true, true, true),
                    resource("promociones", "Promociones", "promociones", true, true, true, true),
                    resource("cajeros", "Cajeros", "cajeros", true, true, true, true),
                    resource("administradores", "Administradores", "administradores", true, true, true, true),
                    resource("usuarios", "Usuarios", "usuarios", true, false, false, true),
                    resource("detalle-ventas", "Detalles de ventas", "detalle-ventas", false, true, true, true),
                    resource("detalle-abastecimientos", "Detalles de abastecimientos",
                            "detalle-abastecimientos", false, true, true, true),
                    resource("ventas", "Ventas", "ventas", false, true, false, false)
            );
        }
        return Collections.emptyList();
    }

    private static ApiResource resource(
            String key,
            String title,
            String path,
            boolean searchable,
            boolean canCreate,
            boolean canUpdate,
            boolean canDelete
    ) {
        return new ApiResource(key, title, path, searchable, canCreate, canUpdate, canDelete);
    }
}
