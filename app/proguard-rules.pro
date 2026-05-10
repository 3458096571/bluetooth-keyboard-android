# ProGuard rules for Bluetooth Keyboard App

# Keep HID related classes
-keep class android.bluetooth.** { *; }
-keep class com.example.bluetoothkeyboard.** { *; }

# Kotlin
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
