# Falhas das ações do diário local — 27/09/2026

## OBSERVED FACTS

Baseline `4933c3c48854981b6eb58c334f6880b5e5d1c1c5`. Adicionar água, apagar água
do dia e salvar respiração propagavam falhas de armazenamento para o launch do
ViewModel. Três casos reproduzidos em laboratório com corpos originais extraídos
sem os wrappers launch; falhas sintéticas dos DAOs, sem inicialização operacional
do MainViewModel. Isso demonstra propagação, não execução de crash em aparelho.

PatientWellnessActions mantém as operações e mensagens de sucesso, trata Exception
da escrita e emite aviso sanitizado. Sucesso somente após retorno da operação.
Cancelamento é relançado, Error não é absorvido, não há retry automático nem
afirmação de rollback. Callback de aviso fica fora do tratamento de gravação.
MainViewModel mantém viewModelScope.launch, duração inválida ignorada e coletores.

Testes usam o helper produtivo, LocalWellnessRecords e Room em memória: sucesso,
preservação de registros anteriores, três falhas antes da escrita, cancelamento ao fechar o banco,
falha simulada após commit sem repetição, cancelamento, duração inválida, Error
e exceção do callback de notificação. Não executam backend, BLE, app operacional,
armazenamento real, emulador ou aparelho. Evidência LOCAL/DEMO no SHA dos checks.

## RECOMMENDATIONS / limites

Candidato PROPOSED/CONCEPTUAL para incorporação. Não representa disponibilidade
REAL do piloto, CI ou autorização de merge/distribuição. PR permanece DRAFT.
Sem nova gravação central ou contrato: BACKEND CONTRACT REQUIRED para garantias
centrais pendentes. Web, ACS e SM Click não recebem mudança. App Paciente conserva
DAOs, schema, IDs, relógio local, filas, permissões e transportes existentes.

Este tratamento cobre somente três operações de escrita. Não adiciona recuperação
de falhas dos coletores, coordenação de cliques ou persistência dos avisos. Mantém
a política de último aviso. O botão de respiração continua zerando o rascunho ao
solicitar salvar; esta correção não preserva a duração para reenvio após falha.
O aviso pede conferir o total, sem prometer restauração ou sugerir reenviar o rascunho.
