# Next2U paciente — tarefas para avaliar o uso

## OBSERVED FACTS — alcance atual

O APK está navegável no emulador isolado do paciente. Foram feitos ensaios
técnicos de navegação, texto ampliado e estados vazios, além de testes de
componentes com dados artificiais (**DEMO**). Não houve sessões com pacientes,
validação de compreensão nem avaliação de anúncios falados com TalkBack.

O emulador não tem paciente provisionado ou relógio conectado. Ele permite
avaliar localização das abas e compreensão das mensagens vazias. Edição de
perfil preenchido, medições registradas e diferentes estados de envio exigem
um cenário de teste apropriado; não devem ser apresentados como disponíveis
nessa instalação vazia. Primeiro acesso e vínculo paciente/relógio continuam
**BACKEND CONTRACT REQUIRED**. Nenhuma nova integração **REAL** foi comprovada.

## RECOMMENDATIONS — roteiro proposto

**PROPOSED / CONCEPTUAL**, ainda não executado. Usar participantes com diferentes
idades, níveis de leitura e familiaridade com celular. Incluir celulares simples,
letras ampliadas e o leitor de tela habitual. Não presumir dificuldade pela idade.

### Antes de começar

- Explicar: "Estamos avaliando o aplicativo. Não é uma prova para você. Se algo
  ficar difícil, isso nos ajuda a melhorar a tela. Você pode parar quando quiser."
- Explicar quais dados e situações são de teste. Usar cadastro de teste autorizado,
  quando a tarefa exigir; não criar identificação de paciente para preencher a tela.
- Perguntar como a pessoa prefere usar o celular: tamanho das letras, leitor de
  tela e apoio de alguém de sua confiança. Apoio não concede acesso ao cadastro
  nem substitui as permissões de cuidador que ainda precisam ser confirmadas.
- Apresentar uma tarefa por vez. Deixar a pessoa tentar; não apontar o botão
  antes de observar sua primeira escolha. Não interpretar dificuldade como erro
  da pessoa. Registrar a ajuda oferecida.

### Tarefas

| Tarefa apresentada à pessoa | Cenário necessário | O que observar |
| --- | --- | --- |
| "Você quer saber se o relógio está ligado ao aplicativo. Mostre onde olharia." | APK vazio, relógio desconectado | Encontra o estado? Distingue relógio desconectado de falta de internet? |
| "Você quer conectar seu relógio. Mostre como começaria." | APK vazio; encerrar antes de buscar um aparelho real | Encontra a ação? Entende a explicação das permissões? Evita escolher um relógio sem confirmar a identificação? |
| "Você quer consultar os registros de ontem. Mostre onde procuraria." | APK vazio para navegação; registros de teste conhecidos para interpretar valores | Localiza Histórico, troca o dia e explica a data? Entende que dia sem registro não significa resultado normal ou zero? |
| "Agora volte para a primeira tela do aplicativo." | Qualquer aba secundária | Usa Início ou Voltar? O resultado corresponde ao que esperava? Com menu aberto, compreende que ele fecha primeiro? |
| "O aplicativo mostra que alguns dados aguardam envio. O que isso quer dizer? O que você faria?" | Cenário de componente DEMO ou base de teste preparada pelo responsável; não disponível no APK vazio | Distingue espera, falha e tentativa? Evita concluir que a equipe recebeu os dados apenas pela fila local? |
| "Você abriu seu perfil, mas ele não apareceu. Explique o que esta mensagem está dizendo." | APK vazio, Meu perfil | Compreende a indisponibilidade e sabe a quem pedir ajuda se persistir? Evita ficar repetindo a mesma tentativa sem orientação? |
| "Corrija o nome deste cadastro de teste. Antes de salvar, desista de sair e continue a correção." | Perfil de teste provisionado; pendente no emulador atual | Distingue Continuar editando de Sair sem salvar? Retoma o texto? Entende que Cancelar não salva? |
| "Consulte este registro e diga de quando ele é e qual valor aparece." | Registros de teste com data/valor conhecidos; leitor de tela quando habitual | Consegue associar data e valor? Entende as unidades ou precisa de explicação? Não testar interpretação clínica. |
| "Esta tela mostra um exercício pausado. Mostre como você continuaria e como guardaria o tempo." | Cenário de interface com contador de teste pausado, sem pedir para a pessoa executar o exercício respiratório | Distingue continuar de salvar? Entende que o tempo não avança enquanto está pausado? Encontra as duas ações com letras ampliadas? |

### Registro por tarefa

Anotar: código da sessão, cenário/versão do APK, aparelho, tamanho de letras,
uso de leitor de tela, tarefa e resultado. Evitar nomes ou dados de saúde nas
anotações de interface. Não solicitar gravação de voz ou imagem por padrão.

- Resultado: **concluiu sem ajuda / com uma pista / com ajuda direta / não concluiu**.
- Primeira ação e pontos em que hesitou ou voltou atrás.
- Toques que não levaram ao resultado esperado e o que a pessoa esperava ver.
- Explicação nas palavras da pessoa, principalmente para dados ausentes e envio.
- Ajuda recebida e mudança que poderia tornar a tarefa mais simples.

Tempo pode ser anotado para comparar versões, sem transformá-lo em nota da pessoa.
Ao final, perguntar: "Qual parte ficou mais confusa?" e "O que você mudaria?"

### Decisão depois das sessões

Separar três resultados: problema de compreensão/apresentação; comportamento
do aplicativo; dependência de integração ou contrato. Priorizar erros que fazem
a pessoa interpretar dados ausentes como normais, confundir recebimento, perder
uma edição ou não encontrar o próximo passo. Uma sessão bem-sucedida não comprova
precisão clínica, integração VE30, recepção pelo Core ou prontidão para produção.
