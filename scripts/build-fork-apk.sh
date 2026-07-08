#!/usr/bin/env bash
#
# Build the on-device fork APK and keep the last two builds around for easy
# rollback / A-B sideloading:
#
#   <dest>/lissen-fork.apk           <- the build you just made
#   <dest>/lissen-fork-previous.apk  <- the one before it
#
# Each run rotates current -> previous, then drops the fresh build in as current.
# Destination defaults to the Obsidian apk folder (which syncs to the phone for
# install); override with LISSEN_APK_DIR for a different machine/workflow.
#
# The `personal` variant is used: it is release-like (non-debuggable, ART AOT) but
# signed with the local Android debug keystore, so it installs and updates on-device
# with no release-keystore setup. `release` is intentionally NOT used here — no fork
# keystore exists yet, so a release APK would be unsigned and refuse to install.
set -euo pipefail

REPO_ROOT="$(git -C "$(dirname "$0")" rev-parse --show-toplevel)"
cd "$REPO_ROOT"

VARIANT="${1:-personal}"                          # override e.g. `build-fork-apk.sh release` if a keystore exists
DEST_DIR="${LISSEN_APK_DIR:-$HOME/Obsidian/apk}"  # synced to the phone; override per machine
CURRENT="$DEST_DIR/lissen-fork.apk"
PREVIOUS="$DEST_DIR/lissen-fork-previous.apk"

# Capitalize first letter for the Gradle task name (assemblePersonal / assembleRelease)
TASK="assemble$(tr '[:lower:]' '[:upper:]' <<<"${VARIANT:0:1}")${VARIANT:1}"
BUILT_APK="$REPO_ROOT/app/build/outputs/apk/$VARIANT/app-$VARIANT.apk"

echo "==> ./gradlew $TASK"
./gradlew "$TASK"

if [[ ! -f "$BUILT_APK" ]]; then
  echo "error: expected APK not found at $BUILT_APK" >&2
  exit 1
fi

mkdir -p "$DEST_DIR"
if [[ -f "$CURRENT" ]]; then
  mv -f "$CURRENT" "$PREVIOUS"
fi
cp -f "$BUILT_APK" "$CURRENT"

echo
echo "==> $DEST_DIR"
ls -lh "$DEST_DIR"/lissen-fork*.apk
