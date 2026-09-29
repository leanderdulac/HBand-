# Candidato de ingestão e migração local6→7

## OBSERVED FACTS — composição

Continuidade autorizada por Rafael, em worktree separado, base local
`72e65f4658af60c44f350607f74bea2a3c5f0131`. Branch
`codex/patient-ingest-reconciliation`. Origens preservadas: paciente local17ab,
primeiro candidato72e65f4 e HBand PR5
`e9a80ef386d207a1bc6fe66bef3969eafa84aae5` (base
`9a239d9113bc671624643acc975b3e10042f4a57`). GitHub confirmou main paciente
`f35d12b26c5a2305004271c2a05068782a1c9fc8`, conta sem push e PR5 DRAFT.

Referência contratual: Core14
`90c3a1d334834f5d7620ee1c7b4e80f938c44154`, base
`51478b6413e336145cca21687b96daf9627b6634`, documento
`docs/contracts/WEARABLE_INGEST_IDEMPOTENCY.md` marcado CONFIRMED.
O PR saiu de DRAFT durante a rodada, sem mudar esses SHAs. Esse estado não
comprova revisão implantada, alinhamento da versão piloto ou homologação.

Port seletivo de interface HTTP/modelos/migração/identidade do PR5; reconciliador
e encaixe no repository reescritos para preservar as garantias locais. Não é merge
completo do PR5: UI/diagnósticos/configuração de chave e total diário de esporte
continuam pendentes. Não foi alterado SDK, backend, governaça, credencial ou aparelho.

## Alterações e achados tratados

- F1: HTTP200 sem recibo por item não confirma envio. Exigir índice, ID da requisição,
  status accepted/duplicate, paciente e reading_id do resultado. Resposta parcial
  confirma somente o item correlacionado; índice/ID repetido, estranho ou incompatível
  conserva a fila. Contadores e posição do array não substituem correlação.
- F2: preparar chunks de até50 registros por patient_id; identidade ausente não
  recebe paciente default. Fila não muda de paciente para caber no envelope.
- F3: preservados LocalWriteTransaction, pausa persistida401/403, consulta EXISTS,
  retry manual, cancelamento e gate compartilhado entre instâncias. Falha ao
  salvar recibo local interrompe a execução sem segunda escrita de falha nem
  continuação do próximo chunk. Política de até5 tentativas e falha recuperável
  manualmente permanece; FAILED não apaga registros.408/429 mantêm política anterior.
- F4: banco passa6→7 com migração aditiva e default Room compatível. UUID gerado
  somente na inserção/migração; se payload já contém identidade válida, preservá-la.
  Payload e campos antigos não são reescritos. IDs duplicados interrompem migração
  com rollback; não inventar outro ID para ocultar conflito. INSERT ABORT impede
  o índice novo de causar substituição silenciosa de uma leitura antiga.
- F6: requisição deriva deterministicamente do payload e identidade persistidos,
  preservando a medição original. Exigir ISO com offset e calendário válido;
  ausência, conflito ou tempo inválido conserva o registro local. Não usar relógio
  do flush. Alias legado deviceId é materializado como device_id; dois valores
  divergentes bloqueiam envio. Request não é gravado como novo payload: o original
  fica intacto, e retries na mesma versão reproduzem a transformação determinística.
- Erros de ingestão não persistem corpos externos que possam conter segredo;
  o prefixo antigo de pausa401/403 continua reconhecido após atualização.

Todo flush usa batch-ingest, inclusive1 item, sem fallback em404. No Core
inspecionado, duplicate por chave natural retorna o frame antigo; /ingest unitário
pode não ecoar o client ID atual. O envelope batch ecoa o ID da requisição e
permite correlacionar sem afrouxar validação. O ID histórico aninhado pode diferir;
não substitui o ID do envelope. A política conservadora unitária continua testada,
mas o repository não usa esse caminho para o flush.

O hash estável do envelope inclui paciente e IDs na ordem enviada. Não ordenar IDs
ao gerar chave: Core cacheia índices relativos à requisição. ORDER BY createdAt,id
estabiliza a ordem local em empates. Rejeitado, timeout e recibo inconclusivo ficam
preservados; somente confirmação contratual muda para SYNCED.

## Verificação e limites

Testes novos verificam recibos ambíguos/parciais/reordenados, mistura de pacientes,
limite50, resposta perdida e replay, natural duplicate com ID histórico diferente,
timestamp/alias legado, cancelamento, pausa de autorização e erro local após aceite.
Room real com SQLite sintético valida migração6→7, todas as seis tabelas, campos
antigos, schema, índice e reabertura; conflito de IDs reverte a migração inteira.
Fixtures de transação/concorrência anteriores agora emitem recibos sintéticos do
contrato atual e usam batch de1 via adaptador exclusivo de teste. Não convertem
resposta vazia em aceite. Testes novos exercitam o fluxo de lote diretamente.

Suíte, build, lint, SHA efetivo, hashes e revisão técnica independente ficam em
`C:/CDev/Next2U-Pilot-2026-09-24/ingest-reconciliation/`. Executor local/Codex,
sem CI do candidato, usando cache offline existente. Autorrevisão do autor:
**SELF_REVIEW_ONLY**; revisão distinta deve identificar o SHA final.

SQLCipher, processo morto, falha elétrica, atualização do aparelho e backend
implantado não foram exercitados. Migrar banco no teste não é instalar o candidato
com segurança no aparelho que guarda a fila. Conflitos de identidade e arquivos
legados não criptografados exigem recuperação explícita; app pode não abrir.
Não foi criada interface de recuperação. Sem alteração do fallback preexistente
de biblioteca SQLCipher ausente; a auditoria desse caminho permanece delimitada.

## RECOMMENDATIONS — dependências do piloto

F5 continua com Leandro: nomes/unidades/proveniência e ausência de métricas no Core
não estão reconciliados com Android. Não converter hrv_score em RMSSD nem temperatura
em temperatura de pele por renomeação. Confirmar revisão implantada, persistência
durável, paciente/device/vínculos aprovados e ambiente para ensaio com falhas.

Não instalar este APK no aparelho com dados nem liberar fila acumulada antes de
ensaio SQLCipher/upgrade e aceite acompanhado do backend. Atualização para7 muda
o schema: voltar a um APK6 não constitui rollback de dados; pode falhar na abertura.
Revisão independente local não é aprovação de publicação, PR, merge ou deploy.

**PROPOSED / CONCEPTUAL:** candidato local. Não há nova capacidade **REAL** ponta a
ponta demonstrada. **BACKEND CONTRACT REQUIRED:** pendências contratuais e de
integração dos canais. Web/ACS/SM Click não foram modificados; ACS continua **DEMO**
e precisa de sessão/autorização, cadastro/visita/device e política offline próprios.
patient_id/device_id e seus donos centrais permanecem; SYNCED não prova que a equipe
já viu a leitura. Mudança de SHA/base exige revalidar checks/composição antes de
integração humana. Hospedagem/DNS/HTTPS permanecem na frente separada.
