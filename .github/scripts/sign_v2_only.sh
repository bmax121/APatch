#!/usr/bin/env bash
# zipalign + sign an APK using ONLY the APK Signature Scheme v2.
#
# The v1 (JAR) and v3/v4 schemes are explicitly disabled so the signed APK
# carries exactly one signature block (v2). Replaces the old
# kevin-david/zipalign-sign-android-release action, which signed with
# apksigner defaults (v1+v2+v3) and offered no way to pick schemes.
#
# Usage: sign_v2_only.sh <apk-dir>
#
# Env (required):
#   ANDROID_HOME          - Android SDK root (set by android-actions/setup-android)
#   BUILD_TOOLS_VERSION   - build-tools version, e.g. 35.0.0
#   KEYSTORE_FILE         - path to the decoded keystore (.jks)
#   KEY_STORE_PASSWORD    - keystore password
#   KEY_ALIAS             - key alias
#   KEY_PASSWORD          - key password
#
# Stdout: path of the signed APK

set -euo pipefail

DIR="$1"
: "${KEYSTORE_FILE:?}"; : "${KEY_STORE_PASSWORD:?}"; : "${KEY_ALIAS:?}"; : "${KEY_PASSWORD:?}"

BUILD_TOOLS="$ANDROID_HOME/build-tools/$BUILD_TOOLS_VERSION"
ZIPALIGN="$BUILD_TOOLS/zipalign"
APKSIGNER="$BUILD_TOOLS/apksigner"

[ -d "$BUILD_TOOLS" ] || { echo "build-tools not found @ $BUILD_TOOLS" >&2; exit 1; }
[ -f "$KEYSTORE_FILE" ] || { echo "keystore not found @ $KEYSTORE_FILE" >&2; exit 1; }

# This project emits exactly one APK per output directory.
mapfile -t APKS < <(ls -1 "$DIR"/*.apk 2>/dev/null || true)
if [ "${#APKS[@]}" -ne 1 ]; then
    echo "expected exactly one APK in $DIR, found ${#APKS[@]}" >&2
    exit 1
fi
APK="${APKS[0]}"

# Zipalign (4-byte, page-align uncompressed .so). zipalign cannot align in
# place, so align into a temp file and swap it back over the original path.
# Its verbose listing goes to stderr so stdout carries ONLY the signed path.
UNALIGNED="$APK.unaligned"
mv "$APK" "$UNALIGNED"
"$ZIPALIGN" -p -v 4 "$UNALIGNED" "$APK" 1>&2
rm -f "$UNALIGNED"

# Sign with ONLY the v2 scheme.
SIGNED="${APK%.apk}-signed.apk"
"$APKSIGNER" sign \
    --ks "$KEYSTORE_FILE" \
    --ks-pass "pass:$KEY_STORE_PASSWORD" \
    --ks-key-alias "$KEY_ALIAS" \
    --key-pass "pass:$KEY_PASSWORD" \
    --v1-signing-enabled false \
    --v2-signing-enabled true \
    --v3-signing-enabled false \
    --v4-signing-enabled false \
    --out "$SIGNED" \
    "$APK"

# Verify, and assert the signature is v2-only (v3 not also present).
VERIFY_OUTPUT=$("$APKSIGNER" verify --verbose "$SIGNED" 2>&1)
if ! grep -q "Verified using v2 scheme.*: true" <<< "$VERIFY_OUTPUT" \
    || grep -q "Verified using v3 scheme.*: true" <<< "$VERIFY_OUTPUT"; then
    echo "ERROR: signed APK is not v2-only:" >&2
    echo "$VERIFY_OUTPUT" >&2
    exit 1
fi

echo "$SIGNED"
