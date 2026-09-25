# Continuidade da prévia ao girar o aparelho

## OBSERVED FACTS

Continuação autorizada de UI/UX do app Paciente. Preflight limpo em
`3a5e1c0dbe70fa91c69da53c9fd110e3e0388708`, branch
`codex/patient-responsive-devices`, remoto `leanderdulac/HBand-`.
GitHub confirmou main `f35d12b26c5a2305004271c2a05068782a1c9fc8`, PR #4
DRAFT com HEAD `9a239d9113bc671624643acc975b3e10042f4a57` e base
`04938c46fdba3ab59789943f41b7a48ea5b4b72e`. Pilha #1/#2 inalterada,
ancestralidade confirmada e nenhuma operação Git pendente. Continuação local
reconciliada conforme FRONTEND_UI_HANDOFF, sem ampliar o escopo a BLE/backend.

Problema observado no M8 e documentado em PATIENT_SHARE_LAYOUT_2026-09-23:
HomeScreen guardava o cartão em remember, descartado ao recriar a Activity.

## Mudança

Um ViewModel de apresentação mantém somente a referência do cartão preparado
durante mudanças de configuração. HomeScreen usa esse estado para abrir/fechar
a prévia. Fechar ou encerrar definitivamente o dono libera a referência; não
há retenção de Activity/Context, gravação de bitmap no Bundle, nova persistência
ou geração automática. Morte do processo inicia sem prévia, por ser transitória.
Mensagens de resultado/erro do diálogo usam estado salvo textual, sem repetir
a ação que as produziu. URI, arquivo, bitmap e resumo preparados são preservados.

Escopo: HomeScreen, estado UI novo, diálogo, testes e este registro.
Sem novas dependências, mudanças no MainViewModel de domínio, SDK, transportes,
permissões ou contratos. Web, ACS e WhatsApp/SM Click sem impacto. IDs e
proveniência dos registros, sincronização/concorrência/offline inalterados.
Fixtures dos testes são **DEMO**; nenhuma nova capacidade **REAL** declarada.
Identidade/recepção central seguem **BACKEND CONTRACT REQUIRED**.

## Verificação

Fonte e testes verificados em `aa1996d3e553496c03f8385e7e1391ac683161ed`,
Windows/Codex local em 23/09/2026, JDK 21.0.12.1 e Gradle 9.3.1 com cache offline.
Comando pelo wrapper: `:app:testDebugUnitTest :app:assembleDebug
:app:compileReleaseKotlin :app:lintDebug -Pandroid.builder.sdkDownload=false
--offline --console=plain --continue`. pnpm não se aplica ao checkout Kotlin.

- Rodada focada: **8 PASS**, antes do commit, mesmo conteúdo confirmado.
- Suíte completa: **186 casos, 185 PASS, 0 falhas, 1 SKIP**, pela limitação
  conhecida de caminhos Android/FileProvider no Windows.
- APK DEBUG e compilação Kotlin release: **PASS**.
- Lint: **FAIL**, mesmos `MissingPermission` em HBandBleManager.kt:1640 e
  `InvalidFragmentVersionForActivityResult` em MainActivity.kt:24; 48 avisos.
  Sem supressões. Comando conjunto termina com falha, não resultado global PASS.

APK `next2u-paciente-share-continuity-aa1996d.apk`, instalado via atualização no
M8 WIFI Android 13, sem limpar dados. SHA-256 do arquivo e da cópia extraída
da instalação: `68B6859E0A29C51CF4CFAA7409B860EE951BD81EB6AB9F99C8A60B7A576CE713`.

Conferência física: cartão preparado em retrato permanece aberto em paisagem
e após fonte 1.0 → 1.6. Capturas inspecionadas e presença da ação Fechar conferida.
Lista dos arquivos weekly_health_progress no cache é idêntica antes/depois das
duas mudanças, sem nova preparação. Depois de Fechar, voltar a fonte 1.0 e
retrato não reabre a prévia. Asserções sobre mesma referência de bitmap/arquivo/
URI/resumo, fechamento e liberação no fim do dono são cobertas por testes UI.
Mensagens de erro foram exercitadas somente com serviços de teste indisponíveis.

Primeira captura após instalação falhou com null root node do UI Automator;
foi descartada. Processo/Activity estavam ativos, sem erro AndroidRuntime na
consulta; captura repetida válida. Não foi tratada como evidência de tela.

Fonte 1.0, orientação do usuário 0 e rotação automática 1 restauradas e relidas.
Nenhum envio, cópia, conexão ou medição disparados. ACS permanece 1.0.6 (16),
última atualização `2026-09-15 13:35:21`. App Paciente deixado em Relógio.
Evidências/logs/APK: `C:/CDev/Next2U-Patient-Delivery/2026-09-23-share-continuity`.
Commit posterior somente documental; app/ idêntico ao candidato verificado.

**SELF_REVIEW_ONLY**, não CI nem revisão independente. Entrega local, sem
push/merge. Smartphone físico e TalkBack completo não exercitados nesta rodada.

## RECOMMENDATIONS

Complementar a inspeção do M8 com smartphone físico e TalkBack completo.
Interromper uma preparação ainda em andamento e morte do processo são cenários
distintos da retenção de um cartão já pronto; não se promete reinício automático.
