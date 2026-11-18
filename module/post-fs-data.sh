#!/system/bin/sh
# Voice Search: runs as root early in every boot (Magisk "post-fs-data"),
# before Android reads its list of apps.
#
# Android keeps a cache of each app's details and treats it as current while
# the cache file is newer than the app's folder. Through Magisk, the app's
# folder always shows the boot-time clock, which on Fire TV reads 1 Jan 2022
# for the first seconds of every boot, so the cache always looks newer and an
# updated app kept its old details (version, permissions, screens).
#
# Fix: when the app file differs from the one seen at the last boot, remove
# this app's cache entry only. Android rebuilds it from the app file.

MODDIR=${0%/*}
APK="$MODDIR/system/priv-app/StremioVoice/StremioVoice.apk"
STAMP="$MODDIR/apk.sha256"

[ -f "$APK" ] || exit 0

NEW=$(sha256sum "$APK" | cut -d' ' -f1)
OLD=$(cat "$STAMP" 2>/dev/null)

if [ "$NEW" != "$OLD" ]; then
    rm -f /data/system/package_cache/*/StremioVoice-*
    echo "$NEW" > "$STAMP"
    log -t StremioVoiceModule "app file changed: cleared its cached details"
fi
