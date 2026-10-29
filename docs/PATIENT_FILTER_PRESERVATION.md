# Preservação de filter_type na fila

## OBSERVED FACTS

Base local: 9c8d6a2ecbecc4bc0bf31705300e59e4f874bcb1, composta sobre PR5.
Core consultado: 75e5e02c839f381069212bb7c7d3a2befa491b83,
saude_responsiva_secure/app/models/schemas.py e docs/openapi/hband-wearable.yaml.
O validador aceita BMO, Wavelet, Butterworth, Raw e Adaptive, com caixa exata.
Ausência usa BMO no Core; null e vazio são aceitos e mantidos pelo modelo.
O YAML enum não representa sozinho esses últimos casos; o validador atual e
o OpenAPI consultado anteriormente são evidências complementares, não um contrato novo.

A normalização legada descartava filter_type explícito. No formato plano,
filtros incompatíveis chegavam ao lote, causando rejeição também dos vizinhos
no transporte sintético. Três testes novos falharam antes da correção.

Mudança limitada a preservar o campo explícito na normalização e verificar sua
compatibilidade antes dos lotes. Linha incompatível fica FAILED localmente com
orientação fixa. Payload original, origem, IDs, data de criação e identidade
de idempotência não são reescritos. Pausa de autorização existente prevalece.
Retry sem corrigir a incompatibilidade mantém a linha local; não inventa filtro.
Ausência, null e vazio permanecem distintos no transporte.

WearableFilterPreservationTest cobre normalização plana/legada, matriz com
vizinhos válidos, reabertura de Room SQLite, retry, novo vizinho e pausa 403.
Há transporte sintético com rejeição do lote por filtro incompatível, sem rede.
Não é um validador completo de ingestão nem teste do processamento de sinal.

## RECOMMENDATIONS / limites

Candidato PROPOSED / CONCEPTUAL, evidência local DEMO. Conferir SHA final,
resultados e revisão independente na entrega externa antes de qualquer integração.
Nenhuma evidência de SQLCipher nativo, morte do processo, atualização Android,
VE30 físico ou deduplicação/persistência no Core decorre desses testes.

App Paciente preserva filtro declarado; Web Profissional, Tablet ACS e
WhatsApp/SM Click não recebem alterações de entidades, IDs, permissões ou APIs.
ACS REAL continua BACKEND CONTRACT REQUIRED. Sem autenticação nova, migração,
pausa global inicial da fila ou primeira accepted isolada. Distribuição, assinatura,
revisão cumulativa sobre PR5 e integração continuam decisões humanas.
