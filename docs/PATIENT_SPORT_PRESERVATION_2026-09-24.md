# Contadores VE30 e preservação do histórico

## OBSERVED FACTS — composição

Base local `95dd9270a8d573522c5a041730104da248060fbb`; branch
`codex/patient-sport-preservation` em worktree isolado. Continuação autorizada
por Rafael, preservando paciente original17ab, candidato anterior95dd e ACSfd8bb.
Paciente PR5 confirmado OPEN/DRAFT no HEAD
`e9a80ef386d207a1bc6fe66bef3969eafa84aae5`, base
`9a239d9113bc671624643acc975b3e10042f4a57`; conta continua sem push.
Core14 OPEN fora de DRAFT no HEAD90c3a1d334834f5d7620ee1c7b4e80f938c44154,
base51478b6413e336145cca21687b96daf9627b6634. Nenhum desses estados prova deploy.

Port seletivo da intenção de PR5 de salvar contadores atuais independentemente
de HR, sem substituir o repository nem o resumo local corrigidos anteriormente.
Não é merge completo do PR5; diagnóstico/configuração de chave seguem fora
deste delta. Contrato de ingestão, schema7 e controles da fila não foram alterados.

### Evidência do SDK

Fonte oficial consultada por API GitHub no SHA
`add57a049a916c210c5463a66b097ad94265e481`:

- [API em inglês](https://github.com/HBandSDK/Android_Ble_SDK/blob/add57a049a916c210c5463a66b097ad94265e481/android_sdk_source/sdkdoc/VeepooSDK%20Android%20Api%20-%20English.md).
- [SportData](https://github.com/HBandSDK/Android_Ble_SDK/blob/add57a049a916c210c5463a66b097ad94265e481/android_sdk_source/apidoc/com/veepoo/protocol/model/datas/SportData.html).

SportData.dis e OriginData.disValue são km; o aplicativo armazenava diretamente
o número em campo chamado distanceMeters. Novas leituras são convertidas para
metros. SportData.kcal está em kcal. readSportStep fornece contadores atuais;
os registros periódicos do histórico têm outra janela e não substituem esses
contadores. O SDK relaciona cálculos de distância/calorias ao perfil configurado
no relógio; este candidato não verifica calibração/perfil real nem muda seu envio.

### Comportamento resultante

- Novo VeepooSportReading copia valores no callback, incluindo o instante local
  de observação. Não atribui esse instante a uma medição clínica. Zero é válido;
  negativos, valores não finitos e overflow não viram contadores medidos.
- WearableRepository.persistSportReading grava uma observação no histórico local
  com dispositivo observado, passos/calorias/distância e os demais campos como
  ausentes pela convenção0 já existente. Não cria HR/PA, item de fila, chamada
  HealthTech, health check ou flush. Exige dispositivo não vazio.
- HBandHealthSyncApp conecta esse callback ao banco. O histórico continua em
  onHistorySamples e conserva seu horário original. applyLatestHistoryToLiveCache
  foi retirado porque republicava vitais antigos como agora, além de substituir
  os contadores com intervalos Origin. Consulta cancelada/antiga não publica
  resultado tardio no fluxo atual; contexto do dispositivo é capturado antes da espera.
- A UI mantém o resumo de maiores valores salvos e horários armazenados. Não
  passou a somar snapshots nem inferir duração ativa, completude diária ou valores
  para dias sem registro. Distância corrigida aparece nos consumidores existentes
  que usam distanceMeters; nenhuma tela nova foi criada.
- Revisão independente identificou consumidor adicional: GeminiHealthAnalyzer.
  A entrada agora exclui observações só de atividade antes das médias/análise;
  map/distinctUntilChanged no coletor impede estímulo novo por anexar apenas
  esporte. A chamada manual também passa pela guarda do analyzer. Não foram
  redesenhadas fórmulas, permissões, provedor ou resultados clínicos herdados.

## Verificação e limites

Testes sintéticos exercitam dados oficiais do SDK, conversão km→m, valores
inválidos/zeros, sequência pós-handshake com fronteira SDK substituída, histórico
sem nova emissão ao vivo, cancelamento/retorno tardio, SQLite de arquivo,
reabertura, fila bloqueada intacta, falha de insert e recuperação para uma nova
tentativa. Também verificam entrada de análise clínica e observação antes da
meia-noite salva depois. Rádio, VE30 físico e SQLCipher não são exercitados.

SHA final, logs, contagens, hashes e revisão distinta estão em
`C:/CDev/Next2U-Pilot-2026-09-24/sport-preservation/`. Checks são locais/Codex,
não CI. Autorrevisão do autor é SELF_REVIEW_ONLY. Sem publicação, merge,
instalação, limpeza de dados ou liberação de fila acumulada.

Limites materiais:

1. A conversão não reescreve registros antigos nem payloads pendentes. O banco
   não distingue a origem/unidade antiga para uma correção retroativa segura;
   importações futuras podem coexistir com números históricos não corrigidos.
2. A observação fica em memória até concluir os extras do handshake e o insert
   assíncrono. Falha de escrita é reportada em log; não existe nova fila durável
   de recuperação desse intervalo. Reabertura após commit não comprova resistência
   a encerramento de processo, falta de energia ou disco cheio.
3. Contadores continuam no cache usado pelas próximas leituras normais de vitais
   e pelo keep-alive preexistente. A garantia de não enviar é restrita ao caminho
   de gravação de esporte; não significa que todo aplicativo ficou sem rede ou
   que esses contadores jamais integrarão uma telemetria futura.
4. InsightInput impede que esta nova fonte só de atividade entre como zeros na
   análise clínica. Outros problemas herdados de análise de leituras parciais,
   estimativas, período, autorização/divulgação e provedor não foram homologados.
5. Valores0/ausentes não se distinguem no schema legado dos demais campos. A UI
   não passa a declarar medição0 ou totais diários completos por causa desta mudança.
6. Sem comprovação de vínculo/proveniência clínica no ambiente. patient_id e
   device_id centrais continuam sob os contratos; esporte local não é comando ACS
   nem comprovante de visibilidade à equipe autorizada.

## RECOMMENDATIONS — sequência de integração

Antes de atualizar aparelho com dados, ensaiar upgrade6→7 com SQLCipher e recuperação
em laboratório. Conferir no VE30 real passos/calorias/distância, perfil sincronizado,
horário da observação e histórico antigo sem emissões clínicas novas. Conferir o
restante do PR5 e validar novo SHA/CI/revisão após a integração humana.

Leandro precisa confirmar ingestão implantada/durável, nomes/unidades/ausência de
métricas clínicas e identidades/permissões de teste. A correção documentada de
distância não resolve HRV score versus RMSSD ou temperatura corporal versus pele.
ACS celular/tablet permanece DEMO e depende de contratos próprios de sessão,
cadastro, visitas/dispositivos e offline. Hospedagem/DNS/HTTPS continuam no outro chat.

Classificação: PROPOSED / CONCEPTUAL para o candidato; BACKEND CONTRACT REQUIRED
para pendências de integração; nenhuma nova capacidade REAL ponta a ponta provada.
Web Profissional e SM Click não foram modificados. Os critérios finais de Rafael
permanecem pendentes; a entrega não autoriza instalação, merge ou aceite do piloto.
