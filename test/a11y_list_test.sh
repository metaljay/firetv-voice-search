#!/bin/sh
# Tests the list functions in module/a11y_list.sh with plain sh. Touches no stick and no real setting.
# Run: sh test/a11y_list_test.sh   (build.sh test does)
. "$(dirname "$0")/../module/a11y_list.sh"

fail=0
check() { # label expected actual
    if [ "$2" = "$3" ]; then echo "PASS $1 -> \"$3\""; else echo "FAIL $1 -> \"$3\" (expected \"$2\")"; fail=$((fail + 1)); fi
}
S="com.metaljay.stremiovoice/.TiviMateHelper"
F="com.metaljay.stremiovoice/com.metaljay.stremiovoice.TiviMateHelper"
O1="com.other.app/com.other.app.Service"
O2="com.third.app/.Reader"

# add
check "add: empty list" "$S" "$(a11y_list_add "")"
check "add: null" "$S" "$(a11y_list_add "null")"
check "add: another app's entry is kept, ours goes last" "$O1:$S" "$(a11y_list_add "$O1")"
check "add: two other entries keep their order" "$O1:$O2:$S" "$(a11y_list_add "$O1:$O2")"
check "add: ours already there, no duplicate" "$S" "$(a11y_list_add "$S")"
check "add: ours (full form) already there, no duplicate" "$F" "$(a11y_list_add "$F")"
check "add: ours between others, no duplicate" "$O1:$S:$O2" "$(a11y_list_add "$O1:$S:$O2")"
check "add: ours in other casing, no duplicate" "COM.METALJAY.STREMIOVOICE/.TIVIMATEHELPER" "$(a11y_list_add "COM.METALJAY.STREMIOVOICE/.TIVIMATEHELPER")"
check "add: ours with spaces, no duplicate" " $S " "$(a11y_list_add " $S ")"

# remove
check "remove: empty list" "" "$(a11y_list_remove "")"
check "remove: null" "" "$(a11y_list_remove "null")"
check "remove: ours only" "" "$(a11y_list_remove "$S")"
check "remove: ours only (full form)" "" "$(a11y_list_remove "$F")"
check "remove: ours plus another, other kept" "$O1" "$(a11y_list_remove "$S:$O1")"
check "remove: another, ours, another: others kept in order" "$O1:$O2" "$(a11y_list_remove "$O1:$S:$O2")"
check "remove: not there, others untouched" "$O1:$O2" "$(a11y_list_remove "$O1:$O2")"
check "remove: ours twice" "$O1" "$(a11y_list_remove "$S:$O1:$F")"
check "remove: other casing" "$O1" "$(a11y_list_remove "$O1:Com.MetalJay.StremioVoice/.tivimatehelper")"
check "remove: stray spaces" "$O1" "$(a11y_list_remove "$O1: $S ")"
check "remove: empty entries dropped" "$O1" "$(a11y_list_remove ":$O1::$S:")"
check "remove: another app's look-alike is kept" "com.metaljay.stremiovoice/.OtherService" "$(a11y_list_remove "com.metaljay.stremiovoice/.OtherService")"

# has
a11y_list_has "$S" && r=yes || r=no;                check "has: ours only" yes "$r"
a11y_list_has "$O1:$F" && r=yes || r=no;            check "has: ours (full form) among others" yes "$r"
a11y_list_has "$O1" && r=yes || r=no;               check "has: not there" no "$r"
a11y_list_has "null" && r=yes || r=no;              check "has: null" no "$r"
a11y_list_has "" && r=yes || r=no;                  check "has: empty" no "$r"
a11y_list_has "$O1:com.METALJAY.stremiovoice/.TIVIMATEhelper" && r=yes || r=no; check "has: other casing" yes "$r"

# add then remove gets back where it started
check "add then remove: round trip" "$O1:$O2" "$(a11y_list_remove "$(a11y_list_add "$O1:$O2")")"

# The functions run in the caller's shell: they must not overwrite the caller's own variables
# (a clobbered loop counter once made `n=$((n + 1))` in service.sh fail and kill the script).
n=5; l=L; e=E; old=O; out=U; list=I; prev=P; rest=R; f=F; tick=T
a11y_list_has "$O1:$S:$O2" && true
a11y_list_add "$O1" > /dev/null
a11y_list_remove "$O1:$S" > /dev/null
a11y_is_ours "$O1" && true
check "no caller variable is overwritten" "5 L E O U I P R F T" "$n $l $e $old $out $list $prev $rest $f $tick"

[ "$fail" = 0 ] && echo "All accessibility-list cases pass." || { echo "$fail accessibility-list case(s) FAILED."; exit 1; }
