# Frontend UI — handoff next2u SAÚDE

## Base obrigatória
- Repositório: https://github.com/leanderdulac/HBand-
- Branch desta PR: `feature/next2u-patient-ui`
- Commit tip das integrações: `04938c4` (também em `cursor/healthsync-sdk-parity-p1-5cdc` / PR #3)
- Pacote Android instalado no S21+ do relatório: `com.aistudio.hbandhealthtech.pxq97m`

## Não usar
- `healthtech/companion-android` e PR #7 (`cursor/sprint-a-dod-hardening-1d1e`) — outro app (`com.healthtech.companion.debug`), tema escuro; não mesclar sobre este trabalho.

## Empilhamento das integrações (referência)
1. PR #1 — SQLCipher / harden ingest  
2. PR #2 — P0 histórico / auto-measure / wear  
3. PR #3 — P1 ECG, glicose, settings avançados + tip com marca next2u SAÚDE  

Esta branch parte do tip do PR #3. Melhorias de interface devem preservar BLE/Veepoo, fila HealthTech e honestidade de dados (sem inventar vitais).

## Escopo sugerido de UI
- Polimento visual Compose (tema claro next2u SAÚDE)
- Textos PT nas telas de sync / fila
- Esconder ou isolar botões só de teste em builds de demonstração
- Cards de medidas avançadas e Visão Geral sem quebrar capability gating

## Contato
Trabalho coordenado com o Professor (Leandro) e o time de frontend (Rafael).
