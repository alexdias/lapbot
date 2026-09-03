# Lapbot

Lapbot is a live timing and audio-announcement app for Alpha Race Hub.

The platform applications are maintained as separate Git submodules:

- [`android/`](android/) contains the native Android app built with Kotlin and Jetpack Compose.
- [`ios/`](ios/) is reserved for the native iOS app.

The Android product specification is available at
[`android/PRODUCT_REQUIREMENTS.md`](android/PRODUCT_REQUIREMENTS.md).

## Android

Requirements:

- JDK 17
- Android SDK Platform 36
- Android Studio (latest stable) or the Android CLI

Open `android/` in Android Studio, or build a debug APK from a terminal:

```bash
cd android
./gradlew assembleDebug
```

The generated APK will be at
`android/app/build/outputs/apk/debug/app-debug.apk`.

## iOS

The iOS application has not been implemented yet.
