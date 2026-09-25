# Preservar leitura fora do intervalo HTTP sem bloquear vizinhos válidos

## OBSERVED FACTS

Base limpa82b395015ddf6d5f7557f75bfd63bd77b59f3f67, checkout isolado
C:/CDev/Next2U-Patient-Batch-Contract-Guard. PR5 revalidado OPEN/DRAFT em
e9a80ef386d207a1bc6fe66bef3969eafa84aae5, base9a239d9113bc671624643acc975b3e10042f4a57.
Composição anterior preservada. GitHub agora informa push=true; isso não autoriza
merge, publicação/distribuição de APK ou liberação de fila.

Resposta encaminhada por Rafael confirma que o Core rejeita integralmente com422
um lote que contenha leitura fora do schema. OpenAPI público1.0.0 observado em
25/09/2026 declara heart_rate numérico com mínimo20 e máximo250, consistente com
WearableTelemetryRequest no Core integrado2c075af5d97d0ad622bf66f834bb096a2e022926.
Não são limites clínicos novos: esta mudança aplica o contrato HTTP existente.

Reprodutor novo sobre a base, com Room local e transporte sintético: fila com
HR72 eHR999. A implementação anterior envia os dois no lote; fixture devolve422
integral, deixando também o válido FAILED. Teste esperado1synced/observado0 falha.
Logs/delta pré-correção preservados na entrega; nenhuma requisição de ingestão real.

## Mudança mínima

Somente o filtro JSON antes da montagem do lote passa a verificar o intervalo
inclusivo20..250 com valor finito e sem truncar decimais. Falha segue o tratamento
local já existente: FAILED para revisão, sem envio, exclusão, alteração de valor,
payload ou clientReadingId. Vizinho válido segue sozinho para o transporte.
Mensagem deixa claro que se trata de limite aceito pela API e preservação local.

Elegibilidade da captura/deduplicação local permanece intacta: leitura999 continua
podendo ser salva, não é descartada para satisfazer o backend. Não tocar mutex,
recebimento, schema, chave, persistência, migração, endpoints, autorização ou filas
existentes. Web, ACS e SMClick não mudam; dados/IDs/donos centrais não são recriados.

Três testes novos: caso misto e retry sem reenviar inválido, limites inclusivos/
frações/não finitos, e conservação da leitura fora do contrato na captura local.
Resultado final/comandos/SHA/revisão em
C:/CDev/Next2U-Pilot-2026-09-25-leandro-response-validation/.

## RECOMMENDATIONS / limites

Teste sintético local, candidato PROPOSED / CONCEPTUAL; não integração REAL.
Esta é pré-validação específica de heart_rate, **não validador completo do schema**.
Outros campos/origem incompatíveis ainda podem provocar422 integral. OpenAPI
publicado não enumera ingest_source, embora o validador do Core liste valores;
registrar separadamente a lacuna documental e a proveniência de origem ausente.
Não inventar origem retroativa, defaults clínicos ou corrigir payloads antigos.

Orientação de Leandro de receber uma leitura nova200accepted antes da fila
histórica é gate operacional. Retry de item/sincronização do app não é prova
isolada: pode selecionar outros pendentes. Não implementar escopo/duração/registro
de liberação por suposição; nenhuma fila liberada ou autorização contornada aqui.

Ensaios nativos históricos de upgrade5c80→466d conservam seus SHAs; não atribuir
essa evidência ao novo código. Nenhum APK instalado, teste nativo repetido ou
atualização do piloto. Revisão independente exigida; SELF_REVIEW_ONLY não é aceite.
Merge, integração, distribuição e uso em aparelhos permanecem humanos.
