# Coordenação local da fila e preservação após cancelamento

## OBSERVED FACTS

Continuação funcional autorizada pelo usuário. Preflight limpo em
`526529da516c8c5958ab36b466ac0dd1735a07e7`, branch
`codex/patient-responsive-devices`, remoto `leanderdulac/HBand-`.
GitHub confirmou main `f35d12b26c5a2305004271c2a05068782a1c9fc8`, PR #4 DRAFT
HEAD `9a239d9113bc671624643acc975b3e10042f4a57`, base
`04938c46fdba3ab59789943f41b7a48ea5b4b72e`; PRs #1/#2 permanecem em
`cc9bbc2c11fda9f7a0b9c4ccd45ba34531bef315` e
`4b65b734adc3b26e2f6d88fa22123e5a115ea2d4`. Ancestralidade confirmada e nenhuma
operação Git pendente. Continuação local reconciliada com os handoffs nativos
e o pedido atual de melhorias além de UI; sem push/merge.

MainViewModel, HBandHealthSyncApp e HBandIngestWorker criam instâncias distintas
de WearableRepository para a mesma base Room. O controle antigo de envio era
por instância, com leitura/escrita separadas. Isso permitia dois processadores
lerem a mesma lista pendente e chamarem a API em paralelo. Manifestos fonte e
debug mesclado inspecionados sem android:process separado.

O catch genérico também tratava CancellationException como erro de rede.
Três regressões reproduzidas antes da correção, com API/DAOs em memória:
segundo envio entrou enquanto o primeiro aguardava; cancelamento tentou gravar
FAILED/retries=5 em registro anteriormente PENDING/retries=4; check de saúde
cancelado substituiu o estado online observado por erro de conexão.
Evidências `regression-before.log/xml`; nenhuma chamada real à API.

## Mudança e limites

WearableRepository compartilha o estado de processamento dentro do processo.
A aquisição é atômica (compareAndSet) e o finally libera o controle em sucesso,
erro ou cancelamento. A instância que encontra uma execução ativa conserva o
resultado já existente "Sincronização já em andamento"; a tela observa o mesmo
estado ocupado quando outra instância está processando.

Cancelamento é relançado antes dos catches genéricos de envio, check de saúde
e chamadas de processamento após enfileirar. Não incrementa tentativas, não
fabrica erro de rede e não altera o último estado observado da API. Resultados
locais de itens já confirmados antes do cancelamento permanecem; o próximo item
continua pendente. Falhas de rede/HTTP mantêm a classificação e limites existentes.

Escopo: um arquivo de produção, testes e este registro. Sem novos endpoints,
payloads, cabeçalhos, chaves de idempotência, schema/migração Room, IDs canônicos,
limites de retry, agendamento, SDK, dependências ou permissões. Não altera os
dados clínicos nem promove o status local SYNCED a confirmação de recebimento
pela equipe. Web, Tablet ACS e WhatsApp/SM Click sem alteração direta.

Conforme ADR-004/006, coordenação em memória não garante envio exatamente uma vez,
deduplicação entre processos/reinícios, recepção central ou atualização dos quatro
canais. Um cancelamento após despacho pode ter resultado remoto desconhecido;
a reconciliação/idempotência do servidor permanece **BACKEND CONTRACT REQUIRED**.
A proteção evita concorrência dos processadores no mesmo processo, não resolve
todos os conflitos com operações de exclusão/retry da fila. Não muda o comportamento
de registros inseridos enquanto uma lista já está em processamento: continuam
aguardando uma execução posterior se não pertencem à lista corrente.
Fixtures **DEMO** somente em testes, sem registros inseridos no tablet.

## Verificação

Sete testes específicos aprovados antes do commit: concorrência entre instâncias,
cancelamento/retry, preservação do check de saúde, liberação após erro de leitura,
falha de transporte seguida de sucesso, classificação HTTP 401/503 e cancelamento
depois de um item já concluído. DAO em memória permite observar qualquer tentativa
indevida de escrita; não comprova transação/disco Room ou comportamento remoto.

Candidato verificado: `3a23210a0442b2ae45fb3f3511359cbab49c0608`.
Execução Codex local/Windows em 23/09/2026, JDK 21.0.12.1+1, Gradle Wrapper 9.3.1,
dependências em cache/offline. Checks nativos aplicáveis, sem comandos pnpm da Web:

```text
./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:compileReleaseKotlin :app:lintDebug :app:lintRelease -Pandroid.builder.sdkDownload=false --offline --console=plain --continue
```

| Check | Resultado |
| --- | --- |
| testDebugUnitTest | 234 testes, 233 aprovados, zero falhas/erros, um ignorado |
| assembleDebug | Sucesso |
| compileReleaseKotlin | Sucesso |
| lintDebug | Zero erros, 48 avisos preexistentes |
| lintRelease | Zero erros, 48 avisos preexistentes |
| git diff --check | Sucesso; árvore limpa após checks |

O caso ignorado é `ProgressImageGeneratorTest.file_provider_sharing_contract_on_android_style_paths`,
limitado por caminhos Android no ambiente Windows. Sete regressões da fila
aprovadas também na suíte completa. Self-review conferiu aquisição única,
liberação somente pelo dono, propagação de cancelamento, escopo e limites.

APK instalado incrementalmente no M8_WIFI Android 13. APK gerado e extraído
do tablet têm SHA-256 igual:
`4E771A8DDF25A756E8F27320E8B263D09AEECE012B49D1B5347A758339238188`.
Permissões dos APKs anterior/novo comparadas por aapt: nenhuma alteração.
Conferidos abertura, navegação Envios, abertura dos registros e estado vazio
preservado em relação à captura anterior. A lista continuou vazia; não houve
inserção de fixture nem disparo manual de sincronização real. Esse ensaio é
somente de instalação/navegação, não prova envio ou concorrência com servidor.

ACS permanece 1.0.6 (16), última atualização 2026-09-15 13:35:21.
Bluetooth=1, fonte=1,0, rotação=0 e rotação automática=1 preservados.
Commit posterior somente documental não muda `app/` nem o APK verificado.
Evidências: `C:/CDev/Next2U-Patient-Delivery/2026-09-23-queue-reliability`.
Verificação **SELF_REVIEW_ONLY**, local/Codex; não CI nem revisão independente.

## RECOMMENDATIONS

Homologar fila com identidade e dados de teste provisionados e recibos do backend
confirmados. A presente rodada comprova comportamento local sob transporte
simulado, sem nova capacidade **REAL** de entrega ou garantia clínica.
