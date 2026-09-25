# Frontend do paciente — continuação em 17/09/2026

## OBSERVED FACTS — preflight

- Checkout: `C:/CDev/Next2U-Patient-Native`, remoto
  `https://github.com/leanderdulac/HBand-.git`, branch `feature/next2u-patient-ui`.
  Início limpo em `8bc0a77ed3f540c36bea5ac913cda80aa7e90a2a`, 41 commits locais
  acima da PR #4; nenhuma operação Git pendente observada.
- GitHub: PR #4 continua aberta/DRAFT, HEAD
  `9a239d9113bc671624643acc975b3e10042f4a57`, base
  `04938c46fdba3ab59789943f41b7a48ea5b4b72e`. A conta continua sem escrita
  (`permissions.push=false`); não foi repetido o push recusado anteriormente.
- A PR #3 foi incorporada à branch da PR #2 em `2026-09-16T01:36:58Z`.
  O novo HEAD da #2 é `4b65b734adc3b26e2f6d88fa22123e5a115ea2d4`; #1 e #2
  permanecem abertas. Não descrever toda a pilha como quatro PRs abertas.
- Reconciliação: GitHub retorna o mesmo tree
  `26122eca31363e775fdf8d7b875b594d7a131836` para `04938c4…` e `4b65b73…`.
  O histórico mudou, mas o conteúdo dessas duas pontas é idêntico. A comparação
  de três pontos aparece divergente porque usa o ancestral comum. A base da #4
  não mudou. Não houve rebase, merge ou troca de base nesta continuação.
- Governança aplicável foi revalidada pela skill `next2u-engineering`.
  O checkout Web está em `89cadcfcfda5a8d823a983a403337ff68a39df50`, branch
  `codex/patient-read-refresh`, com `.validation/` preexistente. Nenhum arquivo
  Web foi alterado. Em relação à revisão anterior, o contrato Web de Pacientes
  registra enumeração territorial para o profissional; esse registro não entrega
  ao aplicativo o fluxo de primeiro acesso/provisionamento do paciente.

## Alterações e verificação por fonte

- `c1ed23505935c9c263b93423dfcfb7411d8399f8`: o perfil pede uma decisão antes
  de descartar campos alterados. Cancelar/voltar/toque fora compartilham a mesma
  rotina de fechamento. Sem alterações, fecha diretamente. Salvar conserva o
  callback e a cópia do perfil existente; IDs, validações e persistência não mudam.
  DEBUG e compilação Kotlin release passaram. Dos oito testes de perfil,
  sete passaram e um falhou na comparação de texto do teste, que incluía o rótulo
  `Nome completo` junto ao valor editável já corretamente restaurado.
- `98782ccb8d680c67255350ac19a58355ba0c280a`: comparação corrigida sem modificar
  o comportamento de produto. Oito testes de perfil e um teste visual passaram.
  Cobertura: fechar sem alterações; não salvar rascunho inválido ao descartá-lo;
  restaurar confirmação/rascunho, continuar editando e salvar com a mesma identidade.
  A captura inicial da confirmação mostrou a janela de trás: não foi aceita como
  evidência visual da confirmação, embora as verificações de semântica passassem.
- `6d61b66bc4d42b845e8959aa61d9a7521d7c5480`: alvo explícito de captura na
  confirmação à frente. Nenhuma alteração de cadastro, API, BLE ou SDK.
  O teste passou, mas a imagem continuou mostrando a janela anterior; rejeitada
  como evidência visual da confirmação.
- `ac626ccc1566592a9e05a93e12b1faf39e376389`: tentativa de obter diretamente
  o bitmap do nó Compose terminou em `ComposeTimeoutException`. Falha arquivada.
  O código de produto não mudou nessa revisão.
