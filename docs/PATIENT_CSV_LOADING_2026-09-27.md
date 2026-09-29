# CSV — consulta pendente não representa zero registros

## OBSERVED FACTS

Base63258c901133e8f1981304f8fe0ca698ed61164e, PR6 sobre
PR5e9a80ef386d207a1bc6fe66bef3969eafa84aae5. O cartão de imagem já usava o
estado nullable de carregamento. CSV ainda recebia allSensorMetrics, com
emptyList inicial: mostrava0registros e permitia prévia apenas do cabeçalho
antes da resposta. Cópia e compartilhamento já estavam visualmente bloqueados
quando a lista era vazia, mas o callback não guardava a disponibilidade.

Reprodução de UI: dois testes, duas falhas. Fixture novo usou a expressão
stateIn original extraída do MainViewModel, emissão sintética suspensa e
CsvExportCard produtivo; não iniciou o ViewModel operacional ou consulta Room
real. Fixture/XML/log estão preservados na entrega externa. O fixture final
usa a política produtiva nullable e amplia asserções; não é byte idêntico ao inicial.

## Correção

Home encaminha ao CSV o mesmo shareSensorMetrics já observado para o cartão,
sem nova consulta/observador adicional. CsvExportCard aceita null e informa
carregamento, sem contagem. Nesse estado não mostra conteúdo de prévia anterior
nem permite abrir prévia, copiar ou compartilhar. Callbacks também recusam
ações sem dados disponíveis; cópia/envio recusam lista vazia confirmada.

Lista vazia confirmada continua mostrando0registros e permite a prévia de
cabeçalho, como antes. Quando chegam dados, a prévia aberta usa a nova resposta;
isso não copia ou compartilha automaticamente. Uma ação já iniciada conserva
seu conteúdo capturado e o cancelamento existente, sem promessa de snapshot
global entre todas as fontes.

Formato CSV, cabeçalho, ordem/campos, escape, valores, arquivo temporário,
FileProvider, Intent e clipboard não mudam. Nenhuma escrita no banco, alteração
de DAO/schema/ID, fila, BLE, transporte ou dependência. Comentário do helper
atualizado para reconhecer seus dois consumidores; comportamento do helper intacto.

Testes cobrem espera sem0, prévia bloqueada, vazio confirmado, callbacks
desabilitados, prévia aberta→pendente→nova resposta, clipboard intacto sem ação
e preservação da cópia explícita de todos os registros pelo teste existente.
Checks/revisão por SHA e resultados efetivos constam da entrega e do PR.

## RECOMMENDATIONS / limites

**LOCAL/DEMO** nos testes, candidato **PROPOSED/CONCEPTUAL**. Não comprova startup
completo, hardware, destinatário operacional ou aceite clínico. Não define
completude, consentimento ou novo contrato CSV; mantém os campos originais.
Falha de consulta permanece sem dados; não há novo modelo de erro/retry.
Histórico/IA e demais consumidores mantêm suas semânticas anteriores.

App Paciente: estado/apresentação da exportação. Web Profissional, Tablet ACS e
WhatsApp/SM Click sem alteração de entidade, contrato, permissão, sincronização
ou offline. Leitura isolada do Leandro e fila histórica inalteradas.
Capacidades centrais pendentes são **BACKEND CONTRACT REQUIRED**; nenhuma nova
capacidade **REAL** inferida. DRAFT enquanto CI ausente; merge/aceite humanos.
