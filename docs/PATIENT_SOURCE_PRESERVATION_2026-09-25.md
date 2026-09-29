# Preservação da origem explícita em payload legado

## OBSERVED FACTS

Base 7707a2b9d00cedcd48a79029bf2df0800b7fcc06, checkout isolado
C:/CDev/Next2U-Patient-Source-Preservation, branch codex/patient-source-preservation.
PR 5 GitHub OPEN/DRAFT em e9a80ef386d207a1bc6fe66bef3969eafa84aae5,
base 9a239d9113bc671624643acc975b3e10042f4a57. Nenhum merge ou push.

O normalizador preservava ingest_source em payload flat, mas descartava o campo
top-level explícito quando havia metrics. Reprodutor sobre a base mais três testes
novos falhou: o corpo enviado após uma resposta perdida já não tinha a origem
armazenada. Transporte sintético e Room em memória/Robolectric, sem rede real.

Correção copia exatamente o valor explícito para o corpo normalizado, inclusive
null/vazio/valor inválido. Não preenche quando ausente, não converte tipo nem
deduz origem a partir de is_real_sensor_data. Não altera a captura de novas
telemetrias, schema, payload persistido, IDs, chave do lote, autorização ou recibos.

Contrato Core CONFIRMED em 2c075af5d97d0ad622bf66f834bb096a2e022926:
docs/contracts/WEARABLE_INGEST_IDEMPOTENCY.md e docs/openapi/hband-wearable.yaml.
Validador permite companion_manual, ble_sim, ble_hband, http; null/vazio são
normalizados no Core para companion_manual. Valor inválido pode provocar 422
integral. Preservar a declaração não equivale a validá-la ou reclassificá-la.

Três novos testes verificam: preservação explícita em flat/legado; ausência
mantida apesar do marcador de sensor; lote de quatro origens explícitas após
resposta perdida/retry, corpo e chave repetidos, confirmação correlacionada e
payload/IDs/createdAt locais conservados. Recibos são simulados no mesmo candidato.
Não atestam perda de processo, SQLCipher nativo, replay entre versões ou Core real.

## RECOMMENDATIONS / limites

First write wins e cache do Core continuam soberanos: um registro já recebido
sem origem pode estar gravado como companion_manual. Este ajuste não corrige
histórico remoto nem troca IDs/chaves para contornar deduplicação. Após upgrade,
o corpo preparado pode passar a incluir a origem que a versão anterior omitia;
um duplicate continua referindo-se ao primeiro registro persistido.

Ausência de origem em telemetryToJson continua lacuna distinta: o modelo não
declara aqui origem durável suficiente para inferência. Campo explícito inválido
legado deixa de ser silenciosamente apagado e pode causar 422; filtro completo
ou guard de enum é outro recorte, não parte desta preservação. Divergência entre
enum do YAML/validador e OpenAPI publicado segue documentada na entrega anterior.

Impacto dos quatro canais: preserva informação declarada pelo paciente no caminho
até Core, sem mudar IDs/donos; não altera Web, ACS ou WhatsApp/SMClick. Contratos
ACS, hardware VE30, login autenticado e gate da primeira leitura accepted pendem.
Classificação: candidato PROPOSED / CONCEPTUAL; ensaios sintéticos/DEMO.
Sem claim REAL, prontidão do piloto, revisão cumulativa ou autorização de merge.
Upgrade nativo comprovado continua somente 5c80→466d, não este candidato.

Comandos, resultados por SHA e revisão independente ficam em
C:/CDev/Next2U-Pilot-2026-09-25-source-preservation/. Nenhum pacote congelado alterado.
