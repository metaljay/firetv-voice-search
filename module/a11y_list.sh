#!/system/bin/sh
# Voice Search: the code that adds or removes our entry (the TiviMate helper) in Android's list
# of enabled accessibility helpers. Used by service.sh and uninstall.sh. The list functions only
# work on text, so they are tested on the Mac with plain sh (test/a11y_list_test.sh). The
# a11y_enable / a11y_disable functions at the bottom do the real changes with `settings`.
#
# The list is one string of entries joined by ":"; "null" or empty means no entries. Our entry may
# be written two ways ("pkg/.Class" or "pkg/pkg.Class"), so both count as ours, ignoring case
# and stray spaces. Other apps' entries are never changed and keep their order.

A11Y_PKG=com.metaljay.stremiovoice
A11Y_SHORT="$A11Y_PKG/.TiviMateHelper"
A11Y_FULL="$A11Y_PKG/$A11Y_PKG.TiviMateHelper"
# What accessibility_enabled was before we first switched it on (so it goes back to that).
A11Y_PREV=/data/adb/stremio_voice_a11y_prev

# An entry without spaces and in lower case, for comparing.
a11y_norm() { printf '%s' "$1" | tr -d ' \t\r\n' | tr 'A-Z' 'a-z'; }

# Is this one entry ours?
a11y_is_ours() {
    n=$(a11y_norm "$1")
    [ "$n" = "$(a11y_norm "$A11Y_SHORT")" ] || [ "$n" = "$(a11y_norm "$A11Y_FULL")" ]
}

# a11y_list_has LIST: succeeds if our entry is in LIST.
a11y_list_has() {
    l=$1
    [ "$l" = null ] && l=""
    old=$IFS; IFS=:
    for e in $l; do
        if a11y_is_ours "$e"; then IFS=$old; return 0; fi
    done
    IFS=$old
    return 1
}

# a11y_list_remove LIST: prints LIST without our entry (empty if nothing is left).
a11y_list_remove() {
    l=$1
    [ "$l" = null ] && l=""
    out=""
    old=$IFS; IFS=:
    for e in $l; do
        [ -z "$(a11y_norm "$e")" ] && continue
        a11y_is_ours "$e" && continue
        out="${out:+$out:}$e"
    done
    IFS=$old
    printf '%s' "$out"
}

# a11y_list_add LIST: prints LIST with our entry added at the end, unless it is already there.
a11y_list_add() {
    l=$1
    [ "$l" = null ] && l=""
    if a11y_list_has "$l"; then
        printf '%s' "$l"
    elif [ -z "$l" ]; then
        printf '%s' "$A11Y_SHORT"
    else
        printf '%s:%s' "$l" "$A11Y_SHORT"
    fi
}

# Switches our helper on. Keeps what accessibility_enabled was, the first time.
a11y_enable() {
    list=$(settings get secure enabled_accessibility_services)
    a11y_list_has "$list" && return 0
    if [ ! -f "$A11Y_PREV" ]; then
        prev=$(settings get secure accessibility_enabled)
        case "$prev" in 1) ;; *) prev=0 ;; esac
        echo "$prev" > "$A11Y_PREV"
    fi
    settings put secure enabled_accessibility_services "$(a11y_list_add "$list")"
    settings put secure accessibility_enabled 1
}

# Switches our helper off. When ours was the only entry, the list is cleared and
# accessibility_enabled goes back to what it was before we turned it on.
a11y_disable() {
    list=$(settings get secure enabled_accessibility_services)
    a11y_list_has "$list" || return 0
    rest=$(a11y_list_remove "$list")
    if [ -z "$rest" ]; then
        settings delete secure enabled_accessibility_services
        settings put secure accessibility_enabled "$(cat "$A11Y_PREV" 2>/dev/null || echo 0)"
    else
        settings put secure enabled_accessibility_services "$rest"
    fi
    rm -f "$A11Y_PREV"
}
