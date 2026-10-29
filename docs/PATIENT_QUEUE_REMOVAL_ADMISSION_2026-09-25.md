# Exclusão e envio — admissão compartilhada

## OBSERVED FACTS

Base local limpa `5c80c1b22c3c4c2d5ac20a2fda017ae1aa019de1`, preservada em
outro worktree. Novo trabalho em `codex/patient-queue-removal-admission`.
GitHub PR5 reconfirmado OPEN/DRAFT, HEAD
`e9a80ef386d207a1bc6fe66bef3969eafa84aae5`, base
`9a239d9113bc671624643acc975b3e10042f4a57`. Candidato local não é o PR remoto.

Envio/retry já tinham admissão global no processo. Exclusão individual, limpeza
total e limpeza dos concluídos acessavam o DAO sem participar da coordenação.
Assim, um envio podia conservar snapshot de linha removida por ação concorrente.
Desabilitar o botão com isSyncing não impedia a intercalação entre clique e coroutine.

O mesmo Mutex global agora admite envio/retry e remoções. A admissão é imediata,
sem espera: operação ocupada recusa a remoção com false, antes de chamar o DAO.
Quando a remoção ganha, nenhum processador lê/envia a fila até a operação terminar.
finally libera a admissão em sucesso, erro ou cancelamento. isSyncing representa
somente o envio; exclusão não aparece como sincronização. Captura/gravação local
continuam independentes do envio e não entram neste Mutex.

O ViewModel trata recusa e falha sem mensagem de sucesso, relança cancelamento
e interrompe o restante da limpeza de teste se a remoção da fila for recusada.
Confirmação informa que fila ocupada recusa exclusão e que envios anteriores não
são desfeitos. As ferramentas de exclusão permanecem de desenvolvimento.

Regressões usam apenas mapas sintéticos, APIs falsas e barreiras de coroutines:
snapshot em processamento recusa as três remoções; remoção admitida primeiro
bloqueia envio/outra remoção; falha ou cancelamento conserva linha e libera
admissão; cancelamento real da coroutine suspensa também libera a admissão.
Fixture @Update agora reproduz Room: não recria uma linha que já foi removida.

Na reprodução pré-correção, dois cenários falharam e um passou. Uma tentativa
anterior incluía falha do próprio teste por exigir identidade de instância da
exceção através de withContext; foi corrigida para comparar tipo/mensagem. Logs
originais preservados, sem contabilizar esse defeito de teste como bug do app.

SHA final, checks completos e revisão independente ficam em
`C:/CDev/Next2U-Pilot-2026-09-25-autonomous-continuation/patient-removal-admission/`.

## RECOMMENDATIONS / limites

Não houve exclusão de fila real, ADB, instalação, tráfego clínico ou execução de
reset. Testes locais não comprovam integração REAL. O Mutex coordena operações
do processo existente; não altera schema, IDs, payload, contratos, credencial,
pausa de autorização, captura ou recibos. Não torna a limpeza de todos os tipos
de dados uma transação única, nem impede novas capturas após uma limpeza.

Ensaios nativos anteriores permanecem vinculados a5c80, não ao novo candidato.
Web, ACS e WhatsApp/SM Click não são alterados. Backend e VE30 físico continuam
pendentes; conta de login ainda depende do Leandro. Classificação de candidato
PROPOSED / CONCEPTUAL; integração BACKEND CONTRACT REQUIRED. Revisão independente
delimitada e incorporação/merge humanos continuam obrigatórios.
