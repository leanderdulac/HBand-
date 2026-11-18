#!/bin/bash
# Builds the KMP shared.framework on a Mac and copies it next to the app.
# Linux CI does not run this. Required before the first Xcode archive.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
./gradlew :shared:linkReleaseFrameworkIosArm64 --no-configuration-cache
SRC="$ROOT/shared/build/bin/iosArm64/releaseFramework/shared.framework"
DEST="$ROOT/iosApp/Vendor/shared.framework"
rm -rf "$DEST"
cp -R "$SRC" "$DEST"
echo "Copied $SRC -> $DEST"
