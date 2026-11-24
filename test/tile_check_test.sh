#!/bin/sh
# Tests the setup-app install rule in module/tile_check.sh with plain sh. `pm` is a stub; nothing
# touches a stick. Run: sh test/tile_check_test.sh   (build.sh test does)
. "$(dirname "$0")/../module/tile_check.sh"

fail=0
check() { # label expected actual
    if [ "$2" = "$3" ]; then echo "PASS $1 -> \"$3\""; else echo "FAIL $1 -> \"$3\" (expected \"$2\")"; fail=$((fail + 1)); fi
}
PKG=com.metaljay.voicesearchtile
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

# The stub: STUB_PM is the whole output "pm list packages --show-versioncode <filter>" would give.
STUB_PM=""
STUB_PATH=""   # what "pm path <pkg>" prints; empty means the package manager doesn't know the package
pm() {
    case "$1" in
        list) printf '%s' "$STUB_PM" ;;
        path) [ -n "$STUB_PATH" ] && { printf '%s\n' "$STUB_PATH"; return 0; }; return 1 ;;
    esac
}

version() { printf '%s\n' "$1" > "$TMP/tile.version"; }
decide() { tile_should_install "$PKG" "$TMP/tile.version" && echo install || echo leave; }

version 2
STUB_PM=""
check "missing -> install" install "$(decide)"
STUB_PM="package:com.other.app versionCode:9
"
check "only other apps installed -> install" install "$(decide)"
STUB_PM="package:$PKG versionCode:1
"
check "older installed -> install" install "$(decide)"
STUB_PM="package:$PKG versionCode:2
"
check "same installed -> leave" leave "$(decide)"
STUB_PM="package:$PKG versionCode:3
"
check "newer installed (e.g. from an app store) -> leave" leave "$(decide)"
STUB_PM="package:$PKG versionCode:10
"
check "versionCode compared as a number, not text (10 vs 2) -> leave" leave "$(decide)"
version 10
STUB_PM="package:$PKG versionCode:9
"
check "module 10, installed 9 -> install" install "$(decide)"
STUB_PM="package:${PKG}x versionCode:99
package:$PKG versionCode:1
"
check "look-alike package name is ignored -> install" install "$(decide)"

# The package is there but its code can't be read (odd pm output): never treat it as missing.
version 5
STUB_PM="garbage
"
STUB_PATH="package:/data/app/x/base.apk"
check "installed, versionCode unreadable -> leave" leave "$(decide)"
STUB_PM=""
check "installed, no list output at all -> leave" leave "$(decide)"
STUB_PATH=""
check "really missing (pm path fails too) -> install" install "$(decide)"

# A missing or odd version file never forces an install over an installed app.
rm -f "$TMP/tile.version"
STUB_PATH="package:/data/app/x/base.apk"
STUB_PM="package:$PKG versionCode:1
"
check "no version file, installed -> leave" leave "$(decide)"
STUB_PM=""; STUB_PATH=""
check "no version file, missing -> install" install "$(decide)"
version "oops"
STUB_PM="package:$PKG versionCode:1
"
check "odd version file, installed -> leave" leave "$(decide)"
printf '2\r\n' > "$TMP/tile.version"
check "version file with CRLF reads as 2 -> install over 1" install "$(decide)"

# The functions run in the caller's shell: they must not overwrite the caller's own variables.
n=5; l=L; e=E; old=O; out=U; list=I; prev=P; rest=R; f=F; tick=T; new=N; tmp=M; have=H
tile_should_install "$PKG" "$TMP/tile.version" >/dev/null
check "no caller variable is overwritten" "5 L E O U I P R F T N M H" "$n $l $e $old $out $list $prev $rest $f $tick $new $tmp $have"

[ "$fail" = 0 ] && echo "All tile-rule cases pass." || { echo "$fail tile-rule case(s) FAILED."; exit 1; }
