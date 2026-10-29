# Área de toque em Ajustes

## OBSERVED FACTS

Continuação autorizada de UI/UX do Paciente para smartphones e tablets.
Preflight limpo em `b5bf188b751ce15becbcfa68ec042a2bd1d690b6`, branch
`codex/patient-responsive-devices`, remoto `leanderdulac/HBand-`.
GitHub confirmou main `f35d12b26c5a2305004271c2a05068782a1c9fc8`, PR #4 DRAFT
HEAD `9a239d9113bc671624643acc975b3e10042f4a57`, base
`04938c46fdba3ab59789943f41b7a48ea5b4b72e`, pilha #1/#2 inalterada.
Continuação local reconciliada conforme FRONTEND_UI_HANDOFF. Sem push/merge;
permissão remota de leitura. Nenhuma operação Git pendente.

## Mudança

Toda a área de Reconectar relógio recebe o toque, incluindo título e descrição.
Um único alvo acessível informa papel de interruptor e estado ligado/desligado;
o interruptor visual não cria uma segunda ação. Altura mínima de 56 dp,
separação de 12 dp entre texto e interruptor e texto usando a largura disponível.
Mesmo estado controlado e callback existentes; nenhuma alteração na reconexão BLE.

Escopo: SettingsTab, seus testes e este registro. Sem alteração de dependências,
SDK, APIs, dados, IDs, permissões, persistência ou contratos. Web, Tablet ACS e
WhatsApp/SM Click sem impacto. Atualização, concorrência e offline inalterados.
Estado sintético apenas nos testes (**DEMO**); não comprova nova capacidade
**REAL** de conexão. Identidade/provisionamento continuam **BACKEND CONTRACT REQUIRED**.

## Verificação

Fonte/testes verificados em `b0d4f24137107da1714bde07474b79e1a8d8a5d6`,
Windows/Codex local, 23/09/2026, JDK 21.0.12.1, Gradle 9.3.1, cache offline.
Comando pelo wrapper: `:app:testDebugUnitTest :app:assembleDebug
:app:compileReleaseKotlin :app:lintDebug -Pandroid.builder.sdkDownload=false
--offline --console=plain --continue`. pnpm não se aplica ao checkout Kotlin.

- Foco SettingsTab: 5 PASS. Acionamento pela descrição, estado controlado,
  exatamente uma chamada por ação, ausência de ação duplicada no interruptor,
  texto sem overflow em 320 dp/fonte 2.0 e 960 dp/fonte 1.6.
- Duas rodadas focadas anteriores falharam na largura medida do título em
  fonte 1.6; título/descrição passaram a ocupar a largura da coluna. Logs das
  falhas preservados, sem classificá-los como PASS.
- Suíte completa: **193 casos, 192 PASS, 0 falhas, 1 SKIP** pela limitação
  conhecida de FileProvider no Windows.
- APK DEBUG e compilação Kotlin release: **PASS**.
- Lint: **FAIL**, 2 problemas anteriores (`MissingPermission`,
  HBandBleManager.kt:1640; `InvalidFragmentVersionForActivityResult`,
  MainActivity.kt:24) e 48 avisos. Nenhuma supressão. Resultado global FAIL.

APK `next2u-paciente-settings-touch-b0d4f24.apk` instalado via atualização
no M8 WIFI Android 13, sem limpar dados. SHA-256 do arquivo e da cópia
extraída da instalação: `54F12D9307BAA39B3D33DA97DF042E1A3301DBDF192C1F182779FD74F299AB17`.

Conferência física em retrato, fonte 1.0 e 1.6: textos completos e uma única
área acessível ligada/clicável. Preferência de reconexão não alternada no
aparelho; acionamento validado nos testes de componente. A tentativa de orientar
por configuração não resultou em paisagem na captura; não se declara paisagem
física validada nesta rodada. Primeira captura após mudança de configuração
retornou null root node e foi descartada; repetição válida inspecionada.
Fonte 1.0, orientação 0 e rotação automática 1 restauradas e relidas.
ACS permanece 1.0.6 (16), última atualização `2026-09-15 13:35:21`.
Nenhum cadastro, envio, medição ou conexão disparados para os testes físicos.

Commit posterior apenas documental; conteúdo app/ permanece no candidato.

Evidências: `C:/CDev/Next2U-Patient-Delivery/2026-09-23-settings-touch`.
**SELF_REVIEW_ONLY**; não CI nem revisão independente.

## RECOMMENDATIONS

Completar homologação em smartphone físico e com TalkBack. As verificações de
layout em smartphone são de componente, sem alegar ensaio físico nesse formato.
