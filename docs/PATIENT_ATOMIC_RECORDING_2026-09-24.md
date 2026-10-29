# Gravação conjunta de leituras e fila local

## OBSERVED FACTS

Continuação autorizada da pendência identificada na auto-revisão. Baseline local
limpo `0d76ff175c78cfdc2e23c878d72885ed329528b3`, descendente da PR #4.
GitHub confirmado em 24/09/2026: main
`f35d12b26c5a2305004271c2a05068782a1c9fc8`; PR #4 HEAD
`9a239d9113bc671624643acc975b3e10042f4a57`, base
`04938c46fdba3ab59789943f41b7a48ea5b4b72e`. PRs #1/#2/#4 preservadas.
Branch: `codex/patient-atomic-recording`. Continuidade não publicada.

O repositório escrevia o histórico e depois a fila, sem transação conjunta.
Cinco testes falharam no baseline: leitura ao vivo, captura manual, segundo
item de fila de um lote histórico, ECG avançado e cancelamento entre escritas.
O banco ficou com gravação parcial. Dois controles de sucesso passaram.
Evidência: `before-fix.log/.xml` na entrega.

Agora `LocalWriteTransaction` é dependência obrigatória do repositório, sem
fallback sem transação. Todos os três pontos de construção de produção
(Application, ViewModel e Worker) recebem a implementação Room `withTransaction`
do mesmo banco de seus DAOs. As fixtures de concorrência usam uma execução
direta explicitamente de teste; rollback é verificado com Room/SQLite real
em memória, não com essas fixtures.

Escopo da transação:
- uma leitura ao vivo/manual e seu item elegível na fila;
- um lote de histórico recebido e seus itens horários selecionados;
- uma medição avançada e, quando elegível pelo critério anterior, seu item ECG;
- cada par histórico/fila da ferramenta de simulação existente, sem acioná-la
  no tablet ou alterar isolamento/elegibilidade.

Chamadas de rede e processamento da fila ficam depois do commit. A checagem
de saúde da captura manual também permanece fora da transação. Se a escrita
falha ou é cancelada antes do commit, as escritas da operação são revertidas.
Falha de rede após commit conserva os dados. Regras de filtros, seleção horária,
IDs, tempos e pausa de autorização permanecem existentes. Dados sem frequência
cardíaca elegível continuam apenas locais, sem inventar uma medição para enviar.

Sem versão/esquema novo de banco, migração, alteração da configuração de
criptografia, limpeza de dados, SDK, permissão, credencial ou contrato de API.
A consulta da fila e a assinatura da aplicação permanecem existentes.

## Verificação e alcance

Onze testes novos usam banco em memória e fixtures sintéticas. Triggers SQL
provocam falha na primeira escrita, na fila e no segundo item de um lote;
um DAO delegado permite cancelar entre as escritas reais. Cobertos também
sucesso ao vivo, seleção horária, histórico sem batimento, medição avançada
apenas local e falha de rede após commit. Nenhuma falha de armazenamento é
provocada no tablet e nenhum registro sintético é inserido nele.

Evidências por SHA, APK, assinatura e teste físico:
`C:/CDev/Next2U-Patient-Delivery/2026-09-24-atomic-recording/`.
Comando: `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:compileReleaseKotlin :app:lintDebug :app:lintRelease -Pandroid.builder.sdkDownload=false --offline --console=plain --continue`.
Os testes em memória verificam a transação Room/SQLite, não homologam
criptografia nem falha física de energia. O teste no aparelho usa sua base
existente, com atualização do APK sem limpeza/migração.

## RECOMMENDATIONS / limites

**REAL local:** atomicidade das escritas cobertas no aplicativo. Fecha a
pendência de gravação parcial identificada em `PATIENT_REVIEW_2026-09-24.md`.
Não reconcilia registros antigos eventualmente parciais, não promete recuperar
uma amostra perdida nem entrega exatamente uma vez. Em erro antes do commit,
a operação não é gravada; coleta/recuperação e limite do processo continuam
sujeitos às condições já documentadas. Nada garante monitoramento clínico
contínuo ou precisão das leituras.

Ingestão central e visualização pela equipe continuam sem homologação;
**BACKEND CONTRACT REQUIRED** onde identidade/autorização/integração ainda não
foram confirmadas. Web Profissional, ACS e WhatsApp sem mudanças.
**SELF_REVIEW_ONLY:** testes locais não são CI ou revisão independente.
Sem publicação, merge ou deploy; revisar composição antes de integração.
