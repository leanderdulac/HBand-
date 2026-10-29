# Preservação da chave de abertura do banco

## OBSERVED FACTS

Base local16b10f86248e3770bd3dbadbfa21f303b91bb082, limpa antes do trabalho.
Branch isolada `codex/patient-key-preservation`, sem substituir candidatas anteriores.
GitHub confirmado: mainf35d12b26c5a2305004271c2a05068782a1c9fc8; PR5 OPEN/DRAFT
noe9a80ef386d207a1bc6fe66bef3969eafa84aae5 e base9a239d9113bc671624643acc975b3e10042f4a57;
conta sem escrita. Core14 OPEN fora de DRAFT no90c3a1d334834f5d7620ee1c7b4e80f938c44154.
Esses estados não comprovam integração ou implantação. Nova continuação autorizada
por Rafael em25/09; escopo limitado à preservação local, sem contratos novos.

O código anterior gerava nova chave quando o par cifrado/IV estava incompleto,
mesmo havendo arquivo de banco. Gravava o par com SharedPreferences.apply() antes
de devolver a chave, sem confirmação síncrona. Descriptografar também podia criar
um novo alias Android Keystore se o anterior estivesse ausente. Essas condições
foram identificadas por inspeção; não provocadas em aparelho com dados.

### Mudança

- Criação só quando nenhum dos dois campos existe e não há banco nem auxiliares
  WAL/SHM/journal, mesmo vazios. Evidência incompleta exige recuperação explícita.
- O par cifrado/IV é gravado na mesma edição com commit() e confirmação antes
  de devolver a chave ao construtor SQLCipher. Falha não produz chave utilizável.
- Após commit falso/exceção, bloqueio permanece durante o processo: preferências
  podem já ter mudado em memória, o que não comprova escrita durável.
- Inicialização serializada. Releituras válidas reutilizam a chave; não renovam
  segredo, não alteram os campos existentes e não escrevem no banco.
- Descriptografar exige alias existente. Alias ausente, inacessível ou de tipo
  inesperado não é substituído. Geração ainda usa o Android Keystore existente.
- Chave temporária de uma criação malsucedida é zerada antes de sair. Respostas
  de erro não incluem causa bruta, caminhos nem material de chave.

Preservados nome/alias, formato Base64, AES/GCM/NoPadding e chave aleatória32bytes.
Sem migração, troca de algoritmo, nova política de autenticação, backup/exportação,
reset de chave, exclusão de arquivo ou schema. Banco continua7; fila/IDs/recibos,
gravação conjunta, pausa de autorização e contadores VE30 não foram alterados.

### Verificação e limites

Doze testes de política usam arquivos sintéticos, preferências Robolectric e
cifra simulada. Cobrem confirmação antes de retorno, reutilização, metadados
parciais/vazios/tipo inválido, arquivos auxiliares órfãos, falha de desencriptação,
tamanho inesperado, commit falso/exceção com memória já atualizada, concorrência,
erro de plataforma e falha ao cifrar, além de reutilização válida com arquivos
existentes. Comparam campos e bytes preservados.

Esses testes não comprovam Android Keystore, AES/SQLCipher no dispositivo, fsync,
falha de energia, perda de processo nem hardware. O contrato de commit do Android
é usado como fronteira local; o armazenamento físico continua precisando de
ensaio. O manifest atual não declara processo adicional; a serialização não é
lock entre processos. Relatório de revisão distinta e resultados por SHA final
ficam em `C:/CDev/Next2U-Pilot-2026-09-25-key-preservation/`.

O caminho legado sem SQLCipher no runtime de teste permanece fora deste delta.
Ausência de chave/metadados inválidos pode impedir início do aplicativo; não foi
adicionada UI de recuperação. A mensagem orienta preservar dados, mas não recupera
segredo perdido nem torna arquivos inacessíveis legíveis. Não reinstalar/limpar
o aparelho e não gerar outra chave como tentativa de correção.

## RECOMMENDATIONS

Revisar e integrar humanamente a composição, revalidar novo SHA/base e executar
ensaio SQLCipher/upgrade apenas em laboratório autorizado antes de atualizar
aparelho com fila. Falha de chave necessita avaliação específica sem sobrescrita;
não há promessa de recuperação de criptografia sem seu material original.

Capacidade operacional integrada permanece **PROPOSED / CONCEPTUAL**. Paciente
recebe uma proteção local testada; Web e WhatsApp/SM Click não mudam. ACS continua
**DEMO** com autenticação/cadastro/visitas/device/offline **BACKEND CONTRACT REQUIRED**.
Não usar esta correção para liberar envio acumulado ou afirmar piloto REAL pronto.
Autorrevisão: SELF_REVIEW_ONLY; testes locais não são CI, revisão ou merge humanos.
