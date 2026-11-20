#!/system/bin/sh
# Voice Search: runs as root late in every boot (Magisk "late_start service").
#
# 1. Waits for the system, then gives the app the two things it needs.
# 2. Installs (or updates) the small "Voice Search" tile app, which puts a
#    tile on Amazon's home screen; that screen hides system apps like ours.
# 3. Starts the app's service.
# 4. Every 10 seconds:
#    - points the voice button at our app while the app is alive, and hands it
#      back to Alexa when the app is not (restarting the app);
#    - does what the app asked for in its updates folder: install a downloaded
#      update, go back to the kept previous version, or restart the stick;
#    - after an update, watches the first start of the new version and goes back
#      to the old one if the new one never runs.
#
# The app (no root) checks GitHub, downloads and proves the zip is genuine. This
# script (root) does the rest. They talk through two files in the app's own
# private folder: the app writes "request", this script writes "status".

MODDIR=${0%/*}
PKG=com.metaljay.stremiovoice
SERVICE="$PKG/.VoiceService"
TAG=StremioVoiceModule
MODID=stremio_voice

say() { log -t "$TAG" "$1"; }

# ---------- updates ----------

UPD=/data/data/$PKG/files/update        # the app's folder for requests and downloads
BACKUP=/data/adb/stremio_voice_prev     # the previous version, kept for "Go back"
TRIAL=/data/adb/stremio_voice_trial     # present while a new version is on trial
NOTICE=/data/adb/stremio_voice_notice   # one-time "went back" message for the app
STAGED=/data/adb/modules_update/$MODID  # where Magisk waits to apply a module at the next boot
LOG=/data/adb/stremio_voice_update.log

LAST_TOKEN=""
LAST_RESULT=""
LAST_ERROR=""

# prop FILE KEY: the value of KEY=value in FILE.
prop() { grep "^$2=" "$1" 2>/dev/null | head -n 1 | cut -d= -f2- | tr -d '\r'; }

RUN_VER=$(prop "$MODDIR/module.prop" version)
RUN_CODE=$(prop "$MODDIR/module.prop" versionCode)

# A kept copy that isn't older than the running version (for example after a zip was
# installed by hand) isn't a "previous version": drop it, so Go back never shows it.
if [ -f "$BACKUP/module.prop" ]; then
    b_code=$(prop "$BACKUP/module.prop" versionCode)
    if [ "${b_code:-0}" -ge "${RUN_CODE:-0}" ] 2>/dev/null; then rm -rf "$BACKUP"; fi
fi

MAGISK=""
for m in "$(command -v magisk 2>/dev/null)" /data/adb/magisk/magisk /data/adb/magisk/magisk32 /data/adb/magisk/magisk64; do
    if [ -n "$m" ] && [ -x "$m" ]; then MAGISK=$m; break; fi
done

fail() { LAST_ERROR="$1"; say "update: $1"; return 1; }

# Gives a file the owner and SELinux label of the app's folder, so the app can read it.
own() {
    uid=$(stat -c %u "$UPD" 2>/dev/null)
    [ -n "$uid" ] && chown "$uid:$uid" "$1"
    chcon --reference="$UPD" "$1" 2>/dev/null
    chmod 600 "$1"
}

# Tells the app where things stand, if that has changed. Called every round, so the
# status is also fresh after a restart (it would otherwise still describe the old version).
write_status() {
    [ -d "$UPD" ] || return 0
    tmp="$UPD/status.tmp"
    {
        echo "running=$RUN_VER"
        [ -f "$STAGED/module.prop" ] && echo "staged=$(prop "$STAGED/module.prop" version)"
        [ -f "$BACKUP/module.prop" ] && echo "prev=$(prop "$BACKUP/module.prop" version)"
        # The notice file holds the message, then the version that failed (the app skips it).
        [ -f "$NOTICE" ] && echo "notice=$(sed -n 1p "$NOTICE")" && echo "notice_version=$(sed -n 2p "$NOTICE")"
        echo "last_token=$LAST_TOKEN"
        echo "result=$LAST_RESULT"
        echo "error=$LAST_ERROR"
    } > "$tmp"
    if cmp -s "$tmp" "$UPD/status"; then
        rm -f "$tmp"
        return 0
    fi
    own "$tmp"
    mv -f "$tmp" "$UPD/status"
}

# Gives a module folder the owner, modes and label Magisk gives modules it installs.
fix_perms() {
    chown -R 0:0 "$1"
    find "$1" -type d -exec chmod 755 {} \;
    find "$1" -type f -exec chmod 644 {} \;
    find "$1" -type f -name '*.sh' -exec chmod 755 {} \;
    chcon -R u:object_r:system_file:s0 "$1" 2>/dev/null
}

# Puts a copy of the kept previous version where Magisk applies it at the next boot.
stage_backup() {
    rm -rf "$STAGED"
    mkdir -p "$(dirname "$STAGED")"
    if ! cp -a "$BACKUP" "$STAGED"; then
        rm -rf "$STAGED"
        return 1
    fi
    fix_perms "$STAGED"
    touch "$MODDIR/update"
    return 0
}

# install SHA256: install the zip the app downloaded (the app has already proved it genuine).
do_install() {
    sha=$1
    zip="$UPD/update.zip"
    case "$sha" in
        *[!0-9a-f]*|"") fail "bad request"; return 1 ;;
    esac
    [ "${#sha}" -eq 64 ] || { fail "bad request"; return 1; }
    [ -f "$zip" ] || { fail "no download was found"; return 1; }
    got=$(sha256sum "$zip" | cut -d' ' -f1)
    if [ "$got" != "$sha" ]; then
        rm -f "$zip"
        fail "the download changed on the way"
        return 1
    fi
    props=$(unzip -p "$zip" module.prop 2>/dev/null | tr -d '\r')
    new_id=$(echo "$props" | grep '^id=' | head -n 1 | cut -d= -f2-)
    new_ver=$(echo "$props" | grep '^version=' | head -n 1 | cut -d= -f2-)
    new_code=$(echo "$props" | grep '^versionCode=' | head -n 1 | cut -d= -f2-)
    if [ "$new_id" != "$MODID" ] || [ -z "$new_ver" ]; then
        rm -f "$zip"
        fail "the download isn't a Voice Search update"
        return 1
    fi
    if [ "${new_code:-0}" -le "${RUN_CODE:-0}" ] 2>/dev/null; then
        rm -f "$zip"
        fail "the download isn't newer than this version"
        return 1
    fi

    # Keep the running version first, to a temporary place, so a failed install
    # leaves any earlier "Go back" copy alone. The stamps are dropped on purpose:
    # without them the cached app details refresh when this copy is restored.
    rm -rf "$BACKUP.tmp"
    if ! cp -a "$MODDIR" "$BACKUP.tmp"; then
        rm -rf "$BACKUP.tmp"
        fail "couldn't keep the current version"
        return 1
    fi
    rm -f "$BACKUP.tmp/apk.sha256" "$BACKUP.tmp/tile.sha256" "$BACKUP.tmp/update" \
        "$BACKUP.tmp/remove" "$BACKUP.tmp/disable"

    # Stage the new version for the next boot. Nothing running is touched.
    rm -rf "$STAGED"
    installed=0
    if [ -n "$MAGISK" ] && "$MAGISK" --help 2>&1 | grep -q -- '--install-module'; then
        "$MAGISK" --install-module "$zip" > "$LOG" 2>&1 && installed=1
    elif [ -x /data/adb/magisk/busybox ]; then
        # Older Magisk without --install-module: unpack the zip ourselves (never META-INF).
        mkdir -p "$STAGED" && /data/adb/magisk/busybox unzip -o "$zip" -x 'META-INF/*' -d "$STAGED" > "$LOG" 2>&1 \
            && fix_perms "$STAGED" && touch "$MODDIR/update" && installed=1
    fi
    if [ "$installed" != 1 ] \
        || [ ! -f "$STAGED/module.prop" ] \
        || [ ! -f "$STAGED/system/priv-app/StremioVoice/StremioVoice.apk" ] \
        || [ "$(prop "$STAGED/module.prop" versionCode)" != "$new_code" ]; then
        rm -rf "$STAGED" "$BACKUP.tmp"
        rm -f "$MODDIR/update"
        fail "Magisk couldn't install it"
        return 1
    fi

    rm -rf "$BACKUP"
    mv "$BACKUP.tmp" "$BACKUP"
    {
        echo "new_ver=$new_ver"
        echo "new_code=$new_code"
        echo "prev_ver=$RUN_VER"
        echo "prev_code=$RUN_CODE"
    } > "$TRIAL"
    chmod 600 "$TRIAL"
    rm -f "$zip" "$NOTICE"
    say "update $new_ver staged, restart to finish"
    return 0
}

# goback: stage the kept previous version. It is one step only: the copy is then removed.
do_goback() {
    if [ ! -f "$BACKUP/module.prop" ]; then
        fail "no earlier version is kept"
        return 1
    fi
    if ! stage_backup; then
        fail "couldn't get the earlier version ready"
        return 1
    fi
    rm -f "$TRIAL" "$NOTICE"
    rm -rf "$BACKUP"
    say "going back to $(prop "$STAGED/module.prop" version), restart to finish"
    return 0
}

# restart: only when something is waiting for it. The caller restarts after writing the status.
do_restart() {
    if [ ! -f "$STAGED/module.prop" ]; then
        fail "nothing is waiting for a restart"
        return 1
    fi
    RESTART_NOW=1
    return 0
}

# Handles one request from the app, if there is one.
handle_request() {
    [ -f "$UPD/request" ] || return 0
    line=$(head -n 1 "$UPD/request" | tr -d '\r')
    rm -f "$UPD/request"
    cmd=${line%% *}
    rest=${line#* }
    LAST_ERROR=""
    RESTART_NOW=0
    case "$cmd" in
        install)
            arg=${rest%% *}
            LAST_TOKEN=${rest#* }
            do_install "$arg"
            ;;
        goback|restart|ack)
            LAST_TOKEN=$rest
            case "$cmd" in
                goback) do_goback ;;
                restart) do_restart ;;
                ack) rm -f "$NOTICE" ;;
            esac
            ;;
        *)
            LAST_TOKEN=$rest
            fail "unknown request"
            ;;
    esac
    if [ $? -eq 0 ]; then LAST_RESULT=ok; else LAST_RESULT=failed; fi
    write_status
    if [ "$RESTART_NOW" = 1 ]; then
        say "restarting at the user's request"
        sync
        reboot
    fi
}

# The new version never ran properly: stage the kept old one and tell the user.
# The stick is never restarted by itself; someone may be watching a programme.
do_revert() {
    t_new=$(prop "$TRIAL" new_ver)
    t_prev=$(prop "$TRIAL" prev_ver)
    rm -f "$TRIAL"
    if [ -f "$BACKUP/module.prop" ] && stage_backup; then
        printf '%s\n%s\n' "Went back to $t_prev because $t_new didn’t start." "$t_new" > "$NOTICE"
        chmod 600 "$NOTICE"
        rm -rf "$BACKUP"
        say "$t_new didn't start; $t_prev is staged, waiting for a restart"
        # Posted as the "shell" user: Android drops notifications posted by root.
        SU=$(command -v su 2>/dev/null)
        [ -z "$SU" ] && SU=/product/bin/su
        "$SU" 2000 -c "cmd notification post -S bigtext -t 'Voice Search' voice-search-revert 'Voice Search $t_new didn’t start. Restart to go back to $t_prev.'" >/dev/null 2>&1
    else
        say "$t_new didn't start and there is no earlier version to go back to"
    fi
    write_status
}

# Is this the first run of a version that was just installed by an update?
TRIAL_ON=0
if [ -f "$TRIAL" ]; then
    t_code=$(prop "$TRIAL" new_code)
    if [ "$t_code" = "$RUN_CODE" ]; then
        TRIAL_ON=1
    elif [ "${t_code:-0}" -gt "${RUN_CODE:-0}" ] 2>/dev/null && [ -d "$STAGED" ]; then
        :   # the update is staged but the stick hasn't restarted yet
    else
        rm -f "$TRIAL"  # left over from something else
    fi
fi

# ---------- the tile app ----------

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
    # -d lets going back install an older tile; if Android refuses that, replace it
    # (the tile holds no settings).
    if pm install -r -d "$tmp" >/dev/null 2>&1 \
        || { pm uninstall "$TILE_PKG" >/dev/null 2>&1; pm install "$tmp" >/dev/null 2>&1; }; then
        echo "$new" > "$TILE_STAMP"
        say "tile app installed"
    else
        say "tile app install failed"
    fi
    rm -f "$tmp"
}

# ---------- start ----------

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
        [ "$TRIAL_ON" = 1 ] && do_revert
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

alive_run=0   # checks in a row that the app was running
dead=0        # checks that found it not running (trial only)
ticks=0       # checks since the trial began
while true; do
    handle_request
    write_status

    if pidof "$PKG" >/dev/null 2>&1; then
        setprop debug.alm.enable true
        alive_run=$((alive_run + 1))
    else
        # App is gone: Alexa gets the button back at once, then we try to bring the app back.
        setprop debug.alm.enable false
        say "app not running, button handed back to Alexa, restarting app"
        am start-foreground-service -n "$SERVICE" >/dev/null 2>&1
        alive_run=0
        dead=$((dead + 1))
    fi

    if [ "$TRIAL_ON" = 1 ]; then
        ticks=$((ticks + 1))
        if [ "$alive_run" -ge 12 ]; then
            # Running steadily for two minutes: the new version is good.
            rm -f "$TRIAL"
            TRIAL_ON=0
            say "new version runs well, trial over"
        elif [ "$dead" -ge 3 ] || [ "$ticks" -ge 36 ]; then
            do_revert
            TRIAL_ON=0
        fi
    fi
    sleep 10
done
