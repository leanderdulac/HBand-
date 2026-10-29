# Frontend do paciente — desenvolvimento e validação

## Ponto atual — 17/09/2026

Fonte `653748ab1da71bde161f4d8991acfeb62619cad9`: 159 de 160 testes passaram,
um ignorado por caminhos Windows; zero falhas/erros. APK DEBUG e Kotlin release
passaram. Lint mantém MissingPermission, InvalidFragmentVersionForActivityResult
e 81 avisos. APK SHA-256 `FD70709ED6E05985BD70270FE836D4A3417E4ABB26B54A964911B55E62354016`.
Última leitura associa nome e valor nos dados de acessibilidade; Início e Histórico
explicitam batimentos por minuto. Dados, cálculos e unidades não mudaram.
APK atualizado no emulador. **SELF_REVIEW_ONLY**; não comprova fala do TalkBack,
compreensão por pacientes ou integração REAL.

### Ponto anterior deste dia — tela deitada

Fonte `02ef1b876cdeac3d52a2a2519d802d084bc35cf1`: 159 de 160 testes passaram,
um ignorado; APK DEBUG e Kotlin release passaram. Lint mantém dois bloqueios e
81 avisos. APK SHA-256 `141C394CED889890B4E1B916E996BAB0FAA0C4C74EBBE330161EAE5393117CE3`.
Cabeçalho compacto em telas baixas: botão de conexão inteiro confirmado no emulador
em paisagem 720×360 dp, fonte 1.0. Abas e perfil acessíveis; retrato e configuração
normal restaurados. **SELF_REVIEW_ONLY**, sem CI, pacientes ou VE30 físico.

### Ponto anterior deste dia — confirmação de pedidos

Fonte `f3ad2e2915614bad837a56b3dc039093b9cb8874`: 158 de 159 testes passaram,
um ignorado por caminhos Windows; zero falhas/erros. APK DEBUG e Kotlin release
passaram. Lint mantém MissingPermission, InvalidFragmentVersionForActivityResult
e 81 avisos; resultado global falha pelo lint. APK SHA-256:
`E09156F665FB46400B30BC53A931844646DC2009CE4103232EEE87B6096C1462`.
Somente mensagens de apresentação: pedido ao relógio não é confirmação de mudança;
falhas de acesso ao envio recebem orientação em português. APK instalado no emulador
isolado, sem rede. **SELF_REVIEW_ONLY**; sem nova prova de integração REAL.

### Ponto anterior deste dia — navegação ampliada

Fonte atual `d71ae476e585940a0ab35bc3895582974d2837af`: 158 de 159 testes
passaram, um ignorado por caminhos Windows. APK DEBUG e Kotlin release passaram;
lint mantém dois bloqueios herdados e 81 avisos. APK SHA-256:
`4DF8E7A440F3BF8483B1C365208B735E24CE7555CEA12D989BA5F509BD56088B`.
Erros de compartilhar/copiar ficam dentro da janela; verificação da conexão usa
linguagem cotidiana. Navegação compacta preserva as cinco rotas em fonte 2.0
e tela pequena; botão de conexão completo confirmado no emulador. Configuração
normal restaurada. **SELF_REVIEW_ONLY**, sem CI ou validação com pacientes/VE30 físico.

### Ponto anterior deste dia — mensagens e pausa

Fonte atual `7fb596434c3dfae34ea9d756c591c62a7f6c2c30`: 155 de 156 testes
passaram, um ignorado por caminhos Windows. APK DEBUG e Kotlin release passaram;
lint mantém dois bloqueios herdados e 81 avisos. APK SHA-256:
`61CD01579127257F9A1C6DDD7A2A212FD255CAAE718BF40A77ACDA119CE96A84`.
Respiração pausa quando a tela sai de exibição; testes de avisos ficam explícitos
também nas notificações Android. Resumos de envio mostram falhas parciais.
Mensagens breves têm mais tempo e ação Fechar; duração mantém número e unidade
juntos. **SELF_REVIEW_ONLY**, sem CI ou teste com pacientes/VE30 físico.

### Ponto anterior deste dia — uso diário

Fonte atual `a48fb401a97737fcf74580601fc2c271b856d0dd`: 153 de 154 testes
passaram, um ignorado por caminhos Windows. APK DEBUG e Kotlin release passaram;
lint mantém os dois bloqueios herdados e 81 avisos. APK SHA-256:
`C36EDC3A76FAAFC2D28C7F7FA8EF8549C2346DFF88187A9A9EAFCD627773390F`.
Respiração recupera rascunho pausado na recriação; meta de água ausente não recebe
valor padrão na tela; mensagens cotidianas em português. No emulador, fonte
ampliada conservou o tempo contado e as ações legíveis, sem salvamento automático.
**SELF_REVIEW_ONLY**, sem CI, teste com pacientes ou nova validação VE30.

### Ponto anterior deste dia — fuso na exibição

Fonte atual `07fef7807cdabcc2658a96209ba6e962e5a11e6f`: 151 de 152 testes
passaram, um ignorado por caminhos Windows. APK DEBUG e Kotlin release passaram;
lint mantém os dois bloqueios herdados e 81 avisos. APK SHA-256:
`0B3278DDFA69057F72BA9101BBE5CD84BB7FA6BB9C76C3B248EC74419D838BFD`.
A lista de leituras passa a usar o fuso atual do celular na atualização da tela;
os timestamps e valores persistidos não mudam. **SELF_REVIEW_ONLY**, sem CI.

### Ponto anterior deste dia — retorno de permissões

Fonte `c2ed35959e387e5683a383de342a5cdda61453f8`: 150 de 151 testes
passaram, um ignorado por caminhos Windows. APK DEBUG e Kotlin release passaram;
lint mantém os dois bloqueios herdados e 81 avisos. APK SHA-256:
`DF67C90A0E84672389DB1791900391E5F10205DAFDDE27CECA4794442862309B`.
Retorno de permissão após perder a ação original pede novo toque sem alegar recusa.
O teste de resultados Android é artificial; nenhum diálogo ou BLE real foi acionado.

### Ponto anterior deste dia — Voltar

Fonte `09a38d6e6f17d887635b3acb6d1446f0f96106b2`: 146 de 147 testes
passaram; um ignorado por caminhos Windows. APK DEBUG e Kotlin release passaram.
Lint reproduziu os dois bloqueios herdados e 81 avisos. APK SHA-256:
`459AFCC139D5F61AB95EA3DE97D8FCFF0A99FA068493483BDFB80610772F1B6C`.
No emulador, Voltar nas quatro abas secundárias retorna ao Início; o perfil fecha
primeiro sem mudar Ajustes. No Início, Voltar continua saindo para o launcher.

### Ponto anterior deste dia — acessibilidade

