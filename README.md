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
import com.addressiq.android.AddressIQEnvironment
import com.addressiq.android.SdkUser

// 1. Initialise once at app start.
AddressIQ.initialize(
    AddressIQConfig(apiKey = "aiq_live_…", environment = AddressIQEnvironment.PRODUCTION),
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
            environment = AddressIQEnvironment.PRODUCTION,
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
    new AddressIQConfig("aiq_live_…", AddressIQEnvironment.PRODUCTION, null));

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

```bash
cd examples/kotlin && gradle assembleDebug   # Kotlin / Compose
cd examples/java   && gradle assembleDebug   # Java (AddressIQJava bridge)
# or installDebug onto a connected device/emulator
```

The Kotlin example exercises the imperative API (digital + physical start),
the lifecycle controls, and a **Launch Collect UI** button that drives
`AddressIQVerifyContract` via `registerForActivityResult` and renders the
returned `verificationCode` / `locationCode` / `status`. Each example's
`settings.gradle.kts` uses `includeBuild("../..")` with dependency
substitution, so the app builds against this repo's SDK source (no publish
step).

## Environment

`AddressIQEnvironment` resolves the API base URL:

- `PRODUCTION` → `https://api.addressiqpro.com`
- `SANDBOX` → `https://api-staging.addressiqpro.com`

Override via `AddressIQConfig.apiUrl` only when routing through a partner
proxy or a hermetic test backend.

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

Publishes to GitHub Packages (automatic `GITHUB_TOKEN`) and Maven Central
(needs `OSSRH_*` + GPG `SIGNING_*` secrets). Run manually for a
`publishToMavenLocal` dry-run.

## Cross-links

- Cross-SDK contract: [`../../geo-tagging/docs/sdk-contract.md`](../../geo-tagging/docs/sdk-contract.md)
- Android SDK guide: [`../../geo-tagging/apps/docs/docs/sdk/android.md`](../../geo-tagging/apps/docs/docs/sdk/android.md)
- Android Java bridge guide: [`../../geo-tagging/apps/docs/docs/sdk/android-java.md`](../../geo-tagging/apps/docs/docs/sdk/android-java.md)

## Contributing

Fork, branch, PR. CI runs the SDK unit tests and assembles both examples against
the local SDK on every push/PR.
