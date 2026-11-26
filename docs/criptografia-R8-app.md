# R8 — Criptografia no aparelho (Next2U Paciente)

Documento para o piloto com go-live em 30/11/2026. Descreve onde os dados ficam, o que passou a ser cifrado nesta entrega e as respostas sugeridas para as declarações da Play Console e da App Store. Não altera a ficha das lojas e não publica build.

A chave Gemini **não foi removida nem desativada**. O resumo com IA continua dependendo de `AI_INSIGHT_ENABLED` e do consentimento do paciente. Nenhuma tela técnica nova foi adicionada.

## Onde os dados ficam

### Android (`br.com.next2u.saude.paciente`)

| Dado | Onde | Em repouso |
| --- | --- | --- |
| Fila de envio, medições do relógio, perfil, hidratação, respiração, medições avançadas | Room `healthtech_wearable_db` | SQLCipher (AES-256). A senha de 32 bytes é gerada no aparelho e guardada cifrada (AES-GCM) em `hband_sqlcipher`; a chave dessa cifra fica no Android Keystore, alias `hband_sqlcipher_master`. |
| Chave do Core (override `X-API-Key`) | Antes: `hband_settings` / `custom_api_key` em texto claro. Agora: `hband_credentials`, valor AES-GCM | Chave de envelopamento no Android Keystore, alias `hband_credential_master`. No Robolectric, que não tem Keystore, os testes usam AES-GCM com chave só na memória do processo. |
| Chave Gemini | `BuildConfig.GEMINI_API_KEY`, lida do `.env` (gitignored) pelo Secrets Plugin. Não é gravada em preferências. | Não sai do binário. O app só chama `https://generativelanguage.googleapis.com` se a flag e o consentimento estiverem ligados. |
| Chave do Core embutida no build | `BuildConfig.HEALTHTECH_INGEST_API_KEY`, também do `.env` | Igual à Gemini: segredo de build, não de preferência. O override do aparelho, quando existe, substitui essa chave. |
| URL do Core, tema, limiares de FC, consentimento da IA, último MAC/nome do relógio, backoff | `hband_settings` e outras preferências | Não são o prontuário nem a chave. O MAC é identificador do relógio, não a fila clínica. |
| Exportação CSV / imagem de compartilhamento | cache temporário, aberto pelo seletor de compartilhamento | Fora do banco. Some com o cache. Não foi cifrado porque o paciente precisa abrir o arquivo. |

Arquivos de backup da nuvem e de transferência de aparelho continuam excluindo o banco, `hband_sqlcipher` e, agora, `hband_credentials` e as cópias temporárias da migração.

### iOS (Next2U Paciente, bundle `br.com.next2u.saude.paciente`)

| Dado | Onde | Em repouso |
| --- | --- | --- |
| Fila, medições, perfil e bem-estar | `Documents/next2u-healthsync-snapshot.bin` | AES-256-GCM (CryptoKit). A chave de 256 bits fica no Keychain (`kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`). O arquivo é gravado com `completeUntilFirstUserAuthentication`. |
| Fila antiga em JSON | `Documents/next2u-healthsync-queue.json` | Texto claro legado. Na abertura, os itens que ainda não estão no snapshot entram nele e o JSON é apagado. JSON ilegível não é apagado. |
| Chave do Core | `Info.plist` / `Next2UIngestApiKey` = `$(NEXT2U_INGEST_API_KEY)` no momento do archive. O override da tela de desenvolvedor fica só na memória. | Não vai para UserDefaults. |
| Chave Gemini | `Info.plist` / `Next2UGeminiApiKey` = `$(NEXT2U_GEMINI_API_KEY)` | Continua no binário. O envio ao Gemini segue a flag e o consentimento. |
| Nome do relógio, UUID, consentimento da IA, lembrete de FC | UserDefaults | Estado de interface, não o prontuário. |

A classe de proteção pedida é `completeUntilFirstUserAuthentication` de propósito: o envio em background depois do primeiro desbloqueio do dia continua possível. `Complete` (só com o aparelho desbloqueado) derrubaria o upload com a tela bloqueada.

## Tráfego

