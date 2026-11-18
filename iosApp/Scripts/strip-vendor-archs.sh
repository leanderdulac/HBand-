#!/bin/bash
# Keep only arm64 device slices in vendored Veepoo / JieLi / GRD / ZipZap / FMDB
# binaries. App Store upload rejects armv7 (and i386 / x86_64 when they sit in a
# plain .framework, not an xcframework).
#
# Policy:
#   - arm64 device: keep
#   - armv7 / armv7s / i386 / x86_64: strip from .framework / .a
#   - arm64-simulator: keep only inside an .xcframework (this tree has none)
#
# On a Mac, prefers `lipo`. On Linux (no lipo), thins the fat header in Python
# so the committed binaries are already arm64-only.
#
# Usage:
#   ./iosApp/Scripts/strip-vendor-archs.sh
# Verify on a Mac:
#   lipo -info iosApp/Vendor/GRDFUSDK.framework/GRDFUSDK \
#              iosApp/Vendor/JL_BLEKit.framework/JL_BLEKit \
#              iosApp/Vendor/JLDialUnit.framework/JLDialUnit \
#              iosApp/Vendor/ZipZap.framework/ZipZap \
#              iosApp/Vendor/ABParTool.framework/ABParTool \
#              iosApp/Vendor/VeepooBleSDK.framework/VeepooBleSDK \
#              iosApp/Vendor/FMDB/libFMDB.a
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
VENDOR="$ROOT/iosApp/Vendor"

log() { printf '%s\n' "$*"; }

is_xcframework_member() {
  case "$1" in
    *.xcframework/*) return 0 ;;
    *) return 1 ;;
  esac
}

archs_of() {
  local path="$1"
  if command -v lipo >/dev/null 2>&1; then
    lipo -info "$path" 2>/dev/null || true
    return
  fi
  python3 - "$path" <<'PY'
import struct, sys
path = sys.argv[1]
raw = open(path, "rb").read(8)
magic = struct.unpack(">I", raw[:4])[0]
FAT, FAT_CIGAM = 0xCAFEBABE, 0xBEBAFECA
ARM64 = 16777228
names = {7: "i386", 12: "arm", 16777223: "x86_64", ARM64: "arm64"}
if magic in (FAT, FAT_CIGAM):
    be = magic == FAT
    endian = ">" if be else "<"
    blob = open(path, "rb").read()
    n = struct.unpack(endian + "I", blob[4:8])[0]
    off = 8
    found = []
    for _ in range(n):
        cputype, cpusub, _o, _s, _a = struct.unpack(endian + "5I", blob[off:off + 20])
        if cputype == 12:
            found.append({9: "armv7", 11: "armv7s"}.get(cpusub, f"arm/{cpusub}"))
        else:
            found.append(names.get(cputype, hex(cputype)))
        off += 20
    print(f"{path}: fat ({len(found)}): " + " ".join(found))
elif raw[:8] == b"!<arch>\n":
    print(f"{path}: thin ar archive (objects should be arm64)")
else:
    print(f"{path}: thin Mach-O (not fat)")
PY
}

thin_arm64() {
  local path="$1"
  if is_xcframework_member "$path"; then
    log "keep xcframework member: $path"
    return
  fi
  if command -v lipo >/dev/null 2>&1; then
    if lipo -info "$path" 2>/dev/null | grep -q "Architectures in the fat file"; then
      if lipo -info "$path" | grep -qw arm64; then
        local tmp="${path}.arm64"
        lipo -thin arm64 "$path" -output "$tmp"
        mv "$tmp" "$path"
        log "thinned (lipo) $path"
        return
      fi
      log "SKIP no arm64 slice: $path"
      return
    fi
    log "already thin: $path"
    return
  fi
  python3 - "$path" <<'PY'
import os, struct, sys
path = sys.argv[1]
data = open(path, "rb").read()
magic = struct.unpack(">I", data[:4])[0]
FAT, FAT_CIGAM = 0xCAFEBABE, 0xBEBAFECA
ARM64 = 16777228
if magic not in (FAT, FAT_CIGAM):
    print("already thin: " + path)
    raise SystemExit(0)
be = magic == FAT
endian = ">" if be else "<"
n = struct.unpack(endian + "I", data[4:8])[0]
off = 8
slice_bytes = None
for _ in range(n):
    cputype, cpusub, offset, size, align = struct.unpack(endian + "5I", data[off:off + 20])
    if cputype == ARM64:
        slice_bytes = data[offset:offset + size]
        break
    off += 20
if slice_bytes is None:
    print("SKIP no arm64 slice: " + path)
    raise SystemExit(1)
tmp = path + ".arm64"
open(tmp, "wb").write(slice_bytes)
os.replace(tmp, path)
print("thinned (python) " + path)
PY
}

log "Vendor: $VENDOR"
for bin in \
  "$VENDOR/GRDFUSDK.framework/GRDFUSDK" \
  "$VENDOR/JL_BLEKit.framework/JL_BLEKit" \
  "$VENDOR/JLDialUnit.framework/JLDialUnit" \
  "$VENDOR/ZipZap.framework/ZipZap" \
  "$VENDOR/ABParTool.framework/ABParTool" \
  "$VENDOR/VeepooBleSDK.framework/VeepooBleSDK" \
  "$VENDOR/FMDB/libFMDB.a"
do
  if [ -f "$bin" ]; then
    thin_arm64 "$bin"
    archs_of "$bin"
  else
    log "missing (ok if rebuilt on Mac): $bin"
  fi
done

log "Done. On a Mac run: lipo -info on the paths above — expect only arm64."
