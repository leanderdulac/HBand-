#!/bin/bash
# Archive + export an App Store Connect IPA. Does not upload.
# Requires a Mac with Xcode, Team H87XXMXA5G, and (for a real Gemini key)
# iosApp/Config/Secrets.local.xcconfig — never commit that file.
#
#   ./iosApp/Scripts/embed-shared.sh
#   ./iosApp/Scripts/strip-vendor-archs.sh
#   ./iosApp/Scripts/archive-testflight.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
PROJECT="$ROOT/iosApp/Next2UPaciente.xcodeproj"
SCHEME="Next2UPaciente"
ARCHIVE="$ROOT/iosApp/build/Next2UPaciente.xcarchive"
EXPORT="$ROOT/iosApp/build/testflight-export"
OPTIONS="$ROOT/iosApp/Config/ExportOptions.plist"

if [ ! -d "$ROOT/iosApp/Vendor/shared.framework" ]; then
  echo "shared.framework ausente. Rode ./iosApp/Scripts/embed-shared.sh primeiro." >&2
  exit 1
fi

"$ROOT/iosApp/Scripts/check-secrets.sh"

mkdir -p "$ROOT/iosApp/build"
rm -rf "$ARCHIVE" "$EXPORT"

xcodebuild \
  -project "$PROJECT" \
  -scheme "$SCHEME" \
  -configuration Release \
  -destination "generic/platform=iOS" \
  -archivePath "$ARCHIVE" \
  -allowProvisioningUpdates \
  archive

xcodebuild \
  -exportArchive \
  -archivePath "$ARCHIVE" \
  -exportPath "$EXPORT" \
  -exportOptionsPlist "$OPTIONS" \
  -allowProvisioningUpdates

echo "IPA em $EXPORT (não enviado). Envie pelo Organizer ou Transporter."
