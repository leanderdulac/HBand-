# Auto-revisão da continuidade paciente/relógio

## OBSERVED FACTS — escopo e preflight

Revisão solicitada pelo usuário, com correções locais autorizadas. Executor:
mesmo agente implementador, portanto **SELF_REVIEW_ONLY**, sem aprovação
independente ou autoridade de merge.

Composição recente examinada: baseline
`ba2125b46769a38daaa2d47652c246d09e890d17` até
`cea7301ef1425d3971211697ef5e9a356f520fa1`. Inclui pausa de autorização,
inicialização/reconexão BLE, resultado de leitura do histórico, gravação fora
da Activity, consulta de pausa e intervalo monotônico. Revisão do diff de
produção, caminhos adjacentes relevantes, testes e evidências de entrega;
não é uma auditoria integral do SDK ou de todo o histórico anterior do produto.

Preflight limpo no HEAD acima, remoto `leanderdulac/HBand-`. GitHub confirmado:
main `f35d12b26c5a2305004271c2a05068782a1c9fc8`; PR #4 DRAFT HEAD
`9a239d9113bc671624643acc975b3e10042f4a57`, base
`04938c46fdba3ab59789943f41b7a48ea5b4b72e`. PRs #1/#2/#4 preservadas.
Continuidade descendente da PR #4, não publicada. Correção nesta branch:
`codex/patient-review-auth-retry`.

## Achado corrigido

**P1 — uma tentativa manual inconclusiva apagava a pausa de autorização.**
Após 401/403, `retryAllFailed` conservava o erro ao passar para PENDING.
Porém IOException ou HTTP 503 sobrescreviam essa evidência. Na próxima leitura,
a consulta já não encontrava bloqueio e liberava processamento automático.
Dois testes reproduziram a perda antes da correção (`before-fix.log/.xml`).

O resultado agora conserva o erro de autorização anterior em falhas de
transporte, respostas do ramo SERVER e 408/429. Não trata esses resultados
como comprovação de acesso restaurado. A classificação/regras de tentativas
existentes são mantidas: 408/429 continuam no ramo CLIENT; demais erros CLIENT
mantêm seu comportamento. Não se muda contrato HTTP, identidade ou credencial.
O erro conservado é evidência anterior, não uma alegação de novo 401/403; contagem
de tentativas e instante da tentativa atual continuam atualizados. A falha de
transporte mais recente não substitui a mensagem persistida de autorização.

Três casos novos, cada um com 401 e 403, cobrem transporte, 503 e 408/429,
nova leitura, nova instância de repositório, ausência de chamada automática,
liberação do gate e recuperação por tentativa manual com resposta de sucesso.
Não se provocam erros nem reenvios com dados reais do tablet.

## Demais áreas examinadas

| Área | Evidência / alcance |
| --- | --- |
| BLE | Inicialização após campos usados na restauração; preferência conferida no setter, callback e restauração; testes de conexão/cancelamento da reconexão existentes |
| Histórico | Desconhecido, incompleto, cancelado e concluído separados; cancelamento propagado; callbacks de progresso guardados pela sessão; sono é consultado quando capacidades estão verificadas |
| Gravação em segundo plano | Coletor único no escopo da aplicação; preferência persistida consultada por leitura; cancelamento preservado; prova física anterior com Activity removida e serviço ativo |
| Fila | Gate compartilhado no processo; evidência persistida entre instâncias; consulta EXISTS equivalente ao classificador sem selecionar payloads |
| Intervalo | Contador monotônico restrito ao processo; data da medição não é reescrita; testes de limite, recuo e elegibilidade |
| Interface | Pausa por autorização tem prioridade sobre erro genérico; contador identifica registros, não número de tentativas; resultado local não afirma recebimento pela equipe |
| Artefatos | Hashes dos três últimos APKs e cópias extraídas conferidos com seus manifestos; isso valida correspondência dos arquivos, não aprovação independente nem reprodutibilidade do build |

Nenhum outro achado novo foi confirmado nas alterações examinadas. Isso não
constitui garantia de ausência de defeitos. Evidências anteriores permanecem
vinculadas aos seus SHAs; o candidato corrigido exige verificações próprias.

## Pendências preexistentes e recomendações

- **Gravação parcial local:** `persistTelemetry` grava o histórico antes da
  fila, sem transação envolvendo ambas. Se a segunda escrita falhar, o histórico
  pode existir sem item de envio. Esse fluxo precede o baseline examinado e
  não é alterado por esta correção de autorização. Próxima prioridade local:
  revisar atomicidade/recuperação com testes de falha entre as escritas.
- O coletor permanece sujeito a encerramento do processo e erro de armazenamento;
  dados recebidos via StateFlow podem ser consolidados. Não há garantia de cada
  callback salvo, monitoramento clínico contínuo ou recuperação retroativa.
- Ingestão central, vínculo/autorização e homologação da visualização pela equipe
  permanecem pendentes; **BACKEND CONTRACT REQUIRED** onde não confirmados.
- Revisão independente deve ser feita por revisor distinto, na composição exata
  publicada, seguindo workflow/ADR-013. Não há CI ou aprovação de release nesta
  entrega. Sem merge/deploy. Web Profissional, ACS e WhatsApp não foram alterados
  nem integralmente revisados nesta etapa focada no paciente.

## Verificação do candidato

SHA final, resultados, assinatura, APK e teste físico ficam na entrega local
`C:/CDev/Next2U-Patient-Delivery/2026-09-24-review/`.
Comando: `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:compileReleaseKotlin :app:lintDebug :app:lintRelease -Pandroid.builder.sdkDownload=false --offline --console=plain --continue`.
**REAL local** para comportamento do aplicativo verificado; testes usam fixtures
isoladas. Uma rodada intermediária ainda falhou no caso 503 durante a correção;
sua evidência foi preservada em `intermediate-checks.log` e `intermediate-results.xml`.
