# Totais locais: carregamento não é zero

## OBSERVED FACTS

Base93ef76f221b1958ebb36a9e9646d8b8f0fd62fe5. A consulta de água emitia0 antes
de receber a resposta Room, inclusive ao trocar de dia. O estado inicial de água
e respiração também era0. A interface e o cartão compartilhável podiam apresentar
esse valor transitório como total confirmado. A revisão do incremento anterior
registrou esse limite; a presente correção trata essa causa local.

Reprodução na classe produtiva com DAO Room cuja emissão foi retardada por gate
de teste:2casos/2falhas, primeiro carregamento e troca de dia após total500.
Mesmo fixture usado na correção, sem inicialização do MainViewModel operacional.

Agora null em memória significa total ainda não carregado. Somente uma resposta
do DAO com SUM vazio é normalizada para0. Água invalida o total ao trocar de dia;
água e respiração começam cada nova observação em null. MainViewModel descarta
replay ao deixar de observar ambos os totais. Respiração continua acumulada.
Nenhuma alteração de entidade, DAO, schema, histórico, ID ou gravação.

HydrationCard informa carregamento, sem número ou percentual inventado; adicionar
água continua disponível. Reset e sua confirmação ficam indisponíveis enquanto
o total não chega, inclusive se o diálogo já estava aberto. BreathingExerciseCard
distingue o tempo salvo desconhecido de0, sem alterar o exercício ou salvamento.

ShareProgressCard espera ambos os totais antes de permitir preparação;0 confirmado
permanece elegível. SharePreparationButton também verifica disponibilidade no
callback e preserva tratamento de falha/cancelamento. O gerador aceita null e
usa Indisponível, inclusive quando argumentos de totais são omitidos; mantém
zero, valores positivos e a recusa visual de negativos preexistente. Não envia
cartão, não muda preview, destinatário, consentimento ou transporte de divulgação.

Testes cobrem fluxo pendente/confirmado, troca de dia, respiração, UI, bloqueio
do callback, confirmação de reset e resumo com valores ausentes/zero/positivos.
Testes de virada de dia anteriores ajustados só para o estado inicial null,
mantendo asserções de consulta, data, IDs, reset e preservação do histórico.
Resultados finais, SHA e revisão distinta na entrega externa.

## RECOMMENDATIONS / limites

Candidato PROPOSED / CONCEPTUAL para incorporação; testes LOCAL/DEMO. Não atesta
capacidade REAL nova, consenso central, proveniência clínica ou autorização de
envio; essas garantias não confirmadas continuam BACKEND CONTRACT REQUIRED.

O bloqueio cobre a disponibilidade dos dois totais locais ao solicitar o cartão.
Não torna todas as fontes uma transação única ou um snapshot clínico autoritativo;
eventos de calendário/Room e geração assíncrona conservam seus limites existentes.
Se a consulta não concluir, permanece carregando: não se inventa dado ou sucesso.
Não foi criado modelo de erro/retry de banco nem reclassificação retroativa.

App Paciente: apresentação/preparação local. Web, ACS e WhatsApp/SM Click sem
alteração de contratos, entidades, permissões, atualização ou offline. Filas,
ingestão e envio isolado/pausa inicial do Leandro não mudam. Sem publicação,
instalação, backend real ou novo ensaio físico; testes locais não são CI.
