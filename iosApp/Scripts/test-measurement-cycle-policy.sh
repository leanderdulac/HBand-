#!/bin/bash
# Testes unitários da política de intervalo do ciclo de medição (sem Xcode).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
OUT="$(mktemp -t mcp-test)"
swiftc -o "$OUT" \
    iosApp/Next2UPaciente/MeasurementCyclePolicy.swift \
  iosApp/Scripts/test-measurement-cycle-policy.swift
"$OUT"
rm -f "$OUT"
