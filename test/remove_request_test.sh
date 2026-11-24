#!/bin/sh
# Tests the "remove" request in module/service.sh with plain sh: the real handle_request, do_remove
# and do_restart functions are cut out of the script and run against a temporary folder, with the
# rest stubbed. Touches no stick. Run: sh test/remove_request_test.sh   (build.sh test does)
SERVICE="$(dirname "$0")/../module/service.sh"

fail=0
check() { # label expected actual
    if [ "$2" = "$3" ]; then echo "PASS $1 -> \"$3\""; else echo "FAIL $1 -> \"$3\" (expected \"$2\")"; fail=$((fail + 1)); fi
}

# The text of one top-level function in service.sh.
extract() { awk -v name="$1" '$0 ~ "^" name "\\(\\) \\{" {p=1} p {print} p && /^\}/ {exit}' "$SERVICE"; }

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
MODDIR="$TMP/module"; UPD="$TMP/upd"; STAGED="$TMP/staged"
mkdir -p "$MODDIR" "$UPD"

# Stubs for everything else the three functions call.
say() { :; }
write_status() { :; }
sync() { :; }
reboot() { echo rebooted >> "$TMP/reboots"; }
fail() { LAST_ERROR="$1"; return 1; }
do_install() { echo "install $1" >> "$TMP/other"; }
do_goback() { echo goback >> "$TMP/other"; }

eval "$(extract do_remove)"
eval "$(extract do_restart)"
eval "$(extract handle_request)"
[ -n "$(extract handle_request)" ] && [ -n "$(extract do_remove)" ] && [ -n "$(extract do_restart)" ] \
    || { echo "FAIL could not find the functions in service.sh"; exit 1; }

# remove: marks the module for removal, answers ok with the same token, request file consumed
echo "remove 111" > "$UPD/request"
handle_request
check "remove: the remove file is created" yes "$([ -f "$MODDIR/remove" ] && echo yes || echo no)"
check "remove: result ok" ok "$LAST_RESULT"
check "remove: token is echoed back" 111 "$LAST_TOKEN"
check "remove: request is consumed" no "$([ -f "$UPD/request" ] && echo yes || echo no)"
check "remove: no restart by itself" no "$([ -f "$TMP/reboots" ] && echo yes || echo no)"
check "remove: nothing else ran" no "$([ -f "$TMP/other" ] && echo yes || echo no)"

# restart after remove is allowed even though no update is staged, and reboots
echo "restart 222" > "$UPD/request"
handle_request
check "restart after remove: result ok" ok "$LAST_RESULT"
check "restart after remove: reboots" rebooted "$(cat "$TMP/reboots" 2>/dev/null)"

# restart with nothing waiting is refused
rm -f "$MODDIR/remove" "$TMP/reboots"
echo "restart 333" > "$UPD/request"
handle_request
check "restart with nothing waiting: failed" failed "$LAST_RESULT"
check "restart with nothing waiting: no reboot" no "$([ -f "$TMP/reboots" ] && echo yes || echo no)"

# remove when the module folder can't be written fails clearly and doesn't reboot
MODDIR="$TMP/missing-folder"
echo "remove 444" > "$UPD/request"
handle_request
check "remove with no module folder: failed" failed "$LAST_RESULT"
check "remove with no module folder: says why" "couldn't mark the add-on for removal" "$LAST_ERROR"
MODDIR="$TMP/module"

# an unknown request is still refused
echo "frobnicate 555" > "$UPD/request"
handle_request
check "unknown request: failed" failed "$LAST_RESULT"

[ "$fail" = 0 ] && echo "All remove-request cases pass." || { echo "$fail remove-request case(s) FAILED."; exit 1; }
