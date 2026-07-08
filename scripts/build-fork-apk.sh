#!/usr/bin/env bash
#
# Build the on-device fork APK and keep the last two builds around for easy
# rollback / A-B sideloading:
#
#   dist/lissen-fork.apk           <- the build you just made
#   dist/lissen-fork-previous.apk  <- the one before it
#
# Each run rotates current -> previous, then drops the fresh build in as current.
# The `personal` variant is used: it is release-like (non-debuggable, ART AOT) but
# signed with the local Android debug keystore, so it installs and updates on-device
# with no release-keystore setup. `release` is intentionally NOT used here — no fork
# keystore exists yet, so a release APK would be unsigned and refuse to install.
#
# APKs live under dist/ and are gitignored — we never commit binaries.
set -euo pipefail

REPO_ROOT="$(git -C "$(dirname "$0")" rev-parse --show-toplevel)"
cd "$REPO_ROOT"

VARIANT="${1:-personal}"                     # override e.g. `build-fork-apk.sh release` if a keystore exists
DIST_DIR="$REPO_ROOT/dist"
CURRENT="$DIST_DIR/lissen-fork.apk"
PREVIOUS="$DIST_DIR/lissen-fork-previous.apk"

# Capitalize first letter for the Gradle task name (assemblePersonal / assembleRelease)
TASK="assemble$(tr '[:lower:]' '[:upper:]' <<<"${VARIANT:0:1}")${VARIANT:1}"
BUILT_APK="$REPO_ROOT/app/build/outputs/apk/$VARIANT/app-$VARIANT.apk"

echo "==> ./gradlew $TASK"
./gradlew "$TASK"

if [[ ! -f "$BUILT_APK" ]]; then
  echo "error: expected APK not found at $BUILT_APK" >&2
  exit 1
fi

mkdir -p "$DIST_DIR"
if [[ -f "$CURRENT" ]]; then
  mv -f "$CURRENT" "$PREVIOUS"
fi
cp -f "$BUILT_APK" "$CURRENT"

echo
echo "==> dist/"
ls -lh "$DIST_DIR" | tail -n +2
