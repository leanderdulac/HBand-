# Verificação de autorização sem carregar os payloads da fila

## OBSERVED FACTS

Continuação autorizada no baseline local limpo
`9ab1aa3f1f0d9c0f765f66d869d3e986697275a5`, descendente da PR #4.
GitHub confirmado em 24/09/2026: main
`f35d12b26c5a2305004271c2a05068782a1c9fc8`; PR #4 DRAFT HEAD
`9a239d9113bc671624643acc975b3e10042f4a57`, base
`04938c46fdba3ab59789943f41b7a48ea5b4b72e`. PRs #1/#2/#4 preservadas;
esta continuidade local não foi publicada. Branch:
`codex/patient-queue-pause-query`.

Antes, cada processamento automático chamava `getAllItemsSync()` e montava
todas as entidades, incluindo seus payloads clínicos, para decidir se havia
um bloqueio de autorização. Isso também acontecia a cada nova leitura salva,
mesmo com o envio já pausado. A fila observada no tablet nesta etapa tinha
351 registros com falha e mais de 600 aguardando envio.

Agora o DAO Room consulta `SELECT EXISTS` sobre status e prefixo do erro,
retornando apenas um booleano. O código gerado foi inspecionado: não seleciona
payloads nem constrói entidades nesse caminho. Não se introduziu cache: cada
verificação consulta a evidência persistida atual. O gate de concorrência do
repositório e o processamento manual existente permanecem os mesmos.

Os prefixos 401/403 foram centralizados no classificador existente. A consulta
usa comparação binária do prefixo para conservar o `startsWith` sensível a
maiúsculas/minúsculas; não usa LIKE insensível a caixa. PENDING com erro de
autorização continua bloqueando após cancelamento de tentativa manual;
SYNCED, erros nulos e falhas sem esses prefixos não bloqueiam.

Sem mudança de versão/esquema do banco, migração, índice, credencial, identidade,
SDK, permissão ou contrato de rede. Nenhum registro é removido ou reescrito pela
consulta. Outros consumidores de listas completas, como a tela de fila,
continuam existentes. Não foi medido ganho de tempo, memória total ou bateria;
a evidência sustenta a eliminação desse carregamento específico.

## Verificação

- DAO gerado executado em Room/SQLite em memória, isolado do banco do tablet:
  equivalência com o classificador em 70 combinações de status/erro; fila vazia,
  mista, atualização de FAILED para PENDING/SYNCED e exclusão; payload sintético
  grande/inválido preservado sem interferir na autorização.
- Repositório com DAO que rejeita carregamento completo ou de pendentes durante
  pausa: nova leitura continua salva e nenhuma chamada de ingestão ocorre.
- Suíte de concorrência existente continua cobrindo autorização, nova tentativa,
  cancelamento e liberação do gate.

Evidências por SHA, testes, APK e validação física em
`C:/CDev/Next2U-Patient-Delivery/2026-09-24-queue-pause-query/`.
Comando final: `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:compileReleaseKotlin :app:lintDebug :app:lintRelease -Pandroid.builder.sdkDownload=false --offline --console=plain --continue`.
Os testes de SQLite em memória não homologam criptografia/migração; o teste
físico usa o banco existente no aparelho sem limpeza ou troca de configuração.

## RECOMMENDATIONS / limites

**REAL local:** verificação da pausa no app paciente. Ingestão central e
recebimento pela equipe continuam dependentes de autorização e homologação;
integrações não confirmadas permanecem **BACKEND CONTRACT REQUIRED**.
Web Profissional, Tablet ACS e WhatsApp/SM Click sem alterações.

**SELF_REVIEW_ONLY:** checks locais não são CI ou revisão independente. Revisar
a composição com as PRs existentes antes de integrar. Sem publicação, merge ou
deploy. Eventual paginação das telas ou retenção de dados exige trabalho próprio;
esta entrega não muda o histórico nem promete resolver todo o consumo de memória.
