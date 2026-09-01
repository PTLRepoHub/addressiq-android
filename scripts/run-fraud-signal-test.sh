#!/usr/bin/env bash
#
# Fraud signals, end to end, using the SDK's OWN collector.
#
# Every other test of these builds `rawPayload` by hand. That proves the engine
# reacts to a payload and nothing about whether a device produces one — which is
# how `isEmulator()` stayed broken on modern AVDs while the suite was green, and
# why EMULATOR_DETECTED never fired in practice.
#
# This installs a stub app carrying a real fake-GPS package id so the
# spoofing-app check has something genuine to find, then runs the collector for
# real and leaves the server to say what it saw.
#
#   ./scripts/run-fraud-signal-test.sh
#
# Needs: the local stack up (api 4000, ingest 4001) and an emulator running.
#
set -euo pipefail
cd "$(dirname "$0")/.."

: "${ANDROID_HOME:=$HOME/Library/Android/sdk}"
export ANDROID_HOME
export PATH="$ANDROID_HOME/platform-tools:$PATH"

API=http://localhost:4000
KEY=aiq_test_demo_bank_seed01
SPOOF_PKG=com.lexa.fakegps

curl -fsS -m 10 "$API/health" >/dev/null || { echo "stack not up at $API"; exit 1; }

echo "==> creating a verification"
LOC=$(curl -fsS -X POST "$API/api/v1/locations/collect" \
  -H "x-api-key: $KEY" -H 'content-type: application/json' \
  -H "Idempotency-Key: iqidem_$(uuidgen)" \
  -d '{"appUserId":"cust_fraud_signal","firstName":"Fraud","lastName":"Signal","phone":"+2348030000099","lat":6.5244,"lon":3.3792,"geofenceRadiusM":150,"locationType":"HOME","formattedAddress":"Fraud signal probe"}' \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["locationCode"])')
VER=$(curl -fsS -X POST "$API/api/v1/verifications/start" \
  -H "x-api-key: $KEY" -H 'content-type: application/json' \
  -d "{\"locationCode\":\"$LOC\"}" \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["verificationCode"])')
echo "    location=$LOC verification=$VER"

# A real installed package is the only honest way to exercise the check. The
# java example is rebuilt under a spoofing applicationId — applicationId only,
# never `namespace`, or the manifest's relative activity stops resolving.
echo "==> building and installing a stub as $SPOOF_PKG"
cp examples/java/build.gradle.kts /tmp/aiq-java-gradle.bak
trap 'cp /tmp/aiq-java-gradle.bak examples/java/build.gradle.kts' EXIT
sed -i.bak "s/applicationId = \"com.addressiq.example.java\"/applicationId = \"$SPOOF_PKG\"/" \
  examples/java/build.gradle.kts
(cd examples/java && ./gradlew assembleDebug --no-daemon -q)
adb install -r -t examples/java/build/outputs/apk/debug/*.apk

echo "==> running the collector for real"
(cd examples/kotlin && ./gradlew :addressiq-android:assembleDebugAndroidTest --no-daemon -q)
adb install -r -t build/outputs/apk/androidTest/debug/sdk-debug-androidTest.apk
adb shell am instrument -w -r \
  -e class com.addressiq.android.RealCollectorFraudInstrumentedTest \
  -e aiqLocationCode "$LOC" \
  com.addressiq.android.test/androidx.test.runner.AndroidJUnitRunner

echo
echo "==> what the server decided"
echo "    expect EMULATOR_DETECTED and SPOOFING_APP, status NOT_AT_ADDRESS"
sleep 15
curl -fsS -H "x-api-key: $KEY" "$API/api/v1/verifications/$VER" \
  | python3 -c 'import sys,json; d=json.load(sys.stdin); print("   ", d["status"], d.get("resolutionReason"), d["scoreBreakdown"].get("fraudFlags"))'

echo "==> removing the stub"
adb uninstall "$SPOOF_PKG" >/dev/null 2>&1 || true
