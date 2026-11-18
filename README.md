# VideoFlow Local para Android

VideoFlow Local para Android permite descargar y convertir videos y audio desde el teléfono, organizar trabajos y reproducir archivos locales. La edición de escritorio tiene su propio repositorio. [VideoFlow Local para Windows](https://github.com/Marco035xd/VideoFlow-Local)

## Instalar

Las APK firmadas y sus sumas SHA-256 están en [Releases](https://github.com/Marco035xd/VideoFlow-Local-Android/releases/latest).

- **ARM64** para la mayoría de los teléfonos Android actuales.
- **ARM32** para equipos con procesador ARM de 32 bits.
- **Universal** incluye ARM de 32 y 64 bits, x86 y x86_64.

Se necesita **Android 10 o posterior**. Descarga la APK en el celular y ábrela para instalar. Si Android solicita permiso para instalar desde esa aplicación de archivos, concédelo para esa instalación.

La APK funciona en el teléfono sin conectarse a una PC. No hay un servidor de VideoFlow que reciba tus videos o cookies. Los sitios de origen siguen necesitando conexión a Internet.

## Funciones incluidas

| Función | Adaptación Android |
| --- | --- |
| YouTube, Facebook y TikTok | Análisis y descargas mediante yt-dlp ejecutado en el dispositivo. También recibe enlaces desde **Compartir → VideoFlow Local**. |
| Lotes de enlaces | Un enlace por línea; cola persistente, progreso, cancelación y reintento. De 1 a 3 trabajos simultáneos. |
| Listas y canales de YouTube | Importación en segundo plano. Un trabajo por video accesible, con omisión de duplicados dentro de la colección. |
| Video | MP4, MKV, WEBM, MOV y AVI, con selección de altura según los formatos disponibles. |
| Audio | MP3, OGG Vorbis, M4A AAC, FLAC, WAV y Opus. Tasas seleccionables para formatos con pérdida. |
| Pistas de audio | El análisis agrupa las pistas por idioma/nombre y permite elegirlas. La selección restringe los formatos: no cambia a otra pista silenciosamente. Las colecciones usan selección automática por video. |
| Conversión local | Selección múltiple de videos y audios desde el selector de Android. FFmpeg crea archivos nuevos y conserva los originales. |
| Compresión | MP4 H.264/AAC, tres intensidades y límite opcional de 1080p/720p/480p. Se comprueba el tamaño real y se rechaza un resultado mayor o igual que el original. No promete un porcentaje exacto. |
| Historial | Registro local de los trabajos completados, búsqueda, reproducción, compartir y apertura externa. Limpiar el historial tiene confirmación y conserva los archivos. |
| Retiradas temporales | Persisten 7, 15 o 30 días. Restauración individual o de todas, sin descargar de nuevo. Vaciar exige confirmación y conserva archivos e historial. |
| Apariencia | Modo del sistema, claro y oscuro, seis temas y ocho opciones de acento. En **Fondos de pantalla** puedes guardar tus fotos, volver a elegirlas y ajustar la opacidad de cada una entre 25 % y 100 %. |
| Idiomas | Español, inglés y portugués. |
| Perfil | Nombre, usuario, descripción, avatares de ejemplo, GIF y banner ajustable con imagen o video. El perfil se guarda en el celular. Suscripciones muestra los beneficios propuestos de Usuario Pro. |
| Sonidos (Tester) | Clic, descarga completada y error con controles y pruebas independientes en Configuración. |
| Reproductor | Media3/ExoPlayer, controles táctiles, velocidad, búsqueda temporal, reproducción local y Picture-in-Picture nativo con acción de reproducir/pausar. |
| Segundo plano | Servicio con notificación y bloqueo de suspensión durante los trabajos. Respeta las restricciones de Android. |
| Motor actualizable | yt-dlp puede comprobar actualizaciones oficiales al abrir o manualmente. FFmpeg y QuickJS están incluidos en la APK. |

Los formatos, alturas, idiomas y acceso dependen de cada publicación y del sitio. Una calidad de audio seleccionada es una tasa de conversión; no mejora la calidad del original. Los códecs que el reproductor puede decodificar dependen también del equipo.

## Uso rápido

1. En **Inicio**, pega un enlace y pulsa **Analizar video** para ver información, calidades y pistas.
2. Elige video con audio, solo audio o solo video, y el formato de salida.
3. Pulsa **Añadir a Descargas**. Para lotes, pega varios enlaces, uno por línea.
4. Los archivos terminados se guardan mediante MediaStore en **Descargas / VideoFlow**. Desde la app puedes reproducirlos, compartirlos o abrirlos con otra app.
5. Usa **Listas y canales** para una colección de YouTube.
6. En **Herramientas**, selecciona varios archivos locales para convertir o comprimir.

## Sesiones de los sitios

Android mantiene separadas las sesiones de Chrome, Brave, Facebook y otras apps. Esta versión no intenta leerlas. Si el sitio solicita iniciar sesión o verificar acceso, usa **Configuración → Importar cookies.txt** con un archivo propio en formato Netscape.

Solo se aceptan cookies de YouTube, Facebook, TikTok y sus subdominios pertinentes. El archivo importado queda en almacenamiento privado sin copia de seguridad, separado del historial. Cada proceso usa una copia temporal; se elimina al terminar. **Olvidar sesión** borra la copia importada. No se necesita importar cookies para los videos que el sitio entregue sin sesión. Una sesión no garantiza el acceso a todas las publicaciones.

## Recuperación y almacenamiento

Las descargas de YouTube incorporan el manejo de servidores alternativos de VideoFlow Windows 1.6.19, como un plugin aislado de yt-dlp. Solo se usan alternativas anunciadas en la URL original de YouTube (`mn`); se conserva la consulta firmada y la validación TLS. Se prueban como máximo dos servidores alternativos tras errores transitorios. No se usan proxies ni se modifican ajustes de red del teléfono. Los rechazos 401/403/429 y errores de certificado no activan esa rotación. En modo de conexión **Automática** queda un intento adicional por IPv4.

Si Android o el usuario detienen el proceso, los trabajos incompletos aparecen como **Interrumpidos** al abrir. Se pueden reintentar; no se presentan como completados. La ventana de PiP es la del sistema Android y se puede mover sin congelar deliberadamente la reproducción.

No se pide acceso global a todo el almacenamiento ni permiso para superponerse sobre otras apps. El selector del sistema permite importar archivos. Los trabajos usan copias de los originales en el espacio privado y exportan el resultado a MediaStore. Borrar datos o desinstalar elimina registros, sesiones y temporales privados; los medios ya publicados en Descargas permanecen en almacenamiento compartido.

## Funciones de PC que se dejaron fuera

No se incluyeron separación de voz e instrumentos por IA, navegador privado, carpeta cifrada, automatización del Explorador de Windows ni lectura de sesiones de navegadores de PC. Tampoco sincronización en la nube o transmisión a TV. Se priorizó la descarga de YouTube/Facebook/TikTok y las herramientas solicitadas.

## Compilar de nuevo

Requisitos: Windows, **JDK 17**, Android SDK con **plataforma 35** y acceso a Internet para resolver dependencias.

```powershell
.\CREAR_APK.ps1
# O indicar rutas:
.\CREAR_APK.ps1 -Jdk 'C:\ruta\jdk-17' -Sdk 'C:\ruta\Android\Sdk'
```

El script usa Gradle Wrapper 8.10.2, compila las APK y ejecuta Android Lint. Genera una clave de firma local exclusiva en `.signing/` la primera vez. **Conserva esa carpeta en privado** para firmar futuras actualizaciones; no la subas a Git. La aplicación de Windows tiene un proyecto y proceso de publicación distintos.

Abre esta carpeta en Android Studio para editarla. Los archivos de equipo `local.properties`, las APK compiladas y las claves están excluidos de Git. Las versiones oficiales se distribuyen en Releases.

## Organización

- `app/src/main/assets/`: interfaz móvil, traducciones, temas, logo e iconos reutilizados con autorización del proyecto original.
- `app/src/main/java/com/videoflow/local/`: motor Android, cola en servicio, biblioteca local, puente de interfaz y reproductor/PiP.
- `app/src/main/assets/plugins/`: recuperación de servidores de YouTube portada del proyecto Windows.
- `docs/`: capturas del emulador y alcance de las comprobaciones.
- `release/`: APK instalables y sumas de verificación.
- `CREAR_APK.ps1`: compilación y firma repetibles.

## Componentes y licencia

Esta adaptación se entrega con código fuente bajo GPL-3.0, compatible con la dependencia youtubedl-android. Consulta `LICENSE` y `THIRD_PARTY_NOTICES.md` para las fuentes, versiones y licencias de los componentes incorporados.
