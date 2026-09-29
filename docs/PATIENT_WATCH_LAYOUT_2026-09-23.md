# Relógio — layout adaptável e entrada de código

## OBSERVED FACTS

Continuação autorizada de UI/UX para smartphones e tablets. Preflight limpo em
`a62f8f9effcc6c0458b20b2a8138caf1531e7c58`, branch
`codex/patient-responsive-devices`, remoto `leanderdulac/HBand-`.
GitHub confirmou main `f35d12b26c5a2305004271c2a05068782a1c9fc8`, PR #4 DRAFT
com HEAD `9a239d9113bc671624643acc975b3e10042f4a57` e base
`04938c46fdba3ab59789943f41b7a48ea5b4b72e`; PRs #1/#2 permanecem na pilha
de integração já documentada. Nenhuma divergência nova ou alteração inesperada.

## Mudança

- Tela Relógio reutiliza o layout adaptável existente: conexão/orientações e
  resultados lado a lado quando há espaço suficiente; uma coluna em janelas
  estreitas ou com letras ampliadas. Mantém ordem e objetos de dispositivos.
- Instruções dizem “aparelho” e “lista”, adequadas às duas disposições.
- Cabeçalho dos resultados expõe a contagem da lista e região dinâmica para
  acessibilidade. É informação sobre a lista, não confirmação de conexão.
- Campo MAC solicita teclado ASCII, sem autocorreção, com ação Concluído.
  Concluído remove foco e fecha o teclado; conectar continua exigindo o botão
  explícito e as mesmas validações/permissões existentes.

Escopo: um componente UI, seus testes e este registro. Não há alteração no SDK,
BLE, permissões, callbacks de domínio, IDs, armazenamento ou integrações.
Web, Tablet ACS e WhatsApp/SM Click ficam sem impacto. Nenhuma nova capacidade
**REAL** é declarada. Fixtures são **DEMO**. Identidade, proveniência e recepção
central permanecem **BACKEND CONTRACT REQUIRED** conforme o handoff.

## Verificação

Fonte e testes verificados no candidato
`48bdb4a3faeff5fa84046bdab591c4f9360f739f`, em execução local Windows/Codex,
JDK 21.0.12.1, Gradle 9.3.1, cache existente offline. Sem dependências novas.
Checks locais não são CI. O commit posterior modifica somente este registro.

- Rodada focada corrigida: **11 PASS**, incluindo continuidade entre abas.
- Suíte completa: **179 casos, 178 PASS, 0 falhas, 1 SKIP**. O caso ignorado
  exige caminhos Android no FileProvider, limitação Windows já documentada.
- APK DEBUG e compilação Kotlin release: **PASS**.
- Lint: **FAIL**, mesmos `MissingPermission` em `HBandBleManager.kt:1640` e
  `InvalidFragmentVersionForActivityResult` em `MainActivity.kt:24`; 48 avisos.
  Nenhuma supressão. O comando conjunto terminou com falha por esses bloqueios;
  não há declaração de resultado global PASS.

O primeiro comando focado falhou ao compilar um teste que encadeava uma ação
de teclado sem valor de retorno; corrigido no teste. Não é registrado como PASS.

### Instalação e conferência visual

APK `next2u-paciente-watch-48bdb4a.apk`, SHA-256
`624DCE7ABAB4E8CD23068200E58BCD12504A0431106E6A54DBFDE12996BEA770`.
Atualizado no M8 WIFI Android 13 sem desinstalação ou limpeza de dados. A cópia
extraída do pacote instalado tem exatamente o mesmo hash.

- Paisagem/fonte 1.0: conexão e resultados lado a lado; ação de ajuda abaixo.
- Retrato/fonte 1.0: conteúdo em uma coluna, navegação lateral preservada.
- Fonte 1.6: conteúdo em uma coluna nas duas orientações; retrato passa a usar
  navegação inferior. Capturas inspecionadas; conteúdo comprido permite rolagem.
- Campo vazio da ajuda abre o teclado; tocar Concluído o fecha e remove o foco.
  Nenhum código foi digitado no aparelho e nenhuma busca/conexão foi disparada.
  Preservação de código e identidade dos dispositivos foi verificada nos testes
  de componentes com fixtures, não com dados de um relógio físico.
- Fonte 1.0, orientação do usuário 0 e rotação automática 1 restauradas e lidas
  novamente. App deixado em Relógio, ajuda fechada, sem teclado aberto.
- ACS permanece 1.0.6 (16), última atualização `2026-09-15 13:35:21`.

Smartphones foram cobertos por testes de componentes em janela estreita;
não houve ensaio em smartphone físico nem emulador nesta rodada.
Evidências, logs, XMLs e APK estão em
`C:/CDev/Next2U-Patient-Delivery/2026-09-23-watch-layout`.

**SELF_REVIEW_ONLY**. A presença de semântica para leitor de tela não equivale
a validação da fala ou da experiência completa com TalkBack. Conexão física com
VE30 não faz parte deste ensaio de apresentação. Nenhum push ou merge.

## RECOMMENDATIONS

Validar compreensão e acessibilidade com usuários representativos, incluindo
smartphone físico. Publicação depende de escrita no repositório; os bloqueios
de lint herdados continuam separados desta melhoria visual, sem supressão.
