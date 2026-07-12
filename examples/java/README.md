# AddressIQ Android — Sample App (Java)

A View-based app that exercises the AddressIQ SDK through its **Java bridge**,
`AddressIQJava` — static methods that return
`java.util.concurrent.CompletableFuture` instead of Kotlin coroutines, so the SDK
is usable from plain Java without a Kotlin toolchain.

It's the Java counterpart of the **[Kotlin example](../kotlin)** and exercises the
same SDK surface: login, the Collect UI, digital / physical / combined
verification, permissions, and lifecycle. The Kotlin app uses Jetpack Compose;
this one builds its UI programmatically so the whole parity surface stays in one
readable Java file.

It links the local SDK via a Gradle composite build (`includeBuild("../..")`), so
your SDK changes show up here after a rebuild.

## Prerequisites

- **JDK 17** (`java -version` → 17.x)
- **Android SDK, API 36** installed, with `ANDROID_HOME` set (e.g.
  `export ANDROID_HOME="$HOME/Library/Android/sdk"`)
- A running **emulator** or a connected device (see below)

## Run it

The easiest path is **Android Studio**: open `addressiq-android/examples/java` and
press **Run** — it provisions the Gradle wrapper and picks a device for you.

From the command line, this example module **ships no Gradle wrapper**, so generate
one once with a host `gradle`, then use `./gradlew`:

```bash
cd addressiq-android/examples/java

# 1. One-time: create the wrapper (skip if it already exists / in Android Studio)
gradle wrapper --gradle-version 8.14.3

# 2. Start an emulator (or plug in a device), then wait for it to boot
emulator -avd <your_avd> &          # `emulator -list-avds` to see names
adb wait-for-device

# 3. Build + install against the local SDK, then launch
./gradlew installDebug
adb shell monkey -p com.addressiq.example.java -c android.intent.category.LAUNCHER 1
```

`installDebug` compiles the SDK too (composite build), so your SDK edits show up
after a rebuild.

## What it does

One scrollable screen grouped into sections, with a **Log** pane at the bottom
(newest first) showing each call's result. It drives both integration tracks:

- **Login** — editable API key (seed `aiq_test_demo_bank_seed01`), app user ID,
  business name, and an environment toggle that cycles
  **Staging → Production → Development** (`MainActivity.java:183-194`). There is
  no user-facing URL field: the hosts resolve from the environment, and
  `DEVELOPMENT` targets a local backend at `http://10.0.2.2:4000`.
  **Continue** runs `AddressIQJava.initialize(...)` then `setUser(...)` → the log
  shows `initialized (STAGING)` and `user set; lifecycle: IDLE`.
- **Collect** (Track A) — **Collect Address** launches the Collect UI via
  `AddressIQVerifyContract` (`registerForActivityResult`); on `Completed` it
  remembers the `locationCode` and starts a digital verification.
- **Verify** (Track B) — **Digital / Physical / Combined** call
  `startVerification` / `startPhysicalVerification` /
  `startDigitalAndPhysicalVerification` on the working `locationCode`.
- **Permissions** — `getPermissionState` and `requestPermissions`.
- **Developer** — `getVerificationState`, `cancelVerification`, `listProviders`.
- **Settings** — `logout`, `reset`.

Every async call goes through the `CompletableFuture` bridge; results are
marshalled back to the UI thread before logging. Example (the login path):

```java
AddressIQJava.initialize(AddressIQJava.config()
    .apiKey(apiKey).environment(environment).build());

AddressIQJava.setUser(AddressIQJava.user().appUserId(appUserId).firstName("Sample").build())
    .whenComplete((unused, err) -> runOnUiThread(() -> {
        if (err != null) log("setUser error: " + err.getMessage());
        else log("user set; lifecycle: " + AddressIQJava.getVerificationState().getState());
    }));
```

This mirrors the Kotlin example's `SampleViewModel`, proving the same SDK surface
works from plain Java (builders, `SdkUser`, `CompletableFuture` results, the
Collect contract, verification, permissions, and lifecycle).

> **Layout note:** the root layout sets `android:fitsSystemWindows="true"`. Apps
> targeting `targetSdk 35+` are drawn edge-to-edge by default, so without this the
> title and button render *underneath* the status/action bar (and the button
> becomes untappable). Keep this on any full-screen layout you add here.

## Troubleshooting

- **`setUser error: …`** — `setUser` calls the backend. On an emulator that can't
  reach the hosted staging API (or with an invalid key), it fails here. Use a
  device/emulator with working network, or point at a reachable backend.
- **`gradle: command not found`** — install Gradle (`brew install gradle`) just to
  generate the wrapper once, or open the module in Android Studio which bundles its
  own Gradle.
- **"Android App Compatibility / 16 KB page size" dialog on launch** — a benign OS
  notice (some bundled native libs aren't 16 KB-aligned). Tap **OK**.
