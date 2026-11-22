#!/bin/sh
# Fails if a GitHub access token (github_pat_... or ghp_... and its relatives) is in any file git
# tracks in the folder given (default: this script's folder). The problem-report token must
# only ever live outside the repository (see README "Reporting a problem" / build.sh).
# The patterns are written so that this file does not match itself.

dir=${1:-$(dirname "$0")}
cd "$dir" || exit 2
git rev-parse --git-dir >/dev/null 2>&1 || { echo "token scan: $dir is not a git folder" >&2; exit 2; }

hits=$(git ls-files -z | xargs -0 grep -IlE 'github_pat_[A-Za-z0-9_]{20,}|gh[pousr]_[A-Za-z0-9]{20,}' 2>/dev/null)
if [ -n "$hits" ]; then
    echo "token scan: a GitHub token-like string is in these tracked files (value not shown):" >&2
    echo "$hits" >&2
    exit 1
fi
exit 0
