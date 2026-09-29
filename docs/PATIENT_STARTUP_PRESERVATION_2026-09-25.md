# Abertura do armazenamento e orientação ao paciente

## OBSERVED FACTS

Base local limpa `b2460187597227ea910e95b098e461ea9f7f6911`; branch isolada
`codex/patient-startup-preservation`. Continuação autorizada por Rafael.
GitHub: PR5 OPEN/DRAFT no `e9a80ef386d207a1bc6fe66bef3969eafa84aae5`, base
`9a239d9113bc671624643acc975b3e10042f4a57`; main
`f35d12b26c5a2305004271c2a05068782a1c9fc8`. Conta sem escrita.
Core14 OPEN fora de DRAFT no `90c3a1d334834f5d7620ee1c7b4e80f938c44154`;
publicação e contrato aplicável ao ambiente continuam sem comprovação.

O Room.build anterior era lazy: construir o objeto não comprovava abertura,
chave ou migração. O aplicativo iniciava o gerenciador BLE, UI e workers sem
uma fronteira comum que permitisse mostrar a falha e manter consumidores parados.

### Mudança

- Application executa abertura real do banco no dispatcher IO, antes de construir
  o gerenciador/coletor ou programar novos trabalhos. Usa o builder/migração
  existentes; SELECT1 confirma abertura, não integridade completa.
- O objeto do banco só é publicado pelo caminho de verificação depois de abrir.
  Em erro, o objeto é fechado e não fica como singleton utilizável; arquivos não
  são excluídos, substituídos ou recuperados automaticamente.
- Gate por processo com OPENING/READY/UNAVAILABLE. Falhas de abertura, chave,
  schema e link da biblioteca nativa viram estado de indisponibilidade sem dados
  privados. Cancelamento mantém consumidores parados e é propagado.
- Enquanto abre, Activity mostra espera. Se falha, mostra orientação para preservar
  instalação/dados e procurar a equipe. Não cria MainViewModel, não carrega Home,
  não solicita permissões nem oferece limpar/reinstalar/trocar chave/retry.
- Ingestão e backup Firestore previamente agendados aguardam o gate; falha retorna
  Result.failure, sem entrar em seus transportes ou provocar retry imediato.
  WorkManager pode voltar a agendar seu trabalho periódico conforme regras próprias;
  cada execução continua passando pelo mesmo gate, sem liberar dados.
- Serviço BLE iniciado/restaurado sem manager pronto termina sem reconectar ou
  manter foreground. Boot/package replaced antecipados não acessam lateinit;
  Application retoma a sessão persistida após READY conforme preferência existente.
- Pedido STOP durante OPENING é conservado em memória, aplicado na construção BLE
  e impede retomada automática. Não apaga pareamento nem muda preferências; conexão
  manual posterior continua possível. É intenção no processo, não estado persistente.
- Pedido de permissões tem marcador rememberSaveable: restauração durante OPENING
  ainda permite o pedido após READY; restauração depois de iniciado não repete.

Banco continua7, formato da chave e migrações anteriores permanecem. Sem endpoint,
autorização, transporte, contrato, segredo ou SDK novo. Fila, atomicidade, IDs e
pausa de autorização são mantidos. Nada foi instalado ou enviado ao backend.

### Verificação e limites

Testes cobrem espera de consumidores, uma única tentativa, concorrência, erro
de chave/native e cancelamento; Room/SQLite sintético abre de verdade e conserva
registro em schema não suportado; workers falham antes de acessar dependências;
serviço restaurado termina sem foreground; STOP atravessa OPENING→READY sem
conexão automática e ainda permite conexão manual. UI cobre conteúdo condicional,
permissões na restauração e fonte ampliada em celular320dp e tablet960dp.

Não é ensaio Keystore/SQLCipher físico, morte de processo, atualização instalada
ou boot/FGS real. SELECT1 não é verificação de todas as tabelas, corrupção ou fsync.
Não recupera chave perdida, arquivo legado nem migração desconhecida. Leituras
enquanto o armazenamento não abre não são capturadas pelo aplicativo; nada aqui
promete monitoramento contínuo ou recuperação dessas amostras.

A fronteira de falha cobre abertura do armazenamento. Erros posteriores de SDK,
programação/configuração ou falhas de disco após READY não são convertidos em
"banco corrompido" nem tratados por esta tela; preservam seus caminhos existentes.
Não há bloqueio distribuído, retry automático de recuperação ou fluxo novo de backup.

## RECOMMENDATIONS

Revisão independente e checks no SHA final registrados no pacote externo
`C:/CDev/Next2U-Pilot-2026-09-25-startup-preservation/`; autorrevisão
SELF_REVIEW_ONLY. Incorporar humanamente e revalidar a nova composição antes
de laboratório físico/atualização. Não usar tela de orientação como prova de
integridade, dados salvos recuperáveis ou autorização para liberar fila.

Paciente permanece candidato local PROPOSED / CONCEPTUAL quanto à operação
integrada. ACS continua DEMO e depende de contratos confirmados de autenticação,
cadastro/visitas/device/offline. Web e WhatsApp/SM Click não mudam. Backend
implantado e aceites por canal continuam BACKEND CONTRACT REQUIRED no alcance
pendente; nenhum contrato ou resultado REAL foi inferido desta proteção.
