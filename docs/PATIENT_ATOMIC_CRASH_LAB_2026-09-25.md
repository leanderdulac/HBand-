# Android cifrado — interrupção da transação local

## OBSERVED FACTS

Base cumulativa ecc459e78a9381637418695c1501a10249c5fb24, limpa; branch isolada
codex/patient-atomic-crash-lab, C:/CDev/Next2U-Patient-Atomic-Crash-Lab.
GitHub paciente PR5 permanece DRAFT e9a80ef386d207a1bc6fe66bef3969eafa84aae5,
base9a239d9113bc671624643acc975b3e10042f4a57; conta sem escrita. Core14 aberto
90c3a1d334834f5d7620ee1c7b4e80f938c44154, publicação/contrato não comprovados.
ACS2 DRAFT e303c3c3263d56b33858c46bec955a6ccc4164d8 sobre PR1 7d80a087bf4333965c8b34f6db790035c05a9ec2.

Escopo: testes Android e script host; nenhum código de produção, contrato, schema,
chave, fila ou regra clínica alterado. Arquitetura/governança compartilhadas e
autorização atual de Rafael para desenvolvimento/testes/revisão preservadas.

StorageAndroidLabTest possui duas fases opt-in, parametrizadas por before-queue
ou after-queue. Em cada banco novo sintético, confirma um par histórico/fila501.
Na transação seguinte, grava histórico502 e, no segundo ponto, também fila502.
Confere estado ainda dentro da transação, grava marcador separado e encerra
somente o próprio processo antes de retornar ao commit.

A instrumentação seguinte exige marcador daquele ponto, PID distinto e mesmos
metadados de proteção da chave. Compara todos os campos de501 e exige ausência de502
em ambas as tabelas; inclusive IDs, payload, tentativas e pausa401 preservados.
Confirma novo par503 e fecha/reabre SQLCipher para verificar501+503 sem reparo.
São usados localWriteTransaction/Room/DAOs produtivos e SQLCipher Android, com dados
inteiramente sintéticos. Não exercita produtor BLE, WearableRepository ou transporte.

Guardas exigem pacote .storagelab, hardware emulador, argumento synthetic-only,
Application Android simples e ausência da permissão INTERNET. O novo banco recusa
seed sobre arquivo existente; recuperação exige arquivo/marcador e nunca semeia.
O banco principal do ensaio anterior permanece separado desses bancos de corte.
Nenhum clear/uninstall/delete/remoção de chave ou envio de fila.

Run-StorageAndroidLab.ps1 mantém fases anteriores e acrescenta
`-IncludeAtomicInterruption`. Sem o switch, continua6 testes; com ele,8 testes
concluídos e2 encerramentos intencionais separados. Uma saída 'Process crashed'
isolada não é sucesso: cada recuperação precisa concluir1 teste e verificar o
marcador/banco. Outro crash/falha permanece falha. Logs brutos são preservados.
Hashes dos APKs antes de instalar são conferidos no pacote instalado antes/depois
e nos arquivos ao final, sem atribuir um build posterior ao teste executado.

Build opt-in e execução somente em AVD novo, distinto do laboratório anterior:
Next2U_Atomic_Lab_20260925, emulator-5582, API35/x86_64 com imagem já instalada.
Pacote C:/CDev/Next2U-Pilot-2026-09-25-atomic-crash-lab registra SHA, artefatos,
resultados, contexto local e revisão independente. Autorrevisão SELF_REVIEW_ONLY.

## RECOMMENDATIONS / limites

Encerramento de processo Android emulado não é queda de energia, falha de disco,
Keystore físico ou prova de atomicidade de todo o pipeline VE30. Este ensaio não
confirma backend, recibos reais, associação de paciente entre canais, atualização
de release ou recuperação após perda de chave. Não instalar no app do piloto.
Web, ACS e WhatsApp/SM Click não mudam. Contratos/ambiente reais continuam
BACKEND CONTRACT REQUIRED; incorporação/revisão/merge permanecem humanos.
