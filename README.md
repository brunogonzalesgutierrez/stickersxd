# Sticker Converter — Android App
Convierte videos MP4/GIF a stickers animados WebP y los agrega directo a WhatsApp.

## Requisitos
- Android Studio Hedgehog o superior (o SDK de línea de comandos)
- JDK 17
- Android SDK 34
- Dispositivo/emulador Android 6.0+ (API 23+)
- WhatsApp instalado en el dispositivo destino

## Setup rápido

### 1. Editar local.properties
Abrí `local.properties` y cambiá la ruta al SDK de Android:
```
sdk.dir=C:\Users\TuNombre\AppData\Local\Android\Sdk
```

### 2. Descargar gradle-wrapper.jar
El archivo `gradle/wrapper/gradle-wrapper.jar` necesita descargarse de Gradle.
Ejecutá esto UNA sola vez:
```
curl -L https://github.com/gradle/gradle/raw/v8.4.0/gradle/wrapper/gradle-wrapper.jar ^
  -o gradle\wrapper\gradle-wrapper.jar
```
O abrí el proyecto en Android Studio — lo descarga automáticamente.

### 3. Compilar e instalar
```bat
gradlew.bat assembleDebug
adb install app\build\outputs\apk\debug\app-debug.apk
```

### 4. Compilar + instalar directo en el celu conectado
```bat
gradlew.bat installDebug
```

## Cómo funciona
1. Elegís un video MP4/GIF desde la galería
2. La app extrae frames con `MediaMetadataRetriever`
3. Los codifica como WebP animado (formato nativo de WA)
4. `StickerProvider` (ContentProvider) expone el archivo a WhatsApp
5. Se lanza el intent oficial de WA para agregar el sticker pack

## Estructura del proyecto
```
app/src/main/java/com/tu/stickerapp/
  MainActivity.kt      — UI principal, flujo de conversión
  StickerConverter.kt  — Extrae frames y genera WebP animado
  StickerProvider.kt   — ContentProvider para WhatsApp
  StickerPack.kt       — Data models

app/src/main/res/
  layout/activity_main.xml
  values/themes.xml
  values/strings.xml
```

## Personalización
- Cambiá `com.tu.stickerapp` por tu package en:
  - `AndroidManifest.xml` (authorities del provider)
  - `build.gradle` (applicationId)
  - Todos los `.kt` (package declaration)
- Modificá `"mi_pack_1"` y `"Mi Pack"` en `MainActivity.kt` y `StickerProvider.kt`

## Troubleshooting
| Error | Solución |
|---|---|
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | `adb uninstall com.tu.stickerapp` |
| Sticker no aparece en WA | Verificar que el WebP sea exactamente 512x512 y < 500KB |
| WA dice "pack inválido" | El pack necesita mínimo 3 stickers — duplicá el mismo sticker 3 veces para probar |
| Provider no encontrado | Verificar que el `authorities` en Manifest coincida con `StickerProvider.AUTHORITY` |

.\gradlew clean assembleDebug

C:\Android\platform-tools\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk

C:\Android\platform-tools\adb.exe logcat -s System.err:W AndroidRuntime:E *:F

C:\Android\platform-tools\adb.exe logcat --pid=$(C:\Android\platform-tools\adb.exe shell pidof com.tu.stickerapp) *:V -v time 2>&1 | Select-String -Pattern "sticker|error|Error|exception|Exception|retriever|frame|webp|WEBP|ANMF|VP8|RIFF|convert|Convert|provider|Provider" -CaseSensitive:$false


C:\Android\platform-tools\adb.exe logcat --pid=$(C:\Android\platform-tools\adb.exe shell pidof com.whatsapp) *:V -v time 2>&1 | Select-String -Pattern "validation|sticker|invalid|error|failed|size|tray|animated|pack" -CaseSensitive:$false

.\gradlew clean assembleDebug

C:\Android\platform-tools\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk

C:\Android\platform-tools\adb.exe logcat -s StickerProvider:D StickerConverter:D


C:\Android\platform-tools\adb.exe logcat -s StickerProvider:D StickerConverter:D WhatsApp:D StickerPackValidator:D AndroidRuntime:E