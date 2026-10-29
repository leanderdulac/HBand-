# Ensaios Android de armazenamento cifrado

## OBSERVED FACTS

Base local5215718f07c5c0604aaec4a2a18f852e356367c6, limpa; branch isolada
codex/patient-storage-android-lab. GitHub paciente PR5 continua DRAFT em
e9a80ef386d207a1bc6fe66bef3969eafa84aae5, base9a239d9113bc671624643acc975b3e10042f4a57,
mainf35d12b26c5a2305004271c2a05068782a1c9fc8; conta sem escrita. Core14 continua
OPEN em90c3a1d334834f5d7620ee1c7b4e80f938c44154, implantação não comprovada.
ACS PR2/PR1 DRAFT sem mudança; Web54 OPEN, configuração/login ainda pendentes.

Esta rodada adiciona testes instrumentados de SQLCipher4.17.0 e Android Keystore,
além da evidência local Robolectric anterior. Não altera código de produção,
schema/migração, formato de chave, fila, contrato, autorização ou endpoint.

### Isolamento explícito

- Somente `-PstorageLab=true`: pacote debug recebe sufixo `.storagelab` e manifesto
  próprio. O build normal mantém applicationId e Application originais.
- A Application de laboratório é a classe Android comum. Sem INTERNET; launcher,
  receiver de boot e serviços BLE desativados. Providers automáticos Firebase e
  AndroidX Startup removidos nesse manifesto. Não há captura ou envio.
- Testes exigem pacote exclusivo, hardware ranchu/goldfish e argumento
  `storageLab=synthetic-only`. Não executar no app que contém dados do piloto.
- Script host exige serial de emulador, qemu, boot concluído, nome exato do AVD,
  pacotes esperados nos dois APKs e ausência de instalação prévia. Se já existe
  pacote, preserva-o e recusa. Não usa clear, uninstall, wipe ou remoção de chave.
- AVD novo separado usa imagem API35/x86_64 previamente instalada. Nenhum SDK
  ou software baixado. Os AVDs anteriores e aparelhos físicos não são alterados.

### Ensaios

1. Seed abre o banco produtivo via openVerified, com chave protegida pelo Keystore
   do emulador, e grava duas linhas sintéticas incluindo pausa com prefixo401 real.
2. Migração cifrada6→7 usa fixture Roomv6 congelada de72e65f4, migração produtiva,
   validação Room e compara todas as seis tabelas/atributos. IDs estáveis ao reabrir;
   índice impede substituição por identidade repetida.
3. IDs duplicados causam rollback completo; reabrir com Roomv6 verifica versão,
   colunas e linhas anteriores. Não apaga nem tenta reparar.
4. Chave errada falha sem substituir o arquivo; chave original volta a ler a linha.
5. Outro processo usa openVerified para reabrir o banco seed, verifica PID diferente,
   metadata cifrada/IV inalterados e campos/IDs/pausa reconhecida pelo código produtivo.
6. Placeholder de identidade ajustado para applicationId efetivo do build.

As fases são explícitas porque reabertura deve ocorrer em nova instrumentação.
Não executar a classe inteira sem filtro: seed e reopen têm pré-condições distintas.
O script verifica número de testes e falhas por fase e conserva saídas e hashes.

### Reprodução e alcance

Build: `./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest -PstorageLab=true
-Pandroid.builder.sdkDownload=false --offline --console=plain`.
Usar tools/Run-StorageAndroidLab.ps1 com SDK, serial, AVD novo, caminhos dos dois
APKs de laboratório e pasta nova de evidências. Assinatura debug é apenas laboratório.
O APK `.storagelab` não é atualização do pacote do piloto.

Proveniência, execução/revisão no SHA final e artefatos:
C:/CDev/Next2U-Pilot-2026-09-25-storage-android-lab/.
Autorrevisão SELF_REVIEW_ONLY; revisão independente por agente distinto.
Checks locais não são CI; integração/merge continuam humanos.

## RECOMMENDATIONS

Mesmo se aprovados, os ensaios só comprovam o alcance no Android emulado. Não
provam Keystore protegido por hardware físico, energia interrompida, boot/FGS,
atualização do APK instalado, BLE/VE30 real, recuperação de chave perdida,
durabilidade sob falha física nem backend. Fechamento/reabertura de conexão e
nova instrumentação não equivalem a reinício do aparelho ou queda durante transação.

Web, ACS e WhatsApp/SM Click não mudam. ACS permanece DEMO; integração entre canais
PROPOSED / CONCEPTUAL, contratos/ambiente pendentes BACKEND CONTRACT REQUIRED.
Solicitar ao Leandro incorporação do paciente ou Write, contratos e revisão Core
implantada, contratos ACS e conclusão do login no domínio. Não liberar fila acumulada.
