# Confirmação do tempo de respiração — 27/09/2026

## OBSERVED FACTS

Baseline `f139ed6a809e9a08d13cff481383bdfbafc2a023`. Teste com o card produtivo
reproduziu perda do rascunho: seis segundos viravam zero no clique antes de qualquer
confirmação. Callback sintético sem banco; não é ensaio operacional do ViewModel.

O card agora conserva segundos e pausa o contador até resultado correlacionado por
token e duração. A escrita continua no escopo do MainViewModel. Estado transitório
com replay em memória mantém confirmação entre abas/recriação; token e rascunho
usam rememberSaveable/SaveableStateHolder existentes. Sucesso da operação chega
antes do aviso, sem confundir exceção do aviso com falha da escrita. Resultado
antigo não limpa outra duração/tentativa. Cliques enquanto salva são bloqueados;
controller marca pendente antes do launch e rejeita tokens já usados nesta instância.

Falha/cancelamento/ausência de recibo deixa resultado não confirmado. Não há retry
automático. Retry manual usa novo token somente após diálogo informando possível
duplicação e pedindo conferir total. Encerrar sem salvar novamente exige confirmação
e descarta apenas rascunho; não remove eventual linha já gravada. Callback/completion
atrasado não sobrescreve confirmação. Cancelamento antes de iniciar também termina
como não confirmado, em vez de manter um indicador de salvamento indefinidamente.

Cobertura local: controller com transporte de escrita sintético; helper produtivo
com Room em memória; UI produtiva com recibos simulados e restauração de estado.
Checks e captura focal vinculados ao SHA na entrega. Nenhum MainViewModel operacional,
backend, BLE, banco real, emulador ou aparelho iniciado.

## RECOMMENDATIONS / limites

LOCAL/DEMO; candidato PROPOSED/CONCEPTUAL para incorporação, não disponibilidade
REAL ou aceite do piloto. Sem nova garantia central: BACKEND CONTRACT REQUIRED.
Web, ACS e SM Click não recebem alteração. Dados/DAOs/schema/IDs/timestamps,
permissões, filas e transportes existentes preservados. Token é correlação efêmera
de UI; não é ID de registro, idempotency key persistente ou autoridade clínica.

Após recriar o processo, só se recupera o rascunho efetivamente salvo pelo Android.
Token restaurado sem recibo é incerto e nunca dispara escrita. Não há diário
transacional contra morte abrupta, restauração de banco ou deduplicação durável.
Nova tentativa manual pode duplicar uma gravação cujo resultado foi perdido;
conferência do total agregado não prova identidade da linha. Esta limitação é
explícita na UI. Total é assíncrono e não substitui confirmação da tentativa.
PR segue DRAFT sem presumir CI, merge, distribuição ou instalação.
