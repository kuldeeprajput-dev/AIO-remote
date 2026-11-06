# AIO Remote

An all-in-one remote for computers and compatible TVs. Use your phone as a touchpad, keyboard, media controller, and presentation remote. For computer connections over Wi-Fi or USB tethering, run the desktop receiver.

## Project layout

- `app/src/main/java/com/example/ui` contains the Compose screens, dialogs, and theme.
- `app/src/main/java/com/example/connection` contains receiver discovery, transports, and the Wi-Fi/USB protocol.
- `app/src/main/java/com/example/input` and `gestures` define remote commands and translate touchpad gestures into input.
- `app/src/test` and `app/src/androidTest` contain Android tests.
- `receiver` contains the Python receiver and its [setup instructions](receiver/README.md).

## Build the Android app

Use JDK 21 and the Android SDK with platform 36.1 installed. Set `ANDROID_HOME` or configure the SDK in Android Studio.

```sh
./gradlew :app:assembleDebug
```

Gradle writes the APK to `app/build/outputs/apk/debug/app-debug.apk`.
