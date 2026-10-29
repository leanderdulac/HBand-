# Nova tentativa de envio executada pelo botão

## OBSERVED FACTS

Continuação das melhorias funcionais autorizada pelo usuário. Preflight limpo
em `deeda38d38a389a074929d656bce1b259122b989`, branch
`codex/patient-responsive-devices`, remoto `leanderdulac/HBand-`.
GitHub confirmou main `f35d12b26c5a2305004271c2a05068782a1c9fc8`, PR #4 DRAFT
HEAD `9a239d9113bc671624643acc975b3e10042f4a57`, base
`04938c46fdba3ab59789943f41b7a48ea5b4b72e`; PRs #1/#2 seguem em
`cc9bbc2c11fda9f7a0b9c4ccd45ba34531bef315` e
`4b65b734adc3b26e2f6d88fa22123e5a115ea2d4`. Ancestralidade confirmada, sem
operação Git pendente. Handoffs reconciliados com o pedido atual, que inclui
melhorias funcionais. Sem push/merge; commits locais entregues em bundle.

No baseline, os botões Tentar enviar novamente chamavam retryFailedItem ou
retryAllFailed. Esses métodos apenas alteravam FAILED para PENDING; não
chamavam o processamento. O agendamento periódico existente é de 15 minutos.
Dois testes reproduziram zero chamadas ao transporte onde a ação deveria
iniciar uma ou duas tentativas. Evidência anterior à correção em
`regression-before.log/xml`, usando somente API e DAOs de teste.

## Mudança e limites

Retry selecionado e retry de todos agora recolocam os registros com falha na
fila e executam o processador existente. Ambas as etapas usam a mesma aquisição
atômica compartilhada com execução automática; uma tentativa ocupada não altera
registros nem dispara envio paralelo. A execução processa a fila pendente após
recolocar os registros selecionados; outros FAILED não selecionados permanecem.

Se o registro solicitado já foi concluído, removido ou não está mais FAILED,
a ação informa ausência de registro elegível; não afirma reenvio nem envia
outros pendentes por causa desse clique obsoleto. A política de reset dos retries
no pedido explícito, classificação HTTP e limites existentes foi preservada.

MainViewModel apresenta QueueProcessResult do retry pelo mesmo caminho do envio
manual. Falhas inesperadas produzem mensagem recuperável; cancelamento continua
sendo propagado. Não há confirmação imediata de envio apenas por mudar o status.

Escopo: WearableRepository, MainViewModel, testes e este relatório. Sem novos
endpoints/payloads/headers, schema/migração, IDs, agendamento, SDK, dependências,
permissões, cadastros, dados clínicos ou arquivos de governança. Web, Tablet ACS
e WhatsApp/SM Click sem mudança direta. O processamento usa o transporte já
existente e autorizado pela ação de envio do paciente.

Conforme ADR-004/006, proteção no processo não garante idempotência do servidor,
entrega exatamente uma vez ou coordenação entre reinícios/processos. Recebimento
oficial, reconciliação após resposta perdida e disponibilidade nos outros canais
permanecem **BACKEND CONTRACT REQUIRED**. SYNCED continua estado local do contrato
existente; não é confirmação de recebimento pela equipe. Fixtures **DEMO** somente
nos testes, sem inserção de dados ou disparo manual de envio no tablet.

## Verificação

Seis casos novos cobrem retry individual imediato, retry de todos, contenção
com outro processador, clique obsoleto, falha de transporte e cancelamento seguido
de processamento posterior. Sete casos anteriores de concorrência/cancelamento
continuam na mesma suíte. API e DAOs em memória; nenhum acesso ao backend real.
Candidato verificado: `dd62b913038a7de4191ecf77f8b8e4c210eef8eb`.
Execução Codex local/Windows em 23/09/2026, JDK 21.0.12.1+1, Gradle Wrapper
9.3.1, dependências em cache/offline. Checks Android aplicáveis ao repositório
nativo, sem comandos pnpm da Web:

```text
./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:compileReleaseKotlin :app:lintDebug :app:lintRelease -Pandroid.builder.sdkDownload=false --offline --console=plain --continue
```

| Check | Resultado no candidato |
| --- | --- |
| testDebugUnitTest | 240 testes, 239 aprovados, zero falhas/erros, um ignorado |
| assembleDebug | Sucesso |
| compileReleaseKotlin | Sucesso |
| lintDebug | Zero erros, 48 avisos preexistentes |
| lintRelease | Zero erros, 48 avisos preexistentes |
| git diff --check | Sucesso; árvore limpa após checks |

O caso ignorado é `ProgressImageGeneratorTest.file_provider_sharing_contract_on_android_style_paths`,
limitado por caminhos Android no ambiente Windows. A suíte específica de fila
passou 13 testes antes do commit e também na execução completa do candidato.
Self-review conferiu o retorno do resultado, aquisição antes de recolocar itens,
liberação no finally, clique obsoleto e preservação de cancelamento.

Instalação incremental no M8_WIFI Android 13 concluída. APK gerado e extraído
do tablet têm SHA-256 idêntico:
`9DF38D468E0D3435FDCDEFC3744F0FC1B750EE25702A4971BE8F021E0F5196AD`.
Permissões declaradas comparadas por aapt com versão anterior: iguais.
Abertura, navegação Envios e abertura da lista conferidas. Fila e lista vazias
antes/depois, sem inserir dados de teste ou disparar envio manual no aparelho.
Esse ensaio comprova instalação/navegação; o retry foi verificado com transporte
simulado, não envio de registro real. Evidência em `physical-queue.json` e capturas.

ACS permanece 1.0.6 (16), última atualização 2026-09-15 13:35:21.
Bluetooth=1, fonte=1,0, rotação=0 e rotação automática=1 preservados.
Commit posterior somente documental não altera `app/` nem o APK verificado.

Evidências: `C:/CDev/Next2U-Patient-Delivery/2026-09-23-queue-retry`.
**SELF_REVIEW_ONLY** local/Codex, não CI nem revisão independente.

## RECOMMENDATIONS

Homologar o envio com identidade/dados de teste provisionados e recibos do backend.
Nenhuma nova capacidade **REAL** de entrega é comprovada por estes testes locais.
