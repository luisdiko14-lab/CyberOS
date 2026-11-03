# CyberOS

CyberOS is a Kotlin/Jetpack Compose Android home shell with a terminal, draggable desktop windows, app catalog, media browser, passive BLE HUD, biometric vault, hardware monitor, Glance widget, and optional overlay service.

## Build

Install Android SDK Platform 36 and Build Tools, then run:

```bash
./gradlew assembleDebug
```

The project uses Gradle 9.5, Android Gradle Plugin 8.13.2, Kotlin 2.2.20, `minSdk 26`, and `targetSdk 35`.

## Android behavior

- Android remains the host operating system. CyberOS can be selected as a home-screen app; it cannot replace Android's kernel, secure keyguard, or system UI without privileged firmware/root access.
- The shell enters directly after BIOS. An optional biometric-only shell gate can be enabled in System; the app has no PIN/password fallback.
- The desktop supports drag, resize, minimize, maximize, tiling, and restore. A two-finger upward swipe launches Firefox; a three-finger tap opens quick diagnostics.
- The vault encrypts app-private notes and imported images with a 256-bit Android Keystore AES-GCM key. Strong biometrics are required per vault operation.
- CyberScript files stay in app-private storage and execute only through the CyberOS command dispatcher. They cannot run arbitrary shell commands.
- Firefox launches natively when installed. Otherwise the Store can open Google Play or download the vendor APK and hand it to Android Package Installer. Android still requires the user to authorize unknown-source installation and confirm the install.
- The manifest contains the requested permission declarations, but declarations do not grant access. CyberOS actively requests permissions only for the corresponding features. Several listed permissions are signature/role-restricted, deprecated, or Play-policy-limited; they are not usable by an ordinary app merely because they appear in the manifest.
- Non-Firefox catalog entries are demo modules. `unlock 3344` reveals a hidden catalog entry; `sideload core` only stages a simulator state and does not install software.
- The shell opens after the BIOS sequence without an app PIN. An optional biometric-only shell gate can be enabled in System; Android device authentication remains under Android's control.
- Overlay, microphone, media, notification, and unknown-source install access are separately granted by the user through Android's runtime permission prompt or Settings.
- Installing Firefox uses Mozilla's Android download endpoint and hands the APK to Android Package Installer. Android requires the user to confirm installation; CyberOS does not silently install packages. The native Firefox app is opened with its launch intent when present; otherwise the Store offers a web fallback.
- Store entries other than Firefox are catalog/demo modules. `unlock 3344` reveals a hidden catalog entry; `sideload core` only stages a simulator state and does not install software.

## Permissions

The overlay service is user-started and foreground-notified. Media access is used for local images and videos; the system photo picker can set the shell background without broad media access. Notification, microphone, BLE scan, and biometric access are requested only when their features are used. SMS, call-log, contacts, account, accessibility, and signature-only permissions are not used to collect data or control the device.