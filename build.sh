#!/usr/bin/env bash
# Build PocketGuard without Gradle/Android Studio.
#   ./build.sh            -> build/pocketguard.apk
#   ./build.sh install    -> build + adb install -r
set -euo pipefail
cd "$(dirname "$0")"
source ./env.sh

KS=keystore/debug.jks
if [ ! -f "$KS" ]; then
  echo ">> generating signing key $KS"
  keytool -genkeypair -keystore "$KS" -alias dbg -keyalg RSA -keysize 2048 -validity 10000 \
          -storepass android -keypass android -dname CN=PocketGuard
fi

rm -rf build && mkdir -p build/gen build/classes build/dex

echo ">> aapt2 compile"
aapt2 compile --dir res -o build/res.zip

echo ">> aapt2 link"
aapt2 link -I "$PLATFORM/android.jar" --manifest AndroidManifest.xml -R build/res.zip \
      --java build/gen --min-sdk-version 28 --target-sdk-version 33 \
      --auto-add-overlay -o build/unsigned.apk

echo ">> javac"
javac --release 17 -Xlint:deprecation -cp "$PLATFORM/android.jar" -d build/classes \
      $(find src build/gen -name '*.java')

echo ">> d8"
d8 --release --min-api 28 --lib "$PLATFORM/android.jar" --output build/dex \
   $(find build/classes -name '*.class')

echo ">> package + align + sign"
(cd build/dex && zip -q ../unsigned.apk classes.dex)
zipalign -f -p 4 build/unsigned.apk build/aligned.apk
apksigner sign --ks "$KS" --ks-pass pass:android --out build/pocketguard.apk build/aligned.apk
ls -l build/pocketguard.apk

if [ "${1:-}" = "install" ]; then
  echo ">> adb install"
  adb install -r build/pocketguard.apk
fi
