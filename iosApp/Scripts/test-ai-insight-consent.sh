#!/bin/bash
# Consentimento do Resumo com IA e ausência de chave Gemini no xcconfig commitado.
# Não precisa de Xcode. Não lê Secrets.local.xcconfig.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
git check-ignore -q iosApp/Config/Secrets.local.xcconfig
OUT="$(mktemp)"
trap 'rm -f "$OUT"' EXIT
swiftc -o "$OUT" \
  iosApp/Next2UPaciente/AiInsightConsent.swift \
  iosApp/Scripts/test-ai-insight-consent.swift
"$OUT"
