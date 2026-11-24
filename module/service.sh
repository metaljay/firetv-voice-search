#!/system/bin/sh
# Voice Search: runs as root late in every boot (Magisk "late_start service").
#
# 1. Waits for the system, then gives the app the two things it needs.
# 2. Installs the setup app (the "Voice Search" tile app, which puts a tile on Amazon's home
#    screen; that screen hides system apps like ours) when it is missing or the add-on's copy is
#    newer. Never a downgrade.
# 3. Starts the app's service.
# 4. Every second (the app's "Alexa" hand-off, and the TiviMate helper), every 10 seconds (the rest):
#    - points the voice button at our app while the app is alive, and hands it
#      back to Alexa when the app is not (restarting the app);
#    - does what the app asked for in its updates folder: install a downloaded
#      update, go back to the kept previous version, remove the add-on (at the next
#      restart), or restart the stick;
#    - after an update, watches the first start of the new version and goes back
#      to the old one if the new one never runs;
#    - when the app has asked for it (a file called "alexa" in its folder), gives the voice
#      button to real Alexa for 15 seconds, then takes it back;
#    - switches the app's TiviMate helper (an accessibility helper limited to TiviMate) on
#      while TiviMate is the chosen app, and off again otherwise.
#
# The app (no root) checks GitHub, downloads and proves the zip is genuine. This
# script (root) does the rest. They talk through two files in the app's own
# private folder: the app writes "request", this script writes "status".

