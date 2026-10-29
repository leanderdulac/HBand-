# Abertura produtiva exige SQLCipher

## OBSERVED FACTS

Base local cumulativa limpa: `71aa75ad7b4311779146cb542b4969e869efd649`.
PR5 confirmado no GitHub em 25/09/2026, DRAFT:
`e9a80ef386d207a1bc6fe66bef3969eafa84aae5`, base
`9a239d9113bc671624643acc975b3e10042f4a57`. Sem escrita no remoto.
Branch isolada: `codex/patient-cipher-required`.

O carregador nativo já lança erro quando SQLCipher falta no Android normal.
Entretanto, reconhece Robolectric pelo fingerprint e retorna `isLoaded=false`.
`AppDatabase.buildDatabase` permitia então usar o helper SQLite simples do Room,
inclusive pelas fábricas produtivas `getDatabase` e `openVerified`.
Esse limite estava explicitamente pendente nos incrementos de preservação.
Não foi observado vazamento ou banco sem criptografia no aparelho do piloto.

As fábricas produtivas passam a exigir biblioteca carregada antes de consultar
arquivo/chave ou construir Room e sempre configuram `SupportOpenHelperFactory`.
Ausência interrompe abertura; o gate existente mantém armazenamento indisponível
e impede os consumidores de iniciar. Não há reparo, exclusão ou recriação.
Testes de schema/SQLite continuam com `schemaPreservingBuilder` explicitamente;
não precisam de fallback na fábrica produtiva.

Quatro regressões exercitam fábrica lazy, abertura verificada, preservação byte a
byte de arquivo opaco/sidecars sintéticos e gate sem início dos consumidores.
Robolectric sem biblioteca Android é a condição controlada da falha; não comprova
SQLCipher nativo nem preservação do aparelho físico. Evidência antes/depois,
checks do candidato e revisão independente ficam no pacote externo
`C:/CDev/Next2U-Pilot-2026-09-25-cipher-required/`.

## RECOMMENDATIONS / limites

Candidato local, sem homologação integrada. Nenhuma mudança de schema, chave,
SDK, permissões, rede, contrato ou recibos. Patient continua origem da captura;
Core é dono central. ACS, Web e WhatsApp não mudam; vínculos e sincronização
dependem de contratos/ambiente. Não instalar sobre aparelho com dados nem enviar
fila acumulada por causa destes checks. Conta de login continua pendente.

O delta deve ser preservado na futura composição humana com PR5. O candidato
continua seletivo e não resolve os conflitos integrais. Novo HEAD exige checks
e revisão aplicáveis; merge e decisão de release permanecem humanos.
