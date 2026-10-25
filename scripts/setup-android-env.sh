#!/usr/bin/env bash
# Install SDK tools outside the repository; supply a complete JDK 17 first.
set -euo pipefail
: "${JAVA_HOME:?Set JAVA_HOME to a complete JDK 17}"
: "${ANDROID_HOME:?Set ANDROID_HOME to the desired SDK directory}"
if [[ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]]; then
  task_tmp=$(mktemp -d)
  trap 'rm -rf "$task_tmp"' EXIT
  curl -fL --retry 3 https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip -o "$task_tmp/tools.zip"
  (cd "$task_tmp" && printf '%s  tools.zip\n' 7ec965280a073311c339e571cd5de778b9975026cfcbe79f2b1cdcb1e15317ee | sha256sum -c -)
  unzip -q "$task_tmp/tools.zip" -d "$task_tmp"
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  mv "$task_tmp/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
fi
sdk_manager="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
"$sdk_manager" --sdk_root="$ANDROID_HOME" --licenses
"$sdk_manager" --sdk_root="$ANDROID_HOME" 'platforms;android-36' 'build-tools;36.0.0' 'platform-tools'
bash "$(dirname "$0")/verify-android-env.sh"
