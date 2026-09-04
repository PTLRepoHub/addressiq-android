#!/usr/bin/env bash
#
# `startVerification` end to end, from an emulator against a real backend.
#
# This is the call that crashed in the field (#28) — every response was decoded
# as `Map<String, String?>`, so `"isExisting": false` threw the moment a user
# picked digital verification, and it had been that way since the SDK's first
# commit. The unit suite now covers the transport over MockWebServer, but a
# stub only ever returns the body the test author imagined; this proves the SDK
# parses what the API actually sends.
#
#   ./scripts/run-start-verification-test.sh              # local stack
#   ./scripts/run-start-verification-test.sh staging      # staging
#
# Needs: an emulator running, and either the local stack up (api on 4000) or
# ADDRESSIQ_STAGING_KEY set for staging.
#
set -euo pipefail
cd "$(dirname "$0")/.."

: "${ANDROID_HOME:=$HOME/Library/Android/sdk}"
export ANDROID_HOME
export PATH="$ANDROID_HOME/platform-tools:$PATH"

TARGET="${1:-development}"

if [ "$TARGET" = "staging" ]; then
  DEPLOYMENT=STAGING
  API=https://api-staging.addressiqpro.com
  KEY="${ADDRESSIQ_STAGING_KEY:?set ADDRESSIQ_STAGING_KEY to run against staging}"
else
  DEPLOYMENT=DEVELOPMENT
  API=http://localhost:4000
  KEY="${ADDRESSIQ_DEV_KEY:-aiq_test_demo_bank_seed01}"
  curl -fsS -m 10 "$API/health" >/dev/null || { echo "stack not up at $API"; exit 1; }
fi

adb devices | grep -q "device$" || { echo "no emulator/device attached"; exit 1; }

# A fresh Location per run: startVerification is idempotent per location, so
# reusing one would exercise the isExisting:true branch only.
echo "==> creating a location via $API"
LOC=$(curl -fsS -X POST "$API/api/v1/locations/collect" \
  -H "x-api-key: $KEY" -H 'content-type: application/json' \
  -H "Idempotency-Key: iqidem_$(uuidgen)" \
  -d '{"appUserId":"cust_start_verification_probe","firstName":"Start","lastName":"Verification","phone":"+2348030000101","lat":6.5244,"lon":3.3792,"geofenceRadiusM":150,"locationType":"HOME","formattedAddress":"startVerification probe"}' \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["locationCode"])')
echo "    location=$LOC"

echo "==> running StartVerificationInstrumentedTest against $DEPLOYMENT"
gradle connectedDebugAndroidTest --no-daemon \
  -Pandroid.testInstrumentationRunnerArguments.class=com.addressiq.android.StartVerificationInstrumentedTest \
  -Pandroid.testInstrumentationRunnerArguments.aiqLocationCode="$LOC" \
  -Pandroid.testInstrumentationRunnerArguments.aiqApiKey="$KEY" \
  -Pandroid.testInstrumentationRunnerArguments.aiqDeployment="$DEPLOYMENT"
