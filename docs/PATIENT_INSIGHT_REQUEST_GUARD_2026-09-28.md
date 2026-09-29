# Geração de resumo somente por revisão explícita — 28/09/2026

## OBSERVED FACTS

Baseline da PR6 `8ecd200748eb1311bc62a2e9be9a6b99352f4fcc`, base PR5
`e9a80ef386d207a1bc6fe66bef3969eafa84aae5`. Checkout isolado e limpo no preflight.
A PR6 retornou a DRAFT antes da edição para invalidar prontidão do HEAD anterior.

O card já separava o resumo não validado da informação do paciente e limitava
o botão de revisão técnica a DEBUG. Entretanto, `MainViewModel.init` observava
as medições e chamava automaticamente `GeminiHealthAnalyzer`. Com configuração
válida, esse serviço existente pode enviar um resumo das medições ao provedor.
O fato foi confirmado por inspeção de código; não se afirma envio observado.

O observador que gerava o texto automaticamente foi removido. O único caminho
da UI é agora a solicitação explícita do botão de revisão DEBUG; RELEASE também
rejeita chamadas desse caminho no controlador, além de esconder o botão.
Construir o controlador não lê medições nem chama o gerador. Não foi criado
consentimento, configuração de ativação ou autorização nova.

`PatientInsightReview` bloqueia cliques repetidos enquanto há uma solicitação
pendente. Falha permite nova tentativa explícita com mensagem neutra, sem
expor detalhes da exceção; cancelamento libera o indicador de espera. A limpeza
existente dos dados também invalida a resposta pendente, de modo que um resultado
antigo não reapareça nem libere outra solicitação ainda em andamento.

Nove testes usam gerador falso e dados sintéticos, cobrindo inatividade,
revisão desabilitada, repetição, erro, cancelamento antes/durante execução e
resposta antiga após limpeza. O pedido explícito aguarda a primeira emissão do
fluxo DAO, sem usar a lista vazia inicial de um StateFlow sem assinantes; falha
nessa leitura impede a geração. Checks, SHA candidato e revisão independente
ficam vinculados à entrega no GitHub. Estes testes não chamam Gemini nem Core.

## RECOMMENDATIONS / limites

**LOCAL / apresentação e controle de solicitação**. Não certifica o conteúdo
gerado, proveniência, período, cálculos, consentimento ou integração REAL de IA.
Esses pontos continuam **BACKEND CONTRACT REQUIRED**. O modo DEBUG não concede
autorização para enviar dados reais e não representa aceite clínico.

O serviço, endpoint, modelo, prompt e fallback legados permanecem inalterados.
Uma chamada manual DEBUG ainda usa esse serviço quando configurado; não foi
executada nesta entrega. Cancelar a coroutine não comprova cancelamento do
pedido HTTP já em andamento; a garantia desta mudança é sobre a apresentação
do resultado e a ausência do gatilho automático.

Nenhuma mudança em banco, fila, recibos, identidade, ingestão, BLE/VE30, backend
ou nos canais Web, ACS e SM Click. Nenhuma instalação em aparelho, merge ou
deploy. Revisões/checks anteriores permanecem históricos nos respectivos SHAs.