MODDIR=${0%/*}
PKG=com.metaljay.stremiovoice
SERVICE="$PKG/.VoiceService"
TAG=VoiceSearchModule
MODID=stremio_voice

# Writes to the system log, and also keeps the last ~200 lines in a file in the app's shared
# folder ("module.log"), which the app reads for "Send a problem report" (the app can't read
# this script's lines from the system log). Trimmed when it passes 300 lines.
say() {
    log -t "$TAG" "$1"
    [ -d "$UPD" ] || return 0
    _say_f="$UPD/module.log"   # _say_ names: say runs in the caller's shell, so a plain n or f would clobber its own
    [ -f "$_say_f" ] || { : > "$_say_f"; own "$_say_f"; }
    echo "$(date '+%m-%d %H:%M:%S') $1" >> "$_say_f"
    _say_n=$(wc -l < "$_say_f")
    if [ "${_say_n:-0}" -gt 300 ]; then
        tail -n 200 "$_say_f" > "$_say_f.tmp" && own "$_say_f.tmp" && mv -f "$_say_f.tmp" "$_say_f"
    fi
}

# ---------- updates ----------

UPD=/data/data/$PKG/files/update        # the app's folder for requests and downloads
BACKUP=/data/adb/stremio_voice_prev     # the previous version, kept for "Go back"
TRIAL=/data/adb/stremio_voice_trial     # present while a new version is on trial
NOTICE=/data/adb/stremio_voice_notice   # one-time "went back" message for the app
STAGED=/data/adb/modules_update/$MODID  # where Magisk looks, at the next boot, for a module to apply
LOG=/data/adb/stremio_voice_update.log
STAGE_TMP=/data/adb/stremio_voice_stage # where a version is unpacked and checked before it is put in place

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

# Magisk's own busybox: the unzip that Magisk itself unpacks modules with.
BB=/data/adb/magisk/busybox

# Left over from a crash part-way through staging: nothing there is ever used.
rm -rf "$STAGE_TMP" "$BACKUP.tmp"

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
        [ -f "$MODDIR/remove" ] && echo "removing=1"
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

# Makes the folder in $STAGE_TMP the module Magisk applies at the next boot. It was unpacked
# or copied elsewhere first and is moved in with ONE rename, so Magisk never sees a half-written
# copy, and nothing is written into the running module (not even its module.prop).
publish_stage() {
    fix_perms "$STAGE_TMP"
    mkdir -p "$(dirname "$STAGED")" || return 1
    rm -rf "$STAGED"
    mv "$STAGE_TMP" "$STAGED" || return 1
    touch "$MODDIR/update"
    return 0
}

# Stages the kept previous version.
stage_backup() {
    rm -rf "$STAGE_TMP"
    if ! cp -a "$BACKUP" "$STAGE_TMP" || ! publish_stage; then
        rm -rf "$STAGE_TMP"
        return 1
    fi
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
    [ -x "$BB" ] || { fail "Magisk's tools weren't found"; return 1; }
    props=$("$BB" unzip -p "$zip" module.prop 2>/dev/null | tr -d '\r')
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

    # Unpack into a temporary folder and check it there. Never META-INF (the signature files);
    # the module has no customize.sh, so nothing else Magisk's installer does is needed.
    # A version that was already staged, and the running module, are not touched until the
    # new one is complete and moved into place in one step.
    rm -rf "$STAGE_TMP"
    mkdir -p "$STAGE_TMP"
    if ! "$BB" unzip -o "$zip" -x 'META-INF/*' -d "$STAGE_TMP" > "$LOG" 2>&1 \
        || [ ! -f "$STAGE_TMP/module.prop" ] \
        || [ ! -f "$STAGE_TMP/system/priv-app/StremioVoice/StremioVoice.apk" ] \
        || [ "$(prop "$STAGE_TMP/module.prop" id)" != "$MODID" ] \
        || [ "$(prop "$STAGE_TMP/module.prop" versionCode)" != "$new_code" ] \
        || ! publish_stage; then
        rm -rf "$STAGE_TMP" "$BACKUP.tmp"
        fail "the update couldn't be unpacked"
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
    TRIAL_ON=0   # an update installed during a trial must not be undone by that trial's revert
    say "update $new_ver staged, restart to finish"
    return 0
}

# goback: stage the kept previous version. It is one step only: the copy is then removed.
do_goback() {
    if [ ! -f "$BACKUP/module.prop" ]; then
        fail "no earlier version is kept"
        return 1
    fi
    helper_off_for_version_change
    if ! stage_backup; then
        HELPER_LOCKED=0; LAST_WANT=""   # nothing was staged: let the helper follow the app's choice again
        fail "couldn't get the earlier version ready"
        return 1
    fi
    rm -f "$TRIAL" "$NOTICE"
    rm -rf "$BACKUP"
    TRIAL_ON=0   # likewise: going back during a trial must not be undone by a revert
    say "going back to $(prop "$STAGED/module.prop" version), restart to finish"
    return 0
}

# remove: take the add-on away at the next restart (the same "remove" file Magisk's own Remove
# button creates; uninstall.sh then takes our accessibility entry out). A restart is then allowed.
do_remove() {
    touch "$MODDIR/remove" || { fail "couldn't mark the add-on for removal"; return 1; }
    say "removal requested, restart to finish"
    return 0
}

# restart: only when something is waiting for it. The caller restarts after writing the status.
do_restart() {
    if [ ! -f "$STAGED/module.prop" ] && [ ! -f "$MODDIR/remove" ]; then
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
        goback|restart|ack|remove)
            LAST_TOKEN=$rest
            case "$cmd" in
                remove) do_remove ;;
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
    helper_off_for_version_change
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
        HELPER_LOCKED=0; LAST_WANT=""
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

# ---------- the TiviMate helper ----------

. "$MODDIR/a11y_list.sh"   # adds or removes our entry in Android's list of accessibility helpers

PREFS=/data/data/$PKG/shared_prefs/voice_search.xml
LAST_WANT=""
HELPER_LOCKED=0   # set when a version change is staged: the helper stays off until the restart

# pref KEY: a text setting the app saved (empty if not set).
pref() { sed -n "s/.*<string name=\"$1\">\(.*\)<\/string>.*/\1/p" "$PREFS" 2>/dev/null | head -n 1; }

# Makes Android's list match what the app is using: our helper is on only while TiviMate is
# the app the voice button searches in. Works only when that has changed since the last look
# (the settings tool is slow to start). Other apps' entries in the list are never touched.
sync_helper() {
    [ "$HELPER_LOCKED" = 1 ] && return 0
    # "active_target" is the app the voice button really searches in (the app writes it: the saved
    # choice, or the fallback when nothing is saved or the saved app is gone). Not written yet
    # (just booted, or an older version): leave things as they are.
    active=$(pref active_target)
    [ -z "$active" ] && return 0
    want=0
    [ "$active" = tivimate ] && want=1
    [ "$want" = "$LAST_WANT" ] && return 0
    LAST_WANT=$want
    if [ "$want" = 1 ]; then
        a11y_enable && say "TiviMate helper switched on"
    else
        a11y_disable && say "TiviMate helper switched off"
    fi
}

# Before a different version is staged (going back, or an automatic revert) our entry comes out:
# the other version knows nothing about it, and would leave it behind.
helper_off_for_version_change() {
    HELPER_LOCKED=1
    a11y_disable
    say "TiviMate helper switched off for the version change"
}

# ---------- the "Alexa" hand-off ----------

ALEXA_WINDOW=15   # seconds the voice button stays with real Alexa
ALEXA_LEFT=0      # seconds of that window still to go; 0 when the app has the button

# Called once a second. The app writes "alexa" to its folder when the listener says just "Alexa".
# Alexa's own text input can't be reached, so the next press goes to her: the diversion switch goes
# off now (it is read live, like when the app dies) and back on once the window has been counted
# down. Counted in these calls, not with the clock, so a clock jump can't stretch or cut it short.
check_alexa() {
    if [ -f "$UPD/alexa" ]; then
        rm -f "$UPD/alexa"
        setprop debug.alm.enable false
        ALEXA_LEFT=$ALEXA_WINDOW
        say "Alexa hand-off: the voice button is Alexa's for ${ALEXA_WINDOW} s"
    elif [ "$ALEXA_LEFT" -gt 0 ]; then
        ALEXA_LEFT=$((ALEXA_LEFT - 1))
        if [ "$ALEXA_LEFT" -eq 0 ] && pidof "$PKG" >/dev/null 2>&1; then
            setprop debug.alm.enable true
            say "Alexa hand-off over: the voice button is ours again"
        fi
    fi
}

# ---------- the tile app ----------

TILE_PKG=com.metaljay.voicesearchtile
TILE_APK="$MODDIR/tile/VoiceSearchTile.apk"
TILE_STAMP="$MODDIR/tile.sha256"
TILE_VERSION="$MODDIR/tile.version"   # the versionCode of the app in tile/, written by build.sh

. "$MODDIR/tile_check.sh"   # tile_should_install

# Installs the setup app when it is missing, or when the add-on's copy has a higher versionCode
# than the installed one (see tile_check.sh). Never a downgrade: the app is also installed on its
# own from app stores, and a newer copy stays.
install_tile() {
    [ -f "$TILE_APK" ] || return 0
    tile_should_install "$TILE_PKG" "$TILE_VERSION" || return 0
    new=$(sha256sum "$TILE_APK" | cut -d' ' -f1)
    # The package manager can't read the add-on's folder, so hand it a copy.
    tmp=/data/local/tmp/VoiceSearchTile.apk
    cp "$TILE_APK" "$tmp" && chmod 644 "$tmp"
    # If Android refuses the in-place update (a copy signed with another key), replace it
    # (the setup app holds no settings).
    if pm install -r "$tmp" >/dev/null 2>&1 \
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
    sync_helper

    if pidof "$PKG" >/dev/null 2>&1; then
        # While the button is lent to Alexa it stays hers; check_alexa takes it back.
        [ "$ALEXA_LEFT" -eq 0 ] && setprop debug.alm.enable true
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
    # Ten one-second waits instead of one ten-second wait, so the hand-off, and the helper after a
    # by-name TiviMate search, are noticed within a second.
    wait_tick=0
    while [ "$wait_tick" -lt 10 ]; do
        sleep 1
        check_alexa
        sync_helper
        wait_tick=$((wait_tick + 1))
    done
done