Fonte `d47c9baaae24776788d6ac283ac5b0d8545d6760`: 146 de 147 testes
passaram, um ignorado pela limitação Windows; APK DEBUG e Kotlin release passaram.
Lint manteve dois bloqueios herdados e 81 avisos. APK SHA-256:
`CB086733503210C96781D55913D27FAB47238F5625481EB848155073BC0521A1`.
Histórico agrupa data/valor para acessibilidade; mudanças de dia e estado de envio
possuem aviso semântico. Testes Compose não equivalem à validação de anúncios
com TalkBack ou pacientes.

### Ponto anterior deste dia — perfil

Fonte `0556dc95f36a678a72f4eedd869e45571bc21e74`: 143 de 144 testes passaram,
um ignorado pela limitação Windows; APK DEBUG e Kotlin release passaram.
Lint reproduziu os mesmos dois bloqueios herdados e 81 avisos. **SELF_REVIEW_ONLY**,
não CI; DRAFT. APK SHA-256:
`EA4F6D2D7FAED7B2B52A7B23110498B0F126A7B1672227BA21754FEBBB05B05C`.

O perfil agora protege rascunhos ao cancelar e orienta quando está indisponível.
Preflight atualizado, reconciliação da PR #3 incorporada à #2 e limites das
capturas estão em [PATIENT_FRONTEND_CYCLE_2026-09-17.md](PATIENT_FRONTEND_CYCLE_2026-09-17.md).
O histórico abaixo conserva os resultados atribuídos às revisões anteriores.

## Ponto de verificação em 15/09/2026, 19:29 BRT

Fonte verificada: `71c585d1040f68db5884433dac0a1e44202cadeb`.
138 testes locais: 137 passaram, 1 ignorado por limitação de caminhos no Windows,
zero falhas/erros. APK DEBUG compilado. Lint ainda falha nos dois erros herdados
listados abaixo (81 avisos nesta execução). **Desenvolvimento em andamento; DRAFT.**
Compilação Kotlin release também passou, sem assinatura/empacotamento release.

### Publicação bloqueada por acesso

Em 15/09/2026, após confirmar novamente base e HEAD da PR #4 no GitHub, o push
de `feature/next2u-patient-ui` foi recusado com HTTP 403: `Permission denied to
rafaeldepaulafigo-web`. Não houve publicação dos commits de implementação, edição
da PR, merge ou mudança de credenciais. O responsável pelo repositório precisa
conceder escrita a essa conta ou publicar os commits preservados localmente.
O desenvolvimento independente continua local. A PR remota seguia em
`9a239d9113bc671624643acc975b3e10042f4a57` na última consulta.
Revalidado às 19:28 BRT: `permissions.push=false`, base da PR #4 em
`04938c46fdba3ab59789943f41b7a48ea5b4b72e`, mesma pilha aberta. Não houve nova
tentativa de push sem alteração dessa permissão.

## OBSERVED FACTS — base confirmada em 15/09/2026

- Repositório: https://github.com/leanderdulac/HBand-.
- Integrações: `04938c46fdba3ab59789943f41b7a48ea5b4b72e`, branch
  `cursor/healthsync-sdk-parity-p1-5cdc`, PR #3.
- Ordem preservada: PR #1 → #2 → #3 → **#4 (DRAFT, frontend)**.
- O GitHub confirmou que a PR #4 já continha o handoff do frontend:
  `feature/next2u-patient-ui`, HEAD inicial `9a239d9113bc671624643acc975b3e10042f4a57`.
  Seu único delta inicial era `docs/FRONTEND_UI_HANDOFF.md`. Por isso o trabalho
  continua nessa branch, sem abrir outra linha concorrente ou usar o main antigo.
- Checkout de trabalho: `C:/CDev/Next2U-Patient-Native`, inicialmente limpo.
- Pacote preservado: `com.aistudio.hbandhealthtech.pxq97m`. Kotlin/Compose nativo,
  AGP 9.1.1, Gradle 9.3.1, minSdk 24, compileSdk 36.1, targetSdk 36.
- O responsável pelo backend identificou esse pacote/tip como o instalado no S21+
  do relatório. A correspondência no aparelho não foi testada independentemente.
- `healthtech/companion-android` e sua PR #7 são outro produto e não entram nesta composição.
- Web e Tablet ACS não foram modificados.

## OBSERVED FACTS — implementação e limites

### Ciclo 1 — `feae3cf25e5febe0937a223a898de44b7e0e5e7a`

- Navegação: Início, Histórico, Relógio, Envios e Ajustes; aba preservada em recriação.
- Retirado botão flutuante sobre o conteúdo. Conectar leva à conexão; ler dados
  mantém o callback de leitura existente e aparece apenas com relógio conectado.
- Opções do relógio recolhidas; simulação exige abrir opções e ferramentas de
  teste, somente em build DEBUG. A origem simulada da bateria continua identificada.
- Estados de envio em português. Falha no serviço não equivale a diagnóstico de
  falta de internet. A fila sem pendências não afirma recebimento pela equipe.
- Removido acesso pelo indicador à ação que marcava registros como sincronizados
  sem enviá-los. A operação de dados não foi alterada.

### Ciclo 2 — `42d474a38a70306563f5689ba1bcb052ad3adaa0`

- Histórico sem gerar números para dias vazios; dados sem data válida/futuros
  não entram na apresentação por dia. Datas usam o fuso local do celular.
- Resumo de hoje não reaproveita ontem ou registros antigos. Retirada estimativa
  de minutos ativos pela quantidade de registros: esse modelo não informa duração.
- Contadores exibidos como maior valor salvo, sem somar snapshots e chamá-los de
  total diário. Zero usado como ausência pelo modelo não é promovido a medição.
- Consulta por tipo, até 15 registros recentes com valor/data disponíveis e
  gráfico opcional de pontos para batimentos, oxigênio e temperatura. Sem limites
  de eixo que recortem valores e sem curva clínica fictícia.
- Batimentos médios desconsideram valores ausentes. Pressão exige os dois valores;
  temperatura e calorias rejeitam valores não finitos na apresentação.
- Removidos geradores de teste da consulta cotidiana; simulador continua nas
  ferramentas de desenvolvimento existentes. Não houve migração de dados.

### Classificação de capacidades

- **REAL:** nenhuma nova capacidade operacional é declarada confirmada por este
  trabalho. Preservar callbacks existentes de conexão, leitura, fila e estados
  Veepoo é um fato de inspeção de código; não satisfaz sozinho a definição de REAL
  de CONTEXT §20, que exige contrato confirmado e evidência de integração real.
  As integrações físicas do relatório continuam sendo relatos do responsável,
  sem novo teste independente neste desenvolvimento de frontend.
- **DEMO:** capturas e fixtures de testes são cenários artificiais de UI; não são
  medições de pacientes. Histórico legado não carrega origem REAL/DEMO por registro.
  A UI não consegue reclassificar registros antigos com segurança.
