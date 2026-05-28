estar conectado pirmero usb al celular y pc y la depuracion activada

.\gradlew clean assembleDebug

C:\Android\platform-tools\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk

C:\Android\platform-tools\adb.exe logcat -s StickerProvider:D StickerConverter:D


C:\Android\platform-tools\adb.exe logcat -s StickerProvider:D StickerConverter:D WhatsApp:D StickerPackValidator:D AndroidRuntime:E