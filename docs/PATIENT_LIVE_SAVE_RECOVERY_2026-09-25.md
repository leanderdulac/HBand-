# Nova leitura após falha de gravação local

## OBSERVED FACTS

Base cumulativa: `02efed39d48759d5076fb8b4a68c5a946a7630da`, preservada.
Branch isolada: `codex/patient-live-save-recovery`.
PR5 GitHub confirmado OPEN/DRAFT, `e9a80ef386d207a1bc6fe66bef3969eafa84aae5`,
base `9a239d9113bc671624643acc975b3e10042f4a57`; candidato não publicado.

O coletor marcava a assinatura/instante do filtro antes de salvar. Se o perfil
ou a transação local falhasse, a próxima emissão com os mesmos valores era
suprimida durante a janela existente de 30 segundos, embora nada tivesse sido
gravado. O documento de background recording delimitava esse comportamento
como pendente. Quatro testes reproduziram a falha na base.

O coletor serial agora restaura o estado anterior do filtro quando `save` lança
erro; o erro é propagado ao tratamento existente e cancelamento continua cancelamento.
Somente salvamento concluído conserva a nova assinatura. Uma tentativa diferente
que falha não elimina a assinatura nem o instante da última gravação concluída.
Elegibilidade, campos da assinatura e intervalo monotônico permanecem iguais.

Não há timer, replay de amostra falha, retry de rede, nova fila ou novo ID contratual.
Apenas uma próxima emissão efetivamente recebida pode ser admitida pelo filtro.
StateFlow pode omitir emissões iguais: não se garante que haverá nova amostra.
O callback produtivo usa enqueueTelemetry: histórico/fila em transação, falha de
envio após commit tratada sem lançar erro de captura; assim a falta de rede não
desfaz a deduplicação de uma gravação local já concluída.

## Verificação

Testes do coletor verificam recuperação com mesmos valores, falhas repetidas e
preservação da assinatura anterior. Testes Room/SQLite com dados sintéticos
provocam rollback da fila/histórico via trigger, aceitam a próxima amostra e
reabrem a base para conferir o par preservado. Controle separado comprova que
rede simulada indisponível após commit não duplica a captura.
Evidência, SHA, checks e revisão independente no pacote externo
`C:/CDev/Next2U-Pilot-2026-09-25-live-save-recovery/`.

## RECOMMENDATIONS / limites

Candidato com evidência local, sem SQLCipher/BLE físico ou backend nesta rodada.
Não promete exatamente uma vez, recuperação retroativa ou monitoramento contínuo.
Não altera dados preexistentes, schema, chave, payload, recibos, pausa de autorização
ou contratos. Core continua dono central; Web, ACS e WhatsApp não mudam.
Conta e ambiente de login, contratos/runtime ACS e ingestão publicada continuam
BACKEND CONTRACT REQUIRED. Incorporar em composição revisável; merge humano.
