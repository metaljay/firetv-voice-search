#!/bin/bash
# Voice Search build script. Uses only tools already on the Mac; downloads nothing.
#
#   ./build.sh test   run the unit tests
#   ./build.sh        run the tests, build the APK, and assemble the Magisk add-on zip
#
# Outputs (in build/, which git ignores):
#   build/StremioVoice.apk     the signed app
#   build/VoiceSearchTile.apk  the small tile app that opens the settings screen
#   build/stremio_voice.zip    the Magisk add-on (carries both)
#
# The signing key is debug.keystore in this folder. It is created the first
# time and then kept, so `adb install -r` updates keep working. Do not delete it.

set -euo pipefail
cd "$(dirname "$0")"

SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
# 36.0.0, not 34.0.0: the 34.0.0 d8 crashes on classes built with this Java.
BUILD_TOOLS="$SDK/build-tools/36.0.0"
ANDROID_JAR="$SDK/platforms/android-34/android.jar"
export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 25)}"
JAVAC="$JAVA_HOME/bin/javac"
JAVA="$JAVA_HOME/bin/java"
KEYTOOL="$JAVA_HOME/bin/keytool"

APP_SRC=app/src
KEYSTORE=debug.keystore
KEY_ALIAS=stremiovoice
KEY_PASS=android

run_tests() {
    echo "== Unit tests"
    rm -rf build/test-classes
    mkdir -p build/test-classes
    # Only the plain-Java classes are tested here; they use no Android classes.
    "$JAVAC" -Xlint:-options -d build/test-classes \
        "$APP_SRC/com/metaljay/stremiovoice/QueryCleaner.java" \
        "$APP_SRC/com/metaljay/stremiovoice/Targets.java" \
        "$APP_SRC/com/metaljay/stremiovoice/ResultRanker.java" \
        test/QueryCleanerTest.java test/TargetsTest.java test/ResultRankerTest.java
    "$JAVA" -cp build/test-classes QueryCleanerTest
    "$JAVA" -cp build/test-classes TargetsTest
    "$JAVA" -cp build/test-classes ResultRankerTest
}

build_apk() {
    echo "== Compile"
    rm -rf build/classes build/dex build/apk
    mkdir -p build/classes build/dex build/apk
    # shellcheck disable=SC2046
    "$JAVAC" --release 8 -Xlint:-options -Xlint:all -d build/classes \
        -classpath "$ANDROID_JAR" $(find "$APP_SRC" -name '*.java')

    echo "== Dex"
    "$BUILD_TOOLS/d8" --lib "$ANDROID_JAR" --min-api 28 --output build/dex \
        $(find build/classes -name '*.class')

    echo "== Package"
    rm -rf build/res && mkdir -p build/res
    "$BUILD_TOOLS/aapt2" compile --dir app/res -o build/res/compiled.zip
    "$BUILD_TOOLS/aapt2" link --manifest app/AndroidManifest.xml -I "$ANDROID_JAR" \
        -R build/res/compiled.zip --auto-add-overlay \
        --min-sdk-version 28 --target-sdk-version 29 -o build/apk/base.apk
    cp build/apk/base.apk build/apk/unaligned.apk
    zip -q -j build/apk/unaligned.apk build/dex/classes.dex
    "$BUILD_TOOLS/zipalign" -f -p 4 build/apk/unaligned.apk build/apk/aligned.apk

    echo "== Sign"
    if [ ! -f "$KEYSTORE" ]; then
        echo "Creating $KEYSTORE (once; keep it)"
        "$KEYTOOL" -genkeypair -keystore "$KEYSTORE" -storepass "$KEY_PASS" -keypass "$KEY_PASS" \
            -alias "$KEY_ALIAS" -keyalg RSA -keysize 2048 -validity 36500 \
            -dname "CN=Stremio Voice Debug" >/dev/null
    fi
    "$BUILD_TOOLS/apksigner" -J-enable-native-access=ALL-UNNAMED sign --ks "$KEYSTORE" --ks-key-alias "$KEY_ALIAS" \
        --ks-pass "pass:$KEY_PASS" --key-pass "pass:$KEY_PASS" \
        --out build/StremioVoice.apk build/apk/aligned.apk
    "$BUILD_TOOLS/apksigner" -J-enable-native-access=ALL-UNNAMED verify build/StremioVoice.apk
    echo "Built build/StremioVoice.apk"
}

# The tile: a tiny ordinary app, so Amazon's home screen shows a "Voice Search"
# tile (it hides system apps). It reuses the main app's icon and banner.
build_tile() {
    echo "== Tile app"
    rm -rf build/tile && mkdir -p build/tile/classes build/tile/dex
    # shellcheck disable=SC2046
    "$JAVAC" --release 8 -Xlint:-options -Xlint:all -d build/tile/classes \
        -classpath "$ANDROID_JAR" $(find tile/src -name '*.java')
    "$BUILD_TOOLS/d8" --lib "$ANDROID_JAR" --min-api 28 --output build/tile/dex \
        $(find build/tile/classes -name '*.class')
    "$BUILD_TOOLS/aapt2" link --manifest tile/AndroidManifest.xml -I "$ANDROID_JAR" \
        -R build/res/compiled.zip --auto-add-overlay \
        --min-sdk-version 28 --target-sdk-version 29 -o build/tile/unaligned.apk
    zip -q -j build/tile/unaligned.apk build/tile/dex/classes.dex
    "$BUILD_TOOLS/zipalign" -f -p 4 build/tile/unaligned.apk build/tile/aligned.apk
    "$BUILD_TOOLS/apksigner" -J-enable-native-access=ALL-UNNAMED sign --ks "$KEYSTORE" --ks-key-alias "$KEY_ALIAS" \
        --ks-pass "pass:$KEY_PASS" --key-pass "pass:$KEY_PASS" \
        --out build/VoiceSearchTile.apk build/tile/aligned.apk
    "$BUILD_TOOLS/apksigner" -J-enable-native-access=ALL-UNNAMED verify build/VoiceSearchTile.apk
    echo "Built build/VoiceSearchTile.apk"
}

build_module() {
    echo "== Add-on zip"
    rm -rf build/module build/stremio_voice.zip
    mkdir -p build/module/system/priv-app/StremioVoice
    cp -R module/. build/module/
    cp build/StremioVoice.apk build/module/system/priv-app/StremioVoice/StremioVoice.apk
    mkdir -p build/module/tile
    cp build/VoiceSearchTile.apk build/module/tile/VoiceSearchTile.apk
    (cd build/module && zip -q -X -D -r ../stremio_voice.zip module.prop post-fs-data.sh service.sh system tile)
    unzip -l build/stremio_voice.zip
    echo "Built build/stremio_voice.zip"
}

case "${1:-build}" in
    test)
        run_tests
        ;;
    build)
        run_tests
        build_apk
        build_tile
        build_module
        ;;
    *)
        echo "usage: $0 [test]" >&2
        exit 2
        ;;
esac
