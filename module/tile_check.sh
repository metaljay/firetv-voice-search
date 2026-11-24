#!/system/bin/sh
# Voice Search: the rule for the setup app ("tile" app) the add-on carries. Sourced by service.sh
# and by test/tile_check_test.sh.
#
# The setup app is also an ordinary app people can install themselves (from an app store), so the
# add-on must never downgrade or overwrite a newer copy. It installs its copy only when the app is
# missing, or when its copy has a higher versionCode. build.sh writes that code into "tile.version".
#
# Variable names start with _tc_ because these run in the caller's shell (see a11y_list.sh).

# tile_installed_code PKG: the installed versionCode of PKG, or nothing when it isn't installed.
tile_installed_code() {
    pm list packages --show-versioncode "$1" 2>/dev/null \
        | grep "^package:$1 " | sed -n 's/.*versionCode:\([0-9][0-9]*\).*/\1/p' | head -n 1
}

# tile_should_install PKG VERSION_FILE: succeeds when the app should be installed (or updated).
tile_should_install() {
    _tc_have=$(tile_installed_code "$1")
    [ -z "$_tc_have" ] && return 0                      # missing: install
    _tc_new=$([ -f "$2" ] && tr -d '\r\n ' < "$2")
    case "$_tc_new" in ''|*[!0-9]*) return 1 ;; esac    # no usable version file: leave what is there
    [ "$_tc_new" -gt "$_tc_have" ]                      # only a higher code installs
}
