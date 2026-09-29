# Continuidade entre abas e navegação de datas

## OBSERVED FACTS

Continuação autorizada pelo usuário das melhorias UI/UX para smartphones e
tablets. Preflight de 23/09/2026: fonte limpa na branch
`codex/patient-responsive-devices`, HEAD
`efaf50553dc9ad0b677eee8aadc833328f8c14b5`.
GitHub reconfirmou `leanderdulac/HBand-`, main
`f35d12b26c5a2305004271c2a05068782a1c9fc8`, PR #4 DRAFT com HEAD
`9a239d9113bc671624643acc975b3e10042f4a57` e base
`04938c46fdba3ab59789943f41b7a48ea5b4b72e`. PRs #1/#2 continuam sendo a pilha
de integração preservada, sem conflito novo; os commits locais de frontend
seguem a continuação documentada. Nenhuma nova autorização histórica foi inferida.

No M8 com o APK anterior, selecionar 22/09 no Histórico, navegar ao Início e
retornar mudava o dia para 23/09. Três regressões de componente reproduziram
a perda do dia, do filtro de medição e do rascunho MAC da ajuda do relógio.
O pequeno wrapper inicial usado para essa reprodução preservava exatamente a
composição condicional anterior, sem retenção de estado.

## Alteração

- Cada aba conserva seu estado restaurável de interface enquanto está fora
  da composição. Não mantém telas executando em segundo plano e não introduz
  armazenamento de domínio, login, sincronização ou comandos automáticos.
- Histórico mantém dia e filtro ao trocar de aba. A ajuda do relógio mantém
  abertura/rascunho; restaurar o código não dispara conexão.
- Controles de dia usam duas colunas somente com largura suficiente e texto
  medido na escala escolhida. Telas estreitas/letras maiores mantêm coluna única.
- “Voltar para hoje” permite sair de um dia anterior em uma ação.
- A orientação de datas usa “aparelho”, adequada a celular e tablet.

Entidades, IDs, callbacks, BLE/VE30, permissões e persistência ficam preservados.
Web, ACS e WhatsApp/SM Click não recebem mudanças. Os testes de componentes usam
**DEMO**; nenhuma nova capacidade **REAL** é declarada. Identidade/proveniência e
recebimento central seguem **BACKEND CONTRACT REQUIRED** conforme handoff.

## Verificação

Resultados do candidato registrados abaixo. Ensaios locais
Windows/Codex com JDK 21.0.12.1, Gradle 9.3.1 e cache existente offline, sem
mudanças de dependências. Checks locais são **SELF_REVIEW_ONLY**, não CI ou
revisão independente. Os dois bloqueios de lint herdados não serão suprimidos.

## RECOMMENDATIONS

Repetir a navegação em smartphone físico e com usuários representativos.
O estado preservado é de interface; não representa cadastro de paciente ou
confirmação de envio ao backend.

## Resultado final

Candidato verificado: `f79704be336f70d0a6f2288a2e322dc35e2c786d`, fonte limpa.
Código de produção idêntico a `eee2d4b596b254d24936558e34fa3774affa8e1b`;
os dois commits seguintes ajustam exclusivamente o teste do cronômetro.
Verificação local/Codex em 23/09/2026, aproximadamente 00:34–00:35 BRT:

`:app:testDebugUnitTest :app:assembleDebug :app:compileReleaseKotlin
:app:lintDebug -Pandroid.builder.sdkDownload=false --offline --console=plain --continue`.

- **175 testes: 174 aprovados, 1 ignorado, zero falhas/erros**. O ignorado mantém
  a limitação Windows já documentada. Seis casos novos verificam dia/filtro,
  ajuda de conexão após restauração, pausa da respiração, layout e retorno a hoje.
- APK DEBUG e compilação Kotlin release: **PASS**, tarefas atuais ou reutilizadas
  pelo Gradle no mesmo conteúdo/configuração, conforme log.
- Lint: **FAIL**, `MissingPermission` em `HBandBleManager.kt:1640` e
  `InvalidFragmentVersionForActivityResult` em `MainActivity.kt:24`; 48 avisos.
  São os mesmos bloqueios herdados, sem supressão. Resultado global não é PASS.

As três regressões de continuidade falharam antes da correção. A rodada focada
posterior aprovou 10 testes. Na suíte completa inicial, o novo teste da respiração
comparava um instante anterior à saída com o estado já estabilizado, diferindo
em um segundo contado durante a transição. Uma tentativa de congelar o relógio
virtual impediu a recomposição no momento esperado e também falhou.
O teste final permite no máximo esse último tick de saída, exige preservação do
tempo, pausa ao retornar, nenhuma contagem dos seis segundos fora da aba, mais
seis segundos sem avanço após voltar e nenhuma chamada de salvar. Não houve
alteração no cronômetro de produção. Logs intermediários foram preservados.

APK `next2u-paciente-navigation-eee2d4b.apk`, SHA-256
`F1AAB68FFDBC0712A4518688D294719394531B5B9D83807F2A94FB708C2A89F0`.
Revalidado com o mesmo hash após os checks finais. Instalado via atualização no
M8 físico, sem desinstalar ou apagar dados.

### Conferência no M8

- Antes: 22/09 → Início → Histórico retornava para 23/09.
- Depois: 22/09 permanece ao trocar de aba e ao girar o tablet; o filtro Pressão
  arterial também permanece ao navegar para Início e retornar.
- Em paisagem, Dia anterior e Dia seguinte ficam lado a lado; Voltar para hoje
  retorna a 23/09 com um toque. Em retrato com fonte 1.6, rótulos e ações foram
  inspecionados visualmente e continuam legíveis; a largura ainda comporta a dupla.
  Empilhamento em janela estreita/fonte extrema está coberto nos testes de componentes.
- Fonte 1.0, rotação automática 1 e orientação do usuário 0 restauradas.
  App deixado no Histórico de hoje. Nenhuma conexão VE30, medição, edição de
  perfil ou envio manual foi disparado para esse ensaio.
- ACS permanece 1.0.6 (16), última atualização 15/09/2026 13:35:21.

Os ensaios desta rodada são tablet físico e componentes simulando tamanhos de
janela; não houve smartphone físico nem nova execução de emulador Android.
Evidências, logs e APK: `C:/CDev/Next2U-Patient-Delivery/2026-09-23-navigation`.

**SELF_REVIEW_ONLY:** diff limitado a três arquivos de UI, dois arquivos de
testes e este relatório. Sem mudanças de contrato, infraestrutura, dependências
ou integração REAL. Nenhum push/merge; publicação continua dependente de escrita
no repositório, conforme o handoff. Esta atualização não declara produção pronta.
