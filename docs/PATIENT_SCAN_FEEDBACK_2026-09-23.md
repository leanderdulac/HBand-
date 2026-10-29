# Motivo de falha da busca Bluetooth

## OBSERVED FACTS

Continuação autorizada das melhorias funcionais do app Paciente. Preflight
limpo em `7fd2f99eaa6c0c3fd924dcaa256477677ec87794`, branch
`codex/patient-responsive-devices`, remoto `leanderdulac/HBand-`.
GitHub confirmou main `f35d12b26c5a2305004271c2a05068782a1c9fc8`, PR #4 HEAD
`9a239d9113bc671624643acc975b3e10042f4a57`, base
`04938c46fdba3ab59789943f41b7a48ea5b4b72e`, PRs #1/#2 inalterados.
Ancestralidade confirmada, sem operação Git pendente. Continuação local
reconciliada conforme FRONTEND_UI_HANDOFF e pedido atual. Sem push/merge;
permissão remota de escrita indisponível conforme preflight anterior.

Antes, falhas da busca eram somente registradas em log. Na tela, Bluetooth
desligado e busca sem resultados terminavam com a mesma apresentação genérica.

## Mudança

Falhas locais passam por um tipo fechado, da sessão/manager ao ViewModel e à
tela: permissão necessária, Bluetooth desligado, scanner indisponível, falha de
busca e falha de encerramento. A causa desligado vem da observação do adaptador;
nenhuma causa específica é deduzida de uma falha genérica de startScan.
O estado é limpo no início de nova tentativa e permanece no ViewModel durante
navegação/recriação. Não é persistido como evento de paciente.
O aviso de Bluetooth desligado descreve a tentativa anterior, sem alegar
monitoramento contínuo do adaptador enquanto a pessoa altera configurações.

A tela apresenta a orientação correspondente com anúncio acessível. Oferece
permissões apenas quando pertinentes, preservando prioridade da resposta da
solicitação de permissão atual. Oculta erro antigo enquanto busca. Não abre
configurações, concede permissão, liga Bluetooth ou tenta novamente sozinha.
Mantidos prazo, proteção contra callbacks antigos e política de conexão.

Escopo: classificação/estado de falha, encadeamento até a tela, testes e este
registro. Sem SDK, dependências, novas permissões, APIs, schemas, IDs, dados de
paciente, transportes ou regras clínicas. Web, Tablet ACS e WhatsApp/SM Click
sem impacto. Concorrência/offline/sincronização de registros inalterados.
Fixtures de teste **DEMO**; mensagens não comprovam conexão **REAL**, leituras
ou integração backend. Contratos backend existentes permanecem inalterados.

## Verificação

Fonte/testes finais em `3082a4af46deffa3e763ba53a6d62c62fed169d5`, Windows/Codex
local, 23/09/2026, JDK 21.0.12.1, Gradle 9.3.1, cache offline.
Comando pelo wrapper: `:app:testDebugUnitTest :app:assembleDebug
:app:compileReleaseKotlin :app:lintDebug :app:lintRelease
-Pandroid.builder.sdkDownload=false --offline --console=plain --continue`.
pnpm não se aplica ao checkout Android Kotlin.

- Rodada focada antes do primeiro commit: **15 PASS**.
- Suíte completa final: **211 casos, 210 PASS, 0 falhas, 1 SKIP** pela limitação
  conhecida de FileProvider no Windows.
- Testes cobrem classificação, prioridade da resposta de permissão atual,
  ausência de abertura automática de configurações, anúncio acessível e nova
  tentativa ocultando erro anterior. Proteções de callbacks antigos revalidadas.
- APK DEBUG, compilação Kotlin release e lint DEBUG/RELEASE: **PASS**.
- Lint: **0 erros e 48 avisos** em cada configuração. Comando conjunto: **PASS**.

O primeiro candidato `205defa8bbdfbf6db892db05baa843fad9f651b5` também passou
nos checks e no ensaio físico de mensagem de Bluetooth desligado, botão de
nova tentativa habilitado e preservação ao navegar Ajustes → Relógio. A revisão
final alterou apenas o texto desse aviso, sua asserção e documentação; a suíte
completa foi repetida no SHA final. Logs anteriores preservados com o SHA.

APK final `next2u-paciente-scan-feedback-3082a4a.apk` instalado via atualização
no M8 WIFI Android 13, sem limpeza de dados. SHA-256 do arquivo e da cópia
extraída da instalação: `4CF41B0C42E94093626CE5B3935E4F2BEA3D6C743F1D05585563AF1EF5DE01D7`.
Na versão final, abertura, navegação a Relógio e tentativa de busca confirmaram
o texto revisado. Captura física inspecionada. Bluetooth já desligado e mantido
em bluetooth_on=0; nenhuma conexão, medição ou concessão de permissão disparada.
ACS permanece 1.0.6 (16), última atualização `2026-09-15 13:35:21`.
Fonte/orientação/rotação automática preservadas em 1.0/0/1.
Commit posterior apenas documental; app/ idêntico ao candidato final.

Evidências: `C:/CDev/Next2U-Patient-Delivery/2026-09-23-scan-feedback`.
**SELF_REVIEW_ONLY**, não CI ou revisão independente.

## RECOMMENDATIONS

Complementar homologação com relógio físico e cenários reais de concessão e
revogação de permissão. Um teste de mensagem não demonstra conexão ou medição.
