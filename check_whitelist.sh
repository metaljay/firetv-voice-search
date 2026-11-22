#!/bin/sh
# Boot guard. Usage: check_whitelist.sh MANIFEST WHITELIST_XML
#
# The stick enforces ro.control_privapp_permissions: a system app that asks for a platform
# permission (anything not "android.*") with no entry in the privapp-permissions file stops the
# stick booting. Fails (and says which) if the manifest asks for one that the file doesn't list
# for this app's package. XML comments are ignored in both files, quotes may be single or double,
# and only the entries inside <privapp-permissions package="<the manifest's package>"> count.
# Used by build.sh; tested by test/check_whitelist_test.sh.
#
# Note: "android.*" permissions are exempt here as briefed, but a privileged android.* permission
# (signature|privileged) would need a whitelist entry too, so check by hand if one is ever added.

manifest=$1
xml=$2
[ -f "$manifest" ] && [ -f "$xml" ] || { echo "boot guard: usage: $0 MANIFEST WHITELIST_XML" >&2; exit 2; }
command -v perl >/dev/null 2>&1 || { echo "boot guard: perl is needed for this check" >&2; exit 2; }

pkg=$(perl -0777 -ne 's/<!--.*?-->//gs; print $1 if /<manifest[^>]*?\bpackage\s*=\s*["\x27]([^"\x27]*)["\x27]/' "$manifest")
[ -n "$pkg" ] || { echo "boot guard: no package name found in $manifest" >&2; exit 2; }

asked=$(perl -0777 -ne 's/<!--.*?-->//gs;
    while (/<uses-permission[^>]*?\bandroid:name\s*=\s*["\x27]([^"\x27]*)["\x27]/g) { print "$1\n" }' "$manifest")

allowed=$(PKG="$pkg" perl -0777 -ne 's/<!--.*?-->//gs;
    while (/<privapp-permissions\s+package\s*=\s*["\x27]\Q$ENV{PKG}\E["\x27]\s*>(.*?)<\/privapp-permissions>/gs) {
        my $block = $1;
        while ($block =~ /<permission\s+name\s*=\s*["\x27]([^"\x27]*)["\x27]/g) { print "$1\n" }
    }' "$xml")

missing=0
for p in $asked; do
    case "$p" in android.*) continue ;; esac
    if ! printf '%s\n' "$allowed" | grep -Fxq -- "$p"; then
        echo "boot guard: $p is in the manifest but not on the whitelist for $pkg in $xml: the stick would not boot" >&2
        missing=1
    fi
done
exit $missing
