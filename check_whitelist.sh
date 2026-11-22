#!/bin/sh
# Boot guard. Usage: check_whitelist.sh MANIFEST WHITELIST_XML
#
# The stick enforces ro.control_privapp_permissions: a system app that asks for a platform
# permission (anything not "android.*") with no entry in the privapp-permissions file stops the
# stick booting. Fails (and says which) if the manifest asks for one that the file doesn't list.
# Used by build.sh; tested by test/check_whitelist_test.sh.

manifest=$1
xml=$2
[ -f "$manifest" ] && [ -f "$xml" ] || { echo "boot guard: usage: $0 MANIFEST WHITELIST_XML" >&2; exit 2; }

missing=0
asked=$(tr '\n' ' ' < "$manifest" | grep -o '<uses-permission[^>]*>' \
    | sed -n 's/.*android:name="\([^"]*\)".*/\1/p')
for p in $asked; do
    case "$p" in android.*) continue ;; esac
    if ! tr '\n' ' ' < "$xml" | grep -q "<permission[[:space:]]*name=\"$p\""; then
        echo "boot guard: $p is in the manifest but not in $xml: the stick would not boot" >&2
        missing=1
    fi
done
exit $missing
