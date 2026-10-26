#!/usr/bin/env bash
set -euo pipefail
: "${JAVA_HOME:?Set JAVA_HOME to JDK 17}"
: "${ANDROID_HOME:?Set ANDROID_HOME to your SDK}"
: "${EASYOVERLAY_SIGNING_PROPERTIES:?Point this to a private signing.properties outside Git}"
test -f "$EASYOVERLAY_SIGNING_PROPERTIES"
bash gradlew :app:assembleRelease --stacktrace
mkdir -p dist
cp app/build/outputs/apk/release/app-release.apk dist/EasyOverlay-v6.1.apk
"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --verbose --print-certs dist/EasyOverlay-v6.1.apk
bash scripts/verify-apk.sh dist/EasyOverlay-v6.1.apk
(cd dist && sha256sum EasyOverlay-v6.1.apk > EasyOverlay-v6.1.sha256)
