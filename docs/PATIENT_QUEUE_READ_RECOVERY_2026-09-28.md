# Recuperação da leitura da fila local

## OBSERVED FACTS

No baseline `77c3dc160217ab6c7803296db301a4e8e72ad068`, uma exceção do
Flow da fila escapava do compartilhamento da apresentação para o escopo da UI.
O teste `QueueReadFailureTest.a_queue_read_failure_does_not_escape_the_presentation_scope`
falhou no baseline. A execução usou uma fonte sintética que falha, sem corromper
um banco físico ou acessar pacientes.

A apresentação agora compartilha uma consulta protegida entre tela, diagnóstico
e projeções de logs. Uma falha descarta a fotografia antiga, apresenta erro
sanitizado e permite iniciar outra leitura por ação explícita. Não há loop de
tentativas em caso de falha persistente. Cancelamento continua sendo cancelamento;
o fim dos observadores encerra a consulta e invalida a fotografia.

Início e Envios ocultam contagens e ações de envio dependentes da fila indisponível.
Envios também abandona a confirmação de exclusão aberta. A navegação e o diagnóstico
distinguem erro de carregamento e zero confirmado. A nova ação apenas reinicia a
observação: não altera linhas, IDs, recibos, status ou bloqueios de autorização,
não remove dados e não agenda envio. O envio já existente em segundo plano não é
cancelado por uma falha da apresentação.

## Verificação e alcance

Testes cobrem erro inicial e após dados, falha persistente sem loop, recuperação
manual com preservação da linha e pausa de autorização, observadores simultâneos,
cancelamento/retomada, contagens obsoletas, exclusão pendente, botão que apenas relê,
diagnóstico e descrição acessível. Testes usam dados fictícios e Compose/Robolectric.
Resultados, candidato e composição exatos são registrados na PR #6 e na evidência
externa `C:/CDev/Next2U-Pilot-2026-09-28-queue-read-recovery/`.

Classificação: implementação local no candidato do App Paciente, validada com dados
sintéticos (DEMO de verificação); não equivale a implantação/aceite clínico REAL.
Não cobre todas as consultas do aplicativo nem repara corrupção de banco. Não
houve instalação no M8 ou teste físico desta falha.

## RECOMMENDATIONS e dependências

Integrar somente após CI e revisão independente na composição PR6 → PR5 vigente,
com merge humano. Manter o aceite físico de atualização e preservação de registros.
Web profissional, Tablet ACS e WhatsApp/SM Click não mudam; nenhum contrato,
permissão, transporte, schema ou comando de backend é criado. VE30 e recebimento
correlacionado no Core continuam com aceite próprio. Escritas/ACS offline e
comunicação ainda dependem de contratos confirmados (BACKEND CONTRACT REQUIRED).
