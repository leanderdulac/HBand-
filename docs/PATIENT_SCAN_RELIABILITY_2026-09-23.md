# Confiabilidade das tentativas de busca Bluetooth

## OBSERVED FACTS

Pedido atual autoriza melhorias funcionais além da UI/UX. Preflight limpo em
`27ce8c600acd5e0ba4d3a90b0194a9b4b405e463`, branch
`codex/patient-responsive-devices`, remoto `leanderdulac/HBand-`.
GitHub confirmou main `f35d12b26c5a2305004271c2a05068782a1c9fc8`, PR #4 DRAFT
HEAD `9a239d9113bc671624643acc975b3e10042f4a57`, base
`04938c46fdba3ab59789943f41b7a48ea5b4b72e`, PRs #1/#2 inalterados.
Ancestralidade confirmada, sem operação Git pendente. Continuação local
reconciliada com FRONTEND_UI_HANDOFF e autorização atual. Sem push/merge;
permissão remota de escrita indisponível conforme preflight anterior.

O código anterior agendava stopScanning sem identificar a tentativa nem remover
o encerramento antigo. Uma busca reiniciada podia ser encerrada no prazo da
anterior. Callbacks antigos não eram rejeitados; onScanFailed alterava apenas
isScanning. A obtenção de bluetoothLeScanner ficava antes do tratamento de erro.

## Mudança

BleScanSession passa a possuir callback e encerramento da tentativa, serializados
no Handler principal. Ao encerrar, invalida a tentativa e remove o encerramento
agendado antes de parar o rádio. Resultados e erros de tentativas antigas são
ignorados; cada nova tentativa mantém seu próprio prazo de 12 segundos.
Falha ao iniciar, falta/revogação de permissão e falha ao parar liberam o estado
local para nova tentativa. Não há retry automático nem nova política de conexão.

HBandBleManager usa a sessão e mantém a mesma instância do scanner para encerrá-la.
Novas buscas limpam a lista anterior; duplicar o pedido durante uma busca ativa
não reinicia nem limpa a tentativa atual. Resultados individuais/em lote usam o
mesmo mapeamento, MAC, RSSI e regras de bateria. Nome Bluetooth usa a proteção
de permissão já criada. Os acessos de hardware antes suprimidos continuam
delimitados, com verificações/captura de exceções na sessão; nenhuma permissão
nova ou supressão adicional de regra foi criada.

Escopo: sessão e integração de busca, testes e este registro. Sem alterações de
SDK Veepoo, handshake, persistência, telemetria, APIs, schemas, IDs, conduta clínica,
dependências ou registros de saúde. Web, Tablet ACS e WhatsApp/SM Click sem impacto.
Ordenação alterada somente nos callbacks locais de descoberta; sincronização e
offline de registros inalterados. Hardware dos testes é **DEMO**; não comprova
conexão **REAL** nem integração backend. Contratos backend permanecem inalterados.

Referência de API: [BluetoothLeScanner](https://developer.android.com/reference/android/bluetooth/le/BluetoothLeScanner).

## Verificação

Fonte/testes verificados em `c9b8919c318bb5e9cac7dbe7ac5324bdf0cd5ac0`,
Windows/Codex local, 23/09/2026, JDK 21.0.12.1, Gradle 9.3.1, cache offline.
Comando pelo wrapper: `:app:testDebugUnitTest :app:assembleDebug
:app:compileReleaseKotlin :app:lintDebug :app:lintRelease
-Pandroid.builder.sdkDownload=false --offline --console=plain --continue`.
pnpm não se aplica ao checkout Android Kotlin.

- Rodada focada anterior ao commit: **15 PASS**. Um caso adicional de callback
  vindo de thread secundária foi incluído no candidato e passou na suíte completa.
- Sete testes novos: reinício com prazo próprio; callbacks antigos individuais,
  em lote e de falha; chamadas repetidas; falha assíncrona/síncrona; permissão
  negada ao iniciar; revogação durante resultados com erro ao parar; resultado
  enfileirado em thread secundária após encerramento.
- Suíte completa: **207 casos, 206 PASS, 0 falhas, 1 SKIP** por limitação conhecida
  de FileProvider no Windows.
- APK DEBUG e compilação Kotlin release: **PASS**.
- Lint DEBUG/RELEASE: **PASS**, 0 erros e 48 avisos em cada configuração.
- Comando conjunto: **PASS**. Permissões do APK idênticas ao pacote anterior,
  conferidas por aapt. Nenhuma atualização de dependência nesta rodada.

APK `next2u-paciente-scan-reliability-c9b8919.apk` instalado por atualização no
M8 WIFI Android 13, sem limpar dados. SHA-256 do arquivo e da cópia extraída
da instalação: `0E4EED83662980C2F2451A8CF94827D72C297F92D381A846AC77DD4ADE59E42B`.

Conferência física: abertura e navegação à tela Relógio. Bluetooth estava
desligado (bluetooth_on=0) e foi preservado. Duas tentativas de Buscar meu relógio
retornaram ao botão habilitado, sem ficar buscando indefinidamente. Permissões
SCAN/CONNECT/localização já estavam concedidas e não foram alteradas. Não se
declara descoberta com rádio ligado, pareamento, medição ou conexão física.
Prazo/callbacks/revogação são evidência de testes, não de relógio físico.
ACS segue 1.0.6 (16), última atualização `2026-09-15 13:35:21`.
Fonte/orientação/rotação automática preservadas em 1.0/0/1.
Commit posterior apenas documental; app/ idêntico ao candidato.

Evidências: `C:/CDev/Next2U-Patient-Delivery/2026-09-23-scan-reliability`.
**SELF_REVIEW_ONLY**, não CI ou revisão independente.

## RECOMMENDATIONS

Complementar com descoberta/conexão autorizada de relógio físico. Testes com
callbacks e temporizador Android simulados não substituem homologação do rádio.
