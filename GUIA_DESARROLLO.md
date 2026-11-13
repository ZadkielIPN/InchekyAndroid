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
- **Identidad y estilo:** el logo de Handel Incheky se usa en los iconos adaptables del launcher, en login/registro y en la cabecera del dashboard. El tema comparte superficies claras, tarjetas delimitadas y campos de formulario outline con borde azul de mayor contraste al enfocarse.

## Flujo de sesión y features

1. Login o registro validan los campos en su ViewModel y llaman a `AuthRepository`.
2. OkHttp recibe y persiste `JSESSIONID`; todas las llamadas Retrofit reutilizan la cookie.
3. Al iniciar y al entrar al dashboard, `SessionRepository` consulta `GET /api/auth/me`; una cookie guardada no se considera prueba de sesión válida.
4. El dashboard valida el rol; al cliente lo lleva directamente al catálogo y conserva el dashboard debajo para acceder a perfil/compras/cerrar sesión. Caja y administración permanecen en el dashboard con accesos permitidos. `ResourceViewModel` usa `ApiRepository` y publica páginas, carga y errores.
5. El cliente puede consultar el catálogo, ver detalles, revisar sus compras y abrir tickets. Caja selecciona cliente, productos y cantidades para registrar ventas; el precio unitario se toma del producto y no se edita en cada renglón. El formulario muestra saldo y máximo aplicable de puntos, un desglose estimado de promociones/lealtad/puntos, total, puntos estimados y cambio o faltante en efectivo. “Efectivo recibido” aparece solo con ese método de pago y se omite del request para los demás métodos. El backend vuelve a calcular precios, descuentos, puntos y total, y rechaza efectivo insuficiente; sus valores son la autoridad final. Administración usa formularios tipados para las operaciones habilitadas, con selectores de entidades relacionadas.

Los formularios nativos se definen por recurso en `ResourceFragment.fieldsFor()` y se transforman en DTOs en `ApiRequestMapper`. Las líneas de venta/abastecimiento se agregan con controles de producto, cantidad y precio/costo. Las líneas repetidas de una venta se consolidan para respetar la regla del backend de no repetir productos. El resumen de venta es informativo; los totales y reglas de negocio se validan/calculan en Spring Boot. Android envía el request requerido y muestra errores del servidor. La app no guarda contraseñas ni usa JWT.

El catálogo y listado de productos muestra las imágenes del campo `imagen` que devuelve la API. Los nombres de archivo se resuelven desde el origen de Retrofit como `/uploads/productos/{imagen}`; las URLs absolutas se conservan. `Glide` descarga y cachea las imágenes con placeholder/error cuando no hay imagen o no puede cargarse. Las acciones principales y secundarias usan tonos azules de la paleta común.

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
| `CAJERO` | Catálogo, clientes, productos/proveedores/abastecimientos/promociones en modo lectura; consulta y registro de ventas con sus artículos; ticket y envío por correo |
| `ADMINISTRADOR` | Recursos administrativos, inventario, promociones, ventas y abastecimientos con sus artículos dentro de cada operación |

La API disponible incluye `/clientes`, `/productos` y `/productos/catalogo`, `/ventas` y `/ventas/mis-compras`, `/proveedores`, `/abastecimientos`, `/promociones`, `/cajeros`, `/administradores`, `/usuarios`, `/detalle-ventas`, `/detalle-abastecimientos` y `/emails/ticket/{id}`. Los detalles de ventas y abastecimientos se gestionan como artículos dentro de su operación principal y no aparecen como accesos independientes en el dashboard. Los listados usan páginas de 20 elementos, con índices base cero en Spring y controles anterior/siguiente en Android. La búsqueda vuelve a la primera página; los controles se deshabilitan durante la carga y respetan los límites informados por `totalPages`. Si un registro eliminado deja la página actual fuera de rango, la lista vuelve a cargar la última página disponible. La respuesta se interpreta desde `content`, `number` y `totalPages`.

El backend no habilita `POST /clientes` ni `PUT`/`DELETE /ventas/{id}`; no se muestran esas acciones en Android. La opción “Ver detalle” presenta la información en un modal con etiquetas legibles y, cuando la respuesta contiene `detalles`, los muestra como artículos con producto, cantidad y precio/costo unitario. Los permisos del servidor siguen siendo autoritativos: el filtrado de la app solo evita ofrecer acciones que el rol no puede realizar. Para cambiar el contrato o un permiso, actualiza primero Spring Boot.

## Configuración de red y sesión

- En el emulador, `10.0.2.2` apunta al `localhost` de la computadora. Para un dispositivo físico, cambia la constante `BASE_URL` de `RetrofitClient` a la IP local de la computadora, asegúrate de que ambos estén en la misma red y permite el puerto `8080` en el firewall.
- `usesCleartextTraffic` se permite solo para `debug`, porque la URL local usa HTTP. Las compilaciones `release` lo desactivan; antes de distribuir la app, usa una URL HTTPS y una configuración de red de producción.
- `SessionCookieJar` almacena cookies en `SharedPreferences` privadas (`MODE_PRIVATE`) para conservar la sesión tras cerrar y abrir la app. El archivo de preferencias se excluye de las copias de seguridad y transferencias del dispositivo. El backend controla la vigencia de la sesión. El almacenamiento privado no equivale a cifrado; producción debe usar HTTPS y revisar los requisitos de protección de sesión del producto.
- `RetrofitClient.clearSession()` borra la cookie local y se ejecuta al cerrar sesión o al recibir una sesión no autorizada.
- La carga de imagen para crear productos usa el selector del sistema y la parte multipart `file`; la parte `producto` lleva JSON.
- Los tickets se descargan al caché privado y se abren con un visor PDF instalado. Caja/administración pueden solicitar el envío por correo.

## Comparación funcional con InchekyAngular

La comparación se realizó con la rama `master` de `ZadkielIPN/InchekyAngular`. Los formularios administrativos de Android y Angular comparten los campos principales de clientes, cajeros, administradores, proveedores, productos, promociones y abastecimientos. Android aplica validación de contraseña de 8 a 20 caracteres, omite la contraseña vacía al editar cuentas y permite dejar sin fecha final una promoción; la fecha final, cuando se captura, debe ser futura y no anterior a la fecha de inicio. Las promociones requieren al menos un producto, igual que Angular.

El producto Android incluye `valoracion` con valor inicial cero y conserva el selector API `promocionIds`; Angular maneja las relaciones de promociones desde el formulario de promociones. La selección de imagen Android permite elegir archivo y conservar una URL de imagen. Los formularios de perfil y registro y el historial de compras con descarga de ticket también están disponibles en Android.

La venta Android mantiene el mismo precio derivado del producto y los cálculos estimados de promoción activa, nivel de lealtad, uso máximo de puntos, total, puntos ganados y efectivo/cambio. `OTRO` permanece como método de pago adicional porque Spring Boot lo define en su enum, aunque Angular no lo muestra. La creación de clientes sigue deshabilitada en Android porque el backend no habilita `POST /clientes`; los permisos del servidor prevalecen sobre cualquier ruta o control disponible en Angular.

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