- **BACKEND CONTRACT REQUIRED:** identidade/proveniência por registro histórico,
  distinção entre dado realmente medido e zero indisponível, tempo autoritativo,
  duração de atividade, confirmação de recepção e visibilidade pela equipe.
- **PROPOSED / CONCEPTUAL:** roteiro de avaliação com pacientes abaixo. Não houve
  teste com pacientes nem avaliação clínica das medições avançadas.

## Verificações locais — não são CI

Ambiente Windows, Codex local; JDK 21.0.12.1+1 em
`C:/CDev/Next2U-Build-Tools/java21/jdk-21.0.12.1+1`, SDK em
`C:/Users/WoWus/AppData/Local/Android/Sdk`, cache Gradle
`C:/CDev/Next2U-Android-Cache`. Plataforma oficial Android 36.1 instalada com
autorização explícita do usuário. Download automático de SDK pelo Gradle desabilitado.
Sem google-services.json ou credenciais privadas adicionadas. O plugin configurado
pelo projeto avisa sobre a ausência do arquivo; não foi acionado backend real.

Comando nativo correspondente a compilação, testes e análise estática:

```powershell
./gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug '-Pandroid.builder.sdkDownload=false' --console=plain
```

Checks pnpm do workflow Web não são executáveis neste projeto Android; não foram
substituídos por resultados fictícios. Não há resultado de CI ou revisão independente
alegado neste documento.

| Candidato | Resultado observado |
| --- | --- |
| `feae3cf25e5febe0937a223a898de44b7e0e5e7a` | Kotlin do aplicativo compilou. Compilação dos testes falhou por importação nova inválida; corrigida no ciclo 2. Lint não concluído nessa execução. Execução começou com o conteúdo staged idêntico ao commit; o commit foi criado durante a execução sem delta de fonte. |
| `42d474a38a70306563f5689ba1bcb052ad3adaa0` | Kotlin do aplicativo e dos testes compilou. 70 testes passaram, zero falhas/erros. Lint: 2 erros e 109 avisos; comando combinado terminou com falha por lint. |
| `a3124ed7483b0ab40eec6cf98810032034ca02c4` | Testes e capturas `Patient*`: 12 testes passaram. Capturas inspecionadas em 320 dp; histórico e ajuda de permissão também com fonte 1,6×. |
| `213793faaaf06b1ed02e4e50f8cbf7785f241335` | 76 testes passaram, zero falhas/erros/ignorados. Relatórios preservados em `app/build/patient-ui-evidence/<SHA>/`. |
| `d8b7e37ea80cca45704629ab66669c2bcb446688` | 82 testes passaram, zero falhas/erros. `:app:assembleDebug` passou. APK de desenvolvimento gerado localmente; não instalado nem testado no S21+/VE30. |
| `06e19973a7dbaf83f642d1fc52ec65ebc45a35ca` | 86 testes passaram; APK passou. Lint: 2 erros, 110 avisos. Novo aviso UseKtx da UI foi corrigido no ciclo seguinte. |
| `2fa6bf4a2f9c9e3d5d2fdaf14eb5fb0154f0627a` | 90 testes, 1 falha no teste de compartilhamento FileProvider no Windows. APK compilado. A falha não foi tratada como aprovação. |
| `a2b48e819fbbc681266889355aaf8d890200af78` | 91 testes: 90 passaram, 1 ignorado no Windows; APK compilado. Renderização da imagem testada separadamente do compartilhamento. |
| `cd595d640c88766dc8c7a50b322e9b8fb034846d` | Capturas de componentes e testes do cartão executados. A revisão revelou que a escala do diálogo de perfil não acompanhava o ambiente do teste; corrigido no candidato seguinte. |
| `e7389e76999c375f2c07a2e8f55d438dc99db04a` | 9 testes/capturas direcionados passaram. Cabeçalho, perfil e água inspecionados em 320 dp com fonte 1,6×; perfil usa escala do ambiente Android simulado. |
| `e5abc2c841ccbc38d9314b549ebdeb76628bcf2a` | 103 testes: 102 passaram, 1 ignorado, zero falhas/erros. APK passou. Lint: 2 erros herdados e 81 avisos; execução combinada terminou com falha por lint. Fonte permaneceu igual durante os checks. |
| `cb42f2c05a9888b32e341df47b052447343d2cc2` | Testes direcionados de sono, resumo automático e capturas passaram; APK compilado. Não é a suíte completa. |
| `41467956ef002e8f4f95cfa8fe4a5ce4a99e4dea` | Duas capturas passaram. Revisão visual identificou quebra de nomes na navegação com fonte 1,6×; limites dos campos do perfil ficaram mais visíveis. |
| `03754e7efb48ced9c48b0ae97c777b3b7d6dd3c1` / `51ffc79d05429f4055f5532c3f3e5c51fd5c8912` | Capturas da navegação geradas; teste sem renderização nativa não encontrou o layout adaptado. A escala local isoladamente não resolveu; teste atualizado para usar a mesma renderização nativa das capturas. Resultados com falha preservados nos SHAs originais. |
| `25cbf89268910dbca05371be74d11f68b6546886` | Testes direcionados de navegação e montagem do Início passaram após habilitar renderização nativa no teste. Suíte completa: 111 testes, 110 passaram, 1 ignorado, zero falhas/erros. APK passou. Lint: 2 erros herdados e 81 avisos. Único delta durante os checks era este documento; fonte igual ao SHA. |
| `337541f54a492095d612b61eaaac8adc36cbb69f` | 4 testes/capturas direcionados passaram; depois 10 capturas de componentes foram regeneradas e preservadas por SHA. Suíte completa: 112 testes, 111 passaram, 1 ignorado, zero falhas/erros. APK passou. Lint: 2 erros herdados e 81 avisos. Único delta durante o check completo: novo documento de handoff; fonte igual ao SHA. |
| `8f5164708c8dbd564a257570e5de9146ca69641c` | 118 testes, 117 passaram, 1 ignorado, zero falhas/erros. APK passou. Lint: 2 erros herdados e 81 avisos. |
| `b9016fe8795f3a82cffeb5239a64909d658da7f3` | 4 testes direcionados de bateria passaram, incluindo captura com letras ampliadas inspecionada. Suíte completa não executada neste SHA. |
| `dd9564147a0b631a67b1c15e1bdaf480084f5a01` | 125 testes, 124 passaram, 1 ignorado, zero falhas/erros. APK passou. Lint: 2 erros herdados e 81 avisos. XML e resumo preservados por SHA; fonte limpa durante os checks. |
| `06d76b0c7db336d8431d7ded26460b163bf15236` | 7 testes direcionados de navegação e leitura passaram. Cobrem estado aberto/fechado, seção ativa e contagem local completa de Envios em dois tamanhos de tela. XML preservado; somente estes documentos tinham delta durante a execução. |
| `279d0f4664164522d7835c30599c6bd726b83700` | 2 testes/capturas passaram: busca do relógio na composição com navegação e parada de ECG antes de informação longa, ambos com fonte 1,6×. Somente delta de documentação durante os checks. |
| `54641eade799248406a37a27a0a35ae2776888b0` | 14 capturas/testes de componentes passaram. Prévia de compartilhamento inspecionada; fechar permanece visível antes/depois de rolar. Não foi acionado compartilhamento externo. Capturas/XML preservados; somente delta de documentação durante os checks. |
| `416bf1901c0ed8a8da4598768c40575a22c47bd9` | 131 testes, 130 passaram, 1 ignorado, zero falhas/erros. APK DEBUG e compileReleaseKotlin passaram. Lint: 2 erros herdados/81 avisos. Diagnóstico AWT/KSP apareceu durante a execução; tarefas de compilação concluíram. Somente delta de documentação; XML e resumo preservados por SHA. |
| `f7eb0b071820c4c7eb6e6bfea45e83901a353fe5` | Teste direcionado de CSV passou: abrir a prévia não copia dados; prévia limitada a cinco e cópia explícita com sete registros completos. Em seguida, suíte completa: 132 testes, 131 passaram, 1 ignorado, zero falhas/erros. APK DEBUG e compileReleaseKotlin passaram. Lint: 2 erros herdados/81 avisos. Somente delta de documentação durante os checks; XML/resumo preservados. |
| `8f746d597b430f547bfee5fb2fcd80c69653ce45` | 5 testes direcionados (fila/histórico de tarefas) e APK DEBUG passaram. Verificam filtragem sem enviar, callback explícito e estados cancelado/bloqueado/desconhecido separados de falha. Fonte limpa. |
| `f696a964e23a7ca07ff4993a81d8ccb26b5cce37` | Mesmos 5 testes direcionados e APK DEBUG passaram após precisar a mensagem de estado desconhecido. XML/resumo preservados. Sem nova execução de lint/suíte completa/release neste SHA. Fonte limpa. |
| `6a3c4f833fe3c264be0226a632cec41f83ab6a04` | 5 testes de perfil e APK DEBUG passaram. Avançar move o foco de altura para peso; concluir remove o foco sem salvar automaticamente. XML/resumo preservados. Somente delta de documentação durante os checks; não é teste de teclado físico. |
| `23fde36b5e60804b28713f6bf9cfcaeb61457b26` | 8 testes de perfil/conexão e APK DEBUG passaram. A mensagem de endereço inválido aparece e desaparece após correção, preservando o endereço enviado ao callback. XML/resumo preservados; somente delta de documentação durante os checks. |
| `3df622bbcb1960319356604afc1a599560941786` | 5 testes de notificações/Ajustes e APK DEBUG passaram. Retorno à tela relê a permissão global; consultar não gera avisos. Intent aponta para este app; mensagens preservam canal/IDs/valores/limites/importância. Notificações de teste existem apenas no ShadowNotificationManager do Robolectric. Somente delta de documentação durante os checks. |
| `bd9e3e154f8ab996a60beeb89287134d619c0562` | Teste/capturas dos limites com fonte 1,6× passou; três capturas inspecionadas. Em seguida: 138 testes, 137 passaram, 1 ignorado, zero falhas/erros. APK DEBUG e compileReleaseKotlin passaram. Lint: dois bloqueios herdados (Error + Fatal), 81 avisos. XML, capturas, log completo e resumo preservados. Somente delta de documentação durante os checks. |

