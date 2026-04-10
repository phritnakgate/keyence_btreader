# Android app with Keyence BT Reader SDK 

Before uploading a code to handheld reader via developer mode or build an APK to install on your device, don't forget to change a parameter ```IS_APP_ON_EMULATOR``` to false, located in ```ScannerService.kt```.
```kotlin
companion object {
  const val IS_APP_ON_EMULATOR = false
}
```

This app is made for using Keyence Barcode BT-A600 reader to send a value to ESP32-LoRa board and handle a callback when a data is sent via LoRA successfully.

**Status Code**
- 0: Bluetooth is not connected and can't send to ESP32
- 1: Data is sent via Bluetooth to ESP32, but can't send to LoRA.
- 2: Data is transferred through LoRA successfully.
