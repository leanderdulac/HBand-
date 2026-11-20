#!/bin/bash
# Compiles vendored MJExtension (the Veepoo demo drop) for macOS and calls the
# selectors that crashed build 23. Does not need a device; the SDK binary is
# iPhoneOS-only, so this proves the categories themselves, not the SDK .o.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SRC="$ROOT/iosApp/Vendor/MJExtension/MJExtension"
OUT="$(mktemp -d /tmp/n2u-mjext-test.XXXXXX)"
trap 'rm -rf "$OUT"' EXIT

clang -fobjc-arc -fmodules -framework Foundation \
  -I "$SRC" \
  "$SRC"/*.m \
  "$ROOT/iosApp/Scripts/test-mjextension-selectors.m" \
  -o "$OUT/test-mjextension-selectors"

"$OUT/test-mjextension-selectors"
