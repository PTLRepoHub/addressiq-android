# AddressIQ — Android SDK

[![CI](https://github.com/PTLRepoHub/addressiq-android/actions/workflows/ci.yml/badge.svg)](https://github.com/PTLRepoHub/addressiq-android/actions/workflows/ci.yml)

`com.addressiq.android:sdk` is the native Android SDK (Kotlin, with a Java
interop bridge) — address collect + verify lifecycle, background location,
and a Jetpack Compose drop-in verify activity.

It mirrors the cross-SDK contract shared with the React Native, iOS, and
Flutter SDKs: same method surface, same public code shapes
(`verificationCode` / `locationCode`), same permission-gating rules.

## Repository layout

```
.                  ← the SDK (com.addressiq.android:sdk): src/main, src/test
  examples/kotlin/ Jetpack Compose app (Kotlin API)
  examples/java/   View-based app using the Java bridge (AddressIQJava)
```

Both examples link to the LOCAL SDK via a composite build
(`includeBuild("../..")` + dependency substitution).

## Install (Gradle)

```kotlin
dependencies {
    implementation("com.addressiq.android:sdk:0.1.0")
}
```

Resolved from **Maven Central**. (Also published to GitHub Packages — that repo
requires a token even when public.) Requires JDK 17 + the Android SDK (API 36),
`minSdk` 24.

## Quick start

```kotlin
import com.addressiq.android.AddressIQ
import com.addressiq.android.AddressIQConfig
import com.addressiq.android.AddressIQDeployment
import com.addressiq.android.SdkUser

// 1. Initialise once at app start.
AddressIQ.initialize(
    AddressIQConfig(apiKey = "aiq_live_…", deployment = AddressIQDeployment.PRODUCTION),
)

// 2. Bind the end user.
AddressIQ.setUser(SdkUser(appUserId = customer.id))

// 3. Make sure location is granted (the SDK gates start* on this — see Permissions).
AddressIQ.requestPermissions(activity)

// 4. Start a digital verification. `context` lets the SDK gate permissions
//    and light up geofence + telemetry collection after the call succeeds.
val result = AddressIQ.startVerification(context = this, locationCode = "loc_abc")
val verificationCode = result["verificationCode"] as? String   // "ver_…"
val status = result["status"] as? String                       // "PENDING"
```

## Collect UI (drop-in widget)

`AddressIQVerifyActivity` is a themed Compose flow (permission → address →
property details → consent → submit). Launch it through the public
`AddressIQVerifyContract` with `registerForActivityResult` — never
`startActivity()` it directly. The contract builds the intent and parses the
typed result.

```kotlin
import com.addressiq.android.ui.AddressIQVerifyContract
import com.addressiq.android.ui.AddressIQVerifyInput
import com.addressiq.android.ui.AddressIQVerifyResult

class MyActivity : ComponentActivity() {
    private val verify = registerForActivityResult(AddressIQVerifyContract()) { result ->
        when (result) {
            is AddressIQVerifyResult.Completed ->
                navigateToSuccess(result.verificationCode, result.locationCode, result.status)
            is AddressIQVerifyResult.Cancelled -> { /* user dismissed */ }
            is AddressIQVerifyResult.Failed    -> showError(result.code, result.message)
        }
    }

    fun launch() = verify.launch(
        AddressIQVerifyInput(
            apiKey = "aiq_live_…",
            appUserId = customer.id,
            deployment = AddressIQDeployment.PRODUCTION,
        ),
    )
}
```

**Result codes.** `AddressIQVerifyResult.Completed` carries the public
`verificationCode` (`ver_…`), `locationCode` (`loc_…`), and `status`
(`PENDING`). On a successful submit the widget also lights up OS-level
collection (geofence registration + telemetry flush) using those codes — the
same wiring the imperative `start*` calls perform.

## SDK API (Kotlin)

All async methods are `suspend`; lifecycle is observable via
`AddressIQ.stateFlow: StateFlow<VerificationLifecycleState>`.

| Method | Purpose |
| --- | --- |
| `initialize(config)` | Configure the SDK (synchronous). |
| `setUser(user)` | Bind the end user. |
| `getPermissionState(context)` | Read-only permission snapshot. |
| `requestPermissions(activity)` | Drive the OS permission prompts. |
| `startVerification(context, locationCode, digitalProvider?, idempotencyKey?, branchId?)` | Start a **digital** verification (`internal_ai` default). |
| `startPhysicalVerification(context, locationCode, provider, …)` | Start a physical verification. |
| `startDigitalAndPhysicalVerification(context, locationCode, physicalProvider, …)` | Start a combined verification. |
| `cancelVerification(verificationCode, idempotencyKey?)` | Cancel an active verification. |
| `listProviders(type?)` | List configured providers. |
| `pauseVerification()` / `resumeVerification()` | Pause / resume collection. |
| `sync()` | Force-flush buffered telemetry. |
| `logout()` / `reset()` | Tear down session / SDK state. |

`startVerification` hits
`POST /api/v1/locations/{locationCode}/verifications/digital` with body
`{"digitalProvider": "internal_ai"}` (overridable), headers `x-api-key` +
`idempotency-key: iqidem_android_<token>`, and returns a map carrying
`verificationCode`, `locationCode`, and `status`. Each `start*` call requires
a `Context` so the SDK can gate on permissions and activate collection.

### Java façade

Java callers use the static `AddressIQJava` bridge — every Kotlin public
method is mirrored, with `suspend` functions surfaced as
`CompletableFuture` (API 24+) and synchronous reads as plain returns.

```java
AddressIQJava.initialize(
    new AddressIQConfig("aiq_live_…", AddressIQDeployment.PRODUCTION, null));

AddressIQJava.setUser(new SdkUser("cust_01J9P7XK", null, null, null, null))
    .thenCompose(ignored ->
        // digital verification — context gates permissions + collection
        AddressIQJava.startVerification(context, "loc_abc", null, null, null))
    .thenAccept(result -> Log.i("AddressIQ", "Started: " + result.get("verificationCode")))
    .exceptionally(throwable -> {
        // PERMISSION_DENIED surfaces here when location grants are missing
        Log.e("AddressIQ", "Failed", throwable);
        return null;
    });
```

Mirrored start* signatures:

- `startVerification(Context, String locationCode, String digitalProvider, String idempotencyKey, String branchId)`
- `startPhysicalVerification(Context, String locationCode, String provider, String agentId, Integer slaHours, String idempotencyKey, String branchId)`
- `startDigitalAndPhysicalVerification(Context, String locationCode, String physicalProvider, …)`

`@JvmOverloads` makes the trailing nullable args optional from Java. Errors
(including `AddressIQError.PermissionDenied`) surface through the future's
exceptional-completion path (`exceptionally` / `whenComplete`).

## Permissions

The SDK owns every step *after* the host app decides to start verification
(cross-SDK §0). It gates each `start*` call on `getPermissionState(context)`:
if `foregroundLocation` or `backgroundLocation` is not `GRANTED`, the call
throws `AddressIQError.PermissionDenied` (code string `"PERMISSION_DENIED"`)
before any REST call is made. Request the grants first:

```kotlin
AddressIQ.requestPermissions(activity)   // foreground → notifications → background
```

Plain-`Activity` (non-AndroidX) hosts forward their
`onRequestPermissionsResult` to `AddressIQ.handlePermissionResult(...)`.

## Example app

Prerequisites: **JDK 17** + the **Android SDK (API 36)**. The example modules
ship no Gradle wrapper, so either open the folder in **Android Studio** (it
provisions one) or use a host `gradle` to generate one first:

```bash
cd examples/kotlin        # or examples/java (Java / AddressIQJava bridge)
gradle wrapper            # one-time — generates ./gradlew (skip in Android Studio)

./gradlew assembleDebug   # build the APK against the local SDK
./gradlew installDebug    # install onto a running emulator / connected device
```

Start an emulator first for `installDebug` (`emulator -avd <name>` or via
Android Studio's Device Manager). The example's `apiKey` is hardcoded to the
seed key `aiq_test_demo_bank_seed01` (editable on-screen) and defaults to the
`STAGING` deployment — no credentials file needed.

The Kotlin example exercises the imperative API (digital + physical start),
the lifecycle controls, and a **Launch Collect UI** button that drives
`AddressIQVerifyContract` via `registerForActivityResult` and renders the
returned `verificationCode` / `locationCode` / `status`. Each example's
`settings.gradle.kts` uses `includeBuild("../..")` with dependency
substitution, so the app builds against this repo's SDK source (no publish
step).

## Deployment vs sandbox — two different things

These are orthogonal, and conflating them is the most common integration mistake:

| | What it selects | How you set it |
|---|---|---|
| **Deployment** | Which AddressIQ **hosts** you talk to | `AddressIQConfig.deployment` |
| **Tenant mode** | Whether your data is **sandbox or production** | **Which API key you paste** |

`AddressIQDeployment` selects the backend — integrators just choose one; the
API, transit-event ingest, and CDN hosts are resolved entirely from it, so you
never pass a URL:

- `PRODUCTION` — the hosted AddressIQ platform.
- `STAGING` — the staging platform.
- `DEVELOPMENT` — a backend running on your host machine, reachable from the
  Android emulator; use this only for local development, never in a shipped app.

**`SANDBOX` is gone.** It existed as a companion `@JvmField val SANDBOX = STAGING`,
which asserted that sandbox was a deployment — it is not. Sandbox-vs-production is
a property of your **API key**: `aiq_test_…` resolves to a sandbox tenant
server-side, `aiq_live_…` to a production one. The SDK never sends a mode and
cannot override the key's. `AddressIQDeployment.valueOf("SANDBOX")` now throws.

The two combine freely: an `aiq_test_…` key on `PRODUCTION` is still sandbox data;
an `aiq_live_…` key on `STAGING` is still production-mode data.

> **Migrating from `environment`?** `environment = AddressIQEnvironment.SANDBOX` →
> drop it and use a sandbox key (`aiq_test_…`), which is almost certainly what you
> meant. Use `deployment = AddressIQDeployment.STAGING` only if you specifically
> wanted the pre-production *hosts*. Java callers who wrote
> `switch (env) { case SANDBOX: … }` already had to migrate (a companion property
> was never a valid case label); now the reference itself no longer resolves.

The `PRODUCTION` and `STAGING` URLs are baked into the published AAR at release
time by `scripts/bake-build-config.sh --strict`, from the `STAGING_*` / `PROD_*`
GitHub repository variables; the checked-in generated source
(`src/main/kotlin/com/addressiq/android/generated/AddressIQBuildConfig.kt`)
carries the public defaults for local builds. `DEVELOPMENT` is never baked.

`AddressIQConfig.resolvedCdnUrl` exposes the per-environment CDN host, and the
verify WebView **does** load the widget from it — under a Subresource-Integrity
pin. `AddressIQWebFlowScreen.kt:288-321` resolves the widget source in order:

1. **`widgetUrl`** — explicit developer override, wins over everything.
2. **Pinned CDN build** — `{cdn}/v{widgetVersion}/iqcollect.js`
   (`cdnWidgetUrl`, `AddressIQWebFlowScreen.kt:222-232`) loaded with
   `integrity="{widgetIntegrity}" crossorigin="anonymous"`
   (`AddressIQWebFlowScreen.kt:313-315`). Chromium **enforces** `integrity`, so
   the CDN can only execute the exact bytes hashed at build time. The
   version/hash pair is baked into `AddressIQBuildConfig` from the repo-root
   `.widget-version` / `.widget-integrity` files, which addressiq-web's release
   fanout writes from the same build the CDN serves; the CDN publishes immutable
   `/v{x.y.z}/` paths (no floating alias) because a mutable URL cannot be pinned.
3. **Bundled asset** (`src/main/assets/iqcollect.js`) — the *fallback*, injected
   by `onerror="__iqWidgetFallback()"`, covering a CDN outage, an offline device
   **and** an SRI mismatch. It is also the sole source when the CDN path is off:
   `DEVELOPMENT` never uses the CDN, and an unbaked version or integrity
   disables it (`AddressIQWebFlowScreen.kt:228-229`).

With neither a pinned CDN build nor the bundled asset the SDK still **fails
closed** (`AddressIQWebFlowScreen.kt:318-321`); an unpinned remote script is
never loaded.

Three details in that markup are load-bearing — each fails *silently* toward
"looks fine, but never actually uses the CDN":

- `crossorigin="anonymous"` is **mandatory**: without it the cross-origin
  response is opaque, `integrity` cannot be evaluated, and every load hard-fails
  into the fallback.
- **Script order**: a blocking classic `<script>` fires `onerror` before the
  parser reaches the next inline script, so `__iqWidgetFallback()` is defined
  *before* the remote tag (which carries no `defer`/`async`).
- The inlined fallback bundle is **escaped** — it contains `</script>`-alike
  sequences that would otherwise terminate the tag.

## Errors

`AddressIQError` (sealed):

- `NotInitialized` — `initialize` was not called first.
- `NoActiveSession` — resume with no active session.
- `PermissionDenied` — location grants missing at `start*` time (code `"PERMISSION_DENIED"`).
- `Http(status, code, msg)` — non-2xx API response.

## Develop

```bash
gradle wrapper       # once, to generate ./gradlew (or use Android Studio)
./gradlew test       # JVM unit tests (smoke test)
```

Requires JDK 17 + the Android SDK (API 36).

## Release

Push a semver tag (`.github/workflows/release.yml`):

```bash
git tag v0.1.0 && git push origin v0.1.0
```

Publishes to GitHub Packages (automatic `GITHUB_TOKEN`) and Maven Central. Run
manually for a `publishToMavenLocal` dry-run.

The workflow first bakes the per-environment URLs into the source with
`scripts/bake-build-config.sh --strict` (`release.yml:49-57`), which **fails the
release** if any of the six `STAGING_*` / `PROD_*` GitHub repository variables is
unset — it will not fall back to the checked-in defaults.

See [`docs/RELEASE.md`](docs/RELEASE.md) for the full flow, the required secrets
and repository variables, and the versioning rules.

## Cross-links

- Cross-SDK contract: [`../../geo-tagging/docs/sdk-contract.md`](../../geo-tagging/docs/sdk-contract.md)
- Android SDK guide: [`../../geo-tagging/apps/docs/docs/sdk/android.md`](../../geo-tagging/apps/docs/docs/sdk/android.md)
- Android Java bridge guide: [`../../geo-tagging/apps/docs/docs/sdk/android-java.md`](../../geo-tagging/apps/docs/docs/sdk/android-java.md)

## Contributing

Fork, branch, PR. CI runs the SDK unit tests and assembles both examples against
the local SDK on every push/PR.

## Running the SDK locally, end to end

Everything below is **development-only**. Every override is honoured solely under
the `development` deployment and **throws** on a staging or production build, even
if the variable is set — a build-time value must never be able to point a shipped
app at an arbitrary host.

### 1. Start the backend

```sh
cd addressiq-node-backend
cp .env.example .env          # set GOOGLE_MAPS_API_KEY if you want the map to load
npm install && npm start      # http://localhost:4000
```

It must bind `0.0.0.0`, not `127.0.0.1`, or nothing off-machine can reach it.

### 2. (Optional) Serve the widget yourself

Only needed if you are **changing the widget**. Otherwise the SDK uses the widget
it already ships.

```sh
cd addressiq-web
npx rollup -c                 # → dist/iqcollect.js
npx serve dist -p 5173
```

Then set `ADDRESSIQ_DEV_WIDGET_URL` to `http://<host>:5173/iqcollect.js` for live
reload without re-vendoring. Point it at a **published** URL
(`https://cdn.addressiqpro.com/v0.5.3/iqcollect.js`) instead to exercise the
remote-load + SRI + `onerror`-fallback paths, which `development` otherwise never
takes because it inlines the bundled asset.

A `file://` path will **not** work: the Android emulator is a separate VM and
cannot see your filesystem, and a physical device certainly cannot. It has to be
served over HTTP.

### 3. Point the SDK at your machine

```sh
cp .env.example .env
```

**Which host do I use?**

| Running on | Host |
|---|---|
| Android emulator | `10.0.2.2` — a special alias for your machine's localhost |
| iOS simulator | `localhost` — it shares your Mac's network |
| **Physical device (either OS)** | your **LAN IP** — `ipconfig getifaddr en0` |

The default is the emulator/simulator literal, which is exactly why these
overrides exist: **a physical device cannot reach `10.0.2.2` or `localhost`.**

Then supply the values — either append them to the gitignored `local.properties`, or export them:

```sh
set -a; source .env; set +a
./gradlew installDebug        # or run from Android Studio
```

### 4. Android only: allow plain HTTP

A LAN IP over plain `http://` is blocked by default. In your **debug** manifest:

```xml
<application android:usesCleartextTraffic="true" …>
```

Debug only — never in a release. (A `network_security_config` scoped to that one
host is the tighter version.)

### Troubleshooting

- **Requests hang / connection refused on a real device** — the backend is bound to
  `127.0.0.1`. Bind `0.0.0.0`.
- **Works on the emulator, fails on a device** — you are still on `10.0.2.2`. Set a
  LAN IP.
- **Android: `net::ERR_CLEARTEXT_NOT_PERMITTED`** — step 4.
- **The map is blank** — your backend has no Maps key. `GET /api/v1/widget/config`
  supplies it; set `GOOGLE_MAPS_API_KEY` in the backend's `.env`. (The key is
  platform-provisioned; no native SDK accepts one, because the key is used by the
  widget, not by native code.)
- **An override "does nothing"** — check `deployment` is `development`. Anywhere
  else it throws rather than being silently ignored, so you would have seen an error.
