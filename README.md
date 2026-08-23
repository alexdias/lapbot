# Lapbot

Native Android live timing and audio-announcement app for Alpha Race Hub, built with Kotlin and Jetpack Compose.

See [PRODUCT_REQUIREMENTS.md](PRODUCT_REQUIREMENTS.md) for the live timing, metrics, announcements, and connection behavior specification.

## Requirements

- JDK 17
- Android SDK Platform 36
- Android Studio (latest stable) or the Android CLI

## Open and run

Open this folder in Android Studio and run the `app` configuration on an emulator or connected Android device.

From a terminal, build a debug APK with:

```bash
./gradlew assembleDebug
```

The generated APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

## Project details

- App name: Lapbot
- Package: `com.example.lapbot` (replace this before publishing)
- Minimum Android version: API 24 (Android 7.0)
- Target/compile Android version: API 36

## Usage

1. Tap `Connect`. When Buckmore has an active session, the status changes to `Live` and timing data appears.
2. Tap `Announcer`.
3. Select an existing kart, or enter its number before it appears on circuit.
4. Enable `Speak laps`.
5. Optionally choose a tone metric, configure tone durations, or set `Metrics since lap` for a driver stint.

Lap announcements and tones continue while the app is backgrounded.
