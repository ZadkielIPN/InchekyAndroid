# Guía de desarrollo de Incheky Android

Esta guía explica cómo está organizada la app Android, cómo se conecta a Spring Boot y dónde extender cada parte. La aplicación está escrita en Java; la interfaz toma como referencia visual la app Angular (paleta azul, fondo claro y formularios en tarjetas), pero usa componentes nativos de Android.

## Requisitos

- Android Studio y un JDK compatible con la versión de Gradle del proyecto.
- Emulador Android o dispositivo físico con acceso de red al backend.
- Backend Spring Boot disponible en el puerto `8080`.

## Estructura

```text
app/src/main/
├── AndroidManifest.xml
├── res/navigation/nav_graph.xml
├── java/com/example/inchekyandroid/
│   ├── InchekyApplication.java
│   ├── MainActivity.java       (única Activity: NavHost)
│   ├── data/
│   │   ├── model/       Auth, profile, session y ApiRequestMapper
│   │   │   └── request/ DTOs de recursos y operaciones de negocio
│   │   ├── remote/      AuthApi, BackendApi, RetrofitClient, SessionCookieJar
│   │   └── repository/  AuthRepository, SessionRepository, ApiRepository
│   └── ui/
│       ├── login/       LoginFragment, LoginViewModel
│       ├── register/    RegisterFragment, RegisterViewModel
│       └── home/        Dashboard/Profile/Resource Fragments y ViewModels
└── res/
    ├── layout/          Host y vistas de login, registro, dashboard, perfil y recursos
    └── values/          colors.xml, strings.xml, themes.xml
```

### Responsabilidad de cada capa

- **Activity anfitriona:** `MainActivity` contiene el `NavHostFragment`; `nav_graph.xml` define login, registro, dashboard, perfil y recursos. Las pantallas son Fragments y usan View Binding.
- **Fragments y layouts:** `LoginFragment` y `RegisterFragment` manejan autenticación; `DashboardFragment` filtra recursos por rol; `ProfileFragment` edita la cuenta; `ResourceFragment` ofrece listados paginados y formularios nativos.
- **ViewModels (`ui/login`, `ui/register`, `ui/home`):** coordinan login/registro, restauración de sesión, perfil, logout y consulta/CRUD, exponiendo `LiveData` para la vista.
- **Repositorios (`AuthRepository`, `SessionRepository`, `ApiRepository`):** encapsulan Retrofit, cuerpos JSON, paginación, carga multipart, tickets PDF y errores HTTP/de red.
- **API remota (`AuthApi`, `BackendApi`):** declara endpoints de autenticación y operaciones comunes de recursos. `ApiResource` es la lista permitida de recursos y operaciones que se presenta por rol.
- **Cliente HTTP (`RetrofitClient`):** configura Retrofit, Gson y OkHttp. Se inicializa una vez desde `InchekyApplication`.
- **Cookie (`SessionCookieJar`):** guarda la cookie de sesión que entrega Spring Boot en preferencias privadas, la reenvía en solicitudes posteriores y la elimina con `clearSession()`.
- **Modelos (`data/model`):** representan requests/responses. `ApiRequestMapper` convierte el formulario al DTO Lombok del endpoint correspondiente antes de enviar JSON con Gson.
- **Recursos (`res`):** contienen vistas, paleta, textos, tema y grafo de navegación. View Binding genera bindings para las vistas XML reutilizadas por los Fragments.
- **Iconografía y regreso:** los iconos vectoriales locales de `res/drawable` mantienen el estilo de las secciones y acciones de Angular sin agregar dependencias externas. Las pantallas de registro, perfil y recursos usan `popBackStack()` mediante un control visible de regreso; el login y el dashboard no muestran ese control.

## Flujo de sesión y features

1. Login o registro validan los campos en su ViewModel y llaman a `AuthRepository`.
2. OkHttp recibe y persiste `JSESSIONID`; todas las llamadas Retrofit reutilizan la cookie.
3. Al iniciar y al entrar al dashboard, `SessionRepository` consulta `GET /api/auth/me`; una cookie guardada no se considera prueba de sesión válida.
4. El dashboard filtra accesos por rol y navega a `ResourceFragment`. `ResourceViewModel` usa `ApiRepository` y publica páginas, carga y errores.
5. El cliente puede consultar el catálogo, ver detalles, revisar sus compras y abrir tickets. Caja selecciona cliente/productos, agrega detalles y promociones vigentes, registra ventas y solicita el envío de tickets. Administración usa formularios tipados para las operaciones habilitadas, con selectores de entidades relacionadas.

Los formularios nativos se definen por recurso en `ResourceFragment.fieldsFor()` y se transforman en DTOs en `ApiRequestMapper`. Las líneas de venta/abastecimiento se agregan con controles de producto, cantidad y precio/costo. Los totales y reglas de negocio se validan/calculan en Spring Boot; Android envía el request requerido y muestra errores del servidor. La app no guarda contraseñas ni usa JWT.

## Endpoints y permisos

La base de Retrofit es `http://10.0.2.2:8080/api/` y los endpoints relativos son:

### Autenticación

- `POST auth/login`: inicio de sesión.
- `POST auth/register`: registro de cliente con `puntos: 0` y `nivelLealtad: "BRONCE"`.
- `GET auth/me` y `PUT auth/me`: restauración y edición de perfil.
- `POST auth/logout`: invalida la sesión del servidor y elimina la cookie local.

