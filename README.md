# Biblia Studio

Aplicación Android minimalista para reproducir capítulos de audio (MP3) offline.

Características incluidas en el scaffold:
- Kotlin + Jetpack Compose
- ExoPlayer para reproducción
- Selección inicial de carpeta mediante Storage Access Framework (SAF)
- Lista de archivos .mp3 en la carpeta seleccionada
- Reproductor minimalista: Play/Pausa grande, retroceder 10s, adelantar 10s, barra de progreso
- Guarda la posición de reproducción por archivo usando DataStore (se restaura al volver a abrir)
- Botón para seleccionar carpeta nuevamente / compartir archivo

Instrucciones rápidas:
1. Clona el repo: git clone git@github.com:walvarezelcastillo-sudo/biblia-studio.git
2. Abre el proyecto en Android Studio (recomiendo Electric Eel / Flamingo o superior).
3. Deja que Gradle sincronice y descarga dependencias.
4. Conecta un dispositivo Android o usa un emulador. Ejecuta la app.
5. Al abrir por primera vez, presiona "Seleccionar carpeta" y elige la carpeta donde están tus MP3. La app recordará la carpeta.

Notas:
- La app usa SAF por lo que no necesita permisos de almacenamiento a nivel de sistema.
- APK/Play store no incluido en scaffold; para generar APK usa Build > Build Bundle(s) / APK(s).

