# Perfil adaptável — smartphone e tablet

## OBSERVED FACTS

Continuação autorizada de UI/UX do app Paciente. Preflight limpo em
`93450d153493f65bee7f424b02a7e40d815c4b32`, branch
`codex/patient-responsive-devices`, remoto `leanderdulac/HBand-`.
GitHub confirmou main `f35d12b26c5a2305004271c2a05068782a1c9fc8`, PR #4 DRAFT
com HEAD `9a239d9113bc671624643acc975b3e10042f4a57` e base
`04938c46fdba3ab59789943f41b7a48ea5b4b72e`; pilha #1/#2 inalterada.
Ancestralidade local confirmada; nenhuma operação Git pendente. Permissão
GitHub somente leitura. Continuação local do FRONTEND_UI_HANDOFF sem push/merge.

## Mudança e fronteiras

O formulário de perfil passa de AlertDialog para uma janela de edição até
960 dp, com margens. Dados pessoais e opções adicionais reutilizam o layout
adaptável: duas colunas com espaço/fonte adequados, uma coluna caso contrário.
Título e rodapé permanecem fora da área rolável; Salvar/Cancelar podem ocupar
linhas separadas quando necessário. Orientação passa a dizer “aparelho”.
A mensagem de perfil indisponível permanece sem oferecer cadastro fictício.

Escopo: UserProfileDialog, testes UI e este registro. Não altera campos,
validação, IDs, callback de salvar, confirmação de descarte ou persistência.
Web Profissional, Tablet ACS e WhatsApp/SM Click sem impacto; SDK/BLE, APIs,
permissões, concorrência, sincronização e offline preservados.
Perfis preenchidos nos testes são fixtures **DEMO**, não disponibilização
**REAL** de cadastro. Provisionamento/identidade central continuam
**BACKEND CONTRACT REQUIRED**. O pedido atual autoriza UI nativa, sem ampliar
as fronteiras do projeto Web ou o escopo do handoff para integração.

## Verificação

Fonte e testes verificados em `ae4a20b925fd7f4cf02defb883e5453015c5a630`,
Windows/Codex local em 23/09/2026, JDK 21.0.12.1 e Gradle 9.3.1, cache offline.
Comando pelo wrapper: `:app:testDebugUnitTest :app:assembleDebug
:app:compileReleaseKotlin :app:lintDebug -Pandroid.builder.sdkDownload=false
--offline --console=plain --continue`. pnpm não é aplicável ao checkout Kotlin.
Sem dependências novas.

- Rodada focada final: **11 PASS**, antes do commit, mesmo conteúdo confirmado.
  `-Proborazzi.test.record=true` gerou as capturas inspecionadas de tablet
  960×600 dp e celular deitado 640×320 dp com fonte 2.0.
- Suíte completa: **189 casos, 188 PASS, 0 falhas, 1 SKIP**, pela limitação
  conhecida de caminhos Android/FileProvider no Windows.
- APK DEBUG e compilação Kotlin release: **PASS**.
- Lint: **FAIL**, mesmos `MissingPermission` em HBandBleManager.kt:1640 e
  `InvalidFragmentVersionForActivityResult` em MainActivity.kt:24; 48 avisos.
  Sem supressão. O comando conjunto termina com falha, não PASS global.

Houve três falhas intermediárias de teste: o provedor Compose externo não
alterou a densidade da janela de diálogo; alterar a configuração Android
durante a execução desmontou a Activity do harness; a janela interna recriada
não preservou a seção expandida. O teste final aplica fonte 1.6 antes da
composição e reabre a seção para inspecionar o rascunho restaurado. Asserta
conteúdo editado e identidade ao salvar; não declara ensaio físico de rotação
com perfil nem persistência da expansão da janela interna. Logs preservados.

APK `next2u-paciente-profile-ae4a20b.apk` instalado via atualização no M8 WIFI
Android 13, sem limpar dados. SHA-256 do arquivo e da cópia extraída da instalação:
`0E4070A944D25D8829179E0DF85E1B70318CED56ECE35A08416AF8D27393D423`.

O M8 estava temporariamente indisponível; reconectou como transporte USB 1
sem serial na listagem. ro.serialno e ro.boot.serialno confirmaram o mesmo
`1NB248K01200322` antes de qualquer instalação. Fonte 1.0, orientação 0 e
rotação automática 1 preservadas e relidas. ACS permanece 1.0.6 (16), última
atualização `2026-09-15 13:35:21`.

Ensaio físico: abrir Meu perfil, conferir indisponibilidade e Fechar, sem
cadastro criado ou editado. O M8 segue sem perfil provisionado; formulário
preenchido verificado somente em componentes com fixtures. Nenhuma medição,
conexão ou envio manual disparados. Smartphone físico/teclado real do formulário
e TalkBack completo não exercitados nesta rodada.
Evidências/logs/APK: `C:/CDev/Next2U-Patient-Delivery/2026-09-23-profile-layout`.
Commit posterior somente documental; app/ idêntico ao candidato verificado.

**SELF_REVIEW_ONLY**, não CI nem revisão independente. Sem declaração de
produção pronta ou integração REAL validada.

## RECOMMENDATIONS

Conferir formulário com cadastro de teste autorizado, teclado real, smartphone
físico e usuários/TalkBack antes de concluir validação de usabilidade completa.
