#!/usr/bin/env bash
set -euo pipefail
: "${ANDROID_HOME:?Set ANDROID_HOME to your SDK}"
artifact=${1:?Usage: verify-apk.sh /path/to.apk}
badging=$("$ANDROID_HOME/build-tools/36.0.0/aapt" dump badging "$artifact")
[[ "$badging" == *"name='com.hycnyl.easyoverlay'"* ]]
[[ "$badging" == *"versionName='6.1'"* ]]
[[ "$badging" == *"versionCode='610'"* ]]
[[ "$badging" == *"sdkVersion:'33'"* ]]
[[ "$badging" == *"targetSdkVersion:'35'"* ]]
python3 - "$artifact" <<'PY'
import sys,zipfile
with zipfile.ZipFile(sys.argv[1]) as apk:
    native=[name for name in apk.namelist() if name.startswith('lib/') and name.endswith('.so')]
    required={'arm64-v8a','armeabi-v7a','x86','x86_64'}
    libraries={name.split('/')[-1] for name in native}
    for library in libraries:
        abis={name.split('/')[1] for name in native if name.endswith('/'+library)}
        assert required <= abis, f'{library} missing ABIs: {required-abis}'
    assert 'assets/LICENSE.txt' in apk.namelist(), 'GPL license missing'
print('Verified EasyOverlay 6.1, formal app ID, Android 13+, license and universal APK for arm64-v8a/armeabi-v7a/x86/x86_64.')
PY
