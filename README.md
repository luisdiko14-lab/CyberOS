# CyberOS

CyberOS is a Kotlin/Jetpack Compose, full-screen Android shell demo with a terminal, app catalog, media browser, permission dashboard, CRT styling, and optional overlay service.

## Build

Install Android SDK Platform 36 and Build Tools, then run:

```bash
./gradlew assembleDebug
```

The project uses Gradle 9.5, Android Gradle Plugin 8.13.2, Kotlin 2.2.20, `minSdk 26`, and `targetSdk 35`.

## Android behavior

- Android remains the host operating system. CyberOS can be selected as a home-screen app; it cannot replace Android's kernel, secure keyguard, or system UI without privileged firmware/root access.
- The in-app lock screen is a visual shell gate, not device authentication. Its demo PIN is `0420`.
- Overlay, microphone, media, notification, and unknown-source install access are separately granted by the user through Android's runtime permission prompt or Settings.
- Installing Firefox uses Mozilla's Android download endpoint and hands the APK to Android Package Installer. Android requires the user to confirm installation; CyberOS does not silently install packages. The native Firefox app is opened with its launch intent when present; otherwise the Store offers a web fallback.
- Store entries other than Firefox are catalog/demo modules. `unlock 3344` reveals a hidden catalog entry; `sideload core` only stages a simulator state and does not install software.

## Permissions

The overlay service is user-started and foreground-notified. Media access is used only to list local images and videos; the system photo picker can set an image as a shell/lock background without broad media access. Notification and microphone permissions are requested when their related feature is used.