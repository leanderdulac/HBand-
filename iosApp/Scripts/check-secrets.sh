#!/bin/bash
# Confirma que a chave de envio ao Core será (ou foi) embutida no build, sem imprimir valores.
#   ./iosApp/Scripts/check-secrets.sh                 # antes de compilar: confere Secrets.local.xcconfig
#   ./iosApp/Scripts/check-secrets.sh path/To.app     # depois: confere o Info.plist do .app
# Secrets.local.xcconfig é gitignored e NÃO é copiado para novos worktrees: copie-o à mão.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
if [ $# -ge 1 ]; then
  key=$(/usr/libexec/PlistBuddy -c "Print Next2UIngestApiKey" "$1/Info.plist" 2>/dev/null || true)
  url=$(/usr/libexec/PlistBuddy -c "Print Next2UCoreBaseUrl" "$1/Info.plist" 2>/dev/null || true)
  [ -n "$key" ] && [[ "$key" != *'$('* ]] || { echo "ERRO: $1 sem Next2UIngestApiKey embutida." >&2; exit 1; }
  [ -n "$url" ] && [[ "$url" != *'$('* ]] || { echo "ERRO: $1 sem Next2UCoreBaseUrl embutida." >&2; exit 1; }
  echo "OK: chave de envio embutida (len ${#key}); URL do Core embutida."
  exit 0
fi
F="$ROOT/iosApp/Config/Secrets.local.xcconfig"
[ -f "$F" ] || { echo "ERRO: $F ausente. Copie do clone principal (nunca commite)." >&2; exit 1; }
val=$(sed -nE 's/^[[:space:]]*NEXT2U_INGEST_API_KEY[[:space:]]*=[[:space:]]*(.*)$/\1/p' "$F" | tail -1 | sed -E 's/[[:space:]]+$//')
[ ${#val} -ge 16 ] && [[ "$val" != YOUR_* ]] || { echo "ERRO: NEXT2U_INGEST_API_KEY ausente ou placeholder em $F." >&2; exit 1; }
grep -qE '^[[:space:]]*NEXT2U_CORE_BASE_URL[[:space:]]*=' "$F" || { echo "ERRO: NEXT2U_CORE_BASE_URL ausente em $F." >&2; exit 1; }
echo "OK: Secrets.local.xcconfig com NEXT2U_INGEST_API_KEY (len ${#val}) e NEXT2U_CORE_BASE_URL."
