# Componentes incorporados

VideoFlow Local Android incorpora estos proyectos. No están afiliados a VideoFlow.

| Componente | Versión fijada | Fuente y licencia |
| --- | --- | --- |
| youtubedl-android library/FFmpeg | 0.18.1 | [Código de la versión 0.18.1](https://github.com/yausername/youtubedl-android/tree/0.18.1) · GPL-3.0. Redistribuye Python, yt-dlp, FFmpeg, QuickJS y bibliotecas nativas de sus paquetes. |
| yt-dlp | La versión incluida por 0.18.1 y las actualizaciones elegidas por el usuario | https://github.com/yt-dlp/yt-dlp · Unlicense para su código; sus paquetes pueden incluir componentes con licencias adicionales. |
| FFmpeg | Paquete incluido por youtubedl-android 0.18.1 | https://ffmpeg.org/ · GPL/LGPL según sus opciones y bibliotecas incorporadas. Se usa la distribución GPL compatible con esta app. |
| Python | Paquete incluido por youtubedl-android 0.18.1 | https://www.python.org/ · PSF License. |
| QuickJS | Paquete incluido por youtubedl-android 0.18.1 | https://bellard.org/quickjs/ · MIT. |
| AndroidX AppCompat | 1.7.0 | https://developer.android.com/jetpack/androidx/releases/appcompat · Apache-2.0. |
| AndroidX WebKit | 1.12.1 | https://developer.android.com/jetpack/androidx/releases/webkit · Apache-2.0. |
| AndroidX Media3 | 1.5.1 | https://github.com/androidx/media · Apache-2.0. |
| Desugar JDK libraries | 2.1.4 | https://github.com/google/desugar_jdk_libs · GPL-2.0 con excepción de classpath. |
| Kotlin standard library | Dependencia transitiva del wrapper | https://github.com/JetBrains/kotlin · Apache-2.0. |
| Jackson y Commons IO | Dependencias transitivas del wrapper | https://github.com/FasterXML/jackson y https://commons.apache.org/proper/commons-io/ · Apache-2.0. |

El código fuente de esta aplicación acompaña a las APK. Las dependencias exactas se resuelven desde Maven Central/Google según `app/build.gradle`; la implementación original y las instrucciones de construcción de Python/FFmpeg se encuentran en el repositorio del wrapper (etiqueta 0.18.1). El texto GPL-3.0 se incluye en `LICENSE` y en los recursos de la APK.

Los logos de las plataformas se reutilizan del proyecto de Windows para identificar enlaces compatibles. Sus marcas pertenecen a sus respectivos titulares.
