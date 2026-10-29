# Resultado de envio parcial sem ocultar pendências

## OBSERVED FACTS

Continuação funcional autorizada. Preflight limpo em
`921acd06609437ae38b887304b62615e7b53289e`, branch
`codex/patient-responsive-devices`, remoto `leanderdulac/HBand-`.
GitHub confirmou main `f35d12b26c5a2305004271c2a05068782a1c9fc8`, PR #4 DRAFT
HEAD `9a239d9113bc671624643acc975b3e10042f4a57`, base
`04938c46fdba3ab59789943f41b7a48ea5b4b72e`. PRs #1/#2 seguem nos HEADs
`cc9bbc2c11fda9f7a0b9c4ccd45ba34531bef315` e
`4b65b734adc3b26e2f6d88fa22123e5a115ea2d4`. Ancestralidade confirmada, sem
operação Git pendente. Handoffs reconciliados com o pedido atual de melhorias
funcionais; sem push/merge, entrega local em bundle.

No baseline, um sucesso seguido de falha transitória produzia syncedCount=1,
failedCount=0 e mensagem de sucesso sem informar o item ainda PENDING. O
ViewModel também ignorava hadTransientFailure no ramo de sucesso parcial.
Falha HTTP permanente sem sucesso caía em mensagem de espera por conectividade,
embora o registro estivesse FAILED. Dois testes reproduziram as mensagens
incorretas antes da correção; evidência em `regression-before.log/xml`.

## Mudança e limites

QueueProcessResult expõe pendingCount para a lista selecionada naquela execução:
tamanho inicial menos concluídos e falhas terminais. skippedCount já pertence
a failedCount e não é subtraído novamente. Inclui registros não tentados quando
o processador para após erros de rede ou autorização.

Um único resumo compõe a mensagem do repository e do ViewModel: concluídos no
aplicativo, com falha e aguardando envio, explicitamente "Nesta tentativa".
Sucesso parcial mantém o aviso de operação incompleta. Falha de autorização
preserva as contagens e a orientação ao paciente, sem copiar detalhes internos
da resposta para essa mensagem. Erros técnicos existentes nos registros de
suporte permanecem; não se afirma sanitização global.

Quando não há PENDING para processar, a mensagem não afirma "Fila limpa".
Registros FAILED ou novos itens inseridos depois da leitura inicial continuam
sendo apresentados pela fila. O resumo da tentativa não pretende ser snapshot
atômico de toda a fila nem confirmação de recebimento pela equipe de saúde.

Escopo: WearableRepository/QueueProcessResult, MainViewModel, testes e relatório.
Sem APIs/payloads, schema/migração, IDs, transporte, limite de retries,
agendamento, permissões, bibliotecas, cadastros ou governança alterados.
Não muda o critério HTTP existente para SYNCED nem a persistência dos registros.
Web, Tablet ACS e WhatsApp/SM Click sem mudança direta; disponibilidade nesses
canais e recibos centrais seguem **BACKEND CONTRACT REQUIRED**, conforme ADR-004/006.
Fixtures **DEMO** apenas nos testes, sem registros criados no aparelho.

## Verificação

Sete casos novos cobrem sucesso parcial, HTTP 422, parada por dois erros,
sucesso seguido de HTTP 401, amostra inválida contada uma vez, inserção posterior
ao snapshot e ausência de PENDING com FAILED existente. A suíte anterior de
cancelamento/concorrência/retry permanece. API e DAOs são simulados em memória;
nenhuma chamada real à API.

Candidato verificado: `a803a0ca0a9e0777d0890b962fa834ab0c43dd06`.
Execução Codex local/Windows em 23/09/2026, JDK 21.0.12.1+1 e Gradle Wrapper 9.3.1,
dependências em cache/offline. Checks nativos aplicáveis, sem comandos pnpm da Web:

```text
./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:compileReleaseKotlin :app:lintDebug :app:lintRelease -Pandroid.builder.sdkDownload=false --offline --console=plain --continue
```

| Check | Resultado no candidato |
| --- | --- |
| testDebugUnitTest | 247 testes, 246 aprovados, zero falhas/erros, um ignorado |
| assembleDebug | Sucesso |
| compileReleaseKotlin | Sucesso |
| lintDebug | Zero erros, 48 avisos preexistentes |
| lintRelease | Zero erros, 48 avisos preexistentes |
| git diff --check | Sucesso; árvore limpa após checks |

O caso ignorado é `ProgressImageGeneratorTest.file_provider_sharing_contract_on_android_style_paths`,
limitado por caminhos Android no Windows. A suíte específica passou 20 casos
antes do commit e novamente no conjunto completo. Self-review conferiu os
consumidores do resultado, contagem de skipped uma única vez e ausência de mudança
nas decisões de persistência/retry ou critérios de sucesso HTTP.

APK instalado incrementalmente no M8_WIFI Android 13. Artefato gerado e extraído
do tablet têm SHA-256 idêntico:
`A90E638AF0EC2B06D73E175F93C010F09B915E1A3732D1AD2C318CFD1A05BD96`.
Permissões declaradas comparadas por aapt com o APK anterior: iguais.
Conferidas abertura, navegação Envios, fila vazia preservada e disponibilidade
da ação de ver registros. Capturas e `physical-queue.json` registram o ensaio.
Sem registros artificiais ou disparo manual de sincronização no tablet; os
cenários de resultados parciais foram exercitados em testes com transporte simulado.

ACS permanece 1.0.6 (16), última atualização 2026-09-15 13:35:21.
Bluetooth=1, fonte=1,0, rotação=0 e rotação automática=1 preservados.
Commit posterior somente documental não altera `app/` nem o APK verificado.

Evidências: `C:/CDev/Next2U-Patient-Delivery/2026-09-23-queue-summary`.
**SELF_REVIEW_ONLY** local/Codex, não CI nem revisão independente.

## RECOMMENDATIONS

Homologar envio/recibos com identidade e dados de teste provisionados. Estes
testes demonstram apresentação fiel ao resultado local sob transporte simulado,
sem comprovar nova capacidade **REAL** de entrega, precisão clínica ou
idempotência remota.
