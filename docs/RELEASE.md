# Releasing the AddressIQ Android SDK

This repo publishes one artifact and releases it through an automated,
tag-triggered pipeline. Read this before cutting a release — Maven Central is
**irreversible**.

## What ships

| | |
|---|---|
| Maven coordinate | `com.addressiqpro.android:sdk` |
| Central Portal namespace | `com.addressiqpro` (verified via `addressiqpro.com`) |
| Registries | **Maven Central** (via the Sonatype Central Portal) and GitHub Packages |
| Android/Kotlin package | `com.addressiq.android` — unrelated to the Maven group; do not confuse them |

The Maven `group` is `com.addressiqpro.android` (`build.gradle.kts:18`,
`build.gradle.kts:130`); `artifactId` is `sdk` (`build.gradle.kts:131`). The
publication includes sources and Javadoc jars (`build.gradle.kts:22-27`).

Current version: `0.2.0` (`version.txt:1`, `.release-please-manifest.json:2`).

## The release flow (do NOT tag by hand)

Releases are fully driven by [release-please](https://github.com/googleapis/release-please)
plus a tag-triggered publish workflow. You never create a `vX.Y.Z` tag manually.

1. **Land Conventional Commits on `main`** (`fix:`, `feat:`, `feat!:`, …). The
   commit types are what determine the next version and the changelog.
2. **release-please maintains a release PR.** `.github/workflows/release-please.yml`
   runs on every push to `main` and opens/updates a PR titled
   `chore: release X.Y.Z` (`release-please-config.json:5`).
3. **Merge that PR.** Merging bumps `version.txt`, updates
   `.release-please-manifest.json`, writes `CHANGELOG.md`, and **pushes tag
   `vX.Y.Z`**.
4. **The tag triggers the publish.** `.github/workflows/release.yml` fires on
   `push: tags: v*.*.*` (`release.yml:17-19`) and publishes.

### Why the tag actually fires a workflow

GitHub does not trigger workflows for events created with the default
`GITHUB_TOKEN` (loop prevention). So `release-please.yml` mints a **GitHub App
token** via `actions/create-github-app-token@v1` and hands it to release-please
(`release-please.yml:29-40`). An App-authored tag push *does* trigger
`release.yml`. This is why the App secrets below are mandatory — without them
the tag is pushed but nothing ever publishes.

### What the publish job does (`release.yml`)

- Sets up JDK 17 (temurin) and **Gradle 8.10** (`release.yml:41-47`).
- **Bakes the per-environment URLs into the source** — `scripts/bake-build-config.sh --strict`
  (`release.yml:49-57`), which rewrites
  `src/main/kotlin/com/addressiq/android/generated/AddressIQBuildConfig.kt` wholesale
  from the six repository variables below. `--strict` **hard-fails** on any unset
  variable (`bake-build-config.sh:62-66`). See [Build-time configuration](#build-time-configuration-the-six-repository-variables).
- On a tag push: `gradle publish -PVERSION="$V" --no-daemon`, where `$V` is the
  tag with the leading `v` stripped (`release.yml:61-64`). This uploads signed
  artifacts to GitHub Packages and to the Central Portal's OSSRH Staging API.
- **Hands the staged deployment to the Portal** with a
  `POST /manual/upload/defaultRepository/com.addressiqpro` call from the same
  job/IP that uploaded (`release.yml:75-95`). With plain `maven-publish`,
  uploading alone is **not** publishing — this step is load-bearing. It hard-fails
  on any non-2xx response.
- First release lands **pending** at <https://central.sonatype.com/publishing>
  and must be published manually. You can enable automatic publishing on the
  namespace afterward (see `SETUP-MAVEN-CENTRAL.md` §6).

## Required GitHub secrets

Set on the `addressiq-android` repo. Absent secrets are gated in
`build.gradle.kts`, so PR CI and local builds stay green — but a real release
needs all of these.

| Secret | What it is / where it comes from |
|---|---|
| `CENTRAL_TOKEN_USERNAME` | Central Portal **user token** (username half). Central Portal → avatar → View Account → Generate User Token. |
| `CENTRAL_TOKEN_PASSWORD` | Central Portal user token (password half), same source. Sent as a Bearer credential — not your login. |
| `SIGNING_KEY` | ASCII-armored GPG **private** key: `gpg --armor --export-secret-keys <KEYID>`. Consumed in-memory (`build.gradle.kts:214-221`). |
| `SIGNING_PASSWORD` | Passphrase for that GPG key (set at key generation). |
| `ADDRESSIQ_BOT_APP_ID` | GitHub App id for the org-owned release App (`contents: write`, `pull_requests: write`). Used by `release-please.yml:33`. |
| `ADDRESSIQ_BOT_PRIVATE_KEY` | Private key for that same GitHub App (`release-please.yml:34`). |

`GITHUB_TOKEN` is automatic (used for GitHub Packages, `release.yml:53`) — not a
secret you set.

The Central token and GPG signing enable the Maven Central path
(`build.gradle.kts:193-208`, `build.gradle.kts:214-221`); the two App secrets
enable the tag-triggered flow itself.

## Build-time configuration (the six repository variables)

The SDK ships no runtime URL knobs — integrators pick an `AddressIQEnvironment`
and the API, ingest, and CDN hosts resolve from it. Those hosts are **baked into
the source at publish time** from six GitHub repository **variables** (Settings →
Secrets and variables → Actions → *Variables*; these are not secrets):

| | Staging | Production |
|---|---|---|
| API | `STAGING_ADDRESSIQ_API_BASE_URL` | `PROD_ADDRESSIQ_API_BASE_URL` |
| Transit-event ingest | `STAGING_ADDRESSIQ_INGEST_BASE_URL` | `PROD_ADDRESSIQ_INGEST_BASE_URL` |
| CDN | `STAGING_ADDRESSIQ_CDN_BASE_URL` | `PROD_ADDRESSIQ_CDN_BASE_URL` |

These **replace** the old single-environment `ADDRESSIQ_API_URL` /
`ADDRESSIQ_INGEST_URL` pair, which were passed to Gradle as `-P` properties and
injected via AGP `buildConfigField`s. Config now lives in a generated Kotlin
object, `AddressIQBuildConfig` (`build.gradle.kts:42-49`) — named so it does not
collide with AGP's own `BuildConfig`.

`DEVELOPMENT` is deliberately **not** baked: it points at a backend on the host
machine (`http://10.0.2.2:4000`, the emulator's alias for your `localhost`), so
it stays a compile-time literal (`AddressIQ.kt:54`, `AddressIQ.kt:66`,
`AddressIQ.kt:79`). Never ship a build configured for `DEVELOPMENT`.

> **Behaviour change — a misconfigured release now FAILS instead of guessing.**
> The old release step passed each URL as a Gradle property and *silently* fell
> back to the checked-in default when the variable was unset — so a misconfigured
> release published an AAR pointing at whatever happened to be committed, with no
> signal. The bake now runs with `--strict`, which hard-fails the release if any
> of the six variables is missing. **All six must be set in GitHub before the next
> release.**

### Local vs CI

| | Command | Unset variable |
|---|---|---|
| **CI / release** (`release.yml:57`) | `scripts/bake-build-config.sh --strict` | **Hard error** — the release fails. |
| **Local** | `scripts/bake-build-config.sh` (or nothing at all) | Falls back to the checked-in safe public default. |

You normally never run the baker locally: the generated file is checked in and
already carries the public defaults, so `gradle build` and the test suite resolve
real hosts with no substitution. Run the non-strict baker only to regenerate that
file after changing a default. It rewrites the file wholesale — edit the script,
never the generated source.

## Migration notes

**`SANDBOX` → `STAGING`.** The environment was renamed. A deprecated
`@JvmField val SANDBOX = STAGING` companion keeps callers compiling
(`AddressIQ.kt:82-93`), and resolves identically.

> **Breaking for Java integrators.** A Kotlin enum cannot carry a deprecated
> alias *entry*, so `SANDBOX` is a **companion property, not an enum constant**.
> Java `switch` statements require enum constants as case labels, so
> `switch (env) { case SANDBOX: … }` **no longer compiles**. Java callers must
> switch on `STAGING`. Kotlin callers — including `when (env)` — are unaffected,
> as are Java callers that merely *reference* `AddressIQEnvironment.SANDBOX` as a
> value. Call this out in the release notes.

## Versioning rules

release-please uses `release-type: simple` with `bump-minor-pre-major: true`
(`release-please-config.json:8-11`). While the SDK is pre-1.0:

| Commit | Bump |
|---|---|
| `fix:` | patch (`0.2.0` → `0.2.1`) |
| `feat:` | minor (`0.2.0` → `0.3.0`) |
| `feat!:` / `BREAKING CHANGE` | **minor** while pre-1.0 (a breaking change does *not* jump to 1.0.0 until you intend it) |

Tags are plain `vX.Y.Z` — `include-component-in-tag: false`
(`release-please-config.json:3`) — matching the `v*.*.*` trigger in `release.yml`.

> **Maven Central releases are irreversible.** A published version of
> `com.addressiqpro.android:sdk` can never be deleted or overwritten — only
> superseded by a higher version. From the moment you click Publish, it is
> permanent.

## Local dry-run

There is **no Gradle wrapper** in this repo. CI pins Gradle **8.10**
(`release.yml:47`); use a matching local Gradle.

```bash
# Assemble + sign + install to your local ~/.m2 — no upload:
gradle publishToMavenLocal -PVERSION="0.0.0-SNAPSHOT"
```

Without `-PVERSION`, the build falls back to `0.0.0-SNAPSHOT`
(`build.gradle.kts:14`). You can also run the CI dry-run
(`workflow_dispatch` with `dry_run=true`), which executes
`publishToMavenLocal` on the runner (`release.yml:66`):

```bash
gh workflow run release.yml --repo PTLRepoHub/addressiq-android -f dry_run=true
```

A green dry-run confirms the GPG key signs and the artifacts assemble before you
touch the irreversible registry.

## One-time setup

Namespace claim (DNS TXT on `addressiqpro.com`), Central Portal user token, and
GPG key creation are documented step-by-step in
[`../../SETUP-MAVEN-CENTRAL.md`](../../SETUP-MAVEN-CENTRAL.md). The GitHub App
provisioning is covered in the repo's release-engineering notes
(`SETUP-GITHUB-APP.md`). The build and workflows are already wired — only these
credentials need provisioning.
