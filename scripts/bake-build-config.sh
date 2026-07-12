#!/usr/bin/env bash
# Regenerates src/main/kotlin/com/addressiq/android/generated/AddressIQBuildConfig.kt
# from the environment.
#
# Reads six GitHub repository variables — three per shippable environment:
#
#   STAGING_ADDRESSIQ_API_BASE_URL      PROD_ADDRESSIQ_API_BASE_URL
#   STAGING_ADDRESSIQ_INGEST_BASE_URL   PROD_ADDRESSIQ_INGEST_BASE_URL
#   STAGING_ADDRESSIQ_CDN_BASE_URL      PROD_ADDRESSIQ_CDN_BASE_URL
#
# `DEVELOPMENT` is NOT baked: it points at the host machine's backend (reached
# from the emulator via 10.0.2.2), so it is a local concern and stays a literal
# in AddressIQEnvironment.
#
# Two more constants — `widgetVersion` and `widgetIntegrity` — are baked from
# FILES, not variables: `.widget-version` and `.widget-integrity` at the repo
# root. addressiq-web's widget-fanout workflow rewrites both in the same PR that
# re-vendors src/main/assets/iqcollect.js, so they always describe the bytes
# actually vendored here. A leading "v" is stripped from the version; the SDK
# reassembles the immutable CDN path as "/v{version}/iqcollect.js" and pins the
# integrity hash on the <script>. Missing or empty file -> "" (never an error,
# not even under --strict: those files are absent until the first fanout PR
# lands, and an empty value simply disables the CDN path and uses the bundled
# asset).
#
# Usage:
#   scripts/bake-build-config.sh            # unset vars keep their defaults (local)
#   scripts/bake-build-config.sh --strict   # unset vars are a hard error (release)
#
# --strict is what release.yml uses. The old workflow passed each URL as a
# Gradle -P property and silently fell back to the build.gradle.kts default when
# the variable was unset — which meant a misconfigured release published an AAR
# pointing at whatever was committed, silently. A release that cannot see its
# config should fail, not guess.

set -euo pipefail

cd "$(dirname "$0")/.."
OUT="src/main/kotlin/com/addressiq/android/generated/AddressIQBuildConfig.kt"

STRICT=0
[ "${1:-}" = "--strict" ] && STRICT=1

# name|default — defaults mirror the checked-in file and are the public hosts.
DEFAULTS="
STAGING_ADDRESSIQ_API_BASE_URL|https://api-staging.addressiqpro.com
STAGING_ADDRESSIQ_INGEST_BASE_URL|https://ingest-api-staging.addressiqpro.com
STAGING_ADDRESSIQ_CDN_BASE_URL|https://cdn-staging.addressiqpro.com
PROD_ADDRESSIQ_API_BASE_URL|https://api.addressiqpro.com
PROD_ADDRESSIQ_INGEST_BASE_URL|https://ingest-api.addressiqpro.com
PROD_ADDRESSIQ_CDN_BASE_URL|https://cdn.addressiqpro.com
"

missing=""

# NB: assign into V_<NAME> directly rather than via `$(resolve …)`. A command
# substitution runs in a subshell, so a `missing` recorded inside one is thrown
# away — which silently turns --strict into a no-op that bakes empty strings.
while IFS='|' read -r name default; do
  [ -n "$name" ] || continue
  val="${!name:-}"
  if [ -z "$val" ]; then
    if [ "$STRICT" = "1" ]; then
      missing="$missing $name"
      continue
    fi
    val="$default"
  fi
  # A base URL with a trailing slash concatenates into `//path`; normalise.
  eval "V_$name=\"\${val%/}\""
done <<< "$DEFAULTS"

if [ -n "$missing" ]; then
  echo "::error::--strict: required build variables are unset:$missing" >&2
  echo "A release must not fall back to checked-in defaults. Set them as GitHub repository variables." >&2
  exit 1
fi

