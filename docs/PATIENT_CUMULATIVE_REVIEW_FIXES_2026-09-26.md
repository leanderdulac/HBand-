# Correções da revisão cumulativa do Paciente

## OBSERVED FACTS

Baseline local c07445fcb0d0e9c789d17b73b15018d512d889f6; composição revisada desde
PR5 e9a80ef386d207a1bc6fe66bef3969eafa84aae5. Correções mínimas em checkout novo;
nenhum candidato, laboratório ou entrega anterior alterado.

**HR-01:** a frequência cardíaca flat em texto hexadecimal `0x1.2p6` podia passar
pelo optDouble local mantendo essa string no corpo enviado. O modelo Core
bcb6035b0599297c946777e3e0701cbc35da1f35 com Pydantic2.13.5 rejeita essa sintaxe.
Em Room sintético com transporte que simula422 integral, a fila com HR72 e esse
valor sincronizou zero em vez de um vizinho válido. A normalização legada também
podia converter sintaxe Java incompatível em um número válido. Strings decimais
permitidas pelo Core, como `7_2`, eram recusadas em vez de processadas.

O guard de HR agora compartilha o parser numérico estreito já usado para SpO2.
O intervalo20..250 e a elegibilidade de captura não mudam. Normalização numérica
legada compatível permanece; valor explícito incompatível é conservado para
recusa local, sem coerção para número nem fallback que o esconda. Payload/ID/
data da fila persistida não mudam. O registro inválido fica FAILED para revisão;
vizinhos compatíveis podem seguir. Nenhum validador completo de schema criado.

Três testes novos falharam no baseline: lote misto, sintaxe legada e decimais
compatíveis. A reprodução usa Room sob Robolectric e transporte simulado; não
recebeu422 real. Probes independentes comparam o modelo Core arquivado e o mapper
compilado, sem servidor. A correspondência de parser depende do runtime Python
testado, não prova todas as versões de Pydantic permitidas pela dependência aberta.

**UI-01:** o botão passou de “Sincronizar Dados Vitais” no PR5 para “Ler dados do
relógio”, afirmando separação de envio, mas seu callback continuou salvando a
leitura disponível e processando os registros pendentes. O rótulo e a explicação
agora explicitam salvar e tentar enviar a fila. A solicitação de sensor é
assíncrona; não há promessa de que o snapshot já disponível acabou de ser medido.
O fluxo operacional não mudou, nem foi implementado o envio isolado do Leandro.

## RECOMMENDATIONS / limites

Candidato PROPOSED / CONCEPTUAL; evidência LOCAL/DEMO. Sem alteração de schema,
IDs, chave de lote, permissões, fila instalada ou endpoints. Os dois parsers de
vitais compartilham sintaxe, mantendo intervalos diferentes. SpO2 deve continuar
passando sua suíte existente. Campos não cobertos pelo guard continuam podendo
receber422 do Core; não se promete recuperação de toda entrada malformada.

As novas regras de normalização podem mudar o corpo de retry de um registro
legado anteriormente mal interpretado. Não corrigem valores já aceitos pelo Core
e não autorizam liberar fila antiga, alterar idempotência ou enviar ao backend.
Os ensaios Android anteriores permanecem nos seus SHAs/APKs, não são novo ensaio
de atualização deste candidato. Armazenamento ficou idêntico, mas isso não
substitui o teste do par de distribuição escolhido nem a verificação física.

Web, ACS e WhatsApp/SM Click permanecem sem mudança direta. Não houve promoção
de contrato DRAFT, capacidade REAL adicional, merge/publicação/distribuição,
contato com terceiros, credenciais novas ou automação. Resultados e pareceres
cumulativos/finais devem ser registrados na entrega externa com SHA completo.
