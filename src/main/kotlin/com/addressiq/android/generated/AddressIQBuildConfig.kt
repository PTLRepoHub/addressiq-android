// Generated build-time configuration — DO NOT EDIT BY HAND.
//
// Rewritten wholesale by `scripts/bake-build-config.sh` at publish time from
// the GitHub repository variables (see .github/workflows/release.yml):
//
//   STAGING_ADDRESSIQ_API_BASE_URL      PROD_ADDRESSIQ_API_BASE_URL
//   STAGING_ADDRESSIQ_INGEST_BASE_URL   PROD_ADDRESSIQ_INGEST_BASE_URL
//   STAGING_ADDRESSIQ_CDN_BASE_URL      PROD_ADDRESSIQ_CDN_BASE_URL
//
// The checked-in values below are the safe public defaults, so a local
// `./gradlew build` and the test suite resolve real hosts with no
// substitution. On a real release the baker runs with --strict and REQUIRES
// every variable above — a published AAR must never silently carry a
// developer's default.
//
// `widgetVersion` / `widgetIntegrity` are NOT environment variables. They are
// read from two FILES at the repo root, `.widget-version` (e.g. "v0.4.0") and
// `.widget-integrity` (e.g. "sha384-…"), which addressiq-web's widget-fanout
// workflow rewrites in the same PR that re-vendors src/main/assets/iqcollect.js.
// They therefore describe exactly the bytes vendored into this repo. The verify
// WebView loads `{cdnUrl}/v{widgetVersion}/iqcollect.js` with that SRI hash
// pinned, falling back to the bundled asset. Either file being absent/empty
// bakes "" here, which disables the CDN path entirely (bundled-only) — that is
// the state before the first fanout PR lands.
//
// `DEVELOPMENT` is deliberately NOT baked from CI: it points at the host
// machine's backend (reached from the emulator via 10.0.2.2), so it is a
// local-only concern and stays a compile-time literal in AddressIQEnvironment.
// Never ship a build configured for `DEVELOPMENT`.
//
// Named AddressIQBuildConfig rather than BuildConfig so it does not collide
// with the Android Gradle plugin's own generated `com.addressiq.android.BuildConfig`.
package com.addressiq.android.generated

internal object AddressIQBuildConfig {
    const val stagingApiUrl = "https://api-staging.addressiqpro.com"
    const val stagingIngestUrl = "https://ingest-api-staging.addressiqpro.com"
    const val stagingCdnUrl = "https://cdn-staging.addressiqpro.com"

    const val prodApiUrl = "https://api.addressiqpro.com"
    const val prodIngestUrl = "https://ingest-api.addressiqpro.com"
    const val prodCdnUrl = "https://cdn.addressiqpro.com"

    /** Vendored widget version, no leading "v" (from `.widget-version`). "" = unknown. */
    const val widgetVersion = "0.5.3"

    /** SRI hash of the vendored widget (from `.widget-integrity`). "" = unknown. */
    const val widgetIntegrity = "sha384-wUErWmll1WWgesjXvSN93KLxHTDLNXdZ4FMR9nT2tQ7tpdBdEuQCDMkHgdssRvkb"
}
