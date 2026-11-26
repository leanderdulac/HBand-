#!/bin/bash
# Testes unitários do filtro da busca de relógio (sem Xcode).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
OUT="$(mktemp "${TMPDIR:-/tmp}/watch-scan-filter.XXXXXX")"
swiftc -o "$OUT" \
  iosApp/Next2UPaciente/WatchScanFilter.swift \
  iosApp/Scripts/test-watch-scan-filter.swift
"$OUT"
rm -f "$OUT"