Login acepta:

```json
{
  "email": "persona@ejemplo.com",
  "contrasena": "tu-contraseña"
}
```

Registro agrega los campos:

```json
{
  "puntos": 0,
  "nivelLealtad": "BRONCE"
}
```

Spring Boot exige contraseña entre 8 y 20 caracteres. Las respuestas de autenticación contienen `id`, `nombre`, `apellido`, `email`, `numeroTelefono` y `rol`.

### Recursos de negocio

`BackendApi` y `ApiRepository` usan `page`/`size`, `q` cuando el controlador lo admite, rutas de detalle y operaciones de escritura. `ApiResource.forRole()` determina qué aparece en cada dashboard:

| Rol | Acceso en Android |
|---|---|
| `CLIENTE` | Catálogo de productos, compras propias y ticket |
| `CAJERO` | Catálogo, clientes, productos/proveedores/abastecimientos/promociones y sus detalles en modo lectura; consulta y registro de ventas; ticket y envío por correo |
| `ADMINISTRADOR` | Recursos administrativos, inventario, promociones, ventas y sus detalles según los métodos del backend |

La API disponible incluye `/clientes`, `/productos` y `/productos/catalogo`, `/ventas` y `/ventas/mis-compras`, `/proveedores`, `/abastecimientos`, `/promociones`, `/cajeros`, `/administradores`, `/usuarios`, `/detalle-ventas`, `/detalle-abastecimientos` y `/emails/ticket/{id}`. El catálogo paginado se interpreta desde `content`, `number` y `totalPages`.

El backend no habilita `POST /clientes` ni `PUT`/`DELETE /ventas/{id}`; no se muestran esas acciones en Android. Los permisos del servidor siguen siendo autoritativos: el filtrado de la app solo evita ofrecer acciones que el rol no puede realizar. Para cambiar el contrato o un permiso, actualiza primero Spring Boot.

## Configuración de red y sesión

- En el emulador, `10.0.2.2` apunta al `localhost` de la computadora. Para un dispositivo físico, cambia la constante `BASE_URL` de `RetrofitClient` a la IP local de la computadora, asegúrate de que ambos estén en la misma red y permite el puerto `8080` en el firewall.
- `usesCleartextTraffic` se permite solo para `debug`, porque la URL local usa HTTP. Las compilaciones `release` lo desactivan; antes de distribuir la app, usa una URL HTTPS y una configuración de red de producción.
- `SessionCookieJar` almacena cookies en `SharedPreferences` privadas (`MODE_PRIVATE`) para conservar la sesión tras cerrar y abrir la app. El archivo de preferencias se excluye de las copias de seguridad y transferencias del dispositivo. El backend controla la vigencia de la sesión. El almacenamiento privado no equivale a cifrado; producción debe usar HTTPS y revisar los requisitos de protección de sesión del producto.
- `RetrofitClient.clearSession()` borra la cookie local y se ejecuta al cerrar sesión o al recibir una sesión no autorizada.
- La carga de imagen para crear productos usa el selector del sistema y la parte multipart `file`; la parte `producto` lleva JSON.
- Los tickets se descargan al caché privado y se abren con un visor PDF instalado. Caja/administración pueden solicitar el envío por correo.

## Dependencias

- **Retrofit + Gson:** definición de endpoints, llamadas asíncronas y conversión JSON.
- **OkHttp:** transporte HTTP y manejo persistente de cookies.
- **AndroidX Lifecycle:** `ViewModel` y `LiveData`.
- **Jetpack Navigation:** grafo y destinos Fragment dentro de una sola Activity.
- **View Binding:** referencias tipadas a vistas; está activado en `app/build.gradle.kts`.
- **Lombok:** reduce código repetitivo en modelos y se ejecuta en compilación, no en tiempo de ejecución.
- **Material Components:** campos, botones y tarjetas de la interfaz nativa.

## Ejecutar y verificar

1. Inicia Spring Boot con su configuración local y confirma que está escuchando en el puerto `8080`.
2. Abre el proyecto en Android Studio y ejecuta la variante `debug` en un emulador o dispositivo configurado.
3. Ingresa o crea una cuenta y prueba el menú de acuerdo con su rol. El dashboard requiere que el backend y la base de datos respondan.
4. Ejecuta las comprobaciones unitarias y de empaquetado desde PowerShell:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

## Extender la aplicación

- Para agregar un recurso, confirma primero controlador, DTO y permiso en Spring Boot; agrega la definición de roles/capacidades a `ApiResource` y las operaciones necesarias a `BackendApi`/`ApiRepository`.
- Para nueva lógica de una pantalla, colócala en el ViewModel; el Fragment observa estado y actualiza vistas.
- Para un recurso nuevo, agrega su ruta/capacidades a `ApiResource`, sus campos y relaciones a `ResourceFragment.fieldsFor()`, y un DTO correspondiente a `data/model/request` con su transformación en `ApiRequestMapper`.
- Registra pantallas en `res/navigation/nav_graph.xml`; `MainActivity` es la única Activity interna/exportada para launcher.
- Mantén textos en `res/values/strings.xml` y colores en `res/values/colors.xml`.
- No agregues credenciales ni secretos a los archivos del proyecto.
