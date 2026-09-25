# Prévia de cartão adaptável

## OBSERVED FACTS

Continuação autorizada de UI/UX do app Paciente. Preflight limpo em
`45c10f998c10e76a91db15d19b9f74bde2ad1a9f`, branch
`codex/patient-responsive-devices`, remoto `leanderdulac/HBand-`.
GitHub confirmou main `f35d12b26c5a2305004271c2a05068782a1c9fc8`, PR #4
DRAFT com HEAD `9a239d9113bc671624643acc975b3e10042f4a57`, base
`04938c46fdba3ab59789943f41b7a48ea5b4b72e`, pilha #1/#2 inalterada.
Ancestralidade local confirmada, sem operação Git pendente. Continuação local
do frontend documentado, sem alteração de base/integração ou push/merge.

## Mudança e limites

ShareProgressDialog aproveita a largura disponível até 960 dp, com margens.
Prévia e resumo/ações ficam lado a lado quando a largura e o tamanho de fonte
permitem, reutilizando PatientSummaryLayout. Caso contrário ficam empilhados.
A imagem tem largura limitada a 360 dp para evitar crescimento desnecessário
em retrato. Fechar permanece fora da área rolável. Descrição da imagem deixa
de pressupor que o texto estará abaixo dela.

Escopo: componente de apresentação, seus testes e este registro. O pedido atual
autoriza UI nativa conforme FRONTEND_UI_HANDOFF; os limites Web não ampliam
esse alcance para SDK, BLE ou backend. Nenhuma alteração na geração de dados,
URI, conteúdo/recipiente de compartilhamento, permissões, clipboard ou intents.
Abrir a prévia continua sem enviar. Não há novo contrato ou capacidade **REAL**.
Fixtures dos testes são **DEMO**; identidade/recepção central seguem
**BACKEND CONTRACT REQUIRED**. Web, Tablet ACS e WhatsApp/SM Click sem impacto;
IDs, concorrência, sincronização e offline preservados.

## Verificação

Fonte e testes verificados no candidato
`2a5dddb5462195f27ab275c79506af60e4e2c74b`, Windows/Codex local em 23/09/2026,
JDK 21.0.12.1, Gradle 9.3.1, cache offline existente. Comando pelo wrapper:
`:app:testDebugUnitTest :app:assembleDebug :app:compileReleaseKotlin :app:lintDebug
-Pandroid.builder.sdkDownload=false --offline --console=plain --continue`.
Gradle substitui pnpm, inaplicável ao checkout Kotlin. Nenhuma dependência nova.

- Rodada focada: 5 PASS antes do commit, mesmo conteúdo confirmado no candidato.
- Suíte completa: 183 casos, **182 PASS, 0 falhas, 1 SKIP**, pela limitação
  conhecida de caminhos Android/FileProvider no Windows.
- APK DEBUG e compilação Kotlin release: **PASS**.
- Lint: **FAIL**, mesmos `MissingPermission` em HBandBleManager.kt:1640 e
  `InvalidFragmentVersionForActivityResult` em MainActivity.kt:24; 48 avisos.
  Sem supressões. Resultado global do comando conjunto não é PASS.

APK `next2u-paciente-share-2a5dddb.apk` instalado por atualização no M8 WIFI
Android 13, sem limpar dados. SHA-256 do arquivo e da cópia extraída da instalação:
`2822EF140E789E8D831F357BA55E67F14A7E7D5261EF4EA2FCDDCCA6E06716A2`.
Evidências/logs/APK: `C:/CDev/Next2U-Patient-Delivery/2026-09-23-share-layout`.

Conferência física: retrato/fonte 1.0 com imagem limitada e texto abaixo;
paisagem/fonte 1.0 com imagem/resumo lado a lado; paisagem/fonte 1.6 com uma
coluna. Rolagem alcança as duas ações; Fechar permanece visível. Capturas
inspecionadas. Cartões foram preparados com os dados disponíveis da instalação,
sem medições; nenhuma fixture adicionada no aparelho, envio ou cópia disparados.
Fonte 1.0, orientação 0 e rotação automática 1 restauradas e relidas.
ACS permanece 1.0.6 (16), última atualização `2026-09-15 13:35:21`.
Smartphone físico e TalkBack completo não foram exercitados nesta rodada.

Limitação observada: girar o aparelho recria HomeScreen e fecha a prévia,
porque activeShareData usa remember. É preciso preparar novamente o cartão na
nova orientação. Esse estado pré-existente não foi alterado; esta rodada não
declara continuidade da prévia ao girar. Não guardar bitmap no estado salvo do
Android como correção automática. A continuidade requer ajuste próprio de UI.

Commit posterior altera apenas este registro; conteúdo app/ permanece igual.

**SELF_REVIEW_ONLY**, não CI nem aprovação independente.

## RECOMMENDATIONS

Conferir a experiência com usuários, smartphone físico e TalkBack completo.
Não confundir ensaio visual ou fixtures com integração REAL ou envio validado.
