# Next2U paciente — acesso e roteiro de revisão

## OBSERVED FACTS

O frontend foi desenvolvido no **HBand-**, em Kotlin/Compose, a partir do tip
`04938c46fdba3ab59789943f41b7a48ea5b4b72e` das integrações. A branch local é
`feature/next2u-patient-ui`; checkout `C:/CDev/Next2U-Patient-Native`.
O pacote permanece `com.aistudio.hbandhealthtech.pxq97m`.

O push foi recusado com HTTP 403 para `rafaeldepaulafigo-web`. Os commits estão
locais; a [PR #4](https://github.com/leanderdulac/HBand-/pull/4) permanece DRAFT,
sem esta implementação publicada. Em 16/09, a #3 foi incorporada à branch da #2;
o conteúdo da integração continua idêntico à base da #4, que não mudou. A ordem
da pilha foi preservada; nenhuma troca de base ou merge foi feita nesta tarefa.
Não usar o companion-android nem o main antigo para testar esta interface.

## O que revisar

- **Início:** conexão em destaque, última leitura datada, dados de hoje sem
  preenchimento fictício e acesso ao histórico. Botão principal sem sobreposição.
  Nome e valor da última leitura ficam associados para o leitor de tela;
  os rótulos de batimentos explicitam a unidade por minuto.
- **Histórico:** dias vazios explícitos, valores positivos disponíveis, contadores
  como maior valor salvo; sem minutos ativos calculados pela quantidade de amostras.
  Botões de dia em largura completa para preservar palavras com letras grandes.
  Data e valor ficam juntos para acessibilidade, com aviso na mudança de dia.
- **Relógio:** busca guiada, negação de permissões e ajuda de conexão; mesma
  identidade do dispositivo selecionado e callbacks de integração.
  Bateria identificada como última leitura; ações de parar nomeiam cada medição.
  Se o pedido de permissão perder a ação original numa recriação da tela, a
  interface pede novo toque, sem afirmar recusa ou escolher um relógio sozinha.
- **Envios:** fila local e novas tentativas sem prometer recebimento pela equipe.
  Mudanças de estado são expostas como avisos para leitores de tela.
  Exclusão exige confirmação e acesso às ferramentas DEBUG.
- **Ajustes:** perfil legível, vírgula decimal e validação sem números substitutos.
  ID do paciente somente leitura. Ferramentas de desenvolvimento em acesso separado.
  Teclado avança entre campos sem salvar sozinho.
  Cancelar um perfil alterado pede uma decisão antes de perder os campos digitados;
  é possível continuar editando. Perfil ausente recebe orientação sem cadastro fictício.
  Avisos de batimentos em português, com orientação quando o Android bloqueia
  notificações e acesso às opções do celular.
- **Uso diário:** água com meta cadastrada, sono sem nota clínica inventada,
  respiração com controles legíveis. Resumo automático identificado como em validação.
  Sem perfil disponível, a tela não apresenta uma meta de água padrão como cadastrada.
  Na recriação da tela, a respiração conserva o tempo já contado e volta pausada;
  continuar ou salvar exige novo toque. Confirmações cotidianas em português.
  Sair temporariamente do aplicativo também pausa o exercício e a vibração.
  Mensagens breves têm ação Fechar e duração ampliada. Avisos de teste DEBUG
  ficam identificados como teste também na notificação do Android.
- **Compartilhamento:** prévia com dados disponíveis e limites explícitos;
  andamento durante a preparação, proteção contra toques repetidos e nova tentativa.
  Exportação CSV opcional conserva o formato original.
  Erros ao abrir o compartilhamento ou copiar texto aparecem dentro da janela;
  a ação de fechar permanece acessível, inclusive nos testes com letras ampliadas.
- **Letras grandes:** navegação se distribui em mais linhas quando necessário;
  as cinco rotas permanecem disponíveis sem reduzir a fonte escolhida.
  Quando seriam necessárias três linhas de ícones, a navegação fica compacta,
  com nomes e contagem de pendências. Fonte 2.0 em 320×640 dp foi conferida no
  emulador; Conectar meu relógio aparece inteiro acima da navegação.
  Ampliar as letras ou girar a tela não repete os pedidos iniciais de permissão
  durante a recriação da interface; a tentativa explícita continua no Relógio.
  A janela Meu perfil também permanece aberta na mudança de tamanho das letras.
  A busca e a parada de medição precedem explicações longas; a prévia mantém
  "Fechar cartão" visível. Contagem de Envios e seções incluem descrições para
  leitor de tela. Ensaio limitado com TalkBack confirmou foco e abertura do perfil;
  fala, avisos e sequência completa de leitura ainda precisam de validação.
  Ao girar o celular, o cabeçalho fica compacto para dar espaço à tarefa atual;
  a marca e o acesso ao perfil permanecem. Botão de conexão completo conferido
  em paisagem 720×360 dp, fonte 1.0.

## Como acessar e testar

O nome exibido pelo Android é **next2u SAÚDE**, já configurado na base e preservado.

O APK DEBUG é gerado em `app/build/outputs/apk/debug/app-debug.apk`. O hash e o
SHA de cada versão estão em [PATIENT_FRONTEND_VALIDATION.md](PATIENT_FRONTEND_VALIDATION.md).
O arquivo pode mudar com o próximo build; conferir o hash antes de comparar.

Foi usada uma chave de desenvolvimento criada localmente, ignorada pelo Git.
**Não desinstalar o aplicativo que contém os dados do S21+ para instalar este APK.**
Para atualizar aquele aparelho, o responsável deve compilar esta branch com a
assinatura e configuração compatíveis. Para uma primeira revisão, usar aparelho
de teste separado e dados de teste identificados.

Compilação e testes no ambiente Android já configurado:

```powershell
./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug '-Pandroid.builder.sdkDownload=false' --console=plain --continue
```

1. Abrir com letras padrão e depois ampliadas; localizar Início, Histórico,
   Relógio, Envios e Ajustes.
   Voltar pelo Android em uma aba secundária deve retornar ao Início. Com Meu
   perfil aberto em Ajustes, Voltar deve fechar a janela primeiro, mantendo Ajustes.
2. Sem relógio, encontrar a ação de conexão. Negar permissão e verificar a ajuda;
   não confundir esse ensaio com conexão física validada.
3. Consultar um dia vazio e um dia com fixtures conhecidas; comparar datas e
   valores com esses registros, sem gerar dados em ambiente REAL.
4. Em Envios, verificar pendência, falha e ausência de pendências. Nenhum desses
   estados sozinho comprova que o profissional recebeu os dados.
5. Editar um perfil de teste: testar vírgula decimal, campo inválido e cancelamento.
   Confirmar preservação do ID. Não usar edição de perfil para trocar de paciente.
   Alterar um campo, cancelar e escolher "Continuar editando"; o rascunho deve
   permanecer. Repetir escolhendo "Sair sem salvar" e conferir o perfil anterior.
6. Adicionar água em um cenário de teste; abrir e cancelar a exclusão. Em respiração,
   iniciar, pausar e salvar. Sair da tela antes de salvar ainda pode perder o tempo
   não salvo; não há promessa de execução em segundo plano.
7. Preparar um cartão e conferir período e informações ausentes. Compartilhamento
   entre aplicativos deve ser validado no Android com destinatário de teste.

As capturas locais em `app/build/patient-ui-evidence/<SHA>/captures/` são
**DEMO**, produzidas por testes de componentes. Não mostram sessão real no S21+,
relógio físico, dados de pacientes nem entrega ao Core.

Também houve execução do APK no emulador isolado `Next2U_Patient_UI_35`, com rede
desligada, permissões negadas e tela pequena/letras ampliadas. As capturas de
emulador ficam nas subpastas `emulator` das evidências por SHA. A imagem Android
35 de tablet foi usada com dimensões reduzidas; não representa aparelho físico
nem teste com VE30. Não usar o AVD de Tablet ACS para reproduzir esse ensaio.

## Dependências para o responsável pelo backend

| Necessidade | Evidência e limite atual |
| --- | --- |
| Publicação do frontend | Conceder escrita à conta `rafaeldepaulafigo-web` ou publicar os commits locais após inspeção. Nenhum acesso alternativo foi criado. |
| Dois erros de lint herdados | Permissão no acesso ao nome Bluetooth em `HBandBleManager.kt:1640`; versão transitiva de Fragment em `MainActivity.kt:24`. Não suprimidos. |
| Identidade e recebimento | **BACKEND CONTRACT REQUIRED**: ID/proveniência por medição, confirmação de recepção e visibilidade para Web/ACS, escopo e consentimento. |
| Primeiro cadastro do paciente | Perfil local pode estar ausente; não gerar `PAT-HBAND-001` nem dados de exemplo para preencher. Confirmar provisionamento/vinculação canônica e atualização compartilhada antes de implementar esse fluxo. |
| Resumo automático | **BACKEND CONTRACT REQUIRED**: distinguir IA remota de regras locais; corrigir período, valores ausentes e minutos ativos; revisar geração automática e consentimento/divulgação. Serviço e gatilho não foram alterados. |
| Virada do dia | `MainViewModel.todayDateString` permanece fixo na criação e é usado para água/respiração. Confirmar atualização de data/fuso antes de garantir o comportamento em sessão que atravessa meia-noite. |
| SDK e dados avançados | Medições, história e proveniência continuam dependendo da validação física e dos contratos; a UI não certifica precisão clínica. |
| Progresso de conexão | O estado interno de conexão em andamento não está exposto à UI como um ciclo completo. Um indicador detalhado depende de contrato observável da integração; não foi simulado por tempo decorrido. |

## RECOMMENDATIONS

Executar as [tarefas para avaliação de uso](PATIENT_USABILITY_TASKS.md) com
pacientes representativos. O roteiro distingue o que pode ser visto no APK vazio
do que exige um cadastro/cenário de teste preparado. Acompanhar compreensão e
conclusão das tarefas, sem atribuir dificuldades
automaticamente à idade. Avaliar apoio de cuidador somente após confirmar identidade,
consentimento e permissões; a função não foi inventada neste frontend.

**REAL:** nenhuma nova capacidade operacional foi confirmada neste trabalho.
Preservação de código e callbacks é um fato de inspeção, não prova suficiente de
contrato e integração REAL. Pesquisa com pacientes é **PROPOSED / CONCEPTUAL**;
não foi realizada. Verificações locais são **SELF_REVIEW_ONLY**, não CI nem revisão
independente. Lint incompleto mantém o candidato em DRAFT; merge permanece humano.