### Ciclos seguintes implementados

- `bd9e3e154f8ab996a60beeb89287134d619c0562`: limites dos avisos com controle de
  ativação separado do título; valores legíveis e botões que se distribuem em
  linhas conforme o espaço e tamanho da letra. Sliders nomeados para leitor de
  tela e seleção de valor exposta na semântica. Intervalos 80–180/35–75, passos,
  presets e callbacks existentes permanecem iguais; não são recomendações clínicas.

- `23fde36b5e60804b28713f6bf9cfcaeb61457b26`: ajuda de endereço explica os pares
  e caracteres aceitos; erro aparece junto ao campo. Perfil diferencia orientação
  de números inteiros e decimais, sem mudar validadores nem substituir dados.
- `3df622bbcb1960319356604afc1a599560941786`: Ajustes consulta a permissão global
  de notificações, atualiza ao voltar para a tela e oferece acesso às opções do
  próprio aplicativo no Android. Bloqueio observado é informado; não se infere
  entrega nem habilitação de cada canal. Textos dos avisos de batimentos e nome do
  canal foram traduzidos. IDs `hr_threshold_alerts`, 1001/1002, limites, importância
  e lógica de disparo preservados; nenhuma notificação real disparada pelo agente.

- `8f746d597b430f547bfee5fb2fcd80c69653ce45` / `f696a964e23a7ca07ff4993a81d8ccb26b5cce37`:
  detalhes de Envios identificam que o antigo botão de atualizar solicita envio.
  Texto "Tentar enviar registros" conserva o mesmo callback; consultar e filtrar
  não solicita trabalho. Estados cancelado, aguardando outra tarefa e situação
  não reconhecida não são rotulados automaticamente como falha. Filtros verticais
  e rótulos com espaço para letras grandes; contagem de concluídos continua local.
- `6a3c4f833fe3c264be0226a632cec41f83ab6a04`: teclado do perfil oferece avançar
  entre campos; concluir no campo de peso fecha o teclado sem salvar sozinho.
  A apresentação esclarece que o perfil exibido está salvo neste celular.

- `f7eb0b071820c4c7eb6e6bfea45e83901a353fe5`: CSV completo preparado apenas
  após ação do usuário, fora da thread da interface; escrita em arquivo usa IO.
  Prévia monta apenas cinco registros. A preparação bloqueia toques repetidos e
  é cancelada ao fechar a seção/sair da composição. Cada compartilhamento cria
  arquivo de cache distinto com o prefixo anterior, evitando sobrescrever uma
  exportação que outro aplicativo ainda esteja lendo. Formato, campos, MIME,
  autoridade FileProvider e permissões de leitura preservados. Função de
  serialização comparada integralmente com a base, igual após normalizar CRLF.
  Não há medição de desempenho em aparelho simples nem teste de envio externo.

- `279d0f4664164522d7835c30599c6bd726b83700`: buscar relógio aparece antes da
  explicação longa de permissões. Iniciar/parar medição aparece antes dos detalhes
  recebidos. As mensagens de permissão não prometem um novo diálogo quando a
  permissão já tiver sido concedida. Solicitação e callbacks preservados.
- `54641eade799248406a37a27a0a35ae2776888b0`: prévia de compartilhamento tem
  título e botão nomeado "Fechar cartão" fora da área rolável; a imagem e o texto
  podem ser consultados sem perder a saída da tela.
