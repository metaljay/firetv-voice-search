#!/bin/bash
# Voice Search build script. Uses only tools already on the Mac; downloads nothing.
#
#   ./build.sh test   run the unit tests
#   ./build.sh        run the tests, build the APK, and assemble the Magisk add-on zip
#
# Outputs (in build/, which git ignores):
#   build/StremioVoice.apk     the signed app
#   build/VoiceSearchTile.apk  the small tile app that opens the settings screen
#   build/voice_search.zip    the Magisk add-on (carries both), signed so the app's
#                              updater accepts it
#   build/stremio_voice.zip   the same file under its old name. Attach BOTH to every
#                              release: 2.3 and earlier only look for the old name.
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
JARSIGNER="$JAVA_HOME/bin/jarsigner"

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
        "$APP_SRC/com/metaljay/stremiovoice/Versions.java" \
        "$APP_SRC/com/metaljay/stremiovoice/UpdateSchedule.java" \
        "$APP_SRC/com/metaljay/stremiovoice/ReleaseNotes.java" \
        "$APP_SRC/com/metaljay/stremiovoice/PackageVerifier.java" \
        "$APP_SRC/com/metaljay/stremiovoice/AssetPicker.java" \
        "$APP_SRC/com/metaljay/stremiovoice/ResultChooser.java" \
        "$APP_SRC/com/metaljay/stremiovoice/TiviMatePrewarm.java" \
        "$APP_SRC/com/metaljay/stremiovoice/Redactor.java" \
        "$APP_SRC/com/metaljay/stremiovoice/ReportBuilder.java" \
        test/RedactorTest.java test/ReportBuilderTest.java \
        test/QueryCleanerTest.java test/TargetsTest.java test/ResultRankerTest.java \
        test/VersionsTest.java test/UpdateScheduleTest.java test/ReleaseNotesTest.java \
        test/PackageVerifierTest.java test/AssetPickerTest.java test/ResultChooserTest.java test/TiviMatePrewarmTest.java test/VerifyZip.java
    "$JAVA" -cp build/test-classes QueryCleanerTest
    "$JAVA" -cp build/test-classes TargetsTest
    "$JAVA" -cp build/test-classes ResultRankerTest
    "$JAVA" -cp build/test-classes VersionsTest
    "$JAVA" -cp build/test-classes UpdateScheduleTest
    "$JAVA" -cp build/test-classes ReleaseNotesTest
    "$JAVA" -cp build/test-classes AssetPickerTest
    "$JAVA" -cp build/test-classes ResultChooserTest
    "$JAVA" -cp build/test-classes TiviMatePrewarmTest
    "$JAVA" -cp build/test-classes RedactorTest
    "$JAVA" -cp build/test-classes ReportBuilderTest
    echo "== Accessibility-list tests (plain sh)"
    sh test/a11y_list_test.sh
    echo "== Boot guard tests (plain sh)"
    sh test/check_whitelist_test.sh
    echo "== Token scan tests (plain sh)"
    sh test/check_no_tokens_test.sh
    # Makes throwaway keys and signed zips in build/verifier-test/; leaves debug.keystore alone.
    "$JAVA" -cp build/test-classes PackageVerifierTest
}

build_apk() {
    echo "== Boot guard: every platform permission the app asks for is on the system whitelist"
    sh ./check_whitelist.sh app/AndroidManifest.xml \
        module/system/etc/permissions/privapp-permissions-com.metaljay.stremiovoice.xml \
        || { echo "The build stops here: the stick would not boot." >&2; exit 1; }
    echo "== Token scan: no GitHub token in any tracked file"
    sh ./check_no_tokens.sh . \
        || { echo "The build stops here: a token must never be committed." >&2; exit 1; }
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
    rm -rf build/module build/voice_search.zip build/stremio_voice.zip
    mkdir -p build/module/system/priv-app/StremioVoice
    cp -R module/. build/module/
    cp build/StremioVoice.apk build/module/system/priv-app/StremioVoice/StremioVoice.apk
    mkdir -p build/module/tile
    cp build/VoiceSearchTile.apk build/module/tile/VoiceSearchTile.apk
    (cd build/module && zip -q -X -D -r ../voice_search.zip module.prop post-fs-data.sh service.sh a11y_list.sh uninstall.sh system tile)
    # Sign the zip as a jar with the key that signs the app. The app refuses an update
    # zip unless it carries this signature (see PackageVerifier). Magisk skips META-INF,
    # so the signature files do not end up in the installed module.
    "$JARSIGNER" -keystore "$KEYSTORE" -storepass "$KEY_PASS" -keypass "$KEY_PASS" \
        -digestalg SHA-256 -sigalg SHA256withRSA build/voice_search.zip "$KEY_ALIAS" >/dev/null
    "$JARSIGNER" -verify build/voice_search.zip >/dev/null \
        || { echo "jarsigner could not verify the zip" >&2; exit 1; }
    # The same check the app runs, against the same key.
    "$JAVA" -cp build/test-classes VerifyZip build/voice_search.zip "$KEYSTORE" "$KEY_ALIAS" "$KEY_PASS"
    unzip -l build/voice_search.zip
    cp build/voice_search.zip build/stremio_voice.zip
    echo "Built build/voice_search.zip (signed), and build/stremio_voice.zip (the same file, old name)"
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
