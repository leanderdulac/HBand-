# Interface adaptável — smartphone e tablet

## OBSERVED FACTS — escopo e origem

O usuário autorizou continuar as melhorias de UI/UX para smartphones e tablets
após instalar o app Paciente no M8_WIFI Android 13 (800×1280, densidade 213).
O ACS `com.next2u.acs.qa` permanece fora do escopo.

Preflight de 22/09/2026: checkout limpo em
`913bfdc8a077a95d3c52689cec2d689b2090e813`, remoto `leanderdulac/HBand-`.
GitHub confirmou main `f35d12b26c5a2305004271c2a05068782a1c9fc8`, PR #4 DRAFT
com HEAD `9a239d9113bc671624643acc975b3e10042f4a57` e base
`04938c46fdba3ab59789943f41b7a48ea5b4b72e` na pilha de integrações existente.
Os 86 commits locais são descendentes do HEAD publicado, já documentados no
handoff; não existe divergência de ancestralidade nem mudança inesperada.
Os PRs #1/#2 pertencem à pilha de integração preservada; #4 é o frontend existente.
A conta possui somente READ. Não houve push, merge ou aprovação independente.

O APK instalado inicialmente corresponde à fonte
`653748ab1da71bde161f4d8991acfeb62619cad9`, SHA-256
`FD70709ED6E05985BD70270FE836D4A3417E4ABB26B54A964911B55E62354016`.
Não há diferença de `app/src/main` entre essa fonte e o HEAD local inicial;
os commits posteriores acrescentam testes e documentação.
A continuação foi isolada na branch `codex/patient-responsive-devices`.

## Mudanças

- Navegação inferior em janelas compactas; lateral quando largura e altura
  comportam os cinco destinos. Os limites consideram a fonte do usuário.
- Conteúdo central com largura limitada, inclusive em janelas grandes.
- Resumo inicial em duas colunas com espaço suficiente, retornando a uma
  coluna em telas estreitas ou com letras maiores. Slots estáveis preservam
  estado local durante o redimensionamento.
- Rótulos de conexão usam “aparelho”, atendendo celular e tablet.
- Mesmas rotas, callbacks, IDs, contagem de envios e seleção de aba.

Somente apresentação Android. Web Profissional, ACS e WhatsApp/SM Click não
recebem mudanças. Paciente/VE30 mantém entidades, permissões, BLE, SDK,
persistência, integrações e contratos existentes. Nenhuma nova integração
**REAL** é declarada. Fixtures de componentes são **DEMO**; identidade,
proveniência e recebimento central continuam **BACKEND CONTRACT REQUIRED**.

## Verificação

Execução local/Codex Windows, JDK 21.0.12.1, Gradle 9.3.1 e SDK já disponíveis;
cache `C:/CDev/Next2U-Android-Cache`, sem mudança de dependências ou configuração.
O primeiro comando de compilação usou o cache padrão e foi interrompido; não
é registrado como aprovação. Os comandos seguintes usam o cache existente/offline.

Testes cobrem celular em retrato/paisagem, tablet em retrato/paisagem, fonte
ampliada, acesso aos cinco destinos, seleção/rascunho após redimensionamento
e reorganização do resumo. Capturas de componentes não são teste físico.
Resultados finais abaixo identificam a composição efetivamente verificada.

**SELF_REVIEW_ONLY**. Os bloqueios de lint herdados e os limites de publicação
do handoff continuam aplicáveis até verificação específica; não são suprimidos.

## RECOMMENDATIONS

Revisar a atualização de teste no M8 e repetir o roteiro em smartphone físico.
Validação com pacientes e VE30 físico não faz parte desta alteração visual.

## Resultado do candidato

Fonte: `0065ec839e76cf754ec15bea20f4f77cace9ab45`.
Tree: `f457582d1435c88759687025a3b644056219b4c4`.
O comando completo iniciou com os seis arquivos staged, antes do commit porque
a identidade Git não estava configurada neste checkout. O commit usou a mesma
identidade já configurada no Web e presente no histórico, somente via `git -c`.
A tree do índice antes do commit e a tree do commit são idênticas; não houve
alteração de fonte durante os checks. Documentação posterior não altera o APK.

Comando: `:app:testDebugUnitTest :app:assembleDebug :app:compileReleaseKotlin
:app:lintDebug -Pandroid.builder.sdkDownload=false --offline --console=plain --continue`.
Execução local/Codex em 22/09/2026, aproximadamente 20:42–20:45 BRT.

- Testes: **169 executados, 168 aprovados, 1 ignorado, zero falhas/erros**.
  O ignorado mantém a limitação Windows existente. Oito testes novos de layout
  passaram, incluindo preservação de seleção/rascunho ao alternar largura e
  preservação de estado interno do resumo ao mudar o número de colunas.
- APK DEBUG e compilação Kotlin release: **PASS**.
- Lint: **FAIL**, dois bloqueios herdados: `MissingPermission` em
  `HBandBleManager.kt:1640` e `InvalidFragmentVersionForActivityResult` em
  `MainActivity.kt:24`; 48 avisos nesta execução. Não houve supressão ou mudança
  da integração/dependências. O comando global terminou com falha pelo lint.
- Primeira rodada focada: 10/12 passaram. Duas verificações usavam coordenadas
  recortadas para conteúdo fora do viewport; corrigidas para coordenadas do
  layout. A suíte final acima passou. Não se registra a rodada inicial como PASS.

APK: `next2u-paciente-responsive-0065ec8.apk`, SHA-256
`03C6943DE11432760A1FEDDF3597692179C3B0FE49D86F0FF099A29B3E64009E`.
O APK foi atualizado com `install -r` no M8, sem desinstalação ou limpeza de dados.
A cópia de `base.apk` extraída após a instalação tem exatamente o mesmo SHA-256.

### Ensaio visual do APK

- M8 físico: navegação lateral em retrato; resumo em duas colunas em paisagem;
  cinco abas abertas e seleção confirmada na hierarquia Android; perfil ausente
  aberto/fechado; fonte 1.6 em retrato muda para navegação inferior legível.
- Emulador isolado `Next2U_Patient_UI_35`, Android 35, sem rede: cinco abas
  abertas em 360×720 dp, fonte 1.0; paisagem 720×360 dp conserva a ação principal
  acima da navegação; 320×640 dp com fonte 2.0 mantém os cinco destinos e o botão
  Conectar meu relógio completo. Não é ensaio em smartphone físico.
- Uma captura de hierarquia durante rotação retornou `null root`; o XML antigo
  foi descartado e a captura refeita depois da estabilização. Imagem transitória
  foi substituída por imagem estável; não conta como evidência de layout final.
- M8 restaurado: fonte 1.0, rotação automática 1, orientação do usuário 0.
  Emulador restaurado a 720×1440 pixels/densidade 320, fonte 1.0 e rotação automática.
- ACS continua 1.0.6 (16), `lastUpdateTime=2026-09-15 13:35:21`, inalterado.

Logs, XMLs de teste/lint, APK e capturas:
`C:/CDev/Next2U-Patient-Delivery/2026-09-22-tablet-review`.

**SELF_REVIEW_ONLY:** diff limitado à apresentação/testes/documentação; nenhuma
alteração em callbacks, IDs, serviços, autorizações, esquema ou dependências.
Não equivale a CI, aprovação independente, produção ou integração REAL validada.
Publicação continua dependente do responsável com escrita no repositório.
