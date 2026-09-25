# Continuidade do formulário de perfil

## OBSERVED FACTS

Continuação autorizada de UI/UX do Paciente. Preflight limpo em
`cada22457350dd7304480c43689cf5312452386e`, branch
`codex/patient-responsive-devices`, remoto `leanderdulac/HBand-`.
GitHub confirmou main `f35d12b26c5a2305004271c2a05068782a1c9fc8`, PR #4 DRAFT
HEAD `9a239d9113bc671624643acc975b3e10042f4a57`, base
`04938c46fdba3ab59789943f41b7a48ea5b4b72e`, pilha #1/#2 inalterada.
Ancestralidade confirmada e sem operação Git pendente. Continuação local
reconciliada conforme FRONTEND_UI_HANDOFF; sem push/merge.

Regressão reproduzida antes da correção: restaurar o editor perdia a expansão
de Metas e contato na janela recriada. O teste anterior precisava reabri-la.

## Mudança

O editor passa a guardar expansão de Metas e contato, Identificação do cadastro
e rolagem junto aos campos, fora da janela de diálogo, vinculados ao mesmo id
de perfil. PatientSection oferece uma variante controlada pelo editor e mantém
a interface anterior para os demais consumidores. As proteções de atividades
em andamento e os rótulos de acessibilidade permanecem.

Abrir/fechar seções e rolar não alteram hasChanges nem geram gravação; Cancelar
sem editar continua fechando diretamente. A confirmação para descartar uma
edição é preservada. Salvar mantém campos e IDs canônicos.

Escopo: dois componentes UI, teste de perfil e este registro. Nenhuma mudança
de dados/validação, SDK/BLE, dependências, APIs, permissões ou persistência
operacional. Web, Tablet ACS e WhatsApp/SM Click sem impacto. Sincronização,
concorrência e offline dos registros sem alteração.
Fixtures **DEMO** apenas nos testes; não há nova capacidade **REAL** de cadastro.
Provisionamento/identidade central permanecem **BACKEND CONTRACT REQUIRED**.

## Verificação

Fonte e testes verificados em `a05ecde9a146f2a8722208daae0340cdb9000acb`,
Windows/Codex local em 23/09/2026, JDK 21.0.12.1, Gradle 9.3.1, cache offline.
Comando pelo wrapper: `:app:testDebugUnitTest :app:assembleDebug
:app:compileReleaseKotlin :app:lintDebug -Pandroid.builder.sdkDownload=false
--offline --console=plain --continue`. pnpm não se aplica ao checkout Kotlin.

- Regressão antes da correção: **FAIL esperado**, seção de metas fechada após
  restauração. Log preservado, sem classificá-lo como PASS.
- Rodada focada posterior: **18 PASS**, antes do commit, mesmo conteúdo confirmado.
- Suíte completa no candidato: **190 casos, 189 PASS, 0 falhas, 1 SKIP**,
  pela limitação conhecida Android/FileProvider no Windows.
- APK DEBUG e compilação Kotlin release: **PASS**.
- Lint: **FAIL**, mesmos `MissingPermission` em HBandBleManager.kt:1640 e
  `InvalidFragmentVersionForActivityResult` em MainActivity.kt:24; 48 avisos.
  Nenhuma supressão. Resultado global do comando conjunto não é PASS.

APK `next2u-paciente-profile-continuity-a05ecde.apk` instalado via atualização
no M8 WIFI Android 13, sem limpar dados. SHA-256 do arquivo e da cópia extraída
da instalação: `37A0A4FE0A3FFFB8946D51CD2B0787283B23F847371B4386A675F44E8345094E`.

Conferência física: abrir/fechar Opções do relógio em Ajustes, abrir Meu perfil,
conferir indisponibilidade e Fechar. Primeira captura logo após instalação
falhou com null root node do UI Automator; descartada e repetida com sucesso.
Nenhum cadastro criado/editado, envio, conexão ou medição disparados.
Fonte 1.0, orientação 0 e rotação automática 1 preservadas e relidas. ACS mantém
1.0.6 (16), última atualização `2026-09-15 13:35:21`.

Continuidade de perfil preenchido é evidência de componente com fixtures:
ambas as seções abertas, mesma posição de rolagem na restauração de mesma janela,
nome/meta editados e mesmos IDs ao salvar. Em mudança de altura, a rolagem pode
ser limitada pelo novo tamanho do conteúdo. Não se declara ensaio físico de
edição/rotação com perfil, smartphone físico ou TalkBack completo.
Evidências/logs/APK: `C:/CDev/Next2U-Patient-Delivery/2026-09-23-profile-continuity`.
Commit posterior somente documental; conteúdo app/ permanece no candidato.

**SELF_REVIEW_ONLY**, não CI ou revisão independente. Sem declaração de
produção pronta.

## RECOMMENDATIONS

Validar com cadastro de teste provisionado e autorizado em aparelho físico,
incluindo teclado, rotação, smartphone e TalkBack. O M8 permanece sem perfil;
nenhum cadastro é criado para simular essa disponibilidade.
