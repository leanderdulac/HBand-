# Início de conexão sem sucesso fictício

## OBSERVED FACTS

Continuação autorizada das melhorias funcionais do app paciente. Preflight limpo
em `edfaded67aa59ad4be098f2749d6fb77eb6454a1`, branch
`codex/patient-responsive-devices`, remoto `leanderdulac/HBand-`.
GitHub confirmou main `f35d12b26c5a2305004271c2a05068782a1c9fc8`, PR #4 DRAFT
HEAD `9a239d9113bc671624643acc975b3e10042f4a57`, base
`04938c46fdba3ab59789943f41b7a48ea5b4b72e`. PRs #1/#2 permanecem nos HEADs
`cc9bbc2c11fda9f7a0b9c4ccd45ba34531bef315` e
`4b65b734adc3b26e2f6d88fa22123e5a115ea2d4`. Ancestralidade da #4 confirmada;
nenhuma operação Git pendente. A continuação local foi reconciliada com os
handoffs nativos e o pedido atual, que ampliou as melhorias além de UI.
Não houve push/merge; acesso remoto permanece somente leitura.

No baseline, falha de início GATT e MAC inválido no caminho Veepoo produziam
`isConnected=true` sem conexão confirmada. GATT também marcava conexão antes
do callback e ignorava status de erro quando newState era CONNECTED.
Teste do manager real com Android simulado reproduziu o primeiro problema:
Bluetooth desligado gerou um dispositivo marcado conectado. Falha esperada
registrada em `regression-before.log/xml`, antes das correções de produção.

## Mudança e limites

- Valida endereço, permissões existentes e disponibilidade do adaptador antes
  de alterar a sessão. Recusa preserva uma conexão já estabelecida e seu endereço.
- Verificação de aparelhos pareados também protege a leitura de isEnabled
  contra revogação de permissão entre a checagem e o acesso.
- GATT mantém tentativa pendente como desconectada. Somente callback atual com
  GATT_SUCCESS e STATE_CONNECTED confirma conexão, persiste o endereço e inicia
  serviço/rotinas. Falha inicial não cria placeholder conectado nem telemetria.
- Falha de callback mantém desconectado, encerra o GATT e usa a política de
  reconexão já existente. Callback de tentativa abandonada/finalizada é ignorado
  para o estado de conexão. A entrega é enfileirada na thread principal, depois
  de connectGatt devolver seu handle; também protege o cancelamento enquanto o
  callback aguarda execução. Não é uma auditoria de todos os callbacks de sensores.
- Caminho Veepoo deixa de criar placeholder para MAC inválido; handshake e
  protocolo continuam existentes. ViewModel só anuncia tentativa quando aceita.

O contrato Android distingue status de sucesso e novo estado no
[callback de conexão](https://developer.android.com/reference/android/bluetooth/BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt,%20int,%20int)).
Não se infere sucesso pelo retorno inicial de connectGatt.

Escopo: HBandBleManager, MainViewModel, teste de regressão e este relatório.
Sem dependências, manifesto, endpoints, schemas, identidade canônica, cadastro,
consentimento, dados clínicos ou governança alterados. Web, Tablet ACS e
WhatsApp/SM Click sem mudança. Concorrência/offline de registros preservados;
mudança de concorrência limitada à identidade do callback de conexão GATT.
Fixtures **DEMO** só em testes; não foram gerados registros de paciente.

## Verificação

Candidato final: `294fb5f3b12c020008c729d5125e01253c90e2db`.
Execução local Codex em Windows, 23/09/2026, JDK 21.0.12.1+1 e Gradle Wrapper
9.3.1, dependências em cache/offline, sem instalações ou mudanças de configuração.
Checks Android aplicáveis ao repositório nativo substituem os comandos pnpm da Web:

```text
./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:compileReleaseKotlin :app:lintDebug :app:lintRelease -Pandroid.builder.sdkDownload=false --offline --console=plain --continue
```

| Verificação | Resultado no candidato final |
| --- | --- |
| testDebugUnitTest | 227 testes, 226 aprovados, zero falhas/erros, um ignorado |
| assembleDebug | Sucesso; APK instalado abaixo |
| compileReleaseKotlin | Sucesso |
| lintDebug | Zero erros, 48 avisos preexistentes |
| lintRelease | Zero erros, 48 avisos preexistentes |
| git diff --check | Sucesso; árvore limpa após checks |

O teste ignorado é `ProgressImageGeneratorTest.file_provider_sharing_contract_on_android_style_paths`,
restrito por caminhos Android no ambiente Windows. Os 14 casos novos executam o
manager com Android/Robolectric simulado: rádio desligado, MAC inválido nos dois
caminhos, revogação/restauração de permissão, adaptador ausente, revogação durante
leitura de estado (APIs 31/36), permissão legada (API 28), exceção em connectGatt,
tentativa pendente, sucesso confirmado, status de falha, preservação da conexão
atual em recusa, callback obsoleto, callback antes do retorno da chamada e callback
de outra thread aguardando execução quando a sessão é encerrada.

Candidato anterior `c2fb5bac94da2b3170cc95fa5389522ed53a3b07` passou seus checks
com 12 casos novos. SELF_REVIEW_ONLY identificou necessidade de enfileirar a
confirmação depois do retorno de connectGatt; correção e dois testes adicionais
produziram o candidato final, revalidado integralmente. Evidência anterior mantida
em `full-checks-c2fb5ba.log`; evidência final em `full-checks.log` e `test-results/`.

Instalação incremental no M8_WIFI Android 13 bem-sucedida. APK gerado e APK
extraído do tablet têm SHA-256 idêntico:
`512AE4CD395C3F16C91A5DE501699B7AC7BEF61CF79CA642EF3AD616AEDFBC5A`.
Manifestos compilados comparados por aapt: mesmas permissões declaradas.
Abertura, navegação Relógio, busca ativa e encerramento por prazo conferidos no
aparelho; dois botões de conexão visíveis ao terminar e estado Relógio desconectado.
Capturas `scan-active.png`, `scan-finished.xml/png` e `physical-scan.json`.
Isso comprova descoberta Bluetooth **REAL** preservada, não conexão ou medições.
Nenhum dispositivo foi selecionado e nenhum registro de paciente foi criado.

ACS preservado em 1.0.6 (16), última atualização 2026-09-15 13:35:21.
Bluetooth=1, fonte=1,0, rotação=0 e rotação automática=1 preservados.
Commit posterior somente documental não altera `app/` nem o artefato verificado.
Evidências: `C:/CDev/Next2U-Patient-Delivery/2026-09-23-connection-start`.
**SELF_REVIEW_ONLY** local/Codex; não CI nem revisão independente.

## RECOMMENDATIONS

Homologar conexão e leituras com o relógio de teste identificado. Testes locais
com callbacks simulados não comprovam conexão **REAL**, handshake VE30 ou precisão
clínica. Nenhum periférico descoberto será escolhido por inferência nesta rodada.
Identidade/provisionamento e entrega ao backend permanecem **BACKEND CONTRACT REQUIRED**.
