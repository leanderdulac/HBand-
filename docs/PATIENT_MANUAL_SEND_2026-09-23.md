# Leitura pontual com um único processamento explícito

## OBSERVED FACTS

Continuação funcional autorizada do app do paciente. Preflight limpo em
`21d4216e5ac3c97023d71269a08275a16c044590`, branch
`codex/patient-responsive-devices`, remoto `leanderdulac/HBand-`.
GitHub confirmou main `f35d12b26c5a2305004271c2a05068782a1c9fc8`, PR #4 DRAFT
HEAD `9a239d9113bc671624643acc975b3e10042f4a57`, base
`04938c46fdba3ab59789943f41b7a48ea5b4b72e`. PRs #1/#2 mantêm os HEADs
`cc9bbc2c11fda9f7a0b9c4ccd45ba34531bef315` e
`4b65b734adc3b26e2f6d88fa22123e5a115ea2d4`. Ancestralidade confirmada;
sem operação Git pendente. Handoffs de frontend reconciliados com o pedido
atual de melhorias funcionais. Sem push ou merge; entrega local em bundle.

A inspeção do baseline encontrou dois processamentos sequenciais na leitura
pontual: MainViewModel chamava enqueueTelemetry, que já processava a fila e
retornava apenas o ID, depois chamava processQueueDetailed novamente. O resultado
mostrado era o da segunda execução. Uma falha de rede podia ser tentada novamente
imediatamente, um erro local podia ser ocultado pelo caminho automático, e uma
falha de autorização podia ser substituída pelo aviso de ausência de PENDING.

## Mudança e limites

persistTelemetry reúne a persistência existente de métrica e registro da fila.
A entrada automática enqueueTelemetry conserva seu retorno de ID, processamento
posterior e política de captura de erros. A nova entrada manual
enqueueAndProcessTelemetry salva a leitura, consulta a saúde da API e retorna
o resultado de um único processQueueDetailed. Falhas locais e cancelamento
chegam ao tratamento da ação manual já existente. A tela chama essa entrada
sem iniciar outro processamento.

A verificação de leitura válida permanece na tela, com uma pré-condição também
na entrada manual. Leituras inválidas não são salvas por essa nova entrada.
A captura automática mantém o comportamento anterior para métricas não elegíveis.
Se houver cancelamento na consulta de saúde, a leitura já salva permanece PENDING.
Não se adiciona transação entre as duas gravações existentes nem se afirma
atomicidade entre métrica e fila. Erro na inserção da fila impede o envio nessa ação.

Escopo: WearableRepository, MainViewModel, testes e relatório.
Sem alteração de BLE/SDK, payloads, IDs, schema, permissões, dependências,
critérios HTTP, limite de retries ou agendamento. O gate compartilhado de
processamento continua vigente. Outro processador pode estar ativo, novos toques
continuam sendo novas ações e os produtores automáticos permanecem independentes;
a correção não é uma deduplicação global das leituras nem garantia de entrega única.

Web, Tablet ACS e WhatsApp/SM Click sem mudança direta. Recibos, idempotência e
reconciliação entre canais continuam **BACKEND CONTRACT REQUIRED** (ADR-004/006).
Fixtures **DEMO** usadas somente nos testes, sem cadastro ou envio artificial
no tablet. Não há nova comprovação de capacidade **REAL** de entrega clínica.

## Verificação

Candidato: `22211d3c95cd2fa34f252303ce2c91d68c491eb1`.
Execução local/Codex Windows em 23/09/2026, JDK 21.0.12.1+1,
Gradle Wrapper 9.3.1, dependências em cache/offline. Checks Android aplicáveis:

```text
./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:compileReleaseKotlin :app:lintDebug :app:lintRelease -Pandroid.builder.sdkDownload=false --offline --console=plain --continue
```

Nove casos novos verificam: retorno do sucesso original, apenas uma tentativa em
falha de rede, preservação do resultado HTTP 401, propagação do erro de gravação,
cancelamento durante envio, gravação antes da consulta de saúde/cancelamento,
leitura inválida, falha de inserção e compatibilidade do caminho automático.
DAOs e API simulados em memória; nenhum teste acessa a base ou API do aparelho.

Na primeira execução focada, um teste exigia identidade de objeto de uma exceção
atravessando coroutines. O erro propagado preservava tipo e mensagem, mas a
recuperação de stack criava uma cópia. A asserção passou a conferir tipo e presença
da causa original na cadeia. Evidência inicial preservada em focused-initial.log/xml;
esta falha de asserção não é apresentada como regressão do aplicativo.

| Check no candidato | Resultado |
| --- | --- |
| testDebugUnitTest | 262 testes: 261 aprovados, zero falhas/erros, um ignorado |
| assembleDebug | Sucesso |
| compileReleaseKotlin | Sucesso |
| lintDebug | Zero erros, 48 avisos preexistentes |
| lintRelease | Zero erros, 48 avisos preexistentes |
| git diff --check | Sucesso |

A suíte específica passou 35 casos antes do commit e novamente no conjunto completo.
O caso ignorado é ProgressImageGeneratorTest.file_provider_sharing_contract_on_android_style_paths,
limitado pelos caminhos Android no Windows. Fontes de app/ idênticos ao candidato
após os checks; relatório ainda não versionado durante a execução.

Instalação incremental concluída no M8_WIFI Android 13. APK gerado e extraído
do aparelho com SHA-256 idêntico:
`09A036226DAD752AA9A784DC70E82A362163868877EDCD69E33C09FE79C3CA87`.
Permissões declaradas iguais às do APK anterior. Conferidas abertura, navegação
Envios, fila vazia preservada e acesso aos registros, com XML e captura visual.
Sem criação de paciente ou envio manual no tablet. Os cenários da leitura pontual
foram exercitados com API e DAOs simulados, não com relógio real conectado.

ACS permanece 1.0.6 (16), última atualização 2026-09-15 13:35:21.
Bluetooth ligado, fonte 1,0, rotação 0 e rotação automática 1 preservados.

Evidências: `C:/CDev/Next2U-Patient-Delivery/2026-09-23-manual-send`.
**SELF_REVIEW_ONLY**, não CI nem revisão independente. Auto-revisão conferiu
chamadores, manutenção do fluxo automático e ausência do segundo processamento
na leitura pontual. Commit documental posterior não altera app/ nem o APK.

## RECOMMENDATIONS

Homologar captura e envio com relógio identificado, identidade provisionada e
ambiente de teste do backend. Confirmar recibos e idempotência antes de afirmar
entrega única ou recebimento pela equipe de saúde.
