# Compartilhamento pela interface Android — laboratório

## OBSERVED FACTS / escopo

Incremento de instrumentação sobre93e4fbb5ba64af6c8011687989a896a01e1c1991,
PR6 empilhado sobre PR5e9a80ef386d207a1bc6fe66bef3969eafa84aae5.
Sem alteração de produção, build, dependências, banco ou contratos.

O teste monta o composable de produção `ShareProgressDialog` com um cartão
sintético criado pelo gerador existente. Usa seu botão real, sem copiar ou
interceptar a construção do Intent. A seleção no chooser Android é limitada
ao receptor próprio `Next2U Lab Receiver`, definido apenas no APK de testes.
Não chama grantUriPermission nem inicia o receptor diretamente.

O receptor executa no UID do APK de testes e abre o stream recebido em ACTION_SEND.
Registra URI, MIME, resumo, flags, hash/tamanho, UID/PID e recusa de escrita.
O teste compara esses valores com o cartão original, exige UID/PID distintos e
negação da leitura antes de usar a interface. Capturas mostram diálogo, chooser
e receptor. O serviço Messenger já existente ganha somente a leitura do recibo
sintético; mantém a filtragem do UID chamador.

Somente `.storagelab` em emulador novo, Application neutra e ambos APKs sem
INTERNET. Marcadores e recibos impedem sobrescrever execução anterior; nunca
apagar dados para repetir. O runner confere fonte limpa/SHA, AVD, hashes e
isolamento dos pacotes e repete a regressão das cinco fases de grant explícito
antes do novo teste de chooser, pois o serviço auxiliar foi ampliado.

Build offline: `:app:assembleDebug :app:assembleDebugAndroidTest -PstorageLab=true`.
Executar `tools/Run-ShareChooserAndroidLab.ps1` com hashes, fonte, serial/nome
do AVD e diretório novo de saída. Resultados efetivos ficam vinculados ao SHA
na entrega externa e no corpo do PR, não são antecipados por este documento.
APKs e dados sintéticos permanecem locais, sem distribuição.

## RECOMMENDATIONS / limites

Classificação **LOCAL/DEMO**. Usa a interface real de compartilhamento isolada
numa Activity de teste; não cobre navegação completa, startup normal, geração
via consultas operacionais, destinatário real, consentimento, aplicativo de
terceiro, aparelho físico, outros Androids ou ciclo completo de concessão após
fechar o receptor. Não comprova exclusão de cópias lidas após revogação.

App Paciente recebe apenas harness; Web Profissional, Tablet ACS e WhatsApp/SM Click
sem alterações. Nenhum ID, entidade, API, permissão operacional ou sincronização
novo. **REAL** não decorre desse ensaio; contratos centrais pendentes seguem
**BACKEND CONTRACT REQUIRED**. Produção/JVM/build sem delta permite preservar
as verificações anteriores em seus SHAs originais sem repetir a suíte toda.
CI ausente mantém DRAFT; revisão local distinta não autoriza merge/aceite humano.

Referências de reprodução: [Compose isolado](https://developer.android.com/develop/ui/compose/testing/common-patterns)
e [UiAutomation](https://developer.android.com/reference/android/app/UiAutomation).
