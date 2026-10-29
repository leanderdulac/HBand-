# Composição preservada sobre PR5 — 25/09/2026

## OBSERVED FACTS

Base GitHub confirmada: PR5 OPEN/DRAFT, HEAD
`e9a80ef386d207a1bc6fe66bef3969eafa84aae5`, base PR4
`9a239d9113bc671624643acc975b3e10042f4a57`. Fonte de preservação:
`c87cce73dc0630d5e986a635bd7b168e9ec6b7ef`, limpa no preflight.

Branch isolada `codex/patient-pr5-preserved-composition`, criada sobre o HEAD
real do PR5. Aplicação da árvore cumulativa c87cce73, seguida de retenção
explícita dos itens do PR5 abaixo. É um novo commit de proposta com pai PR5,
não merge no GitHub, rebase de branches existentes ou autorização de integração.
Os dois históricos originais permanecem preservados. O SHA candidato e os
resultados efetivos serão registrados externamente após o commit, em
`C:/CDev/Next2U-Pilot-2026-09-25-independent-completion/`.

O diff em relação a c87cce73 limita-se a este documento e quatro arquivos:
`app/build.gradle.kts`, `HealthtechRepository.kt`,
`IngestPayloadMapperTest.kt`, `AppDatabaseMigrationsTest.kt`.
Não foram alterados os mecanismos de captura, fila, armazenamento ou transporte
principal da versão c87cce73. Essa relação de conteúdo não transfere
automaticamente checks/revisões antigas ao novo SHA.

## Reconciliação completa dos 26 arquivos alterados pelo PR5

Prefixo padrão: `app/src/main/java/com/example/`; testes em `app/src/test/java/`.
Comparação de origem: PR4 → PR5. A matriz anterior de 18 conflitos era uma
simulação de merge de outro SHA; esta entrega é uma composição explícita,
sem marcadores de conflito nem seleção automática ours/theirs.

