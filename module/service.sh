#!/system/bin/sh
# Voice Search: runs as root late in every boot (Magisk "late_start service").
#
# 1. Waits for the system, then gives the app the two things it needs.
# 2. Installs (or updates) the small "Voice Search" tile app, which puts a
#    tile on Amazon's home screen; that screen hides system apps like ours.
# 3. Starts the app's service.
# 4. Every 10 seconds, points the voice button at our app while the app is alive,
#    and hands it back to Alexa when the app is not (restarting the app).

MODDIR=${0%/*}
PKG=com.metaljay.stremiovoice
SERVICE="$PKG/.VoiceService"
TAG=StremioVoiceModule

say() { log -t "$TAG" "$1"; }

TILE_PKG=com.metaljay.voicesearchtile
TILE_APK="$MODDIR/tile/VoiceSearchTile.apk"
TILE_STAMP="$MODDIR/tile.sha256"

# Installs the tile app when it is missing or the add-on carries a different copy.
install_tile() {
    [ -f "$TILE_APK" ] || return 0
    new=$(sha256sum "$TILE_APK" | cut -d' ' -f1)
    if pm path "$TILE_PKG" >/dev/null 2>&1 && [ "$new" = "$(cat "$TILE_STAMP" 2>/dev/null)" ]; then
        return 0
    fi
    # The package manager can't read the add-on's folder, so hand it a copy.
    tmp=/data/local/tmp/VoiceSearchTile.apk
    cp "$TILE_APK" "$tmp" && chmod 644 "$tmp"
    if pm install -r "$tmp" >/dev/null 2>&1; then
        echo "$new" > "$TILE_STAMP"
        say "tile app installed"
    else
        say "tile app install failed"
    fi
    rm -f "$tmp"
}

# Wait for the system to finish booting.
while [ "$(getprop sys.boot_completed)" != "1" ]; do
    sleep 3
done

# Wait until the package manager knows our app (give up after about two minutes).
tries=0
until pm path "$PKG" >/dev/null 2>&1; do
    tries=$((tries + 1))
    if [ "$tries" -gt 40 ]; then
        say "app not installed, leaving the voice button with Alexa"
        setprop debug.alm.enable false
        exit 0
    fi
    sleep 3
done

pm grant "$PKG" android.permission.RECORD_AUDIO
appops set "$PKG" SYSTEM_ALERT_WINDOW allow
install_tile
am start-foreground-service -n "$SERVICE" >/dev/null 2>&1
say "started"
sleep 3

while true; do
    if pidof "$PKG" >/dev/null 2>&1; then
        setprop debug.alm.enable true
    else
        # App is gone: Alexa gets the button back at once, then we try to bring the app back.
        setprop debug.alm.enable false
        say "app not running, button handed back to Alexa, restarting app"
        am start-foreground-service -n "$SERVICE" >/dev/null 2>&1
    fi
    sleep 10
done
