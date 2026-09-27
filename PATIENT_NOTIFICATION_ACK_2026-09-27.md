# Confirmação do aviso efetivamente exibido

## OBSERVED FACTS

Baseline `379129ef7ff4d1d95e5a764bcbde21f84a338589`, PR6 OPEN/DRAFT sobre
PR5 `e9a80ef386d207a1bc6fe66bef3969eafa84aae5`, confirmados no GitHub.
Clone isolado; arquivos/entregas/dados anteriores preservados.

Home aguardava showSnackbar e depois chamava dismissNotification sem identificar
o aviso concluído. O ViewModel apagava incondicionalmente o estado atual. Se
outra mensagem chegasse antes dessa conclusão antiga, ela seria apagada.
UiNotification também usava hora em milissegundos como identidade; mensagens
iguais no mesmo tick tornam-se iguais para StateFlow e para a chave do efeito.

Reprodução: duas falhas locais com expressões do estado/limpeza antigas extraídas
e tick1234 fixo para representar duas publicações no mesmo milissegundo. Não é
execução do MainViewModel, rede ou um teste probabilístico do relógio físico.
Primeiro caso força a sequência publicar A, publicar erro B, concluir A; segundo
publica mensagens iguais no mesmo tick. A fixture final usa o helper produtivo,
não é idêntica à baseline.

PatientNotificationState conserva a política de mostrar a última mensagem. Cada
publicação recebe identidade volátil distinta por contador atômico; esse número
não é timestamp, ID de paciente/leitura nem autoridade de ordem de domínio.
O fechamento usa compareAndSet do aviso exibido: conclusão antiga não apaga um
aviso diferente. O efeito existente da Home foi extraído mantendo texto, ação
Fechar e duração Long; passa o objeto exibido ao fechamento. Cancelamento do
efeito não confirma aviso. O armazenamento segue privado ao ViewModel.

Quatro casos de estado cobrem fechamento antigo, repetição, fechamento atual/
repetido e rajada de10000 identidades. Dois casos Compose usam efeito e
SnackbarHost produtivos para substituir aviso em exibição e fechar o novo,
incluindo textos/flags idênticos. Não são startup/ciclo completo de Activity.

## Alcance e RECOMMENDATIONS

Sem fila nova de mensagens ou promessa de mostrar todos os avisos. Um aviso
novo ainda pode substituir o anterior, conforme política já existente. A
mudança protege a confirmação contra evento diferente, não cria prioridade
clínica, persistência de alertas ou arbitragem temporal entre produtores.
Contador e avisos vivem em memória; não persistem entre processos.

MainViewModel: apenas armazenamento/publicação/fechamento dos avisos. Agendamento,
checkHealth, runQueueAction, tratamento de falhas, envios, workers, dados,
ID/payload, DAO/schema e transporte permanecem intactos. Não duplica a leitura
isolada atribuída a Leandro. Paciente/VE30: feedback de interface. Web Profissional,
Tablet ACS, Core e WhatsApp/SM Click: sem contratos, entidades, permissões ou
sincronização novos. IDs canônicos e fronteiras preservados.

Evidência **LOCAL/DEMO**, candidato **PROPOSED / CONCEPTUAL** para incorporação,
sem nova capacidade **REAL** atestada. Contratos centrais pendentes continuam
**BACKEND CONTRACT REQUIRED**. Manter gates humanos de integração/distribuição;
CI ausente mantém DRAFT. Não houve mensagem a terceiros, leitura real, mudança
de configuração ou instalação de APK. Evidências/SHA final/patch/bundle/pareceres
em `C:/CDev/Next2U-Pilot-2026-09-27-notification-ack`.