- `416bf1901c0ed8a8da4598768c40575a22c47bd9`: explicação da limpeza DEBUG
  deixa explícito que apagar registros locais não troca identidade do paciente
  nem confirma exclusão em outros serviços. Operação existente não foi acionada
  nem alterada.

- `06d76b0c7db336d8431d7ded26460b163bf15236`: seções com semântica de título e
  estado aberto/fechado; Envios oferece ao leitor de tela a quantidade completa de
  registros pendentes no aplicativo, mesmo quando o indicador visual abrevia 99+.
  Testes verificam a árvore de acessibilidade Compose; TalkBack físico pendente.

- `8f5164708c8dbd564a257570e5de9146ca69641c`: mensagens de conexão e falha
  compreensíveis nas medições avançadas; detalhes técnicos de erro somente em DEBUG.
  A ação de parar usa o nome da medição, sem competir com iniciar/ler durante a
  execução. Traçado ECG conserva os pontos recebidos, evita estouro numérico na
  escala visual e informa que a escala foi ajustada. Gate e SDK preservados.
- `b9016fe8795f3a82cffeb5239a64909d658da7f3`: bateria com indicação de última
  leitura; valores fora de 0–100 não são apresentados como percentuais válidos.
  Simulação não aparece na apresentação de release. Aviso de bateria baixa com
  botões grandes e explicação sobre leitura antiga quando desconectado.
- `dd9564147a0b631a67b1c15e1bdaf480084f5a01`: preparação do cartão fora da
  thread da interface, com andamento, proteção contra toques repetidos e nova
  tentativa em caso de falha. Sair da composição cancela a preparação e impede
  abertura tardia de diálogo. Compartilhamento continua dependendo de ação do
  paciente; mesmo gerador, formato e FileProvider.

