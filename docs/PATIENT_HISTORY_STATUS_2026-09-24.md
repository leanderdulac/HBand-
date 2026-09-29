# Resultado da consulta de histórico do relógio

## OBSERVED FACTS

Continuação autorizada do aplicativo do paciente. Baseline local limpo
`acf1f25e4d8c1400c41238e573ef43e22a7e7541`, descendente da PR #4.
GitHub reconsultado em 24/09/2026: main
`f35d12b26c5a2305004271c2a05068782a1c9fc8`; PR #4 DRAFT, HEAD
`9a239d9113bc671624643acc975b3e10042f4a57`, base
`04938c46fdba3ab59789943f41b7a48ea5b4b72e`. Pilha das PRs #1/#2/#4
preservada; a continuação local não foi publicada nem incorporada à pilha.
Branch deste incremento: `codex/patient-history-status`.

O cliente concluía `pullHistory` como `done`, com timestamp de conclusão,
mesmo sem capacidades verificadas ou depois de falhas/cancelamento por flag.
O log resumia contagens sem consultar `lastError`. A tela mostrava a falha,
mas podia começar por “nenhum registro informado”. Cancelamento de coroutine
era capturado como falha comum e o `finally` do gerente podia reativar sensores.

O incremento distingue consulta não iniciada, interrompida, incompleta e
concluída. Apenas a consulta concluída ganha timestamp de conclusão. Tela e log
usam a mesma apresentação; falha não afirma ausência de dados no relógio.
Contagens parciais informadas permanecem visíveis. O histórico local e a fila
de envio continuam separados do resultado da consulta ao relógio.

Cancelamento propaga como cancelamento; callback de progresso abandonado não
sobrescreve a sessão atual. O bloco final só inicia sensores na consulta atual,
ainda ativa e não cancelada. Não foram alterados protocolo SDK, payloads,
IDs, banco, credenciais, intervalos, quantidades de tentativas ou regra clínica.

Impacto nos quatro canais: somente apresentação/lifecycle do cliente paciente
e SDK existente. Web Profissional, ACS e WhatsApp/SM Click sem alterações.
Ingestão central segue bloqueada pela autorização anteriormente observada;
este incremento não resolve nem comprova entrega no Core.

Testes comportamentais exercitam o cliente com a fronteira SDK substituída:
capacidades não verificadas, cancelamento antes/durante/depois da chamada,
resposta vazia concluída, falha com fallback/retries. Compose confere falha
sem registros, cancelamento com contagem e consulta parcial.
Esses cenários são sintéticos e não comprovam precisão do sensor.

Verificação do candidato e instalador: SHA completo, hashes, comandos,
resultados e evidências físicas serão registrados na entrega local
`C:/CDev/Next2U-Patient-Delivery/2026-09-24-history-status/`.
Comando: `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:compileReleaseKotlin :app:lintDebug :app:lintRelease -Pandroid.builder.sdkDownload=false --offline --console=plain --continue`.

## RECOMMENDATIONS / limites

**REAL local:** mudança de estado do cliente que usa o SDK existente; confirmação
no aparelho deve ficar vinculada ao APK efetivamente instalado. Falhas e
cancelamentos sintéticos não equivalem a homologação física completa. Não
forçar falha na rede ou apagar registros do aparelho para criar esses cenários.
Não prometer recuperação de dias anteriores nem monitoramento clínico contínuo.

**BACKEND CONTRACT REQUIRED:** integrações ainda sem contrato confirmado continuam
pendentes; este código não cria capacidades centrais. Revisão independente,
reconciliação da pilha e publicação permanecem separadas da entrega local.
**SELF_REVIEW_ONLY**; sem CI, aprovação independente ou merge.
