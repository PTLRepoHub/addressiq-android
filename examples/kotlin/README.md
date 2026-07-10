# AddressIQ Android — Sample App (Kotlin)

A small Jetpack Compose app that shows the AddressIQ SDK end to end: log in, open
the address widget, and start a verification.

The UI is the shared AddressIQ web widget hosted in a `WebView`. This app supplies
the native pieces (location permission, the API config) and reads back the result.

It links the local SDK via a Gradle composite build (`includeBuild("../..")`), so
your SDK changes show up here after a rebuild.

## Prerequisites

- **JDK 17** (`java -version` → 17.x)
- **Android SDK, API 36** installed, with `ANDROID_HOME` set (e.g.
  `export ANDROID_HOME="$HOME/Library/Android/sdk"`)
- A running **emulator** or a connected device (see below)

## Run it

The easiest path is **Android Studio**: open `addressiq-android/examples/kotlin`
and press **Run** — it provisions the Gradle wrapper and picks a device for you.

From the command line, this example module **ships no Gradle wrapper**, so
generate one once with a host `gradle`, then use `./gradlew`:

```bash
cd addressiq-android/examples/kotlin

# 1. One-time: create the wrapper (skip if it already exists / in Android Studio)
gradle wrapper --gradle-version 8.14.3

# 2. Start an emulator (or plug in a device), then wait for it to boot
emulator -avd <your_avd> &          # e.g. `emulator -list-avds` to see names
adb wait-for-device

# 3. Build + install against the local SDK, then launch
./gradlew installDebug
adb shell monkey -p com.addressiq.example -c android.intent.category.LAUNCHER 1
```

`installDebug` compiles the SDK too (composite build), so your SDK edits show up
after a rebuild. The app's `apiKey` defaults to the seed key
`aiq_test_demo_bank_seed01` in the `SANDBOX` environment — editable on-screen, no
credentials file needed.

On the login screen you set:
- **API key** and **App user ID** — your test credentials.
- **Environment** — Sandbox or Production (the hosted APIs).
- **Local API URL** *(optional)* — point at a local backend instead (see below).
- **Business name** — a fallback only; the widget normally gets the business
  name/logo/colour from the backend.

The map needs a Google Maps key. This example reads it from the
`GOOGLE_MAPS_API_KEY` environment variable at build time:

```bash
GOOGLE_MAPS_API_KEY=your-key ../gradlew installDebug
```

## Run against the local backend

The `addressiq-node-backend` package is the AddressIQ **Node server SDK**, with a
sample `server.js` you can run as your server. It talks to the real AddressIQ API
(the `geo-tagging` app on `http://localhost:4000`) — or serves fake data offline.

1. Start the sample server on your machine:
   ```bash
   cd addressiq-node-backend
   node server.js                 # real: forwards to your local AddressIQ API
   #   …or for offline fake data:
   MOCK_UPSTREAM=1 node server.js
   ```
   It listens on `http://localhost:3355`.
2. In the app's login screen, set **Local API URL** to **`http://10.0.2.2:3355`**.

> On the Android emulator, `10.0.2.2` is a special alias for your computer's
> `localhost` — the emulator can't use `localhost` directly (that points at the
> emulator itself). On a real device, use your computer's LAN IP. Plain-HTTP URLs
> also need a cleartext-traffic allowance in the app's debug manifest.

## What the buttons do

- **Collect Address** — opens the widget (intro → business consent → verify where
  you live → pick a saved address or add a new one). On success the app starts a
  verification and shows the result.
- **Addresses / Developer / Settings** — inspect collected codes, call the raw
  verification APIs, and manage the session.

## Troubleshooting

- **"Android App Compatibility / 16 KB page size" dialog on launch** — a benign
  OS notice (some bundled native libs aren't 16 KB-aligned). The app runs in
  page-size compatibility mode; tap **OK** / **Don't Show Again**.
- **Collect widget shows a black screen** — the widget loads its branding/config
  from the backend on open. On an emulator that can't reach the hosted API
  (`net::ERR … -201` TLS failures in `adb logcat`), it won't paint. Fixes:
  run against a reachable backend via **Local API URL** (see above), use a device
  with working network, or point at production with a valid key.
- **`gradle: command not found`** — install Gradle (`brew install gradle`) just to
  generate the wrapper once, or open the module in Android Studio which bundles
  its own Gradle.
- **Map step falls back to manual entry** — no Google Maps key. Pass one at build
  time: `GOOGLE_MAPS_API_KEY=your-key ./gradlew installDebug`.
