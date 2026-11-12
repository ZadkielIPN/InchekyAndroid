# Guía de desarrollo de Incheky Android

## Requisitos

- Android Studio y un JDK compatible con la versión de Gradle del proyecto.
- Un emulador Android o un dispositivo con acceso a la misma red que el backend.
- Spring Boot ejecutándose en el puerto `8080`.

## Inicio de sesión

La pantalla envía las credenciales a `POST /api/auth/login`. El cuerpo JSON usa los nombres del API:

```json
{
  "email": "persona@ejemplo.com",
  "contrasena": "tu-contraseña"
}
```

La respuesta exitosa contiene `id`, `nombre`, `apellido`, `email`, `numeroTelefono` y `rol`. El backend consultado no devuelve un token en este endpoint. La app muestra el nombre recibido y no persiste credenciales ni sesión local.

## Arquitectura

El proyecto separa el punto de entrada, la presentación, el estado, el acceso a datos y los modelos:

- `MainActivity`: punto de entrada Android; activa Edge-to-Edge, crea el coordinador y delega el botón Atrás.
- `ui/AppController`: coordina la navegación, las pantallas y sus interacciones. No realiza llamadas HTTP directamente.
- `ui/login/LoginViewModel`: valida la entrada, coordina el inicio de sesión y expone `LiveData` para carga, error y usuario.
- `ui/home/AppViewModel`: coordina las operaciones de la aplicación y publica los resultados para la presentación.
- `data/repository/AuthRepository`: invoca el API de autenticación y convierte respuestas HTTP o fallos de conexión en mensajes para la vista.
- `data/remote/AuthApi`: declara las rutas Retrofit y el cuerpo de cada solicitud.
- `data/remote/RetrofitClient`: configura Retrofit, Gson y la URL base.
- `data/model/LoginRequest` y `UsuarioSession`: definen los datos enviados y recibidos. Lombok genera constructores y accesores.
- `res/layout/activity_main.xml`: define la pantalla; View Binding genera `ActivityMainBinding` a partir de este layout.

## URL del backend

`RetrofitClient` usa `http://10.0.2.2:8080/api/`, que permite al emulador Android acceder al `localhost` de la computadora. Para un dispositivo físico, cambia `10.0.2.2` por la dirección IP local de la computadora que ejecuta Spring Boot. El dispositivo debe estar en la misma red y el firewall debe permitir la conexión.

Solo la variante `debug` permite tráfico HTTP sin cifrar (`usesCleartextTraffic`); `release` lo desactiva. Antes de distribuirla, configura el endpoint de producción con HTTPS.

## Dependencias

- **Retrofit + Gson:** llamadas HTTP asíncronas y conversión JSON.
- **AndroidX Lifecycle:** `ViewModel` y `LiveData` para conservar y observar el estado de la pantalla.
- **Lombok:** reduce código repetitivo en los modelos; se incluye como dependencia de compilación y procesador de anotaciones, no en tiempo de ejecución.
- **View Binding:** acceso tipado a las vistas del layout, activado en `app/build.gradle.kts`.

## Ejecutar y verificar

1. Inicia Spring Boot y confirma que `/api/auth/login` está disponible.
2. Ejecuta la aplicación en el emulador o dispositivo configurado.
3. Ingresa correo y contraseña. La app valida campos y formato de correo antes de llamar al backend.
4. Comprueba el mensaje de bienvenida o el error mostrado para la respuesta del servidor.

Las rutas para registro, perfil y cierre de sesión existen en el cliente web, pero esta app implementa únicamente el inicio de sesión solicitado.
