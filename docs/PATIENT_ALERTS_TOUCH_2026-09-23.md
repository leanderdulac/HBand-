# Controle identificado dos avisos

## OBSERVED FACTS

Continuação autorizada de UI/UX do app Paciente para celular e tablet.
Preflight limpo em `8a659d89e2b917c8d3417b44e855837c3db767ad`, branch
`codex/patient-responsive-devices`, remoto `leanderdulac/HBand-`.
GitHub confirmou main `f35d12b26c5a2305004271c2a05068782a1c9fc8`, PR #4 DRAFT
HEAD `9a239d9113bc671624643acc975b3e10042f4a57`, base
`04938c46fdba3ab59789943f41b7a48ea5b4b72e`, pilha #1/#2 inalterada.
Ancestralidade confirmada, árvore limpa e sem operação Git pendente.
Continuação local reconciliada conforme FRONTEND_UI_HANDOFF e pedido atual.
GitHub confirma pull=true, push=false; entrega local, sem push/merge.

## Mudança

A ativação dos avisos passa a ter o rótulo adjacente “Ativar avisos neste
aparelho”. Toda a linha é tocável, com mínimo de 56 dp, espaço entre texto e
interruptor, uma única ação acessível e indicação de ligado/desligado.
Textos de avisos/permissão referem-se ao aparelho, atendendo também tablets.
Mantidos os limites, presets, estado, callbacks e dependência das leituras e
da permissão de notificação. Não se infere entrega de notificação pela ativação.

Escopo: SettingsTab, PatientNotificationSettings, teste dos controles e este
registro. Nenhuma alteração de regras clínicas, notificações emitidas, SDK/BLE,
dependências, dados, IDs canônicos, APIs, permissões ou persistência. Web, Tablet
ACS e WhatsApp/SM Click sem impacto; concorrência/sincronização/offline inalterados.
Fixtures **DEMO** apenas nos testes. Não declara nova capacidade **REAL** de
monitoramento. Provisionamento/identidade permanecem **BACKEND CONTRACT REQUIRED**.

## Verificação

Fonte e testes em `abc788acecff2d00447d715673abf5328682862f`, Windows/Codex
local, 23/09/2026, JDK 21.0.12.1, Gradle 9.3.1, cache offline.
Comando pelo wrapper: `:app:testDebugUnitTest :app:assembleDebug
:app:compileReleaseKotlin :app:lintDebug -Pandroid.builder.sdkDownload=false
--offline --console=plain --continue`. pnpm não se aplica ao checkout Kotlin.

- Foco antes do commit: **7 PASS**, mesmo conteúdo confirmado no candidato.
  Toque no rótulo em 320 dp/fonte 2.0 e 960 dp/fonte 1.6, texto sem overflow,
  uma única ação acessível, callback uma vez por ação, limites preservados e
  nenhum callback de teste de aviso acionado. Testes existentes de presets,
  permissão, intents e notificações em ambiente simulado passaram.
- Suíte no candidato: **195 casos, 194 PASS, 0 falhas, 1 SKIP**, pela limitação
  conhecida do FileProvider no Windows.
- APK DEBUG e compilação Kotlin release: **PASS**.
- Lint: **FAIL**, os mesmos `MissingPermission` em HBandBleManager.kt:1640 e
  `InvalidFragmentVersionForActivityResult` em MainActivity.kt:24, com 48 avisos.
  Nenhuma supressão; o resultado global do comando não é PASS.

APK `next2u-paciente-alerts-touch-abc788a.apk` instalado por atualização no M8
WIFI Android 13, sem limpeza de dados. SHA-256 do arquivo e da cópia extraída
da instalação: `AF2644830AFD883D86DBB101085015F17796A14E52488912EDC91B1F49F557C9`.

Conferência física em retrato/fonte 1.0: rótulo, interruptor, textos de permissão
e rolagem legíveis. Avisos permaneceram ligados com limites locais de 50–100 bpm,
conforme observado antes da instalação; preferência e limites não foram tocados.
Acionamento testado somente nos componentes, sem disparar aviso no tablet.
Fonte 1.0, orientação 0 e rotação automática 1 preservadas e relidas.
ACS segue 1.0.6 (16), última atualização `2026-09-15 13:35:21`.
Não se declara teste físico de smartphone, paisagem ou TalkBack nesta rodada.
Commit posterior apenas documental, conteúdo app/ idêntico ao candidato.

Evidências: `C:/CDev/Next2U-Patient-Delivery/2026-09-23-alerts-touch`.
**SELF_REVIEW_ONLY**, não CI ou revisão independente.

## RECOMMENDATIONS

Concluir homologação em smartphone físico e TalkBack. A verificação de
smartphone por componente não substitui esses ensaios.
