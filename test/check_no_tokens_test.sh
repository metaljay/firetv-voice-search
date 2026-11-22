#!/bin/sh
# Tests for check_no_tokens.sh. Plain sh; run by build.sh test.
cd "$(dirname "$0")/.." || exit 1
ROOT=$(pwd)
T=build/token-scan-test
rm -rf "$T"; mkdir -p "$T"
fails=0

expect() { # expect LABEL WANT_EXIT FOLDER
    sh "$ROOT/check_no_tokens.sh" "$3" >"$ROOT/$T/out" 2>&1
    got=$?
    if [ "$got" = "$2" ]; then echo "PASS $1"; else echo "FAIL $1 (exit $got, wanted $2)"; cat "$ROOT/$T/out"; fails=$((fails + 1)); fi
}

mkrepo() { rm -rf "$ROOT/$T/r"; mkdir -p "$ROOT/$T/r"; (cd "$ROOT/$T/r" && git init -q); }
fake_pat="github_pa""t_$(printf 'A1b2C3d4E5%.0s' 1 2 3)"
fake_ghp="gh""p_$(printf 'Z9y8X7w6V5%.0s' 1 2 3)"

mkrepo; echo "nothing secret here" > "$T/r/a.txt"; (cd "$T/r" && git add a.txt)
expect "clean repository" 0 "$T/r"

mkrepo; echo "key $fake_pat" > "$T/r/b.txt"; (cd "$T/r" && git add b.txt)
expect "fine-grained token in a tracked file" 1 "$T/r"

mkrepo; echo "key $fake_ghp" > "$T/r/c.txt"; (cd "$T/r" && git add c.txt)
expect "classic token in a tracked file" 1 "$T/r"

mkrepo; echo "key $fake_pat" > "$T/r/d.txt"
expect "token only in an untracked file is not scanned" 0 "$T/r"

mkrepo; echo "the prefix github_pa""t_ alone and ghp_ short" > "$T/r/e.txt"; (cd "$T/r" && git add e.txt)
expect "prefix without a token body" 0 "$T/r"

expect "this project's own tracked files" 0 "."
outside=$(mktemp -d)
expect "not a git folder: a warning, but the build goes on" 0 "$outside"
grep -q "not a git folder" "$ROOT/$T/out" && echo "PASS ...and it says so" || { echo "FAIL no warning printed"; fails=$((fails + 1)); }
rmdir "$outside"

[ "$fails" = 0 ] && echo "All token-scan cases pass." || { echo "$fails token-scan case(s) FAILED."; exit 1; }