- `a3124ed7483b0ab40eec6cf98810032034ca02c4`: busca guiada do relógio,
  tratamento de permissão negada, identidade exata do resultado preservada e
  conexão por MAC em ajuda, com validação de formato. Retirados atalhos fixos
  de outro relógio e o endereço inválido com `VE`. Manifesto/SDK preservados.
  Na solicitação de localização já existente, Android 12+ recebe os dois níveis
  declarados juntos, como requer a [documentação Android](https://developer.android.com/develop/sensors-and-location/location/permissions/runtime).
  Essa mudança é no pedido da UI, não uma nova permissão declarada ou coleta.
- `213793faaaf06b1ed02e4e50f8cbf7785f241335`: início prioriza conexão, última
  leitura e registros de hoje; acesso direto ao histórico. Outras medições e
  opções ficam em seções. Medição em andamento força a seção aberta para
  preservar acesso a parar. Retirados traçado decorativo de ECG, rótulos clínicos
  calculados pela UI e alegação de leitura ao vivo para snapshot antigo.
- `d8b7e37ea80cca45704629ab66669c2bcb446688`: perfil sem valores de exemplo
  usados como correção silenciosa; aceita vírgula decimal, preserva casas decimais,
  chave local e ID do paciente. Identificação somente leitura. Perfil ausente não
  permite criação fictícia. Ajustes separam ferramentas de teste/cópia/limpeza
  em DEBUG e seções fechadas. Limites locais não são chamados de zona segura.
- `06e19973a7dbaf83f642d1fc52ec65ebc45a35ca`: fila com pendências primeiro,
  ações de exclusão restritas ao suporte DEBUG e confirmação antes de apagar.
  “Concluído no aplicativo” não é recibo do Core. Removidas datas de execução
  fabricadas da apresentação de tarefas, sem alterar as operações existentes.
- `2fa6bf4a2f9c9e3d5d2fdaf14eb5fb0154f0627a` e
  `a2b48e819fbbc681266889355aaf8d890200af78`: cartão compartilhável sem números
  substitutos, pontuações clínicas inventadas ou períodos falsos. Sete dias de
  calendário, ausência explícita, máximos identificados, água de hoje e respiração
  total salva separados. Prévia acessível em texto, sem envio automático.
  Gerador de bitmap é apresentação; FileProvider, pacote e contratos preservados.
- `cd595d640c88766dc8c7a50b322e9b8fb034846d` e
  `e7389e76999c375f2c07a2e8f55d438dc99db04a`: capturas de componentes, cabeçalho
  com nome separado do botão e até duas linhas (nome completo no perfil), saudação
  só no Início. Água em botões verticais de 56 dp, meta sem substituição silenciosa,
  ausência de meta explícita e confirmação para apagar somente o dia existente.
- `e5abc2c841ccbc38d9314b549ebdeb76628bcf2a`: opções do relógio em linguagem
  cotidiana, estado ainda não verificado distinto de indisponível. Controles P0
  respeitam também o gate de conexão já utilizado nas ações P1. Switches com nome
  acessível; callbacks/IDs/estados do SDK mantidos. Histórico recebido do relógio
  não é confundido com envio ao serviço. Metadados técnicos ficam em suporte DEBUG.
- `cb42f2c05a9888b32e341df47b052447343d2cc2`: sono apresenta os campos do último
  registro datado nos sete dias de calendário, sem pontuação de qualidade, REM
  inventado ou média semanal baseada em um snapshot. Tempo acordado separado do
  sono; fases ausentes explícitas. Nenhuma alteração no mapeamento/persistência.
  Resumo automático em validação; texto sem proveniência só aparece na revisão
  técnica DEBUG. Geração e serviço existentes não foram modificados.
- `41467956ef002e8f4f95cfa8fe4a5ce4a99e4dea` até
  `25cbf89268910dbca05371be74d11f68b6546886`: navegação extraída e adaptada ao
  tamanho real dos rótulos; mesmas cinco rotas e IDs de teste. Fonte não é reduzida
  para caber. Contorno de campos com maior contraste. Ação de conectar aparece
  antes dos detalhes de bateria. CSV em acesso secundário, botões grandes,
  mensagens em português e escolha explícita do destinatário. A função de
  serialização CSV foi comparada e mantida idêntica após normalizar quebras de linha;
  nomes de colunas, IDs, FileProvider e ACTION_SEND preservados.
- `337541f54a492095d612b61eaaac8adc36cbb69f`: ferramentas DEBUG de Ajustes
  agrupadas sob um único acesso; controles de respiração verticais, com nomes
  distintos para iniciar, pausar, continuar e salvar. Tempo salvo identificado,
  números com contraste conforme a cor e aviso para salvar antes de sair.
  Protocolo de fases, vibração, temporizador e persistência não foram alterados.

Capturas são componentes Robolectric com fixtures artificiais (**DEMO**), não
execução completa do APK. Na revisão inicial do perfil, a escala local Compose
não alcançava a janela do diálogo; `RuntimeEnvironment.setFontScale` corrigiu o
teste. As capturas anteriores desse diálogo não comprovam letras ampliadas.

O teste de compartilhamento com FileProvider é ignorado apenas quando o separador
do sistema de arquivos não é `/`. O bytecode local de AndroidX Core 1.18.0 confirmou
comparação com `rootPath + '/'`; o Windows usa outro separador. A renderização do
bitmap tem teste independente e captura inspecionada. Compartilhamento entre apps
continua pendente em Android; não houve substituição/mock do provider de produção.

Comandos adicionais efetivamente executados:

```powershell
./gradlew.bat :app:recordRoborazziDebug --tests 'com.example.ui.components.PatientHistoryScreenTest' '-Pandroid.builder.sdkDownload=false' --console=plain
./gradlew.bat :app:testDebugUnitTest :app:recordRoborazziDebug --tests 'com.example.ui.components.Patient*' '-Pandroid.builder.sdkDownload=false' --console=plain
./gradlew.bat :app:testDebugUnitTest '-Pandroid.builder.sdkDownload=false' --console=plain
./gradlew.bat :app:testDebugUnitTest :app:assembleDebug '-Pandroid.builder.sdkDownload=false' --console=plain
./gradlew.bat :app:recordRoborazziDebug --tests 'com.example.ui.components.PatientVisualReviewTest' --tests 'com.example.ui.components.PatientHydrationTest' '-Pandroid.builder.sdkDownload=false' --console=plain
./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug '-Pandroid.builder.sdkDownload=false' --console=plain --continue
./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:compileReleaseKotlin '-Pandroid.builder.sdkDownload=false' --console=plain --continue
```

APK do candidato `d8b7e37ea80cca45704629ab66669c2bcb446688`: 45.460.618 bytes,
SHA-256 `8ce1c761b29e39390faed276ad844affbc0e136af9a14006dbf8b8e41316be0c`.
Foi usada uma chave Android Debug criada localmente no arquivo ignorado
`debug.keystore`; não é evidência de compatibilidade da assinatura com o app do
relatório. Não desinstalar o app que contém dados para testar este APK. Para
atualizar esse aparelho, o responsável deve compilar a branch com a configuração
e assinatura adequadas. Arquivos em `app/build` podem ser substituídos pelo próximo
build; o hash identifica exatamente este artefato histórico.

APK do candidato `e5abc2c841ccbc38d9314b549ebdeb76628bcf2a`: 45.968.413 bytes,
SHA-256 `0c0e882c7b6c62233f2faf9ea2b2c07c69d9cb954c136288c832865047efe122`.
Local: `app/build/outputs/apk/debug/app-debug.apk`. As mesmas limitações de
assinatura e instalação acima se aplicam. XML dos testes, lint e resumo com data,
SHA e hash foram preservados em `app/build/patient-ui-evidence/<SHA>/` (ignorado).

APK do candidato `25cbf89268910dbca05371be74d11f68b6546886`: 45.940.443 bytes,
SHA-256 `c25e367781c755efed5e28c8841bed681a31a78f5fcf19a81a753f45d36d2266`.
A captura `home_connection_large_text` combina cabeçalho, navegação e cartão
com cenário vazio. O teste verifica que conectar fica visível sem rolar em 320 dp
com fonte 1,6×. É uma montagem de componentes, não inicialização do APK/SDK.

APK do candidato `337541f54a492095d612b61eaaac8adc36cbb69f`: 45.942.102 bytes,
SHA-256 `75a721e2684722aee69ddbbbb7fe5eb165f853c14ac558621746a4279c5fc520`.
Inspeção local com `aapt2 dump badging`: pacote preservado, versionCode 1,
versionName 1.0, minSdk 24, targetSdk 36; ABIs arm64-v8a, armeabi-v7a e x86_64.
Isso confirma metadados do APK, não execução em aparelho nem compatibilidade de assinatura.

APK do candidato `dd9564147a0b631a67b1c15e1bdaf480084f5a01`: 45.942.110 bytes,
SHA-256 `9d17b768869aae241383102f8cef92535bbe3730c111a3e8c1f028fc04373faf`.
Teste de preparação assíncrona cobre toques repetidos, saída da tela e nova tentativa.
Isso não comprova compartilhamento entre aplicativos em Android físico.

APK do candidato `416bf1901c0ed8a8da4598768c40575a22c47bd9`: 45.943.114 bytes,
SHA-256 `2bb9ef056ba84a9d3dcecafc84b773bc3531b41dea72690b77f32a0ae9d51cbb`.
As 14 capturas mais recentes foram geradas no SHA `54641eade799248406a37a27a0a35ae2776888b0`;
a diferença seguinte de produto foi a explicação de limpeza DEBUG e remoção de
imports sem uso no diálogo. Não representam execução do APK inteiro.

APK do candidato `f7eb0b071820c4c7eb6e6bfea45e83901a353fe5`: 45.946.814 bytes,
SHA-256 `bb7dd12d0aa467685d26e146e81a3edd558cc38b25f3b90e898fdc43dfde2130`.
Teste de cópia usa clipboard simulado do Robolectric; nenhum dado real foi copiado
para a área de transferência do computador ou enviado a outro aplicativo.

APK do candidato `bd9e3e154f8ab996a60beeb89287134d619c0562`: 45.950.591 bytes,
SHA-256 `3aa304b7c0aa73f99e710fc6b238caab886310f668bdbc1d5c5247d9d9c82240`.
Verificação completa do comando registrada em `app/build/patient-ui-evidence/<SHA>/gradle-full.log`.

APK do candidato `89fde29ff84aa9b508476f99e9445ed62280e73b`: 45.950.657 bytes,
SHA-256 `b31f41f591ca8a607de0a00e7cfd74f4619043f7446a9f3d7349fcdbbb4b7355`.
16 testes direcionados passaram; a rodada consolidada manteve 137 aprovados e
1 ignorado. Lint mantém os dois bloqueios abaixo e 81 avisos. Evidências,
três capturas do Histórico e XML estão em `app/build/patient-ui-evidence/<SHA>/`.

APK do candidato `71c585d1040f68db5884433dac0a1e44202cadeb`: 45.950.775 bytes,
SHA-256 `ab4d7b39a3b66d4e6191f1c45ed97c83065582663b4123839b260e45390ad04e`.
Cinco testes de perfil passaram, seguidos da suíte completa: 138 testes,
137 aprovados e 1 ignorado, sem falhas/erros. DEBUG e Kotlin release passaram;
lint mantém 2 bloqueios/81 avisos. Logs e XML preservados por SHA.

Bloqueios de lint em trechos herdados da base:

1. `HBandBleManager.kt:1640`: MissingPermission ao acessar nome do dispositivo.
2. `MainActivity.kt:24`: InvalidFragmentVersionForActivityResult; dependência
   Android Fragment anterior à versão mínima exigida pela análise.

Esses achados não foram suprimidos. Correção no BLE/SDK e mudança de dependências
ficam para o responsável e escopo aplicável. **Verificação completa pendente.**

## Impacto nos quatro canais e dependências

| Canal | Impacto e dependências |
| --- | --- |
| App Paciente / VE30 | Apresentação Compose, bitmap/exportação, textos das notificações Android existentes e testes. IDs, serviços, BLE, schemas, persistência, contratos e permissões declaradas existentes preservados. A fila local não comprova suporte offline completo. |
| Web Profissional | Sem alterações. Confirmar vinculação canônica paciente/relógio, autorização de acesso e confirmação da última recepção pelo Core antes de afirmar que os dados estão disponíveis à equipe. |
| Tablet ACS | Sem alterações. Depende da mesma identidade, consentimento, escopo territorial e reconciliação de atualizações. A UI mobile não define essas regras. |
| WhatsApp / SM Click | Sem alterações nem mensagens enviadas. Divulgação, identidade do destinatário e consentimento continuam dependentes dos contratos próprios. |

Cuidador não foi implementado por troca de perfil/ID: identidade, consentimento e
permissões ainda precisam de contrato confirmado. Nenhum novo transporte/API foi criado.

### Dependências observadas durante a inspeção, ainda pendentes

- **BACKEND CONTRACT REQUIRED:** o analisador automático retorna somente texto,
  sem distinguir resposta remota de fallback local. Reaproveita métricas antigas
  quando os sete dias estão vazios e calcula minutos ativos pelo número de amostras.
  `MainViewModel` também o aciona automaticamente ao receber métricas. A revisão de
  origem, período, consentimento/divulgação e gatilho depende do responsável; não
  foi alterado o serviço nem enviado dado pelo agente.
- A interpretação de qualidade de sono da UI foi retirada em `cb42f2c`; isso não
  valida as fases nem a proveniência dos registros do SDK.
- `MainViewModel.todayDateString` é fixado na criação do ViewModel e reutilizado
  para água/respiração. Confirmar atualização na virada do dia e fuso; não foi
  modificado o armazenamento nem inventado um período vindo do backend.
- **BACKEND CONTRACT REQUIRED:** `UserProfileDao` lê o perfil local `CURRENT_USER`;
  a entidade possui valores padrão de exemplo, inclusive `PAT-HBAND-001`.
  Nesta inspeção não foi encontrado provisionamento de identidade canônica do
  paciente. O frontend não cria perfil com esses valores quando a leitura é nula
  e não permite trocar o ID. Primeiro cadastro/vinculação REAL e atualização
  compartilhada permanecem dependentes do contrato do responsável pelo backend.
  Um registro existente não pode ser classificado como DEMO apenas pelo nome.
- **PROPOSED / CONCEPTUAL:** indicador detalhado de conexão em andamento depende
  de estado observável da integração. `HBandBleManager.isConnectingVeepoo` é privado;
  o fluxo público consumido pela tela expõe busca e dispositivo conectado, sem
  todo o ciclo de conexão. Não foi criado progresso por temporizador nem alterado
  BLE/SDK para expor um contrato novo. Pedido de referência do primeiro acesso foi
  enviado ao usuário; as partes independentes continuam.

## Revisão de fronteiras em 15/09/2026 — somente leitura

Skill `next2u-contract-consistency`, concluída antes deste registro pela execução
de engenharia. Fonte mobile local: `6a3c4f833fe3c264be0226a632cec41f83ab6a04`;
PR #4 remota em `9a239d9113bc671624643acc975b3e10042f4a57`, base `04938c46fdba3ab59789943f41b7a48ea5b4b72e`.

**OBSERVED FACTS:** `MainViewModel.saveUserProfile` grava por `UserProfileDao` e
repassa `patientId` ao BLE. Não comprova gravação do cadastro central. A entidade
histórica `HBandSensorMetricEntity` não carrega `patientId` nem
`isRealSensorData`; `WearableRepository.enqueueTelemetry` salva a métrica antes
da decisão de enfileirar e troca uma data que não conseguiu interpretar pelo
horário atual. O simulador também grava nessa entidade. Data armazenada e origem
de medição precisam ser distinguidas pelo contrato; a UI não migrou dados nem
escolheu outro campo como autoridade temporal. A prévia/CSV usam ACTION_SEND e
FileProvider; isso não fornece recibo nem verificação da identidade do destinatário.

**Reconciliação de autoridades:** o checkout Web mudou por trabalho externo para
`33171c6f9de0616acd7b6a423c99891bc0d0c854`, branch local
`codex/web-alert-simulation-lock` (404 no GitHub para essa branch). Nenhum arquivo
Web foi editado nesta tarefa. Os blobs de CONTEXT, INTEROPERABILITY,
PATIENTS_BACKEND_CONTRACT e ADR-010 lidos localmente foram comparados diretamente
com GitHub `main` (`f9e7d39a740ba1178e1f3ac4bf84073f71efe6bb`) e são iguais.
P16 de Pacientes mantém escrita/propagação como pendência; os GETs profissionais
não constituem contrato de primeiro acesso do paciente nem de comunicação.

**RECOMMENDATIONS / BACKEND CONTRACT REQUIRED:** confirmar provisionamento e vínculo
`patient_id`/`device_id`, proveniência persistida, significado/autoridade das datas,
confirmação de recepção e regras de divulgação. Primeiro acesso e cuidador dependem
desses contratos. Web/ACS consomem a mesma identidade central; compartilhar arquivo
pelo Android não cria integração WhatsApp/SM Click. Nenhuma capacidade nova foi
classificada REAL; fixtures continuam DEMO e a avaliação com pacientes é proposta.

## RECOMMENDATIONS — roteiro para futura validação com pacientes

Usar aparelho simples e outro com letras ampliadas; incluir pessoas idosas e
pessoas com diferentes níveis de familiaridade digital. Explicar que se avalia o
aplicativo, não a pessoa. Usar dados de teste identificados e consentimento adequado.

1. **Encontrar o relógio:** “Mostre como você saberia se o relógio está conectado.”
2. **Conectar:** “Seu relógio foi desconectado. O que você faria agora?” Observar
   compreensão das permissões, sem orientar a sequência antes da tentativa.
3. **Consultar hoje/ontem:** “Encontre os registros de hoje. Agora veja o dia anterior.”
4. **Dia vazio:** “O que esta mensagem significa?” Verificar se a pessoa entende
   que não ter registro não significa estar tudo normal ou ter dado perdido confirmado.
5. **Envio pendente:** “Esses registros já chegaram à equipe?” Observar se a tela
   comunica o limite da informação sem induzir garantia de recebimento.
6. **Letras grandes:** repetir conexão e histórico com escala ampliada, leitor de
   tela quando usado pelo participante e sem depender apenas da cor.
7. **Pedir apoio:** observar onde a pessoa buscaria ajuda. Não compartilhar identidade
   nem alterar cadastro para simular permissões de cuidador.
8. **Registrar água:** “Registre 500 mL. Agora veja como cancelar a exclusão dos
   registros de hoje.” Não orientar a pessoa a beber uma quantidade específica.
9. **Compartilhar:** preparar a prévia com dados de teste; pedir que identifique
   período, informação ausente e destinatário. Encerrar antes de enviar dados reais.
10. **Leitor de tela:** localizar Envios, compreender a contagem de pendências e
    abrir/fechar uma seção. Conferir anúncio, foco e ordem no TalkBack real; não
    inferir aprovação de acessibilidade apenas dos testes de semântica.
11. **Avisos no celular:** em aparelho de teste, comparar aviso ativado no aplicativo
    com notificações bloqueadas pelo Android. Encontrar as opções do sistema e
    voltar; conferir atualização da mensagem. Não pedir que o participante provoque
    alteração dos batimentos nem confundir configuração com recebimento garantido.

Registrar conclusão sem ajuda/com ajuda/não concluída, passos, erros, trechos
incompreendidos, esforço relatado e sugestões. Tempo é uma observação, não critério
para julgar o participante. **Este roteiro ainda não foi executado com pacientes.**

## Capturas atualizadas — 15/09/2026

**OBSERVED FACTS:** `recordRoborazziDebug` executado no HEAD
`7c3c4d6ca65b6157967c9c9d965b83a6c0758a08`, com os filtros
`PatientVisualReviewTest` e `PatientHeartAlertSettingsTest`: 15 testes passaram,
sem falhas ou ignorados, produzindo 17 capturas. A fonte em `app` é idêntica ao
candidato `bd9e3e154f8ab996a60beeb89287134d619c0562`; a diferença é documental.
Capturas e XML preservados em
`app/build/patient-ui-evidence/7c3c4d6ca65b6157967c9c9d965b83a6c0758a08/`.
São montagens de componentes **DEMO**, incluindo letras ampliadas; não são
execuções do APK em aparelho nem testes com pacientes. Não somar esta rodada
à suíte consolidada de 138 testes como se fossem testes distintos.

Atualização no candidato `71c585d1040f68db5884433dac0a1e44202cadeb`:
`recordRoborazziDebug` com `PatientVisualReviewTest`, `PatientHeartAlertSettingsTest`
e `PatientHistoryScreenTest` passou (17 testes), gerando 20 capturas atuais,
incluindo os controles de dia em largura completa. Todas preservadas em
`app/build/patient-ui-evidence/<SHA>/captures/`, com XML separado da suíte completa.
Apenas documentos mudaram durante a captura; a fonte do aplicativo permaneceu
no SHA indicado. São testes de componentes, distintos dos ensaios de APK abaixo.

## Execução do APK em emulador isolado — 15/09/2026

**OBSERVED FACTS:** foi criado o AVD `Next2U_Patient_UI_35` (`emulator-5580`)
usando apenas emulador, imagem Android 35 e aceleração já instalados. Nenhum
componente de sistema adicional foi instalado. A imagem existente é Google Play
Tablet x86_64, com perfil de hardware Pixel 7 e tamanho de tela sobrescrito;
não equivale a teste em celular físico. O AVD Pixel_Tablet e o dispositivo físico
conectado permaneceram intocados. Wi-Fi e rede padrão foram desligados antes
da abertura do app; o ensaio não confirma capacidade offline da integração.

- APK `bd9e3e154f8ab996a60beeb89287134d619c0562`: abriu as cinco abas após
  negar localização, dispositivos próximos e notificações. Ao mudar o tamanho
  das letras/tela, `MainActivity` repetiu os pedidos de permissão.
- Correção `d17b3368208c3a745e41e450286ad05cb8cb3ad9`: somente a decisão de
  pedir permissões na recriação da Activity foi alterada; lista de permissões,
  BLE/SDK e contratos preservados. APK compilou em DEBUG e Kotlin release.
  Hash `5158e2ef9e5bd525b54f2339bfb79fc9adc9bc2d1e6cea0f3158848a8c500cbd`.
  Instalação/limpeza inicial de dados de teste atingiram apenas esse novo AVD.
  Negar uma vez, ampliar de 1,3× para 1,6× e reduzir de 720×1280 para 480×960
  pixels (densidade 240) não repetiu pedidos. Flags USER_SET sem USER_FIXED
  foram verificadas, evitando confundir bloqueio do Android com a correção.
  Busca explícita voltou a pedir acesso; a recusa mostrou a mensagem de busca
  não iniciada e o acesso às permissões. Nenhuma busca física foi autorizada.
- Nesse tamanho, os botões de dia do Histórico quebravam palavras. Correção
  `89fde29ff84aa9b508476f99e9445ed62280e73b`: botões em coluna, largura inteira.
  Inspeção do APK mostrou ambos legíveis; tocar no dia anterior mudou de
  15/09 para 14/09. O teste automatizado percorreu os seis dias anteriores e
  voltou a hoje, verificando os limites sem criar registros. O texto da fila
  também deixou de orientar retorno à própria aba Envios.
- Correção `71c585d1040f68db5884433dac0a1e44202cadeb`: o estado de abertura do
  perfil passou a usar estado restaurável da interface. Antes, mudar as letras
  fechava a janela; depois, ela permaneceu aberta no APK. A comparação usa
  o caso de perfil ausente, sem criar um cadastro artificial. Não comprova
  edição/restauração de dados de um paciente provisionado; os cinco testes
  existentes de perfil usam fixtures, incluindo IDs e validação de campos.
  Uma captura inicial de hierarquia falhou transitoriamente; o arquivo antigo
  não foi mantido como evidência. As comparações antes/depois usam dumps válidos.

Capturas/XML por SHA em subpastas `emulator` e `emulator-before`. O buffer de
falhas da imagem contém erros de serviços Google de voz preinstalados; não são
do pacote Next2U. Não foram observadas falhas do app durante esse roteiro.
As permissões nativas aparecem em inglês porque o Android deste AVD está em
inglês; os textos do aplicativo vistos estão em português.

**RECOMMENDATIONS:** repetir em Android/celular representativo e com TalkBack.
Este é um ensaio técnico local **SELF_REVIEW_ONLY**, sem pacientes, relógio,
conta central, entrega de dados ou compartilhamento externo. Não comprova
capacidade **REAL** de integração. Os cenários vazios e de permissão são de teste;
as pendências **BACKEND CONTRACT REQUIRED** de identidade e proveniência continuam.

## Auto-revisão

**SELF_REVIEW_ONLY**, realizada pelo mesmo agente implementador. Não equivale a
aprovação independente, CI, autorização de merge ou publicação. PR permanece DRAFT.
Revalidar base, HEAD e aplicabilidade de cada evidência ao continuar os ciclos.
