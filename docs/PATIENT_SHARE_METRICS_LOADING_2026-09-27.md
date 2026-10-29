# Cartão aguarda a consulta das medições

## OBSERVED FACTS

Baseline7b2f6d9956b8cf8cf9a344585483d8c4a1848ba7, PR6 sobre
PR5e9a80ef386d207a1bc6fe66bef3969eafa84aae5. O estado allSensorMetrics começa
como emptyList antes da resposta Room. Home passava esse valor ao cartão,
cujo botão aguardava somente água e respiração. Se ambos os totais chegassem
primeiro, era possível preparar um cartão indicando zero registros enquanto
a consulta de medições permanecia pendente.

Reprodução: UI produtiva ShareProgressCard com emissão sintética suspensa e
expressão stateIn original extraída do MainViewModel. A asserção de botão
desabilitado falhou. Não foi instanciado o MainViewModel operacional, nem
exercitado banco físico/BLE/backend; fonte do fixture/log/XML preservados na
entrega externa. A reprodução cobre a combinação de estado e UI, não startup completo.

## Correção mínima

Estado nullable dedicado `shareSensorMetrics` observa o fluxo original do
repositório, não o StateFlow que já converte pendência em vazio. null significa
nenhuma resposta; emptyList emitida permanece um resultado válido. O estado
descarta replay ao perder o último observador e aguarda nova resposta ao retomar.
Home passa esse estado somente ao cartão. O botão exige também medições
carregadas; o callback existente continua recusando preparo enquanto desabilitado.

O fluxo allSensorMetrics dos consumidores existentes, inclusive histórico e IA,
não foi alterado. Há uma observação adicional da consulta Room enquanto o estado
do cartão é observado. Sem nova escrita, DAO, entidade, schema, ID, timestamp,
reclassificação de dados ou dependência. Uma lista vazia confirmada e totais0
confirmados continuam permitindo preparar o cartão.

Testes cobrem espera, vazio confirmado, preservação de linhas/IDs, retorno ao
carregamento, suspensão/retomada, observadores concorrentes e tentativa de clique
sem disponibilidade. Checks e revisão independente vinculados ao SHA final na
entrega e no PR; execuções antigas conservam seus próprios SHAs.

## RECOMMENDATIONS / limites

**LOCAL/DEMO** nos testes, candidato **PROPOSED/CONCEPTUAL** para incorporação.
Não é transação atômica entre água/respiração/medições, nem garantia clínica de
completude ou atualização instantânea durante geração assíncrona. Não altera
cartão já preparado. Se consulta não retornar, permanece carregando; não inventa
sucesso nem adiciona política de erro/retry. CSV e outros consumidores ficam fora
deste recorte e mantêm suas semânticas anteriores.

App Paciente: preparo local. Web Profissional, Tablet ACS e WhatsApp/SM Click
sem mudança de contrato, permissão, sincronização, concorrência ou offline.
Não interfere na leitura isolada do Leandro, fila histórica ou tráfego. Capacidades
centrais não confirmadas permanecem **BACKEND CONTRACT REQUIRED**; nenhuma
capacidade **REAL** inferida. CI ausente mantém DRAFT; merge/aceite humanos.
