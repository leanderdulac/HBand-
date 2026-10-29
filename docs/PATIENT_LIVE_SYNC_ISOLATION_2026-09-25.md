# Captura local independente da espera de rede

## OBSERVED FACTS

Base cumulativa preservada: `95fd0538f8954d6386fee75379aada4531628ee0`.
Branch isolada: `codex/patient-live-sync-isolation`.
PR5 GitHub confirmado OPEN/DRAFT em `e9a80ef386d207a1bc6fe66bef3969eafa84aae5`,
base `9a239d9113bc671624643acc975b3e10042f4a57`; sem escrita no remoto.

O callback de captura ao vivo usava enqueueTelemetry, que salva e depois aguarda
processQueue. Enquanto HTTP aguardava, o coletor de StateFlow também aguardava;
emissões intermediárias podiam ser confluídas. Reprodução local com rede suspensa
esperava [72,76,80] e observou apenas [72]. Não é contagem de perdas no aparelho.

A Application agora chama a transação local persistTelemetry, exposta apenas
internamente no módulo. Depois do retorno solicita processamento por um canal
de sinais Unit com capacidade conflada: um processador serial e no máximo um
sinal aguardando. O canal não guarda leituras; elas já estão no histórico/fila Room.
Erro de salvamento não emite sinal. O coletor não aguarda rede. Os métodos antigos
de captura manual/histórico mantêm seus comportamentos, assim como o Worker existente.

Processamento continua no mesmo processQueueDetailed: admissão global em processo,
pausa401/403 durável, identidade/recibos/retries existentes. Falha comum de sincronização
é relatada separadamente; não vira falha de captura nem desfaz seu filtro. Não há
timer, loop de retry sem sinal ou novo WorkManager. Novo sinal pode existir enquanto
o processamento anterior termina; a serialização impede dois envios desse processador.

Cancelar o coordenador cancela seus filhos. Cancelamento independente do processador
cancela o coordenador; encerramento normal do fluxo limpa o processador sem cancelar
artificialmente o dono. Sinais podem ser descartados ao encerrar; leituras confirmadas
localmente permanecem na fila para os mecanismos existentes. Não se promete envio
imediato de cada sinal, inclusive se outro chamador ocupar a admissão global.

## Verificação e limites

Testes de corrotinas cobrem rede suspensa, coalescência limitada, erro de gravação,
erro de processamento, cancelamento e encerramento normal. Testes Room/SQLite com
dados sintéticos gravam três pares durante HTTP bloqueado, cancelam e reabrem o
banco; outro caso mantém captura após401 sem novas chamadas HTTP e conserva pausa.
Nenhum SQLCipher nativo, BLE/VE30 físico ou backend é exercitado nesta rodada.

O teste de reprodução usava o encadeamento antigo save+sync no mesmo callback;
o teste final usa os callbacks separados, como a Application alterada. A fonte
da reprodução está arquivada junto ao XML, sem alegar teste idêntico nas duas versões.
Resultados por SHA e revisão independente no pacote
`C:/CDev/Next2U-Pilot-2026-09-25-live-sync-isolation/`.

StateFlow continua conflado: escritas locais lentas, ausência de nova emissão,
cancelamento e morte de processo ainda limitam captura. Não há coleta garantida
de toda amostra nem recuperação retroativa. Backend segue dono central; IDs,
schema, chave, autorização, contratos, SDK e os outros canais não mudam.

## RECOMMENDATIONS

Candidato local revisável, sem instalação, liberação de fila ou homologação.
Preservar este delta na composição humana com PR5, especialmente Application,
LiveReadingRecorder e WearableRepository. Checks locais não revalidam toda a
composição ou substituem a revisão publicada. Conta/login, contratos/runtime ACS
e ingestão publicada permanecem BACKEND CONTRACT REQUIRED.
