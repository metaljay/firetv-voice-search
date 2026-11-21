#!/system/bin/sh
# Voice Search: run by Magisk when the add-on is removed.
# Takes our entry out of Android's list of enabled accessibility helpers (the TiviMate helper),
# and nothing else. Magisk runs this early in the boot, before Android's settings are
# available, so the work is done in the background once the boot has finished.
# (It does not run when the add-on is only switched off with the "disable" file.)

. "${0%/*}/a11y_list.sh"   # read now: the module folder is gone by the time the background part runs

(
    while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 3; done
    a11y_disable
) >/dev/null 2>&1 &
