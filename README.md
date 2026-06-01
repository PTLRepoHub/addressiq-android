# AddressIQ — Android SDK

[![CI](https://github.com/PTLRepoHub/addressiq-android/actions/workflows/ci.yml/badge.svg)](https://github.com/PTLRepoHub/addressiq-android/actions/workflows/ci.yml)

`com.addressiq.android:sdk` is the native Android SDK (Kotlin, with a Java
interop bridge) — address collect + verify lifecycle, background location,
and a Jetpack Compose drop-in verify activity.

## Repository layout

```
.                ← the SDK (com.addressiq.android:sdk): src/main, src/test
  example/       runnable Compose app, linked to the LOCAL SDK (composite build)
```

## Install (consumers)

```kotlin
dependencies {
    implementation("com.addressiq.android:sdk:0.1.0")
}
```

Resolved from **Maven Central**. (Also published to GitHub Packages — that repo
requires a token even when public.)

## Develop

```bash
gradle wrapper       # once, to generate ./gradlew (or use Android Studio)
./gradlew test       # JVM unit tests (smoke test)
```

Requires JDK 17 + the Android SDK (API 36).

## Example against your local SDK

```bash
cd example
gradle assembleDebug   # or installDebug onto a device
```

`example/settings.gradle.kts` uses `includeBuild("..")` with dependency
substitution, so the app builds against this repo's SDK source (no publish step).

## Release

Push a semver tag (`.github/workflows/release.yml`):

```bash
git tag v0.1.0 && git push origin v0.1.0
```

Publishes to GitHub Packages (automatic `GITHUB_TOKEN`) and Maven Central
(needs `OSSRH_*` + GPG `SIGNING_*` secrets). Run manually for a
`publishToMavenLocal` dry-run.

## Contributing

Fork, branch, PR. CI runs the SDK unit tests and assembles the example against
the local SDK on every push/PR.
