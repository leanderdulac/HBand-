#!/bin/bash
# Decisões de segundo plano / Keychain (sem Xcode nem SDK).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
OUT="$(mktemp "${TMPDIR:-/tmp}/background-sync-policy.XXXXXX")"
swiftc -o "$OUT" \
  iosApp/Next2UPaciente/BackgroundSyncPolicy.swift \
  iosApp/Scripts/test-background-sync-policy.swift
"$OUT"
rm -f "$OUT"
