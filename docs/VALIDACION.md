# Comprobaciones de la versión Android 1.0.0

Entorno: emulador **Pixel 7, Android 14 / API 34, x86_64**. Comprobaciones realizadas para esta primera adaptación; no representan pruebas en todas las marcas de teléfonos.

| Comprobación | Resultado observado |
| --- | --- |
| Compilación Debug y Release | Correctas con Gradle 8.10.2 / AGP 8.7.3 / JDK 17. |
| Android Lint Release | 0 errores. Avisos sobre versiones más nuevas de dependencias y detalles de implementación/localización; no se usó un baseline para ocultar errores. |
| Firma de APK ARM64 | Verificada con apksigner, esquema APK Signature v2. Clave local exclusiva del proyecto. |
| Instalación final Release | Instalación limpia de la APK x86_64 firmada, apertura correcta, estado Lista, sin cierre inesperado; la aplicación final no es depurable. |
| Interfaz móvil | Viewport de 412 px; sin desbordamiento horizontal en Inicio. |
| Análisis YouTube | Dos videos accesibles devolvieron título, pista(s) y alturas. Uno anunció alturas de 1080p a calidades inferiores. |
| Descarga YouTube MP4 | Archivo completo de 533.915 bytes, duración aproximada de 19 segundos, exportado a Descargas/VideoFlow. |
| Descarga YouTube OGG | Archivo completo de 267.999 bytes. FFprobe confirmó contenedor OGG, códec **Vorbis**, duración de 19,008 segundos. |
| Recuperación CDN YouTube | Los primeros intentos con el motor sin adaptación fallaron por conexión. Al incluir el plugin de servidores alternativos, MP4 y OGG terminaron correctamente. |
| Análisis TikTok | Un enlace de las pruebas públicas del extractor devolvió título, tres grupos de audio y altura 1024. No se hizo una descarga completa de TikTok. |
| Análisis Facebook | Dos enlaces de las pruebas públicas del extractor devolvieron `Cannot parse data` sin sesión. Se informa como solicitud de revisar el acceso/sesión; **no se validó una descarga completa de Facebook**. |
| WAV → MP3 | Audio sintético de 4 segundos: resultado de 97.388 bytes guardado mediante MediaStore. |
| WAV → OGG | Audio sintético de 4 segundos: resultado de 22.528 bytes. |
| MP4 → MP4 | Video generado localmente: resultado de 432.645 bytes, conservando la entrada. |
| Compresión | Entrada sintética de 656.206 bytes → 204.402 bytes. Es una observación de este archivo, no una reducción garantizada. |
| Selección desde Android | El selector del sistema abrió un MP4 de Descargas y se importaron 204.402 bytes; se detectó su duración de 4,032 s. |
| Cola | Cuatro conversiones procesadas, dos en paralelo, con resultados e historial. |
| Retirar y restaurar todas | Cuatro registros retirados y devueltos a la cola. Historial y URI de los archivos sin cambios; no se iniciaron nuevas descargas. |
| Vaciar retiradas | Se observó y aceptó un diálogo nativo. Se eliminó el registro temporal, conservando su historial y el archivo, que seguía apareciendo en el selector de Android. |
| Persistencia | Tras forzar el cierre y abrir, se conservaron registros y ajustes (idioma, tema y retención). Los trabajos que estaban activos pasaron a Interrumpidos. |
| Reproductor / PiP | Reproducción de un archivo local; Android informó una tarea en modo `pinned`. La ventana PiP permaneció al navegar a Inicio. |
| Separación del proyecto PC | `git status --short` en Descargador de YouTube quedó vacío. No se trasladó su base de datos, cookies, historial o configuración al proyecto Android. |

Las pruebas de conversión usaron audio y video sintéticos generados localmente. Los medios descargados para comprobar YouTube permanecieron solo en el emulador/directorio de trabajo y **no forman parte de las APK**.

Quedan por comprobar en teléfonos físicos las distintas arquitecturas ARM, restricciones específicas de batería de cada fabricante, el acceso autenticado a Facebook/TikTok/YouTube, una colección completa y pistas multilingües seleccionadas en una descarga real. La implementación está incluida, pero no se presenta como validada en esos escenarios.
