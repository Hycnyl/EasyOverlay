#!/usr/bin/env bash
set -euo pipefail
: "${JAVA_HOME:?Set JAVA_HOME to a JDK 17 installation}"
: "${ANDROID_HOME:?Set ANDROID_HOME to the Android SDK}"
"$JAVA_HOME/bin/java" -version
"$JAVA_HOME/bin/javac" -version
test -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
test -f "$ANDROID_HOME/platforms/android-${ANDROID_API:-36}/android.jar"
printf 'Android SDK and JDK are ready.\n'
