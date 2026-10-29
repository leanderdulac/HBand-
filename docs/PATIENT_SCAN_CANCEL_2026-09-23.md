# Cancelamento explícito da busca de relógios

## OBSERVED FACTS

Usuário informou Bluetooth ligado e autorizou continuar o desenvolvimento.
Preflight limpo em `93f225a63011cd0406dd396ceff236716917d0ea`, branch
`codex/patient-responsive-devices`, remoto `leanderdulac/HBand-`.
GitHub confirmou main `f35d12b26c5a2305004271c2a05068782a1c9fc8`, PR #4 DRAFT
HEAD `9a239d9113bc671624643acc975b3e10042f4a57`, base
`04938c46fdba3ab59789943f41b7a48ea5b4b72e`, PRs #1/#2 inalterados.
Ancestralidade confirmada e sem operação Git pendente. Continuação reconciliada
conforme FRONTEND_UI_HANDOFF/pedido atual. Sem push/merge; remoto sem escrita
conforme preflight anterior.

No M8, bluetooth_on=1 confirmado. APK anterior extraído corresponde ao SHA-256
`4CF41B0C42E94093626CE5B3935E4F2BEA3D6C743F1D05585563AF1EF5DE01D7`, candidato
`3082a4af46deffa3e763ba53a6d62c62fed169d5` (app/ idêntico ao baseline acima).
Busca física exibiu estado buscando e dois botões de conexão visíveis; depois
retornou a Buscar meu relógio. Permaneceu desconectado. Isso comprova descoberta
**REAL** de anúncios Bluetooth no aparelho, não identidade VE30, pareamento,
leituras, disponibilidade clínica ou integração backend.

## Mudança

Durante uma busca, Parar busca chama ViewModel → manager → sessão existente.
O encerramento invalida callbacks e cancela o prazo já implementado, preserva
a lista encontrada e permite nova busca. Não desconecta relógio, escolhe
dispositivo nem altera reconexão automática. A ação de parada não solicita
permissão: mesmo após revogação deve poder liberar o estado local. Buscar ou
conectar continuam sob as verificações existentes.

Componente exibe o botão somente durante busca e com callback disponível;
não introduz botão sem ação em consumidores anteriores. Alvo mínimo de 56 dp.

Escopo: três arquivos de UI/ViewModel, dois testes e este registro. Sem alteração
de SDK, protocolo, biblioteca, API, schema, ID, permissão declarada, registro de
saúde ou persistência. Web, Tablet ACS e WhatsApp/SM Click sem impacto.
Concorrência/offline de registros inalterados; proteção contra callbacks antigos
reutilizada. Fixtures **DEMO** somente nos testes; descoberta física não comprova
integração backend nem leitura de sinais de saúde.

## Verificação

Código verificado no candidato `5ba7b1a67cedf60e8e96eda68bcfda4eb11fd511`.
Execução local Codex/Windows, JDK 21.0.12.1+1, Gradle Wrapper offline:
`:app:testDebugUnitTest :app:assembleDebug :app:compileReleaseKotlin
:app:lintDebug :app:lintRelease -Pandroid.builder.sdkDownload=false --offline
--console=plain --continue`. Build bem-sucedido. 213 testes: 212 aprovados,
zero falhas/erros e um ignorado (`ProgressImageGeneratorTest`, contrato de
FileProvider com caminhos Android não executável neste ambiente Windows).
Lint debug e release: zero erros e 48 avisos preexistentes em cada variante.
`git diff --check` aprovado. Permissões declaradas dos APKs anterior e novo
comparadas com aapt: nenhuma diferença.

Testes novos cobrem parada com permissões revogadas sem abrir solicitação,
nova busca ainda sujeita a permissão, largura 320 dp/fonte 2,0, alvo mínimo de
56 dp, preservação dos dispositivos e ausência de conexão/desconexão incidental.
A suíte existente também cobre encerramento da sessão e callbacks obsoletos.

Instalação incremental no M8_WIFI Android 13 concluída. APK gerado e APK extraído
do aparelho têm o mesmo SHA-256:
`AACE4019D49D40B3FB26639E61750CA18947C4CABD3C1038D56EE7D3D1BBC0D7`.
Bluetooth ligado confirmado. Ensaio físico mostrou Parar busca e procura ativa;
toque de parada concluído 2.139 ms após iniciar, antes do limite de 12 segundos.
Capturas antes/depois confirmam retorno a Buscar meu relógio e manutenção da
lista. Nova tentativa exibiu busca ativa, encontrou anúncios e terminou pelo
prazo normal. Estado Relógio desconectado permaneceu; nenhum dispositivo foi
selecionado. A primeira captura XML/PNG teve atraso entre leituras, por isso a
verificação de cancelamento foi repetida com capturas rápidas e tempo registrado
em `physical-cancel.json`, `cancel-before-tap.png` e `cancel-after-tap.png`.
Resultado da nova tentativa em `physical-retry.json` e `retry-active.png`.

ACS permanece 1.0.6 (16), última atualização 2026-09-15 13:35:21.
Configuração preservada: Bluetooth=1, fonte=1,0, rotação=0, rotação automática=1.
Sem perfil de paciente provisionado, pareamento ou leituras clínicas nesta rodada.
Evidência física comprova descoberta e controle de busca **REAL**, com os limites
acima; cenários de revogação/fonte ampliada foram verificados em testes locais.
Commit posterior somente deste relatório não altera `app/` nem o APK verificado.

Evidências: `C:/CDev/Next2U-Patient-Delivery/2026-09-23-scan-cancel`.
**SELF_REVIEW_ONLY**, não CI ou revisão independente.

## RECOMMENDATIONS

Homologar conexão e leituras com identificação confirmada do relógio de teste.
Nenhum dos dispositivos encontrados é selecionado por inferência nesta rodada.