- Core e Gemini já usavam HTTPS. A configuração de ingest rejeita URL que não seja HTTPS sem usuário, senha, query ou fragmento.
- Android: `android:usesCleartextTraffic="false"` e `network_security_config.xml` com `cleartextTrafficPermitted="false"`. Vale também para API 24–27, em que o padrão da plataforma ainda permitia HTTP.
- iOS: ATS padrão. `NSAllowsArbitraryLoads` está `false` e não há exceção de domínio. `ITSAppUsesNonExemptEncryption` permanece `false` (ver declaração da App Store abaixo).

## Migração sem apagar a fila

### Android — banco em texto claro

Aparelhos que já abriram o banco com SQLCipher **não são regravados e não trocam de chave**. O arquivo cifrado não tem o cabeçalho `SQLite format 3` e a abertura segue o caminho anterior.

Se o arquivo ainda for SQLite comum:

1. `PRAGMA wal_checkpoint(FULL)`. Se o checkpoint não concluir, a migração para e o arquivo fica como está.
2. Lê versão e células (fila, perfil, chave que porventura esteja numa tabela, demais tabelas).
3. Copia o arquivo para `healthtech_wearable_db.plaintext-preserved`.
4. Exporta para um arquivo temporário com SQLCipher, usando a mesma senha de 32 bytes que o Room passa ao `SupportOpenHelperFactory`. A senha é criada no Keystore só nesse caso; um arquivo opaco (já cifrado ou irreconhecível) **não** ganha senha nova.
5. Relê o temporário e compara com o snapshot. Se diferir, ou se o temporário ainda parecer SQLite sem cifra, o original permanece.
6. Troca o arquivo. Se a troca ou a releitura falhar, o texto claro volta para o lugar.
7. Só então apaga a cópia em texto claro, o arquivo aposentado e os sidecars `-wal` / `-shm` / `-journal` (o checkpoint já tinha incorporado o WAL).
8. `openVerified` apaga sobras de texto claro **depois** que `SELECT 1` no banco cifrado funciona.

Falha no meio do caminho: o app entra no estado de armazenamento indisponível que já existia. A orientação continua sendo não limpar os dados do app e não reinstalar — isso apagaria a fila e a chave do Keystore.

### Android — chave do Core

- Preferência ausente continua ausente (vale a chave do build).
- Override vazio continua vazio e **não** reativa a chave do build.
- `custom_api_key` em texto claro é envelopada e removida só depois do `commit` da cópia cifrada. Se o Keystore falhar, o texto claro permanece e a sessão ainda usa o valor.
- Se a cópia cifrada não abre e o texto claro ainda está lá, o texto claro é mantido.

### iOS — snapshot e fila

- Snapshot que abre com a chave do Keychain não é reescrito.
- Arquivo que é JSON com `schemaVersion` e não abre como AES-GCM é cifrado num temporário, conferido byte a byte do texto e só então substitui o original (`replaceItemAt`). Falha deixa o arquivo como estava.
- A fila JSON legada é absorvida pelo snapshot (itens cujo `clientReadingId` ainda não está lá) e apagada só depois da gravação cifrada. JSON inválido não é apagado.

## O que os testes cobrem — e o que não cobrem

Nesta entrega, `:app:testDebugUnitTest` (934 testes) e `:shared:jvmTest` (75 testes) passaram sem falha. A suíte roda na JVM (sem `libsqlcipher.so` e sem Android Keystore):

- AES-GCM de verdade na chave do Core e no snapshot de migração: o arquivo resultante não contém a chave nem a leitura em claro, e a leitura volta igual.
- Export que falha ou snapshot que não confere **não** substitui o SQLite original.
- Arquivo opaco não é migrado e não recebe chave nova.
- Cabeçalho SQLite pode receber a chave envelopada sem alterar os bytes do arquivo.
- Fila JSON legada entra no snapshot e o arquivo some; JSON ilegível fica.
- A decisão de migrar o snapshot iOS (`SnapshotAtRest`) aceita só JSON com `schemaVersion`.
- O manifesto e o `Info.plist` bloqueiam cleartext; `GEMINI_API_KEY` e `AI_INSIGHT_ENABLED` continuam referenciados.

Não comprovam, e o piloto não deve tratar como comprovado:

- Android Keystore, SQLCipher nativo, falha de energia no meio do `rename`, nem o aparelho físico.
- Compilação Swift/Xcode deste delta (o runner desta entrega é Linux).
- Um banco SQLCipher já em uso no piloto: o código não o reabre com outra senha. Isso precisa continuar verdadeiro no aparelho de ensaio antes de atualizar quem tem fila acumulada.