# Widget version + SRI hash, from files written by addressiq-web's fanout PR.
# `tr -d` strips the trailing newline printf'd by the workflow; the version also
# loses its leading "v" so the SDK can build "/v${V}/iqcollect.js" itself.
read_file_trimmed() {
  [ -f "$1" ] || { printf ''; return; }
  tr -d ' \t\r\n' < "$1"
}
V_WIDGET_VERSION="$(read_file_trimmed .widget-version)"
V_WIDGET_VERSION="${V_WIDGET_VERSION#v}"
V_WIDGET_INTEGRITY="$(read_file_trimmed .widget-integrity)"

mkdir -p "$(dirname "$OUT")"

cat > "$OUT" <<EOF
// Generated build-time configuration — DO NOT EDIT BY HAND.
//
// Rewritten wholesale by \`scripts/bake-build-config.sh\` at publish time from
// the GitHub repository variables (see .github/workflows/release.yml):
//
//   STAGING_ADDRESSIQ_API_BASE_URL      PROD_ADDRESSIQ_API_BASE_URL
//   STAGING_ADDRESSIQ_INGEST_BASE_URL   PROD_ADDRESSIQ_INGEST_BASE_URL
//   STAGING_ADDRESSIQ_CDN_BASE_URL      PROD_ADDRESSIQ_CDN_BASE_URL
//
// The checked-in values below are the safe public defaults, so a local
// \`./gradlew build\` and the test suite resolve real hosts with no
// substitution. On a real release the baker runs with --strict and REQUIRES
// every variable above — a published AAR must never silently carry a
// developer's default.
//
// \`widgetVersion\` / \`widgetIntegrity\` are NOT environment variables. They are
// read from two FILES at the repo root, \`.widget-version\` (e.g. "v0.4.0") and
// \`.widget-integrity\` (e.g. "sha384-…"), which addressiq-web's widget-fanout
// workflow rewrites in the same PR that re-vendors src/main/assets/iqcollect.js.
// They therefore describe exactly the bytes vendored into this repo. The verify
// WebView loads \`{cdnUrl}/v{widgetVersion}/iqcollect.js\` with that SRI hash
// pinned, falling back to the bundled asset. Either file being absent/empty
// bakes "" here, which disables the CDN path entirely (bundled-only) — that is
// the state before the first fanout PR lands.
//
// \`DEVELOPMENT\` is deliberately NOT baked from CI: it points at the host
// machine's backend (reached from the emulator via 10.0.2.2), so it is a
// local-only concern and stays a compile-time literal in AddressIQEnvironment.
// Never ship a build configured for \`DEVELOPMENT\`.
//
// Named AddressIQBuildConfig rather than BuildConfig so it does not collide
// with the Android Gradle plugin's own generated \`com.addressiq.android.BuildConfig\`.
package com.addressiq.android.generated

internal object AddressIQBuildConfig {
    const val stagingApiUrl = "$V_STAGING_ADDRESSIQ_API_BASE_URL"
    const val stagingIngestUrl = "$V_STAGING_ADDRESSIQ_INGEST_BASE_URL"
    const val stagingCdnUrl = "$V_STAGING_ADDRESSIQ_CDN_BASE_URL"

    const val prodApiUrl = "$V_PROD_ADDRESSIQ_API_BASE_URL"
    const val prodIngestUrl = "$V_PROD_ADDRESSIQ_INGEST_BASE_URL"
    const val prodCdnUrl = "$V_PROD_ADDRESSIQ_CDN_BASE_URL"

    /** Vendored widget version, no leading "v" (from \`.widget-version\`). "" = unknown. */
    const val widgetVersion = "$V_WIDGET_VERSION"

    /** SRI hash of the vendored widget (from \`.widget-integrity\`). "" = unknown. */
    const val widgetIntegrity = "$V_WIDGET_INTEGRITY"
}
EOF

echo "[bake] wrote $OUT"
grep -E 'const val' "$OUT" | sed 's/^/  /'
