# Correções de permissão Bluetooth e compatibilidade Android

## OBSERVED FACTS

Pedido atual autoriza explicitamente corrigir os dois erros anteriores de lint,
incluindo o ponto BLE e a dependência Android antes fora do escopo de UI.
Preflight limpo em `1f22c17fca3c34c39223fb741190a0c430864f02`, branch
`codex/patient-responsive-devices`, remoto `leanderdulac/HBand-`.
GitHub confirmou main `f35d12b26c5a2305004271c2a05068782a1c9fc8`, PR #4 DRAFT
HEAD `9a239d9113bc671624643acc975b3e10042f4a57`, base
`04938c46fdba3ab59789943f41b7a48ea5b4b72e`, PRs #1/#2 inalterados.
Ancestralidade confirmada, sem operação Git pendente. Continuação local
reconciliada com FRONTEND_UI_HANDOFF e autorização atual. Remoto de leitura;
sem push/merge.

## Causa e correções

`MissingPermission`: o recebimento GATT consultava BluetoothDevice.name sem
tratar SecurityException. A consulta foi isolada em bluetoothDeviceNameOrFallback,
que captura especificamente a negativa de permissão, inclusive revogação após
conexão, e preserva o mesmo nome conhecido já usado quando a consulta retorna
null. Sem nova leitura protegida, retry, mudança de MAC/ID ou nome inventado
pelo helper. A proteção é deste acesso; não é auditoria de todo o SDK BLE.

`InvalidFragmentVersionForActivityResult`: dependencyInsight confirmou Fragment
1.1.0 transitivo de play-services-base/basement 18.9.0, consumidos por Firebase.
Constraint de implementação com versão catalogada 1.8.9 eleva o componente
transitivo; não exclui bibliotecas dos serviços Google, não altera MainActivity
nem suprime a regra. 1.8.9 está disponível no cache e atende a exigência >=1.3.0;
foi escolhida para limitar o ajuste à linha compatível com as dependências
existentes, sem migração geral para a linha 1.9.

Referências primárias: [BluetoothDevice.getName](https://developer.android.com/reference/android/bluetooth/BluetoothDevice#getName())
e [Fragment 1.8.9](https://developer.android.com/jetpack/androidx/releases/fragment#1.8.9).

Escopo: manager/helper de nome Bluetooth, catálogo/constraint Android, testes
e este registro. Sem APIs, schemas, dados de paciente, permissões novas, regras
clínicas, transportes ou alteração de identidade. Web, Tablet ACS e WhatsApp/SM
Click sem alteração; atualização/concorrência/offline de registros preservados.
Bluetooth simulado somente nos testes (**DEMO**); não demonstra conexão **REAL**
com relógio nesta rodada. Contratos backend existentes permanecem inalterados.

## Verificação

Fonte e testes verificados em `91aa3b709fcff5459671f61a5e3d5e9807a1a438`,
Windows/Codex local, 23/09/2026, JDK 21.0.12.1, Gradle 9.3.1.
Comando pelo wrapper: `:app:testDebugUnitTest :app:assembleDebug
:app:compileReleaseKotlin :app:lintDebug :app:lintRelease
-Pandroid.builder.sdkDownload=false --offline --console=plain --continue`.
Dependências Gradle do cache. Na primeira rodada focada, o Robolectric baixou
seus ambientes Android 28/31; a rodada final utilizou os ambientes disponíveis.
pnpm não se aplica ao checkout Android Kotlin.

- Rodada focada anterior: **9 PASS** e lint DEBUG PASS; versão de teste ainda
  usava factory depreciada do shadow, depois substituída por BluetoothManager.
  A alteração foi incluída no candidato e revalidada na suíte completa.
- Suíte completa no candidato: **200 casos, 199 PASS, 0 falhas, 1 SKIP**,
  pela limitação conhecida do FileProvider no Windows.
- Os cinco casos novos cobrem leitura/revogação/concessão e nome/dispositivo
  ausente em APIs 31/36, além de leitura no Android 28. O teste comprova que a
  consulta direta lança SecurityException antes de verificar o fallback.
- APK DEBUG e compilação Kotlin release: **PASS**.
- Lint DEBUG e RELEASE: **PASS**, **0 erros**, 48 avisos em cada configuração.
  Os 48 avisos DEBUG têm os mesmos IDs/mensagens/arquivos que antes. Nenhuma
  supressão ou baseline de lint adicionados. Comando conjunto: **PASS**.
- dependencyInsight: Fragment **1.8.9** em debugRuntimeClasspath e
  releaseRuntimeClasspath, substituindo a exigência transitiva 1.1.0.
- Permissões declaradas no APK comparadas por aapt: idênticas ao APK anterior.

APK `next2u-paciente-android-fixes-91aa3b7.apk` instalado por atualização no M8
WIFI Android 13, sem limpeza de dados. SHA-256 do pacote e da cópia extraída
da instalação: `D2E52BD3DEB0C72C1D0B4F82336359217B167B7ED32817E8BA1FD2BA1B7F7BEE`.
Conferência física: abertura do app e navegação Início → Relógio, sem solicitar
busca/conexão/medição ou alterar permissões reais. Telas inspecionadas.
ACS permanece 1.0.6 (16), última atualização `2026-09-15 13:35:21`.
Fonte/orientação/rotação automática preservadas em 1.0/0/1.
Commit posterior apenas documental; fonte app/ e catálogo idênticos ao candidato.

Evidências: `C:/CDev/Next2U-Patient-Delivery/2026-09-23-android-lint-fixes`.
**SELF_REVIEW_ONLY**, não CI nem revisão independente.

## RECOMMENDATIONS

Complementar com ciclo autorizado de conexão/permissão em relógio físico;
teste de plataforma simulada e abertura do app não substituem essa homologação.
