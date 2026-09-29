# Intervalo de gravação independente da hora civil

## OBSERVED FACTS

Continuação autorizada no baseline local limpo
`7ee7da10e84e249cd8b60284cf6b220885cfb31f`, descendente da PR #4.
GitHub confirmado em 24/09/2026: main
`f35d12b26c5a2305004271c2a05068782a1c9fc8`; PR #4 DRAFT no HEAD
`9a239d9113bc671624643acc975b3e10042f4a57`, base
`04938c46fdba3ab59789943f41b7a48ea5b4b72e`. PRs #1/#2/#4 preservadas.
Branch local: `codex/patient-monotonic-recording`.

`IngestDeduper` usava `System.currentTimeMillis()` para controlar a repetição
de leituras iguais. Após recuo da hora civil, `nowMs - lastAt` ficava negativo
e continuava menor que o intervalo mínimo, rejeitando a mesma assinatura até
alcançar novamente a hora antiga mais 30 segundos. Um teste com recuo injetado
reproduziu essa rejeição antes da correção. Evidência em `before-fix.log/.xml`.
Não se alterou a hora do computador/tablet para reproduzir o caso.

Agora o intervalo usa `System.nanoTime()` convertido em milissegundos, com
fonte injetável para testes determinísticos. Esse tempo relativo é restrito ao
processo: não é persistido, enviado nem convertido em data de medição.
Um recuo de fonte injetada reinicia a janela ao aceitar a leitura atual,
evitando bloqueio atrás do valor anterior. O deduplicador conserva o intervalo
de 30 segundos, sua assinatura e os filtros de elegibilidade existentes.
Leituras com assinatura diferente continuam elegíveis sem esperar a janela;
callbacks rejeitados não adiam o limite da última leitura aceita.

Horário/valor da medição, fuso, data do aparelho, serialização, IDs, banco e
contratos de envio permanecem os existentes. Não se converte relógio local em
autoridade clínica/central e não se corrige retrospectivamente o histórico.
A separação entre duração local e ocorrência segue o alcance de ADR-007.

## Verificação

Quatro casos novos cobrem recuo da fonte de intervalo, avanço/recuo da data na
amostra com duração independente, mudança de valor e filtros sem consumir a
janela, origem negativa do contador e rejeições que não prolongam a janela.
Os testes existentes do coletor continuam cobrindo preferência, vida útil,
gravação posterior a falha e cancelamento. Dados são sintéticos somente nos
testes; nenhum registro simulado é inserido no tablet.

Evidências por SHA, APK e validação física em
`C:/CDev/Next2U-Patient-Delivery/2026-09-24-monotonic-recording/`.
Comando final: `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:compileReleaseKotlin :app:lintDebug :app:lintRelease -Pandroid.builder.sdkDownload=false --offline --console=plain --continue`.
O teste físico é de coleta e preservação da pausa após atualização; o salto
temporal é coberto pelos testes determinísticos, não por mudança do relógio real.

## RECOMMENDATIONS / limites

**REAL local:** decisão de repetição das leituras no app paciente. O intervalo
não é agendamento nem garantia de coleta durante suspensão/encerramento do
processo. Não há validação clínica, recuperação de dados antigos ou recibo de
ingestão central. Identidade/autorização e integrações ainda sem confirmação
permanecem **BACKEND CONTRACT REQUIRED**. Web Profissional, Tablet ACS e
WhatsApp/SM Click sem alterações.

**SELF_REVIEW_ONLY:** verificações locais, sem CI nem revisão independente.
Continuidade local não publicada; revisar a composição com as PRs existentes
antes de integração. Sem merge ou deploy.
