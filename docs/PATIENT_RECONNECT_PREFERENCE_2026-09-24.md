# Respeitar a preferência de reconexão automática

## OBSERVED FACTS

Continuação autorizada do app paciente. Preflight limpo no baseline local
`0f29fefa9daa858502cf9de8ebde8e989d13ee6e`, descendente da PR #4.
GitHub confirmado em 24/09/2026: main
`f35d12b26c5a2305004271c2a05068782a1c9fc8`; PR #4 DRAFT no HEAD
`9a239d9113bc671624643acc975b3e10042f4a57`, base
`04938c46fdba3ab59789943f41b7a48ea5b4b72e`. PRs #1/#2/#4 e suas bases
preservadas. A continuidade local não foi publicada. Branch deste incremento:
`codex/patient-reconnect-preference`.

O agendamento verificava a preferência, mas seu callback e a restauração
solicitada pelo serviço não a verificavam. O setter apenas persistia o valor.
Três testes reproduziram nova conexão indevida: desligar após agendar;
desligar e religar antes do callback antigo; pedir restauração pelo serviço
com a preferência desligada. Cada cenário produziu dois GATTs em vez de um.
Evidência anterior à correção: `before-fix.log/.xml` na entrega local.

O setter agora cancela a tentativa agendada ao desligar. O callback e
`reconnectLastDevice` também conferem a preferência antes de iniciar conexão.
O caminho manual `connectDevice` continua disponível e desligar a opção não
desconecta a sessão já estabelecida. Religá-la não ressuscita o callback antigo;
perdas futuras com a opção ligada seguem a política existente.

Escopo: três mudanças no gerente BLE, testes de regressão e este documento.
Sem alteração de SDK, número/intervalo das tentativas, IDs, credenciais,
telemetria, armazenamento, manifesto ou dependências. Web Profissional,
Tablet ACS e WhatsApp/SM Click sem mudanças. A fila de envio continua separada
do estado BLE e respeita a pausa de autorização implementada anteriormente.

## Verificação e limites

Cinco novos casos exercitam o gerente existente, callbacks Android simulados
e relógio virtual: os três problemas reproduzidos, conservação da sessão ativa
e reconexão permitida depois de perda com a preferência ligada. Dados de teste
são sintéticos; não se introduzem leituras no banco do aparelho.

Evidências do SHA candidato, APK, testes, lint e validação física ficam em
`C:/CDev/Next2U-Patient-Delivery/2026-09-24-reconnect-preference/`.
Comando final: `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:compileReleaseKotlin :app:lintDebug :app:lintRelease -Pandroid.builder.sdkDownload=false --offline --console=plain --continue`.

**REAL local:** controle do cliente BLE existente. Não comprova precisão
clínica, estabilidade prolongada ou recebimento na plataforma. **SELF_REVIEW_ONLY**:
checks locais não são CI nem revisão independente. Sem publicação, merge ou
deploy. Contratos de integrações ainda ausentes continuam **BACKEND CONTRACT REQUIRED**.

## RECOMMENDATIONS

Manter a preferência originalmente escolhida pelo usuário após a validação
física. Não desligar o rádio do tablet para simular falhas durante a coleta.
Revisão independente e reconciliação da pilha antecedem publicação; autorização
do Core e homologação ponta a ponta permanecem pendentes do responsável.
