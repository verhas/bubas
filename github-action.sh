#!/usr/bin/env bash
#
# Runs locally what .github/workflows/build.yml runs in CI, so a red build can be found before
# pushing rather than after.
#
# It is a copy of the workflow, not an approximation: the same Maven command with the same
# profile, the same mdship invocation over the same file list, and the same staleness gate. When
# the workflow changes, this changes with it or it stops being worth running.
#
#   ./github-action.sh            build, test, rehearse the release, check the documentation
#   ./github-action.sh --offline  the same, without asking Maven to reach the network
#   ./github-action.sh --skip-mvn just the documentation gate, when only prose changed
#
set -uo pipefail

cd "$(dirname "$0")"

# The workflow pins mdship, because the gate compares generated text and tool and author have to
# agree. See the comment on the step in build.yml.
readonly PINNED_MDSHIP=1.2.0

offline=""
skip_mvn=""
for arg in "$@"; do
    case "$arg" in
        --offline) offline="-o" ;;
        --skip-mvn) skip_mvn=1 ;;
        *) echo "unknown option: $arg" >&2; exit 2 ;;
    esac
done

step() { printf '\n\033[1m==> %s\033[0m\n' "$1"; }
fail() { printf '\033[31mFAILED: %s\033[0m\n' "$1" >&2; exit 1; }

# ---------------------------------------------------------------- what CI is given

step "Environment"
java -version 2>&1 | head -1
echo "CI builds on temurin 21; a different JDK here proves less than it looks."

command -v mdship >/dev/null || fail "mdship is not installed: pip install mdship==$PINNED_MDSHIP"
installed=$(mdship --version 2>&1 | grep -oE '[0-9]+\.[0-9]+\.[0-9]+' | head -1)
echo "mdship $installed (CI pins $PINNED_MDSHIP)"
if [ "$installed" != "$PINNED_MDSHIP" ]; then
    echo "WARNING: your mdship differs from the pin; the gate may agree here and not in CI."
fi
# A local editable checkout can report the pinned number and behave differently, which has bitten
# this project before. Say where the module actually comes from so the number can be trusted.
python3 -c 'import mdship, os; print("mdship module:", os.path.dirname(mdship.__file__))' 2>/dev/null

# ---------------------------------------------------------------- the build CI runs

if [ -z "$skip_mvn" ]; then
    step "Test, and rehearse the release"
    echo "mvn -B -P release verify -Dgpg.skip=true"
    mvn -B --no-transfer-progress $offline -P release verify -Dgpg.skip=true \
        || fail "the build or the tests"

    # CI uploads these and fails the step when they are missing, so their absence is a failure
    # here too rather than a quiet difference.
    step "The sample vocabulary export exists"
    for f in bubas-test/target/vocabulary.md bubas-test/target/vocabulary.json; do
        [ -s "$f" ] || fail "$f was not written by the test run"
        echo "  $f"
    done
else
    step "Skipping Maven (--skip-mvn)"
    echo "The documentation gate compares generated text against outputs the tests write."
    echo "Without a build those outputs are whatever the last build left behind."
fi

# ---------------------------------------------------------------- the documentation gate

step "Check the documentation is not stale"
# The same list as the workflow. Root documents other than README and SPEC are not gated by CI,
# and are reported below rather than checked, so this stays a copy of what CI does.
files=(README.md SPEC.md)
while IFS= read -r f; do files+=("$f"); done < <(find DOCUMENTATION -name '*.md' | sort)
echo "${#files[@]} files"

# Working-tree edits would make `git diff` fire for a reason CI never sees, so the comparison is
# against a snapshot taken now. Same question as CI asks — did regenerating change anything — and
# answerable while there is work in progress.
snapshot=$(mktemp -d)
trap 'rm -rf "$snapshot"' EXIT
for f in "${files[@]}"; do
    mkdir -p "$snapshot/$(dirname "$f")"
    cp "$f" "$snapshot/$f"
done

# mdship's exit code is the point: `mdship update` is the only command that verifies generated
# content against its recorded checksum. `mdship validate` checks structure and will pass on a
# file this rejects, so it is not a substitute and its output must never be discarded.
if ! mdship update "${files[@]}" 2>&1 | tee "$snapshot/mdship.log"; then
    grep '^Error:' "$snapshot/mdship.log" >&2 || true
    fail "mdship could not update the documentation"
fi
if grep -q '^Error:' "$snapshot/mdship.log"; then
    grep '^Error:' "$snapshot/mdship.log" >&2
    fail "mdship reported an integrity error"
fi

stale=0
for f in "${files[@]}"; do
    if ! diff -q "$snapshot/$f" "$f" >/dev/null; then
        [ "$stale" -eq 0 ] && echo
        echo "STALE: $f"
        diff -u "$snapshot/$f" "$f" | head -20
        stale=1
    fi
done
[ "$stale" -eq 0 ] || fail "the committed documentation no longer matches the code"

# ---------------------------------------------------------------- what CI does not check

step "Not gated by CI"
ungated=$(git ls-files '*.md' | grep -vE '^(README|SPEC)\.md$|^DOCUMENTATION/' || true)
if [ -n "$ungated" ]; then
    echo "These are outside the workflow's file list, so nothing checks them on a push:"
    echo "$ungated" | sed 's/^/  /'
fi

printf '\n\033[32mAll green — this is what CI will see.\033[0m\n'
