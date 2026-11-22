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

# Names used inside these functions all start with _a_: they run in the caller's shell (not a
# subshell), so a plain name such as n or list would overwrite the caller's own variable of that name.

# Is this one entry ours?
a11y_is_ours() {
    _a_n=$(a11y_norm "$1")
    [ "$_a_n" = "$(a11y_norm "$A11Y_SHORT")" ] || [ "$_a_n" = "$(a11y_norm "$A11Y_FULL")" ]
}

# a11y_list_has LIST: succeeds if our entry is in LIST.
a11y_list_has() {
    _a_l=$1
    [ "$_a_l" = null ] && _a_l=""
    _a_old=$IFS; IFS=:
    for _a_e in $_a_l; do
        if a11y_is_ours "$_a_e"; then IFS=$_a_old; return 0; fi
    done
    IFS=$_a_old
    return 1
}

# a11y_list_remove LIST: prints LIST without our entry (empty if nothing is left).
a11y_list_remove() {
    _a_l=$1
    [ "$_a_l" = null ] && _a_l=""
    _a_out=""
    _a_old=$IFS; IFS=:
    for _a_e in $_a_l; do
        [ -z "$(a11y_norm "$_a_e")" ] && continue
        a11y_is_ours "$_a_e" && continue
        _a_out="${_a_out:+$_a_out:}$_a_e"
    done
    IFS=$_a_old
    printf '%s' "$_a_out"
}

# a11y_list_add LIST: prints LIST with our entry added at the end, unless it is already there.
a11y_list_add() {
    _a_l=$1
    [ "$_a_l" = null ] && _a_l=""
    if a11y_list_has "$_a_l"; then
        printf '%s' "$_a_l"
    elif [ -z "$_a_l" ]; then
        printf '%s' "$A11Y_SHORT"
    else
        printf '%s:%s' "$_a_l" "$A11Y_SHORT"
    fi
}

# Switches our helper on. Keeps what accessibility_enabled was, the first time.
a11y_enable() {
    _a_list=$(settings get secure enabled_accessibility_services)
    a11y_list_has "$_a_list" && return 0
    if [ ! -f "$A11Y_PREV" ]; then
        _a_prev=$(settings get secure accessibility_enabled)
        case "$_a_prev" in 1) ;; *) _a_prev=0 ;; esac
        echo "$_a_prev" > "$A11Y_PREV"
    fi
    settings put secure enabled_accessibility_services "$(a11y_list_add "$_a_list")"
    settings put secure accessibility_enabled 1
}

# Switches our helper off. When ours was the only entry, the list is cleared and
# accessibility_enabled goes back to what it was before we turned it on.
a11y_disable() {
    _a_list=$(settings get secure enabled_accessibility_services)
    a11y_list_has "$_a_list" || return 0
    _a_rest=$(a11y_list_remove "$_a_list")
    if [ -z "$_a_rest" ]; then
        settings delete secure enabled_accessibility_services
        settings put secure accessibility_enabled "$(cat "$A11Y_PREV" 2>/dev/null || echo 0)"
    else
        settings put secure enabled_accessibility_services "$_a_rest"
    fi
    rm -f "$A11Y_PREV"
}
