# Releitura do histórico VE30 — cópias locais

## OBSERVED FACTS

Base desta correção independente: `ebe7d2fc6377e85e0dcb47d8dcb05b648c5b1814`.
Não depende dos deltas visuais do PR12 ou de comandos SDK do PR13.
A issue15 registra o ensaio físico em outra composição (HEAD do PR13):
1.714 linhas preservadas ao reiniciar, 586 adicionais após reconectar, das quais
557 repetiam todos os campos exportados exceto o ID autogerado.
Essa evidência reproduz o problema; não é execução do novo candidato.

`persistHistorySamples` criava novamente cada métrica e uma identidade nova de
fila para a seleção horária. `REPLACE` não impedia cópias: a chave é o ID local
autogerado, não o conteúdo da leitura. Os arquivos afetados são iguais nessa
base e no HEAD do PR13 observado durante a reprodução.

O candidato compara snapshots já armazenados com todos os campos persistidos
(aparelho, string de horário, epoch e valores), ignorando somente o ID local.
Uma diferença em qualquer campo conserva uma linha separada, sem substituir a
anterior. A consulta por aparelho e janela de tempo, a comparação e as gravações
ocorrem na transação Room existente. Não cria índice, tabela, migração ou chave
de domínio; não remove cópias antigas. Horário de origem inválido não participa
da supressão: o fallback de inserção não prova identidade da observação.

A seleção horária continua considerando o lote completo. Seu vencedor só cria
uma nova entrada de fila se o snapshot for novo. Recibos/IDs existentes não são
reescritos; entradas sincronizadas removidas não são reconstruídas da releitura.
O processamento normal da fila permanece após o commit, inclusive suas tentativas
das pendências existentes. Portanto retries/estado podem evoluir normalmente.
O retorno do método passa a contar apenas linhas inseridas nesta execução; o
callback de aplicação não usa esse retorno para declarar completude do SDK.

Isso é supressão conservadora de cópias na representação local atual, não uma
nova identidade clínica universal. O schema existente não guarda o `Kind` do
SDK nem patient_id na tabela de métricas. Não é possível reconstruir metadados
que já foram descartados; conteúdo indistinguível nessa representação não
comprova eventos físicos distintos. Evoluir proveniência/identidade ou conciliar
atribuição a paciente requer escopo e contrato próprios, sem inferir vínculos.

## Verificação e limites

`WearableHistoryReplayTest` usa Room/SQLite e transportes sintéticos: callbacks
repetidos no lote, releitura após reabrir o banco, repositórios concorrentes,
diferenças nos campos, rollback da fila, cópias preexistentes preservadas,
recibos sincronizados, pendências, seleção horária e horário inválido. Oito
cenários iniciais falharam na base, antes da correção. Resultados do candidato,
CI e revisão pertencem à entrega com SHA exato.

PROPOSED / CONCEPTUAL: candidato e evidência sintética, sem distribuição.
REAL: ensaio físico anterior limitado à reprodução, não comprovação da correção.
BACKEND CONTRACT REQUIRED: correlação/ingestão central continuam com seus próprios
contratos e testes. Web, ACS e WhatsApp não mudam; não há API, permissão, schema
ou comportamento clínico novo. O ANR do SDK da issue14 não é corrigido aqui.

## RECOMMENDATIONS

Após CI e revisão independente, decisão humana de integração. Validar o APK da
composição final no laboratório preservando os IDs/valores anteriores; repetir
o mesmo histórico após reinício e comparar conteúdo, não apenas contagens.
Não executar limpeza destrutiva das cópias já armazenadas no aparelho.