- Inspeção do [código Roborazzi 1.59.0](https://github.com/takahirom/roborazzi/blob/1.59.0/roborazzi/src/main/java/com/github/takahirom/roborazzi/Roborazzi.kt)
  confirmou que uma captura de nó com várias janelas redireciona para a tela
  inteira, independentemente do alvo selecionado. A ordem visual observada nesse
  ambiente não comprova a ordem no Android físico. Não atualizar dependências
  nem mudar o fluxo do produto para contornar a ferramenta.
- `0556dc95f36a678a72f4eedd869e45571bc21e74`: confirmação extraída em componente
  de apresentação idêntico, utilizado pelo mesmo fluxo. O teste de comportamento
  continua abrindo as duas janelas; a revisão visual captura o componente isolado
  e será identificada como tal. O aviso de perfil ausente deixa de prescrever
  tentativas repetidas, orientando procurar a equipe do cadastro se persistir.
  Ajustes oferece "Sobre meu perfil" quando ele está ausente; callback preservado.
  Oito testes de perfil e quatro testes de perfil/captura passaram. As imagens
  isoladas da confirmação e do perfil ausente foram inspecionadas em 320 dp,
  fonte 1,6×: texto e ações cabem sem cortes. Isso não é captura da sobreposição
  de janelas nem teste de perfil provisionado no aparelho.

### Verificação completa — `0556dc95f36a678a72f4eedd869e45571bc21e74`

Execução local em 17/09/2026, aproximadamente 09:19 BRT, Windows/Codex, no mesmo
JDK 21.0.12.1+1, SDK 36.1 e cache Gradle registrados na validação anterior:

```text
:app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:compileReleaseKotlin
-Pandroid.builder.sdkDownload=false --console=plain --continue
```

- 144 testes: 143 passaram, um ignorado por caminhos Windows, zero falhas/erros.
- APK DEBUG e compilação Kotlin release passaram; não houve empacotamento release.
- Lint: um Error `MissingPermission` em `HBandBleManager.kt:1640` e um Fatal
  `InvalidFragmentVersionForActivityResult` em `MainActivity.kt:24`, ambos herdados;
  81 avisos. Resultado global do comando falhou por esses dois bloqueios.
- APK preservado com SHA-256
  `EA4F6D2D7FAED7B2B52A7B23110498B0F126A7B1672227BA21754FEBBB05B05C`.
- Fonte de produto após os checks idêntica ao commit candidato; alterações
  documentais da continuação são registradas em commit separado.

### Emulador — mesma fonte

APK instalado com atualização no AVD isolado `Next2U_Patient_UI_35`, serial
`emulator-5580`. Rede padrão ausente e Wi-Fi desligado antes da abertura. Nenhum
dispositivo físico, AVD ACS ou dado de paciente foi usado. O app abriu no Início;
"Meu perfil" mostrou a nova orientação. Ao passar para 320×640 dp, fonte 1,6×,
a janela permaneceu aberta e todo o texto e "Fechar" ficaram visíveis. Fechar
retornou ao Início. Capturas/XML em `emulator` na evidência da fonte.

Não foi exercitado o formulário preenchido nesse APK, pois o emulador não tem
cadastro provisionado. A confirmação de descarte continua verificada com fixtures
de componentes, incluindo restauração do rascunho. A inspeção do buffer de falhas
não mostrou falha do pacote Next2U nesse ensaio; os erros de serviços Google de
voz pertencem à imagem Android existente. Ao terminar, o emulador ficou visível
no Início com 360×720 dp e fonte padrão para navegação do usuário.

Os arquivos de execução ficam em `app/build/patient-ui-evidence/<SHA>/`.
Uma falha não é apagada nem atribuída à revisão corrigida. Os checks são locais,
**SELF_REVIEW_ONLY**, sem CI ou revisão independente. Os dois bloqueios herdados
de lint foram reproduzidos nesta rodada; não foram
suprimidos nem corrigidos fora do escopo frontend.

## Ciclo seguinte — acessibilidade do histórico e dos envios

- Antes da edição, checkout limpo em `ab4513e9ecb816f34baf9417c84d62e41f0b5fee`;
  GitHub revalidou a PR #4 aberta/DRAFT, base `04938c4…` e HEAD remoto `9a239d9…`
  sem mudanças. Arquivos previstos: apresentação do Histórico, resumo de sete dias,
  indicador de envio e testes desses componentes.
- `a0a0fdd50d73f50cef52776c4b4711074122f497`: data/horário e valor de cada leitura
  ficam no mesmo nó de acessibilidade. Resumos diários agrupam rótulo e valor;
  a data selecionada é região de aviso educado (`Polite`) e Histórico é cabeçalho.
  Quatro testes de Histórico passaram, incluindo navegação dos sete dias, medida
  ausente e correspondência entre data/valor. Captura com letras 1,6× inspecionada;
  os controles permanecem legíveis. Sem mudança de cálculo, seleção ou registros.
- `d47c9baaae24776788d6ac283ac5b0d8545d6760`: título e explicação do envio formam
  região de aviso educado, para expor mudanças sem deslocar o foco. Contagens,
  progresso e botões ficam fora desse grupo. Mantidos textos e callbacks; fila
  concluída continua explicitamente distinta de recebimento pela equipe.
- Verificação completa da fonte `d47c9ba…`: mesmo comando e ambiente acima,
  147 testes, 146 passaram e um ignorado pela limitação Windows; zero falhas/erros.
  APK DEBUG e Kotlin release passaram; lint manteve dois bloqueios herdados e 81
  avisos. APK SHA-256 `CB086733503210C96781D55913D27FAB47238F5625481EB848155073BC0521A1`.
- As verificações conferem a árvore semântica Compose. Não equivalem a audição
  de anúncios, teste de TalkBack em aparelho físico ou avaliação com pacientes.

Ensaio adicional do APK `0556dc9…`, antes da atualização: na ajuda de conexão,
um texto incompleto (`12`) manteve a orientação de correção; a ação de concluir
do teclado o fechou e revelou o botão. Nenhuma busca, conexão ou gravação de
cadastro ocorreu. XML/imagem vinculados à fonte original; não atribuídos ao
novo APK. Ao voltar ao Início, o campo de teste deixou a composição. O APK
`d47c9ba…` foi então instalado no mesmo emulador isolado.

## Ciclo seguinte — botão Voltar do Android

**OBSERVED FACTS:** no APK `d47c9baaae24776788d6ac283ac5b0d8545d6760`, abrir
Histórico e pressionar Voltar levou ao launcher Android, sem retornar ao Início.
A hierarquia antes e a observação de foreground fundamentaram a correção.
Checkout limpo em `b988664b384cc266731671d9112485365fa0e609`; mudança limitada
à navegação local de `HomeScreen.kt`.

`09a38d6e6f17d887635b3acb6d1446f0f96106b2` acrescenta um `BackHandler` que retorna
das quatro abas secundárias ao Início. Ele não atua quando o perfil, a prévia de
compartilhamento ou o inspetor JSON estão abertos, nem na aba Início. Não altera
conexão, permissões, estado de dados ou callbacks de integração.

- `:app:assembleDebug :app:compileReleaseKotlin` passaram.
- `:app:testDebugUnitTest :app:lintDebug --continue`: 146 dos 147 testes passaram,
  um ignorado por caminhos Windows; lint reproduziu Error `MissingPermission`,
  Fatal `InvalidFragmentVersionForActivityResult` e 81 avisos. Download automático
  de SDK desabilitado em ambos os comandos, mesmo ambiente local já descrito.
- APK SHA-256 `459AFCC139D5F61AB95EA3DE97D8FCFF0A99FA068493483BDFB80610772F1B6C`.
- APK atualizado somente no emulador do paciente. Teste de navegação confirmou:
  cada aba secundária → Voltar → Início; Meu perfil em Ajustes → Voltar fecha
  somente a janela; outro Voltar retorna ao Início. Voltar no Início continua
  levando ao launcher. O menu de escolha de medição também fechou com Voltar
  sem sair do Histórico. O app foi reaberto depois para navegação do usuário.
- O script de ensaio recusou inicialmente seu próprio preflight por comparar
  o array de saída do comando AVD com uma expressão de texto. Nenhuma navegação
  havia ocorrido; corrigida apenas a leitura do nome do emulador e preservado
  o log da falha. A execução corrigida passou nas seis verificações previstas.

Evidência local por SHA: `build-check.log`, `test-lint-check.log`, testes/XML de
lint, `verify-back.ps1`, hierarquias e `emulator-back-check.log`. Sem CI ou revisão
independente; nenhum ensaio com paciente ou relógio físico.

## Ciclo seguinte — retorno de permissões após recriação

**OBSERVED FACTS:** a ação e a lista de permissões solicitadas usam `remember`,
enquanto o registro de resultado sobrevive à recriação. Se o resultado chega
depois de perder aquela ação, o ramo anterior mostrava recusa sem conhecer a
solicitação. A [documentação Android](https://developer.android.com/training/basics/intents/result#test)
orienta manter o estado necessário separadamente e permite injetar um registro
de resultados para teste, sem abrir outra Activity.

- `c316b80530ce3eda817353886b10cc0cf40742d8`: três testes usando
  `ActivityResultRegistry` artificial. Concessão e recusa normais passaram;
  restauração falhou por não encontrar orientação para retomar. Falha arquivada.
- `b5179d68ec7574eb8974e9f5d7b9bde58abc38dc`: na ausência da ação original ou
  das permissões solicitadas, a tela pede novo toque, sem classificar o retorno
  como recusa e sem adivinhar qual dispositivo conectar. Os três testes passaram.
  O fluxo normal conserva a lista de permissões e o callback original.
- `c2ed35959e387e5683a383de342a5cdda61453f8`: teste adicional com dois relógios
  de nome igual confirma que o retorno tardio não conecta o primeiro; após nova
  escolha explícita, somente o segundo objeto/ID é encaminhado. Quatro casos de
  resultado de permissão passaram, sem abrir diálogo Android nem chamar BLE real.
- Verificação completa em `c2ed359…`: 151 testes, 150 passaram, um ignorado por
  caminhos Windows; zero falhas/erros. APK DEBUG e Kotlin release passaram. Lint
  manteve os dois bloqueios herdados e 81 avisos. Mesmo comando/ambiente anteriores.
- APK SHA-256 `DF67C90A0E84672389DB1791900391E5F10205DAFDDE27CECA4794442862309B`.

A alteração é de recuperação da interface. Não há nova permissão, alteração
no manifesto, SDK, BLE, contrato ou estratégia de conexão. Resultados artificiais
são **DEMO** e não validam conexão VE30 ou diálogo de permissão em aparelho físico.

## Ciclo seguinte — formatação após mudança de fuso do celular

**OBSERVED FACTS:** a lista de leituras guardava um `SimpleDateFormat` da primeira
composição. Mesmo após atualizar a interface, ele conservava o fuso anterior,
embora o texto da tela diga que usa o horário do celular. ADR-007 foi relido:
a correção fica na exibição local, sem promover esse fuso a autoridade territorial
ou alterar instante, recebimento, recência, ordenação operacional ou prazo.

- `b3408b8a1a24776ba2c3ea70a9015980eae4ad1e`: teste da tela com o mesmo registro
  e mudança do fuso de teste UTC para America/Sao_Paulo falhou ao não exibir o
  novo horário após recomposição. O fuso original do processo de teste é restaurado
  em `finally`; não há mudança no Windows, em paciente ou em outro aplicativo.
- `07fef7807cdabcc2658a96209ba6e962e5a11e6f`: formatter usa o fuso atual em cada
  atualização já existente da interface. Nenhuma nova temporização ou infraestrutura,
  nem mudança no valor/timestamp persistido, período consultado ou origem do dado.
- Verificação completa da fonte: 152 testes, 151 passaram, um ignorado por caminhos
  Windows; zero falhas/erros. APK DEBUG e Kotlin release passaram. Lint mantém um
  Error, um Fatal e 81 avisos, mesmos bloqueios herdados. Mesmo comando/ambiente.
- APK SHA-256 `0B3278DDFA69057F72BA9101BBE5CD84BB7FA6BB9C76C3B248EC74419D838BFD`.

O teste confere a exibição na atualização, não uma reação instantânea ao evento
do sistema. Sem novo teste com registros reais ou com relógio físico; sem
homologação de timestamp de medição ou garantia temporal entre os quatro canais.

## Ensaio limitado com TalkBack — mesma fonte 07fef780

**OBSERVED FACTS:** APK da fonte `07fef7807cdabcc2658a96209ba6e962e5a11e6f`
instalado no emulador isolado `Next2U_Patient_UI_35`, serial `emulator-5580`.
TalkBack preinstalado, versão `15.0.0.639625893`. Foi observado foco verde no
botão Meu perfil e no título da janela de perfil indisponível. A abertura foi
confirmada por captura e hierarquia posteriores ao toque duplo; Voltar fechou
a janela. A tentativa de avançar pelo teclado não confirmou a sequência de foco.

O emulador está sem áudio. Este ensaio não valida fala, pronúncia, avisos de
mudança de estado, ordem completa de leitura ou uso por pessoas com deficiência.
Não é teste com paciente. Capturas imediatas anteriores à atualização da tela
foram preservadas como tentativas: `profile-open.png/xml` mostram ainda o Início;
somente `profile-open-confirmed.png/xml` comprovam a janela aberta.

A preparação encontrou pedidos repetidos de notificação do próprio TalkBack.
A permissão de notificação foi concedida temporariamente apenas a esse serviço
no emulador e depois revogada, retornando a `granted=false` e às mesmas flags
observadas antes. Ao concluir, `accessibility_enabled=0`, chave
`enabled_accessibility_services` ausente (`null`), `Bound services:{}`. Fonte
1.0, resolução 720×1440 e densidade 320 restauradas; aplicativo no Início,
sem rede, cadastro provisionado ou relógio conectado. Nenhum outro dispositivo
foi operado. Evidências locais em `app/build/patient-ui-evidence/07fef7807cdabcc2658a96209ba6e962e5a11e6f/talkback`.

GitHub reconsultado antes deste registro: PR #4 aberta/DRAFT, HEAD remoto
`9a239d9113bc671624643acc975b3e10042f4a57`, base
`04938c46fdba3ab59789943f41b7a48ea5b4b72e`; conta ainda sem permissão de escrita.
Nenhuma nova tentativa de push. Registro documental, sem alteração da fonte
ou reatribuição dos checks locais de `07fef780…` ao novo commit de documentos.

## Ciclo seguinte — rascunho de respiração e mensagens cotidianas

**OBSERVED FACTS:** os três valores do exercício (fase, segundos restantes da
fase e tempo contado) usavam somente `remember`. `9867cfb6e241369745a884c8eb66e422013e2d20`
passou esses valores a `rememberSaveable`; o estado em execução continua usando
`remember`, para voltar pausado após recriação. Não há cálculo de tempo ausente,
salvamento automático, nova duração de fase, rotina de fundo ou mudança no DAO.

O primeiro teste (`c9f4b6cbc46d7fac532a40835d4708a17ddaa03a`) foi executado após
corrigir a passagem do argumento Gradle pelo PowerShell, mas mantinha o relógio
de teste congelado durante restauração. As falhas daquele teste, de `9867cfb…` e
da primeira correção `93ee4d0760565a59c739ce9709358cbed3f88ce0` não comprovam
isoladamente o comportamento real: faltava permitir os dois frames que removem e
recriam a composição. `5070e32c047b453cda3ff122a8b9d8a85408b758` corrigiu o ensaio;
os dois casos passaram. Confirmam recuperação pausada, contagem parada, nenhum
callback automático e um único callback com os segundos contados após salvar.
O [StateRestorationTester Android](https://developer.android.com/reference/kotlin/androidx/compose/ui/test/junit4/StateRestorationTester)
verifica estado local Compose; não substitui ensaio de Activity ou armazenamento.
Logs de todas as tentativas foram preservados por SHA.

`a48fb401a97737fcf74580601fc2c271b856d0dd` também:

- Removeu da apresentação o fallback de 2500 mL quando o perfil está ausente.
  No APK anterior `07fef780…`, o emulador sem perfil mostrava “Meta cadastrada:
  2500 mL por dia”; no novo, mostra “Nenhuma meta de água cadastrada”. Capturas e
  hierarquias antes/depois foram preservadas nas respectivas fontes. O valor
  existente em um perfil disponível continua sendo usado; nenhuma meta clínica
  foi recomendada, calculada ou regravada.
- Traduziu confirmações cotidianas em `MainViewModel.kt`: água, respiração,
  limites e avisos de batimentos, reconexão, perfil e novas tentativas de envio.
  Solicitação de ECG/histórico não declara medição real ou recebimento concluído.
  A mudança no ViewModel é exclusivamente nos textos de `showNotification`;
  callbacks, permissões, condições, constantes, IDs, payloads e gravações não mudam.
  Mensagens técnicas retornadas pelas integrações continuam pendentes de contrato
  para tratamento mais preciso; não foram substituídas por sucesso genérico.

### Verificação da fonte a48fb401

Mesmo ambiente local anteriormente registrado; comando
`:app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:compileReleaseKotlin`,
com `-Pandroid.builder.sdkDownload=false --console=plain --continue`.
154 testes: 153 passaram, um ignorado por caminhos Windows, zero falhas/erros.
APK DEBUG e Kotlin release passaram. Lint manteve Error `MissingPermission`,
Fatal `InvalidFragmentVersionForActivityResult` e 81 avisos; comando global
terminou com falha pelo lint. **SELF_REVIEW_ONLY**, sem CI/revisão independente.
APK SHA-256 `C36EDC3A76FAAFC2D28C7F7FA8EF8549C2346DFF88187A9A9EAFCD627773390F`.

Ensaio no mesmo emulador isolado: iniciado exercício local, ampliada fonte para
1.6 e conferido “Exercício pausado”, 14 segundos contados, Continuar exercício
e Concluir e salvar legíveis. Captura anterior mostrava 13 segundos; houve um
segundo entre a leitura e a recriação. Conferência posterior manteve 14 segundos.
Não foi acionado salvar nem usado sensor; não é duração clinicamente validada.
Fonte 1.0 restaurada e mudança de aba descartou o rascunho de teste sem gravar.
Testes automatizados de salvar usam callback artificial (**DEMO**).

## Ciclo seguinte — sair do aplicativo, avisos de teste e leitura das mensagens

**OBSERVED FACTS:** teste `e0333957ab877914750d53be6e96891c8aa056d9` reproduziu a
ausência de pausa na respiração ao passar o ciclo de vida da tela para CREATED.
Os dois testes anteriores de recuperação continuaram passando. A falha ficou
arquivada com o SHA e o contexto do teste de componente.

`acd46e3cb26a0a3d6d76b85bf8c53b993b128ba7` observa `ON_STOP` na tela de respiração:
pausa o contador e cancela a vibração. Retornar não retoma nem salva por conta
própria. A duração das fases, o callback de salvar e o armazenamento não mudaram.
Suíte completa: 155 testes, 154 passaram e um ignorado; APK DEBUG e Kotlin release
passaram. Lint: os mesmos dois bloqueios e 81 avisos. APK SHA-256
`A29673BDDFB61C7C21D52B10C63746C092C5BC66CB66A7C73D3BE316F404E6CF`.

`8cb8b4ee298f50c754f3fd176e4be71185823f05` acrescentou identificação explícita
de teste nas notificações Android acionadas pelos dois botões de teste DEBUG.
Avisos normais preservam os textos anteriores, canal `hr_threshold_alerts`, IDs
1001/1002, valores, limites e prioridade. Testes usam o NotificationManager
artificial do Robolectric; nenhuma notificação de teste foi enviada a aparelho
físico. O resumo da tentativa de envio agora apresenta `syncedCount` e
`failedCount` existentes, incluindo falhas parciais, sem mudar o processamento.
Suíte completa: 156 testes, 155 passaram e um ignorado; APK DEBUG/Kotlin release
passaram, mesmos bloqueios de lint e 81 avisos. APK SHA-256
`F998F9265D3ED2CE8C57A7AE253284984FCC1975E36AFFA50ACBB317CA5CB4BA`.

No emulador com a fonte `8cb8b4e…`, o contador mostrava 16 segundos antes da
ação Home do Android. O launcher foi confirmado como tela ativa; ao retornar,
o app mostrou “Exercício pausado” e 18 segundos. O intervalo inclui a transição
até `ON_STOP`; não se declara precisão de cronômetro. Nova conferência manteve
18 segundos, sem reinício ou salvamento. A troca de aba descartou o rascunho de
teste. Hierarquias/captura/estado do launcher estão na pasta dessa fonte.

`7fb596434c3dfae34ea9d756c591c62a7f6c2c30` mantém número e unidade de duração
juntos na quebra de linha; captura anterior havia mostrado “s” isolado.
As mensagens breves do app usam duração Long do componente Android e ação
“Fechar”. Isso não garante tempo de leitura suficiente para todo usuário; a
compreensão continua pendente de validação. O pedido de envio e o fallback
sem mensagem também deixam de mostrar nomes internos ou inglês.

Verificação completa de `7fb5964…`, mesmo comando/ambiente local: 156 testes,
155 passaram, um ignorado por caminhos Windows; zero falhas/erros. APK DEBUG e
Kotlin release passaram. Lint manteve Error `MissingPermission`, Fatal
`InvalidFragmentVersionForActivityResult` e 81 avisos; resultado global com falha
pelo lint. APK SHA-256 `61CD01579127257F9A1C6DDD7A2A212FD255CAAE718BF40A77ACDA119CE96A84`.
Fonte instalada no mesmo emulador, sem rede ou paciente provisionado.

**SELF_REVIEW_ONLY.** Diff dos dados, manifesto e dependências continua vazio
contra a base de integração. O diff-check completo também aponta três quebras
Markdown por dois espaços em `FRONTEND_UI_HANDOFF.md`, já presentes no handoff
inicial da PR; nenhum delta de whitespace novo foi incluído neste ciclo.
Nenhum check local é CI nem constitui aprovação independente.

## Ciclo seguinte — erros na janela de compartilhamento e verificação de conexão

**OBSERVED FACTS:** `a1ce63940612ba33f7e26ad668b10c2c25a15bf6` reproduziu duas
falhas com contexto Android artificial: a indisponibilidade do seletor de
compartilhamento não tinha mensagem dentro da janela; uma SecurityException
ao acessar a área de transferência escapava da ação de copiar.
`0a96d3a0471c1fd4b1652e5b208f3fd57ae7b952` mantém a resposta dentro da janela e
trata a falha ao copiar sem anunciar sucesso. O callback de mensagem existente
foi preservado; URI, texto, permissões do Intent e seleção de destinatário não
mudaram. Os dois testes passaram com fonte 1.6, incluindo erro e Fechar cartão
visíveis. Nenhum seletor real, destinatário ou dado de paciente foi usado (**DEMO**).

Na fonte `7fb5964…`, a ação Verificar serviço de envio foi acionada no emulador
sem rede; exibiu uma mensagem em inglês com endereço interno e erro de DNS.
`f2018eb47c5a1f62e8c2460c579b590857c04b44` apresenta orientação em português:
serviço acessível não comprova envio; HTTP 401/403 pede apoio para o acesso;
demais falhas orientam conferir internet e consideram indisponibilidade do serviço.
O resultado técnico `ApiHealthState` não foi alterado nem houve nova chamada,
permissão ou contrato. Somente a mensagem de apresentação foi mapeada.

Verificação completa da fonte `f2018eb…`: 158 testes, 157 passaram e um ignorado;
zero falhas/erros. APK DEBUG e Kotlin release passaram; lint manteve os mesmos
dois bloqueios e 81 avisos. APK SHA-256
`AB17F3FAE5D55C85C37D5AF06D01F5EE375FAB4D7503AA6F018896EB7F83D6D3`.
No APK dessa fonte, o script `verify-offline-feedback.ps1` confirmou a orientação
em português e o fechamento pelo botão Fechar. Apenas leitura da disponibilidade
do serviço, com `Active default network: none`; nenhuma sincronização foi acionada.
Capturas/hierarquias antes e depois estão atribuídas às respectivas fontes.

## Ciclo seguinte — fonte 2.0 em tela de 320 × 640 dp

**OBSERVED FACTS:** na fonte `f2018eb…`, a navegação com três linhas de ícones
cortava a parte inferior do botão Conectar meu relógio no emulador 480×960,
densidade 240, fonte 2.0. A captura `font-2-small/home.png` preserva o achado.

O primeiro teste de componente (`b26dd8e4390df93c725dcc218185db653d6ea0e8`)
passou sem representar as bordas do sistema. A fixture foi corrigida em
`8f1cf02b70d20b4bdf26990fb88a6c51f239cc13` para reservar 36 dp no topo e 24 dp
na base, como observado naquele emulador; então a verificação da altura inteira
do botão falhou. Não se usa o primeiro resultado como prova contra a captura real.

`d71ae476e585940a0ab35bc3895582974d2837af` compacta a navegação quando seriam
necessárias três ou mais linhas: mantém os nomes dos cinco destinos, seleção,
áreas de toque de pelo menos 56 dp e badge de pendências. O contador completo
continua nos dados de acessibilidade. As apresentações em uma ou duas linhas
mantêm os ícones. Os quatro testes de navegação passaram, incluindo o botão
inteiro acima da navegação e o encaminhamento das cinco rotas.

Verificação completa de `d71ae476…`: 159 testes, 158 passaram, um ignorado por
caminhos Windows; zero falhas/erros. APK DEBUG e Kotlin release passaram; lint
mantém Error `MissingPermission`, Fatal `InvalidFragmentVersionForActivityResult`
e 81 avisos. Mesmo ambiente/comando local; resultado global falha pelo lint.
APK SHA-256 `4DF8E7A440F3BF8483B1C365208B735E24CE7555CEA12D989BA5F509BD56088B`.

`recordRoborazziDebug` gerou uma captura adicional da fixture na mesma fonte;
o teste unitário comum não tinha habilitado gravação de imagens. A primeira
tentativa de abrir essa imagem ausente não produziu evidência; a captura válida
foi inspecionada depois da tarefa de gravação. Fixture com 125 pendências é **DEMO**.

APK instalado no emulador do paciente, ainda com fonte 2.0. Capturas confirmaram
botão Conectar completo, seleção das quatro abas secundárias, acesso às ações
iniciais de Histórico/Relógio/Ajustes e leitura da janela de perfil indisponível
com Fechar visível. Voltar fechou o perfil. Conteúdo comprido, como Envios, exige
rolagem; não foi alegada visibilidade de todo o conteúdo ao mesmo tempo.
Nenhuma busca, conexão de relógio, alteração de perfil ou envio foi acionado.

Ao concluir: fonte 1.0, resolução 720×1440, densidade 320, TalkBack desativado,
sem rede, tela Início. Evidência em `font-2-small`, `restored-home.*` e
`extreme-navigation-check.log`, com script preservado. **SELF_REVIEW_ONLY**;
ensaio técnico, não teste com pacientes, aparelho físico ou integração VE30.

## Ciclo seguinte — pedidos ao relógio e orientação de acesso

**OBSERVED FACTS:** `f3ad2e2915614bad837a56b3dc039093b9cb8874` altera somente
11 mensagens do MainViewModel. Configurações automáticas, detecção no pulso e
desconexão passam a informar pedido realizado, orientando conferir o estado.
Falhas de autorização orientam procurar a equipe responsável; leitura ausente
não é apresentada como disponível para envio. Condições, callbacks, IDs e valores
não mudaram. Verificação local em 17/09, mesmo ambiente/comando completo:
159 testes, 158 passaram, um ignorado; APK DEBUG e Kotlin release passaram;
lint com dois bloqueios herdados e 81 avisos. Fonte e árvore limpa conferidas.
APK SHA-256 `E09156F665FB46400B30BC53A931844646DC2009CE4103232EEE87B6096C1462`.
Resultados arquivados pelo SHA; instalado no emulador exclusivo sem rede.
GitHub revalidado: PR #4 DRAFT, head 9a239d9…, base 04938c4…; PRs abertos 1/2/4.
Nenhum novo contrato, teste VE30 físico, CI ou revisão independente.

Na inspeção seguinte, girar o emulador para 720×360 dp, fonte 1.0, deixou o botão
Conectar meu relógio parcialmente fora da área inicial: cabeçalho fixo ocupa altura
que poderia servir à ação. Captura/hierarquia `landscape-home.*` dessa fonte.
**RECOMMENDATIONS:** compactar o cabeçalho em telas baixas, mantendo marca,
acesso ao perfil e fonte escolhida; confirmar o botão completo e as rotas.

## Ciclo seguinte — altura disponível ao girar o celular

**OBSERVED FACTS:** o teste `6aa0180…` reproduziu o botão de conexão cortado
antes da correção. `02ef1b876cdeac3d52a2a2519d802d084bc35cf1` compacta o
cabeçalho quando a altura configurada é de até 400 dp: mantém marca, Meu perfil
e tamanhos de texto; reduz espaço vertical e omite a saudação nessa configuração.
O nome completo continua no perfil, sem alteração do cadastro.

Verificação completa em 17/09: 160 testes, 159 passaram, um ignorado por caminhos
Windows; APK DEBUG e Kotlin release passaram. Lint: mesmos dois bloqueios e 81
avisos. Mesmo comando/ambiente local, resultado global falha pelo lint. APK SHA-256
`141C394CED889890B4E1B916E996BAB0FAA0C4C74EBBE330161EAE5393117CE3`.
Fonte e árvore limpa conferidas; **SELF_REVIEW_ONLY**.

No emulador 720×360 dp, fonte 1.0, Conectar meu relógio ficou inteiro acima da
navegação. As quatro abas secundárias foram selecionadas; o perfil abriu e fechou.
As capturas foram inspecionadas. Conteúdo das outras abas pode exigir rolagem;
não se afirma que todas as suas ações caibam na primeira área visível.
Após instalar o APK, a primeira captura ainda estava em retrato; foi preservada
como `portrait-after-install.*`. A rotação foi reaplicada e a captura válida de
paisagem foi obtida depois. Scripts, logs e hierarquias estão na pasta desse SHA.
Ao concluir: Início, retrato, fonte 1.0, rotação automática 1/user_rotation 0,
sem rede e acessibilidade desativada, como antes do ensaio. Sem gravação de dados,
teste com pacientes, envio ou conexão com relógio físico.

## Ciclo seguinte — unidade e associação das leituras

**OBSERVED FACTS:** a última leitura tinha nome e valor em nós separados de
acessibilidade, enquanto o histórico já fazia essa associação. A fonte
`653748ab1da71bde161f4d8991acfeb62619cad9` reúne nome/valor de batimentos e das
demais leituras nesse cartão. Os rótulos de batimentos em Início e Histórico
explicitam a unidade por minuto. Nenhum valor, cálculo, unidade recebida, timestamp,
ID, exportação ou integração foi alterado.

Testes de componente existentes foram ampliados para confirmar que batimentos/72 bpm
e oxigênio/medição indisponível pertencem ao mesmo nó acessível. Fixtures **DEMO**,
sem inserir registros no emulador. Suite completa local: 160 testes, 159 passaram,
um ignorado por caminhos Windows; zero falhas/erros. APK DEBUG e Kotlin release
passaram; lint mantém os dois bloqueios e 81 avisos, com falha global pelo lint.
Mesmo comando e ambiente Android previamente registrados, em 17/09.
APK SHA-256 `FD70709ED6E05985BD70270FE836D4A3417E4ABB26B54A964911B55E62354016`.
Fonte/árvore limpa conferidas; instalado no emulador isolado. **SELF_REVIEW_ONLY**.

GitHub revalidado: base/head da PR #4 inalterados, DRAFT, `push:false` para a conta.
Sem publicação de commits, CI ou revisão independente. A associação semântica
não comprova pronúncia do TalkBack nem compreensão; a tarefa com pacientes que
relaciona data, valor e unidade permanece **PROPOSED / CONCEPTUAL**.

## Verificação adicional — saída do compartilhamento em tela baixa

**OBSERVED FACTS:** o candidato de testes `290a366…` acrescentou o cenário
640×320 dp com fonte 2.0, falha artificial ao abrir o seletor e fechamento da
janela. Os três testes de PatientShareDialogFeedbackTest passaram (25 s).
A ação Fechar cartão conservou altura de pelo menos 56 dp e ficou inteira na
janela de 320 dp; o callback de fechar foi chamado uma vez. A hipótese de corte
não foi confirmada nesse cenário, portanto não houve nova alteração de produto.

Checks restritos a essa classe, com log/resultados no SHA completo da pasta de
evidência; não são uma nova suíte completa. O código de produto permanece idêntico
a `653748ab1da71bde161f4d8991acfeb62619cad9`, fonte do APK instalado e do pacote
`2026-09-17-e560247`. Esses testes **DEMO** não abrem seletor real, copiam dados
de paciente ou comprovam a fala de um leitor de tela. **SELF_REVIEW_ONLY**.

## Fronteiras e classificação

Somente apresentação e navegação do aplicativo: `patient_id` e ID do perfil são
preservados; não é criado cadastro. Core, SDK/BLE, banco, contrato, manifesto,
dependências, Web, Tablet ACS e WhatsApp/SM Click permanecem sem mudanças por
esta tarefa. Não há nova escrita central, comunicação, permissão ou garantia offline.

**DEMO:** fixtures e testes de componentes. **REAL:** nenhuma nova capacidade
operacional de integração confirmada. **BACKEND CONTRACT REQUIRED:** primeiro
acesso, vínculo paciente/VE30, identidade/consentimento do cuidador, proveniência
e confirmação de recebimento. A autorização atual cobre frontend reversível;
nenhuma pergunta de aprovação foi necessária para esta melhoria.

## RECOMMENDATIONS — validação proposta

**PROPOSED / CONCEPTUAL:** acrescentar ao roteiro com pacientes uma tarefa de
alterar um campo de teste, cancelar, escolher continuar editando e corrigir o valor;
depois repetir escolhendo sair sem salvar. Observar se a pessoa distingue cancelar
a edição de salvar e se consegue retomar sem ajuda. Usar letras ampliadas e o
leitor de tela habitual quando aplicável. Esse teste com pacientes ainda não ocorreu.
