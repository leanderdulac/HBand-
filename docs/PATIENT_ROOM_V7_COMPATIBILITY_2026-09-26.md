# Compatibilidade das duas origens Room7 — candidato local

## OBSERVED FACTS

Base local: `ffe5210e553b810fb0105703543a297d2b7b2719`.
PR5 publicado: `e9a80ef386d207a1bc6fe66bef3969eafa84aae5`, base
`9a239d9113bc671624643acc975b3e10042f4a57`, OPEN/DRAFT no preflight.
Checkout isolado: C:/CDev/N2U-Room-Compat. Entrega desta rodada:
C:/CDev/Next2U-Pilot-2026-09-26-room-compatibility.

O PR5 cria `clientReadingId TEXT NOT NULL` sem default SQL. O candidato local e
a migração6→7 declaram `DEFAULT ''`, ainda com versão7. Os outros cinco modelos
de entidade são idênticos entre esses SHAs. Dois bancos sintéticos, gerados por
Room a partir dessas definições, reproduziram a diferença: o banco PR5 falhou na
abertura com o candidato, e o banco da definição local abriu normalmente.

Erro reproduzido: Room esperava identidade `8c1c10f67ac6804264da2ec717d7588b`,
mas encontrou `5bb2fc8a1c38045eea75fd732f41ea36`. Não foi executado APK do PR5 nem
observado banco instalado no aparelho; a reprodução cobre seus schemas publicados
com Room2.7.0 e SQLite sintético em arquivo, via Robolectric.

## Correção delimitada

A versão passa a8, com caminho explícito6→7→8. A entidade e o default canônico
atual permanecem. Em7→8, somente as duas declarações de fila conhecidas são
aceitas. A forma local já compatível não reescreve tabelas. Na forma publicada,
a migração copia todas as oito colunas para a tabela canônica, substitui a tabela
dentro da transação de upgrade do Room, restaura o índice único e preserva a
marca de AUTOINCREMENT, inclusive numa fila vazia após exclusões anteriores.

Não normaliza payloads, regenera IDs, altera estados/tentativas/horários nem
modifica outras tabelas, chaves ou passphrase. Objetos/colunas/constraints
inesperados da fila são recusados. A reconstrução recusa referência estrangeira
à fila (incluindo nomes com maiúsculas, que o SQLite considera equivalentes) e
colisão de nome temporário. Room continua responsável por validar o
schema final e atualizar sua identidade; nenhuma escrita direta em
`room_master_table`, fallback destrutivo ou recuperação por limpeza foi adicionada.

Testes cobrem as duas origens7, a cadeia6→7→8, histórico/fila completos,
marcadores401/403/SYNCED, reabertura, unicidade, contador acima de MAX(id), fila
vazia e recusa/rollback quando o schema final não é válido. Os testes Android
existentes foram adaptados à versão8 para compilação; isso não equivale à sua
execução nem permite reutilizar os APKs/pins dos laboratórios históricos.

Resultados finais, SHA exato, comandos, logs e revisão independente constam na
nova entrega externa. A primeira tentativa do runner falhou na montagem do comando
Windows antes de executar Gradle; a reprodução válida está separada em
`repro-attempt2.log` e `repro-before.xml`.

## RECOMMENDATIONS e limites

Este candidato é **PROPOSED / CONCEPTUAL**; o ensaio é **LOCAL / DEMO**. Não
comprova SQLCipher nativo, interrupção física durante a migração, assinatura da
instalação real, update Android ou captura VE30. Não modifica contratos Core,
envio isolado, sincronização automática ou reconciliação de replay remoto.

Fixar a versão efetivamente instalada e o par exato de artefatos antes do ensaio
de atualização. Depois de migrar para8, voltar a código que conhece somente7 não
é rollback suportado; ele deve recusar a abertura preservando o banco. Não
instalar/atualizar, desinstalar ou limpar dados como parte desta entrega.

App Paciente conserva paciente/device/clientReadingId, banco e chaves; Web, ACS
e SM Click não recebem mudanças, permissões ou APIs. Capacidades centrais ainda
não confirmadas continuam **BACKEND CONTRACT REQUIRED**. A revisão incremental
não aprova a composição inteira desde PR5, distribuição, merge ou o piloto.
