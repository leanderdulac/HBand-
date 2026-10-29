# Recuperação Android entre resposta e recibo local

## OBSERVED FACTS

Candidato de testes sobre `ee670b38acaa592788301da67399d198c8259f9f`, branch
codex/patient-receipt-crash-lab. GitHub PR5 permanece OPEN/DRAFT em
e9a80ef386d207a1bc6fe66bef3969eafa84aae5, base9a239d9113bc671624643acc975b3e10042f4a57,
conta sem escrita. Core14 continua aberto em90c3a1d334834f5d7620ee1c7b4e80f938c44154;
contrato inspecionado anteriormente e forma das fixtures preservados. Não se
presume implantação ou homologação do backend.

Mudam apenas teste Android, runner host e este registro. Nenhum código produtivo,
contrato, schema, transporte, política de retries ou identidade muda. Os quatro
canais conservam entidades e fronteiras. ACS/Web/WhatsApp não recebem alterações.

ReceiptRecoveryAndroidLabTest usa WearableRepository.processQueueDetailed,
reconciliador e DAO Room/SQLCipher produtivos. O transporte é objeto sintético
injetado, sem rede. Com três itens e lote máximo2, devolve uma resposta accepted
para os dois primeiros. Um delegate do DAO intercepta a gravação do resultado
SYNCED e encerra o próprio processo antes do primeiro ou do segundo recibo.
No segundo corte, o primeiro recibo já retornou de sua gravação Room.

Antes do corte, asserts conferem uma única chamada da fixture, itens601/602,
quantidade de gravações iniciadas, campos e estado local exatos. Marcador separado
grava PID, snapshot sintético, pedido/chave iniciais e digest dos metadados de
proteção da chave; nunca expõe a chave bruta. Recuperação exige marcador/banco,
PID diferente e mesmo digest; nunca semeia ou repara a fila na reabertura.

Reenvio confere payload/ID por item. Antes do primeiro recibo, o mesmo lote deve
reproduzir JSON/chave; após um recibo salvo, o lote muda legitimamente de601/602
para602/603. O item já SYNCED não é reenviado nem regravado. A fixture assume aceite
anterior para601/602 e retorna duplicate, enquanto603 recebe accepted. Essa hipótese
simulada não comprova armazenamento remoto, deduplicação nem replay em servidor.

Após a recuperação, fecha/reabre SQLCipher e compara todos os campos; uma terceira
instrumentação/processo confirma o estado recuperado sem novo envio. Runner exige
AVD novo Next2U_Receipt_Lab_20260925/emulator-5588, pacotes de laboratório ausentes,
APK sem INTERNET e hashes instalados antes/depois. Teste exige pacote separado,
hardware emulador, Application simples e argumento synthetic-only. Sem wipe,
uninstall, exclusão de bancos/chaves, fila real ou acesso ao VE30.

Dois encerramentos intencionais são relatados separadamente de quatro testes
recovery/reopen. 'Process crashed' sozinho não é sucesso; cada corte exige
recuperação verificada. Resultados por SHA, artefatos e revisão distinta ficam em
Next2U-Pilot-2026-09-25-autonomous-continuation/patient-receipt-recovery.

## RECOMMENDATIONS / limites

Evidência local sintética; candidato PROPOSED / CONCEPTUAL. Não é perda elétrica,
falha de disco, Keystore físico, rede real, atualização de release ou aceite do
piloto. Backend/contrato implantado continuam BACKEND CONTRACT REQUIRED.
Autorrevisão SELF_REVIEW_ONLY; revisão independente e integração humanas.
