# Contenção de cópia e recuperação em nuvem sem contrato validado

## OBSERVED FACTS

Base76889fd4aaf71be4f08ef913dde1b547dcb1c6aa, limpa; worktree isolada
C:/CDev/Next2U-Patient-Cloud-Recovery-Guard, branch codex/patient-cloud-recovery-guard.
Rafael autorizou desenvolvimento/correções de preservação e recuperação; não
criar contratos nem promover DEMO a REAL. Skill next2u-engineering e governança
compartilhada de evidência, autorização e recuperação aplicadas (ADRs004/010/012/013).

GitHub revalidado: paciente mainf35d12b26c5a2305004271c2a05068782a1c9fc8,
PR5 DRAFT e9a80ef386d207a1bc6fe66bef3969eafa84aae5 sobre9a239d9113bc671624643acc975b3e10042f4a57;
pull=true/push=false. Core14 OPEN fora de DRAFT em90c3a1d334834f5d7620ee1c7b4e80f938c44154,
base51478b6413e336145cca21687b96daf9627b6634, sem prova de implantação.
ACS2/1 DRAFT e dependência conservada. Web54 OPEN no170d6097, login chega ao
formulário Auth0; conta/sessão/logout continuam sem aceite.

### Problema observado no código legado

FirestoreBackupManager escrevia diretamente em health_metrics_backup, usando
deviceId+timestampMillis como documento. Restore lia snapshot da coleção toda,
sem escopo/vínculo validado pelo cliente e sem contrato confirmado entre as
entregas disponíveis. Dados sem campos recebiam defaults clínicos: FC72,
pressão120/80, SpO298, temperatura36.6 e HRV65; instante ausente virava agora.
Cada restore inseria novas linhas locais. A existência do código não comprova
configuração Firebase, regras remotas permissivas, acesso indevido ou execução real.
Não foi consultado nenhum dado ou regra Firestore.

O scheduler também criava backup periódico ao agendar ingestão. O painel de
desenvolvimento oferecia backup/restauração e mensagens de sincronização. Isso
não é evidência de recuperação homologada nem de identidade/escopo autorizados.

### Contenção implementada

- Manager conserva os dois pontos de entrada, retornando erro tipado de contrato
  ausente antes de acessar Context, Room, Firebase ou transporte. Cancelamento
  é propagado. Nem configuração Firebase nem flag local liberam esse caminho.
- Removidos o parser com valores clínicos inventados e a escrita/leitura direta
  desse backup. Não substituídos por parser, endpoint, filtragem ou contrato supostos.
  Código histórico permanece no Git para revisão/decisão futura do responsável.
- UI de desenvolvimento apresenta indisponibilidade e orientação para preservar
  os dados. Remove botões de executar e indicadores de sucesso desse recurso;
  ações legadas do ViewModel só informam indisponibilidade.
- Scheduler deixa de criar trabalhos Firestore; ingestão conserva seus nomes,
  constraints e política. Nenhum trabalho é cancelado globalmente ou fila apagada.
- Classe FirestoreSyncWorker permanece compatível com pedidos já persistidos.
  Ela falha imediatamente pelo bloqueio contratual, sem aguardar banco e sem
  solicitar retry/backoff. Um periódico antigo pode executar novamente no próximo
  intervalo e encontrará o mesmo bloqueio; Result.failure não o cancela definitivamente.

Nenhuma alteração de banco7, migração, chave, IDs, recibos Core, pausa401/403,
captura VE30 ou compartilhamento entre canais. Não apaga/restaura/preenche dado.
O recurso fica indisponível também se algum build antigo tinha Firebase configurado;
é contenção deliberada, não implementação alternativa ou migração da nuvem.

### Evidências e limites

Testes: manager recebe Context inutilizável e ainda retorna somente erro tipado;
arquivo SQLite sintético no nome/caminho padrão mantém bytes, métricas com valores
ausentes e fila/ID/pausa após ambas as chamadas; cancelamento não vira sucesso ou
resultado normal. Worker falha com gate OPENING e READY. UI sem ação de nuvem
e texto legível em celular320dp/fonte2 e tablet960dp/fonte1.6.
Não houve conexão Firebase, ensaio de autorização remota, recuperação real ou
reexecução do laboratório Android anterior; aquela evidência mantém seus SHAs.
Resultados e revisão independente do candidato exato no pacote externo:
C:/CDev/Next2U-Pilot-2026-09-25-cloud-recovery-guard/.

## RECOMMENDATIONS

Capacidade de backup/recuperação central: BACKEND CONTRACT REQUIRED. Leandro deve
confirmar dono/serviço, identidade e escopo do conjunto, autorização server-side,
proveniência, campos e ausência, seleção do snapshot, deduplicação/conflitos,
atomicidade/rollback, retenção e aceite de restauração sem sobrepor registros.
Não basta configurar google-services.json ou preencher patientId no cliente.
Uma futura implementação precisa verificar armazenamento pronto antes de gravar,
sem reintroduzir os defaults ou ativar o código antigo por flag de conveniência.

ACS continua DEMO e depende de contratos próprios. Web/WhatsApp não mudam. Este
candidato exige revisão independente e integração humana; não é CI, PR pronto ou
piloto homologado. Não liberar fila acumulada; preservar instalação com dados.
