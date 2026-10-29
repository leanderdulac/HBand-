# Recuperação de leituras locais: histórico, perfil e diário

## OBSERVED FACTS

No baseline `087af3b42066524412b4018adc4408a075e7e2ff`, a observação do histórico
não tratava falha da fonte. `LocalReadFailureTest` reproduziu uma exceção sintética
escapando para o escopo da apresentação. Perfil e totais do diário também eram
observados diretamente por stateIn, e a leitura inicial do perfil tinha uma
corrotina separada sem recuperação de falha.

O candidato introduz estados explícitos Loading, Failed e Ready para essas fontes.
Ready preserva exatamente o valor emitido; não substitui erro por lista vazia ou
zero. Um perfil ausente confirmado é Ready(null); o null transitório dos totais
continua carregamento, inclusive na mudança do dia local. Ao deixar a última
observação, o replay é descartado. Cancelamento permanece cancelamento. Falha
persistente não gera loop; ação explícita ou retomada de observação inicia leitura.

Histórico, resumo diário e sono mostram erro com releitura. O CSV exige histórico
disponível; o cartão combinado exige também os dois totais. As preparações antigas
já concluídas não são revogadas e arquivos exportados não são removidos. A falha do
total de água abandona a confirmação de exclusão aberta. A falha do total de
respiração muda somente sua apresentação, preservando exercício/rascunho e recibos.
Novas tentativas de leitura não salvam nem reiniciam exercícios.

O perfil conserva a última leitura apenas como baseline do editor durante falha ou
carregamento; campos e salvamento ficam indisponíveis. O rascunho e seu vínculo de
identidade são preservados. A guarda antes da escrita exige perfil novamente lido
com os mesmos id/patientId. Perfil ausente ou identificação diferente não autoriza
salvar o rascunho anterior. A inicialização aguarda a primeira leitura confirmada
para informar o ID ao gerenciador existente. Conexão manual e spot-check aguardam
leitura concluída, sem tratar erro como ausência de perfil. Comportamento legado de
perfil ausente confirmado e ID previamente mantido pelo gerenciador não é redefinido.

## Verificação

Testes sintéticos cobrem erro inicial/após valor, falha persistente, releitura,
zero/ausência confirmados, transição do diário, cancelamento, ciclo de observadores,
startup do perfil, guarda de identidade, exportações, confirmação de exclusão,
rascunho de perfil durante restauração, clique duplicado e exercício em andamento.
Não instanciam uma sessão operacional de MainViewModel nem danificam banco real.
Resultados e composição exatos constam na PR #6 e na evidência externa
`C:/CDev/Next2U-Pilot-2026-09-28-local-read-recovery/`.

## RECOMMENDATIONS e limites

Exigir CI e revisão independente da composição PR6 → PR5 vigente; merge humano.
Testes locais/Compose são DEMO de validação, não publicação ou aceite clínico REAL.
Não houve instalação no M8, comunicação a contatos, envio de fila histórica,
mudança de schema, migração, API, credencial, permissão ou interpretação de valores
clínicos. Esta recuperação não repara corrupção do banco. Rascunhos em memória
não são backup; morte do processo e autorização central conservam seus limites.

Serviços BLE, reconexão automática, workers e ingestões já em andamento não são
revogados por falha da apresentação. Guardas de leitura não constituem um contrato
de autorização/consentimento. Web profissional, Tablet ACS e WhatsApp/SM Click não
recebem comportamento ou contrato novo. A primeira leitura VE30/recibo Core, ACS
offline durável e aceite dos 300 pacientes permanecem sujeitos às evidências e
contratos confirmados (BACKEND CONTRACT REQUIRED onde pendentes).
