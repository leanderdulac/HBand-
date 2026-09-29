# Falhas locais de gravação durante o envio

## OBSERVED FACTS

Continuação funcional autorizada do app do paciente. Preflight limpo em
`257936040a14cc29a45ad21f2f9576fd15184167`, branch
`codex/patient-responsive-devices`, remoto `leanderdulac/HBand-`.
GitHub confirmou main `f35d12b26c5a2305004271c2a05068782a1c9fc8`, PR #4 DRAFT
HEAD `9a239d9113bc671624643acc975b3e10042f4a57`, base
`04938c46fdba3ab59789943f41b7a48ea5b4b72e`. PRs #1/#2 seguem nos HEADs
`cc9bbc2c11fda9f7a0b9c4ccd45ba34531bef315` e
`4b65b734adc3b26e2f6d88fa22123e5a115ea2d4`. Ancestralidade confirmada,
sem operação Git pendente. Handoffs reconciliados com o pedido atual de
melhorias funcionais. Entrega local; sem push ou merge.

Dois testes reproduziram o defeito antes da correção: uma exceção ao gravar o
resultado de HTTP bem-sucedido era capturada como erro de rede, provocando uma
segunda atualização da mesma linha. Isso podia ocultar o erro e continuar o
lote ou substituir um SYNCED já persistido por PENDING quando a primeira gravação
havia concluído, mas sua confirmação falhava. Evidências: regression-before.log/xml.

## Mudança e limites

A captura de falhas de transporte agora cobre somente a chamada HTTP e a leitura
do corpo de erro. A gravação do resultado ocorre fora desse bloco. Se ela falhar,
QueuePersistenceException preserva a causa e interrompe o lote atual, sem uma
segunda gravação que reclassifique a falha local como erro de internet.
CancellationException continua sendo propagada sem conversão. A proteção de
concorrência é liberada em finally, inclusive quando o armazenamento falha.

Todas as gravações de resultado do processador usam esse tratamento: sucesso,
HTTP de autorização/cliente/servidor, erro de transporte e amostra inválida.
A preparação de registros FAILED para retry continua anterior ao envio, sem
alteração de política. Novas execuções não são bloqueadas permanentemente.
O fluxo de inclusão de telemetria e o agendamento automático permanecem; não
se afirma suspensão global de novas tentativas após uma falha local.

As ações manuais de fila exibem orientação específica sobre a impossibilidade
de salvar o resultado, sem mostrar a causa técnica. O envio iniciado pela leitura
pontual utiliza o mesmo tratamento e resumo, evitando uma exceção de persistência
não tratada nessa ação. O teste físico não provocou defeito no armazenamento.

Escopo: WearableRepository, MainViewModel, testes e este relatório.
Sem mudança de APIs, payloads, schema/migração, IDs, transporte, critérios HTTP
para SYNCED, limite de retries, agendamento, permissões, dependências ou governança.
Web, Tablet ACS e WhatsApp/SM Click sem mudança direta. Recibos, reconciliação e
idempotência entre canais continuam **BACKEND CONTRACT REQUIRED** (ADR-004/006).
O servidor pode ter recebido um registro cujo resultado não foi salvo localmente;
a correção não garante entrega única nem confirmação pela equipe de saúde.
Fixtures **DEMO** apenas em testes em memória, sem pacientes criados no aparelho.

## Verificação

Candidato: `90379f1b2d6defb59d7b418e9b046935a5e3a180`.
Execução local/Codex Windows em 23/09/2026, JDK 21.0.12.1+1,
Gradle Wrapper 9.3.1, dependências em cache/offline. Checks nativos aplicáveis,
sem comandos pnpm da Web:

```text
./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:compileReleaseKotlin :app:lintDebug :app:lintRelease -Pandroid.builder.sdkDownload=false --offline --console=plain --continue
```

Seis casos novos cobrem armazenamento antes/depois da gravação de sucesso,
HTTP 401/422/503, falha de gravação de erro de rede, cancelamento na persistência
e amostra inválida. Verificam interrupção do lote, apenas uma tentativa de gravação,
preservação do registro, recuperação explícita do armazenamento e liberação da
proteção de concorrência. A suíte específica passou 26 testes antes do commit.
Transporte e DAOs são simulados, sem abertura da base do aparelho ou chamadas reais.

| Check no candidato | Resultado |
| --- | --- |
| testDebugUnitTest | 253 testes: 252 aprovados, zero falhas/erros, um ignorado |
| assembleDebug | Sucesso |
| compileReleaseKotlin | Sucesso |
| lintDebug | Zero erros, 48 avisos preexistentes |
| lintRelease | Zero erros, 48 avisos preexistentes |
| git diff --check | Sucesso |

A suíte específica passou novamente os 26 casos no conjunto completo.
O teste ignorado é ProgressImageGeneratorTest.file_provider_sharing_contract_on_android_style_paths,
limitado pelos caminhos Android no Windows. Os fontes de app/ permaneceram
idênticos ao candidato após os checks.

Instalação incremental concluída no M8_WIFI Android 13. APK gerado e extraído
do tablet com SHA-256 idêntico:
`33443D3680441049B945C223E3997EA36A183A31BDBD92301DD2A61BA640277B`.
Permissões declaradas iguais às do APK anterior. Conferidas abertura, navegação
Envios, fila vazia e disponibilidade de consulta aos registros, com XML e captura
visual. Sem inserção de pacientes ou disparo manual de envio no tablet; os casos
de erro foram executados somente com API e armazenamento simulados.

ACS permanece 1.0.6 (16), última atualização 2026-09-15 13:35:21.
Bluetooth ligado, fonte 1,0, rotação 0 e rotação automática 1 preservados.

Evidências: `C:/CDev/Next2U-Patient-Delivery/2026-09-23-queue-storage`.
**SELF_REVIEW_ONLY**, não CI nem revisão independente. A auto-revisão conferiu
os chamadores, separação transporte/persistência, preservação de cancelamento e
políticas existentes. O commit documental posterior não altera app/ nem o APK.

## RECOMMENDATIONS

Homologar recuperação de armazenamento e reconciliação remota com identidade e
dados de teste provisionados, antes de afirmar capacidade **REAL** de entrega.
A falha após recebimento HTTP exige confirmação do contrato de idempotência do
backend; não deve ser resolvida inventando um novo recibo ou identificador local.