| Arquivo | Decisão da composição |
| --- | --- |
| app/build.gradle.kts | Retém literalmente o gate de chave ausente/placeholder do PR5, acrescentado à configuração c87 (SQLCipher/ABIs, Fragment e laboratório isolado). Não provisiona chave nem aprova sua distribuição no APK. |
| HBandHealthSyncApp.kt | Conserva abertura verificada, transação e captura separada da espera HTTP de c87; ingestão e esporte do PR5 já conciliados seletivamente. |
| data/hband/HBandBleManager.kt | Mantém esporte separado de vitais, histórico sem republicação como leitura ao vivo e cancelamento de c87. |
| data/hband/VeepooHistoryReadSettings.kt | Argumentos de leitura do SDK portados do PR5; conserva documentação e limites de evidência locais. |
| data/hband/VeepooHistorySync.kt | Argumentos do PR5 mantidos; preserva snapshots de esporte, capacidade, cancelamento e indicação de sincronização incompleta. |
| data/ingest/IngestApiKey.kt | Mantém validação mais restritiva de c87, sem presumir autorização do servidor. |
| data/ingest/IngestDiagnostics.kt | Mantém projeção explícita carregada, contagens, pausa e origem sanitizada. |
| data/ingest/IngestPayloadMapper.kt | Mantém identidade/tempo duráveis e deduplicação que recupera após falha de gravação. |
| data/ingest/IngestReconciler.kt | Mantém recibos por item com paciente/ID/índice; não aceita 200 vazio, contadores ou posição como confirmação suficiente. |
| data/local/AppDatabase.kt | Mantém SQLCipher obrigatório, abertura verificada e recusa não destrutiva de versões/formato não suportados. |
| data/local/AppDatabaseMigrations.kt | Migração 6→7 preserva IDs do payload; colisão reverte a migração, sem novos IDs para contornar duplicidade. |
| data/local/IngestQueueEntity.kt | Identidade durável e default compatível com migração. |
| data/model/WearableData.kt | Retém campos do PR5 e validação adicional de patient_id nos recibos. |
| data/remote/HealthTechApiService.kt | Idêntico ao PR5: assinaturas existentes de ingestão/batch e Idempotency-Key. Não são novos endpoints inventados nesta entrega. |
| data/remote/RetrofitClient.kt | Conserva snapshot coerente destino/chave, HTTPS, redirects bloqueados e erros sanitizados de c87. |
| data/repository/WearableRepository.kt | Conserva histórico/fila atômicos, pausa401/403 durável, admissão compartilhada, recibos estritos e esporte local; não restaura marcação de sincronizado sem recibo. |
| ui/HomeScreen.kt | Conserva resumo de registros salvos, diagnóstico sanitizado e ausência de ações não homologadas de restauração. |
| ui/MainViewModel.kt | Conserva diagnóstico carregado, injeção de transação/transporte e health sem medição sintética; trocar configuração não libera pausa. |
| ui/components/DailyHealthSummaryCard.kt | Não usa últimos20 de outro dia nem estima duração por contagem; exibe fonte salva e limitações. |
| ui/components/SettingsTab.kt | Mantém projeção sanitizada e recuperação explicitamente indisponível. |
| worker/HBandIngestWorker.kt | Mantém política de retry do PR5 e gate de abertura/transação/transporte de c87. |
| com/healthtech/companion/net/HealthtechRepository.kt | Idêntico ao PR5: BODY reduzido a BASIC e headers sensíveis redigidos. Caminho legado, sem reativar smokeHeart na interface. Não é auditoria completa de logs. |
| com/example/data/hband/VeepooHistoryReadSettingsTest.kt | Idêntico ao PR5; conserva testes de argumentos. |
| com/example/data/ingest/IngestPayloadMapperTest.kt | Reúne testes locais de relógio/dedup com assertions de mensagens401/403 sanitizadas e client_reading_id do PR5. |
| com/example/data/ingest/IngestReconcilerTest.kt | Conserva testes estritos de resposta ambígua, correlação, paciente/IDs e tempo de c87; expectativas permissivas do PR5 não são reintroduzidas. |
| com/example/data/local/AppDatabaseMigrationsTest.kt | Restaura teste do PR5 de versões6→7, em adição às provas locais/nativas de migração existentes. |

Os demais arquivos de c87 foram mantidos integralmente, inclusive seus testes,
DAOs, verificação de chave, inicialização e bloqueio das ações Firestore legadas.
Documentos históricos conservam seus SHAs/limites originais.

## Verificação prevista e limites

Executar suíte JVM, debug/test APK, compilação Kotlin release e lint debug/release
no novo commit. Exercitar a recusa de assembleRelease sem chave como teste
negativo: falha esperada do gate não é build de release aprovado. Nenhuma chave
real será copiada/gerada; nenhuma fila será liberada. A instrução antiga do gate
para fornecer chave não é decisão de provisionamento nem política de credencial.

Não há alteração de versionCode (1), schema (7), assinatura debug ou instalação
no piloto. Um APK debug da composição continua candidato de desenvolvimento;
não representa release apropriada para atualização do aparelho existente.

## RECOMMENDATIONS

Revisar esta composição completa antes da incorporação humana; ela não é
READY FOR HUMAN MERGE. Confirmar configuração/provisionamento, identidade,
contrato e runtime de ingestão, assinatura e procedimento de recuperação antes
do uso no piloto. Testar VE30 físico e os cinco critérios integrados no ambiente
autorizado. Conta do Rafael, contratos/runtime ACS e offline continuam pendentes.

Classificação: candidato **PROPOSED / CONCEPTUAL** com provas locais; ACS **DEMO**;
integração **BACKEND CONTRACT REQUIRED**. Core continua dono central. Nenhuma
mudança de contrato, ID canônico, permissão ou comportamento dos canais Web,
ACS e WhatsApp é criada por esta composição.
