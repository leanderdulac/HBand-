# FileProvider — permissão entre identidades Android

## OBSERVED FACTS / alcance do candidato

Incremento sobre `5ceb860d1a7b624ff2fe8de7e2ac45e5e0f926b6`, publicado no PR6
empilhado sobre PR5 `e9a80ef386d207a1bc6fe66bef3969eafa84aae5`.
Somente instrumentação, runner e este documento: sem mudança de produção,
dependências, banco, build, contrato ou configuração operacional.

`ProviderGrantAndroidLabTest` gera dois cartões sintéticos pelo gerador existente.
`ProviderProbeService`, presente somente no APK de instrumentação, executa em
outro processo/UID; o teste confere ambos explicitamente. O serviço usa apenas
Android/JDK, pois o classpath de produção não está disponível nesse processo.
Ele recusa pacote normal, hardware físico, INTERNET e comandos de outro UID.

O ensaio exige emulador novo, pacote `.storagelab`, Application neutra, ausência
de INTERNET nos dois APKs e ausência de banco operacional. Recusa repetição
após criar um marcador, inclusive se uma execução anterior falhou. Não apaga dados.

Cinco fases, com abertura real de URI pelo UID receptor:

1. Sem grant, leitura recusada por SecurityException.
2. Grant explícito de leitura para o pacote receptor permite ler bytes/hash exatos.
3. A segunda URI não concedida continua recusada.
4. Abertura `rw` com somente leitura concedida é recusada; nenhum byte é escrito.
5. Revogação impede uma nova abertura. Arquivos originais permanecem idênticos.

`tools/Run-ProviderGrantAndroidLab.ps1` exige SHA de fonte limpa, hashes dos APKs,
serial/nome do AVD e diretório novo de evidência. Recusa pacotes preexistentes.
Build offline com SDK/cache já disponíveis:
`:app:assembleDebug :app:assembleDebugAndroidTest -PstorageLab=true`.
Resultados efetivos e SHA completo pertencem à entrega externa e ao corpo do PR;
este documento descreve a reprodução, não antecipa aprovação.

## RECOMMENDATIONS / limites

Classificação **LOCAL/DEMO**: receptor próprio de laboratório, sem destinatário
operacional. O teste utiliza `Context.grantUriPermission`; não comprova a entrega
do grant pelo ACTION_SEND/chooser da interface, uso em aparelho físico, versões
Android diferentes, persistência em cache, aceitação por terceiros ou revogação
de descritores já abertos/cópias já lidas. Não amplia consentimento nem divulgação.
Referências: [FileProvider](https://developer.android.com/reference/androidx/core/content/FileProvider)
e [Messenger entre processos](https://developer.android.com/develop/background-work/services/bound-services).

Produção e testes JVM idênticos ao candidato anterior: não repetir suítes inteiras
sem nova questão concreta e não atribuir execuções antigas ao novo SHA. Revisão
distinta local exigida; CI ausente mantém PR DRAFT. Merge/aceite continuam humanos.

Quatro canais: somente harness do App Paciente; Web Profissional, Tablet ACS e
WhatsApp/SM Click sem alterações. Nenhuma entidade, ID canônico, permissão
operacional, API, sincronização, concorrência ou comportamento offline novo.
Capacidade **REAL** não é atestada pelo ensaio; contratos centrais não confirmados
continuam **BACKEND CONTRACT REQUIRED**.
