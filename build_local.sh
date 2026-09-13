#!/usr/bin/env bash
set -e
cd /root/projects/minebench
export ANDROID_HOME=/opt/android-sdk
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"
source /root/.secrets/keystores-android/minebench-keystore.env
export MINEBENCH_KEYSTORE_PASSWORD="$PASS"
export MINEBENCH_KEY_PASSWORD="$PASS"
export MINEBENCH_KEY_ALIAS="minebench"
./gradlew --no-daemon testDebugUnitTest assembleRelease "$@"
echo "=== BUILDS ==="
ls -la app/build/outputs/apk/release/ 2>/dev/null || true
