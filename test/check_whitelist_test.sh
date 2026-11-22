#!/bin/sh
# Tests for check_whitelist.sh (the boot guard). Plain sh; run by build.sh test.
cd "$(dirname "$0")/.." || exit 1
T=build/whitelist-test
rm -rf "$T"; mkdir -p "$T"
fails=0

expect() { # expect LABEL WANT_EXIT MANIFEST XML
    sh ./check_whitelist.sh "$3" "$4" >"$T/out" 2>&1
    got=$?
    if [ "$got" = "$2" ]; then echo "PASS $1"; else echo "FAIL $1 (exit $got, wanted $2)"; cat "$T/out"; fails=$((fails + 1)); fi
}

wl() { # wl FILE PERMISSION...  -> a whitelist file listing them
    f=$1; shift
    { echo '<permissions><privapp-permissions package="x">'
      for p in "$@"; do echo "        <permission name=\"$p\" />"; done
      echo '</privapp-permissions></permissions>'; } > "$f"
}

mf() { # mf FILE PERMISSION...  -> a manifest asking for them
    f=$1; shift
    { echo '<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="x">'
      for p in "$@"; do echo "    <uses-permission android:name=\"$p\" />"; done
      echo '</manifest>'; } > "$f"
}

# The real files must pass.
expect "the project's own manifest and whitelist" 0 app/AndroidManifest.xml module/system/etc/permissions/privapp-permissions-com.metaljay.stremiovoice.xml

mf $T/m1 android.permission.INTERNET android.permission.RECORD_AUDIO; wl $T/w1
expect "only android.* permissions, empty whitelist" 0 $T/m1 $T/w1

mf $T/m2 android.permission.INTERNET com.amazon.tv.launcher.permission.SHOW_SEARCH_RESULTS; wl $T/w2
expect "platform permission missing from the whitelist" 1 $T/m2 $T/w2

wl $T/w3 com.amazon.tv.launcher.permission.SHOW_SEARCH_RESULTS
expect "platform permission listed" 0 $T/m2 $T/w3

mf $T/m4 com.amazon.vizzini.SPEECH_RECOGNIZER com.amazon.tv.launcher.permission.SHOW_SEARCH_RESULTS
wl $T/w4 com.amazon.vizzini.SPEECH_RECOGNIZER
expect "one of two platform permissions missing" 1 $T/m4 $T/w4

printf '<manifest package="x">\n<uses-permission\n    android:name="com.example.PRIVATE"\n    />\n</manifest>\n' > $T/m5
wl $T/w5
expect "tag spread over several lines is still checked" 1 $T/m5 $T/w5

wl $T/w6 com.amazon.tv.launcher.permission.SHOW_SEARCH_RESULTS_EXTRA
expect "a longer name is not a match for a shorter one" 1 $T/m2 $T/w6

# Stricter parsing (review fixes)
P=com.amazon.tv.launcher.permission.SHOW_SEARCH_RESULTS
mf $T/m7 "$P"
printf '<permissions><!-- <permission name="%s" /> -->\n<privapp-permissions package="x"></privapp-permissions></permissions>\n' "$P" > $T/w7
expect "entry only inside an XML comment is not on the whitelist" 1 $T/m7 $T/w7

printf '<manifest package="x">\n<!-- <uses-permission android:name="%s" /> -->\n<uses-permission android:name="android.permission.INTERNET" />\n</manifest>\n' "$P" > $T/m8
wl $T/w8
expect "a commented-out manifest permission is not asked for" 0 $T/m8 $T/w8

printf "<manifest package='x'>\n<uses-permission android:name = '%s' />\n</manifest>\n" "$P" > $T/m9
wl $T/w9a
expect "single quotes and spaces around = in the manifest are still checked" 1 $T/m9 $T/w9a
printf "<permissions><privapp-permissions package = 'x'><permission name = '%s'/></privapp-permissions></permissions>\n" "$P" > $T/w9b
expect "single quotes and spaces around = in the whitelist are accepted" 0 $T/m9 $T/w9b

printf '<permissions><privapp-permissions package="some.other.app"><permission name="%s" /></privapp-permissions></permissions>\n' "$P" > $T/w10
expect "entry listed under another app's package does not count" 1 $T/m7 $T/w10

printf '<permissions><privapp-permissions package="some.other.app"><permission name="%s" /></privapp-permissions><privapp-permissions package="x"><permission name="%s" /></privapp-permissions></permissions>\n' "other.PERM" "$P" > $T/w11
expect "our package's block is found after another app's block" 0 $T/m7 $T/w11

printf '<manifest>\n<uses-permission android:name="%s" />\n</manifest>\n' "$P" > $T/m12
expect "a manifest with no package name is an error" 2 $T/m12 $T/w3

expect "missing file is an error" 2 $T/nope $T/w1

[ "$fails" = 0 ] && echo "All whitelist cases pass." || { echo "$fails whitelist case(s) FAILED."; exit 1; }