## Respostas sugeridas — Play Console

Não preencher a ficha por esta entrega. Texto para quem for declarar:

**Segurança dos dados**

- Os dados são criptografados em trânsito? **Sim.** HTTPS/TLS para o Core e para o Gemini. Cleartext desligado (`usesCleartextTraffic=false` e Network Security Config).
- Os dados são criptografados em repouso? A pergunta da Play fala sobretudo do que **vocês** armazenam nos servidores. No aparelho, o banco local é SQLCipher e a chave do Core override é AES-GCM com chave no Android Keystore. No servidor, a resposta continua sendo a do Core (fora deste app).
- Os dados de saúde (frequência cardíaca, oxigenação, pressão, temperatura, atividade, perfil) são coletados para o funcionamento do monitoramento e enviados ao Core só pela fila, com a chave de API no header `X-API-Key`.
- O resumo com IA é opcional: só com `AI_INSIGHT_ENABLED` e consentimento. O destino é a API Google Gemini por HTTPS. A chave Gemini não deve ser colada no formulário.

**Criptografia / exportação (se a Play perguntar)**

- O app usa criptografia além de HTTPS: SQLCipher (AES-256) e AES-GCM via Android Keystore.
- Algoritmos publicados, sem criptografia proprietária. Enquadra-se, em geral, na isenção de criptografia de massa (mass-market) da EAR dos EUA. Quem envia o app confirma com o jurídico; este documento não é parecer.

## Respostas sugeridas — App Store Connect

**Does your app use encryption?** Sim, mas só criptografia do sistema e HTTPS.

**Is it exempt?** A resposta coerente com o binário atual (`ITSAppUsesNonExemptEncryption = false`) é **sim, isento**:

- HTTPS/ATS (URLSession).
- CryptoKit AES-GCM, Keychain e Data Protection, que são APIs da Apple.
- Não há algoritmo proprietário nem SQLCipher no iOS.

A isenção usual é a de criptografia que faz parte do sistema operacional, para confidencialidade dos dados do usuário. Se o jurídico entender que a isenção não se aplica, o plist precisa passar a `true` e o formulário de exportação (ERN / autodeclaração anual francesa, se for o caso) precisa ser enviado **antes** do próximo archive. Isso não foi feito aqui de propósito: mudar o booleano dispara o questionário no upload e esta entrega não publica build.

**ATS:** sem exceção de cleartext.

**Privacidade (nutrition labels):** os tipos de dado de saúde não mudam com o R8. Muda a proteção no aparelho, não a coleta.

## Riscos da migração

1. **Keystore ou Keychain perdido** (limpar dados, desinstalar, alguns bugs de OEM, restauração sem o item do Keychain): o banco/snapshot cifrado não abre e o app **não** cria outra chave por cima. A fila fica no arquivo, ilegível, até recuperação com o material original. Não limpar o app como tentativa de conserto.
2. **Queda de energia entre o rename e o apagamento do texto claro:** a cópia `.plaintext-preserved` ou `.plaintext-retired` pode permanecer. Ela só é apagada depois que o banco cifrado abre. Enquanto isso há uma cópia em claro no diretório privado do app, fora do backup configurado.
3. **Checkpoint WAL ocupado:** a migração não começa. O app fica indisponível e o SQLite antigo permanece. É o mesmo sintoma de “armazenamento indisponível”, com os dados no arquivo.
4. **SQLCipher nativo não foi exercitado neste runner.** O teste de migração usa AES-GCM no lugar do `sqlcipher_export`. Um erro no exportador nativo, no aparelho, cai no caminho que preserva o arquivo — mas a fila não sobe até o banco abrir.
5. **Chave do Core:** se a envelopagem falhar, o texto claro continua em `hband_settings` mais uma sessão. Se a envelopagem funcionar e o `commit` da URL falhar, a chave anterior é restaurada.
6. **HTTP de SDK de terceiro** (relógio) passa a falhar em Android anterior à API 28 se algum endpoint for cleartext. Core e Gemini são HTTPS. Não há exceção de domínio.
7. **iOS:** a fila JSON ilegível fica no Documents até alguém decidir o que fazer. Não é apagada automaticamente.
8. **Atualizar um aparelho do piloto com fila acumulada** continua exigindo ensaio num aparelho de laboratório, não neste repositório. Esta entrega não faz merge, não assina release e não mexe na ficha da Play nem do TestFlight.
