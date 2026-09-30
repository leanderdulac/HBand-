# VE30 — histórico combinado e início das leituras ao vivo

## OBSERVED FACTS

Base: PR12, `299bc24575fff454e616a455706f04648d087fd2`. Esta alteração é
separada da simplificação visual e depende dessa base para preservar a UI.

No ensaio físico isolado, o histórico aguardou três timeouts de 90 segundos
antes de iniciar a frequência cardíaca. Houve retorno de amostras após iniciar
esse estágio. O aparelho informou protocolo de histórico 5. Isso não comprova
precisão clínica, persistência das amostras ou ingestão no Core.

A [API oficial do SDK, revisão add57a0](https://github.com/HBandSDK/Android_Ble_SDK/blob/add57a049a916c210c5463a66b097ad94265e481/android_sdk_source/sdkdoc/VeepooSDK%20Android%20Api%20-%20English.md)
determina `IOriginData3Listener` para os protocolos 3 e 5, com retornos de HRV
e SpO2 incluídos na leitura geral. Para outras versões indica `IOriginDataListener`.
Também orienta evitar operações demoradas simultâneas. O aplicativo já processava
os retornos combinados, mas repetia as consultas legadas de HRV/SpO2 depois.

O candidato usa a consulta combinada nos protocolos 3/5, preserva seus registros,
IDs e horários e contabiliza cada tipo sem duplicá-lo. Protocolos legados mantêm
as consultas separadas. Falha no histórico combinado continua incompleta;
não se transforma em sucesso ou ausência confirmada. A sequência de operações,
os timeouts, a política de repetição, cancelamento, banco e SDK não mudam.
Não são iniciados sensores simultaneamente com a transferência do histórico.

## Verificação e limites

`VeepooCombinedHistoryTest` executa o cliente real com callbacks sintéticos do SDK:
protocolos 3/5, protocolo legado 2, versão 4 sem inferência de listener combinado,
falha da consulta geral, falha posterior de sono e retorno vazio concluído.
Confere comandos emitidos, preservação de amostras/IDs/horários e contagem por tipo.
Seis de sete cenários falharam na base antes da correção; o legado 2 passou.
Testes sintéticos não medem a latência física.

**PROPOSED / CONCEPTUAL:** candidato de correção, não distribuição operacional.
Evidências locais por SHA, CI e reteste físico são registradas na entrega e no PR.
**REAL:** o ensaio anterior comprovou retorno do sensor somente no laboratório,
sem envio. **BACKEND CONTRACT REQUIRED:** recepção e conciliação Core continuam
fora desta alteração. Web, ACS e WhatsApp não recebem alteração; não mudam IDs,
contratos, permissões, proveniência temporal ou filas internas do paciente.

## RECOMMENDATIONS

Retestar a latência após atualização preservadora do pacote BLE LAB. Uma melhora
local não garante o mesmo tempo em todos os aparelhos, firmwares ou volumes de
histórico. Submeter a composição exata a CI e revisão independente. Merge humano
por Leandro, após integração e revalidação da dependência PR12.
