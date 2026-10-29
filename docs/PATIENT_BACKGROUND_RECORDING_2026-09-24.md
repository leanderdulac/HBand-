# Gravação das leituras independente da tela

## OBSERVED FACTS

Continuação autorizada no baseline local limpo
`494a81c7264cd8930c58a18bf58478926152e786`, descendente da PR #4.
GitHub confirmado em 24/09/2026: main
`f35d12b26c5a2305004271c2a05068782a1c9fc8`; PR #4 DRAFT no HEAD
`9a239d9113bc671624643acc975b3e10042f4a57`, base
`04938c46fdba3ab59789943f41b7a48ea5b4b72e`. PRs #1/#2/#4 preservadas.
Branch deste incremento: `codex/patient-background-recording`.

A gravação automática de `latestTelemetry` estava em `MainViewModel`, com
vida útil da tela. A conexão BLE pertence à aplicação. Destruir a Activity
cancelava seu coletor de gravação enquanto o serviço e os callbacks continuavam.
No tablet, a Activity foi removida às 10:12:32; o processo 28923 e o serviço
foreground permaneceram ativos. A Activity já não constava da lista de tarefas
e os logs ainda mostravam batimentos positivos às 10:15/10:16. Ao reabrir,
a gravação voltou. As capturas de contagem incluem intervalos com tela aberta;
não representam uma contagem exata dos registros perdidos em segundo plano.

O coletor agora pertence ao mesmo `bleScope` da aplicação. Há um único coletor,
independente dos observadores de tela. O ViewModel mantém a apresentação e a
avaliação de limites cardíacos existente; esta alteração não move alertas para
segundo plano. Cada leitura consulta a preferência persistida
`auto_ingest_live` (Guardar novas leituras), conservando seu valor padrão.
O perfil local é consultado antes de gravar, com o fallback existente do gerente
BLE. Sem novo ID, vínculo, contrato, permissão, SDK ou esquema de banco.

Conservados os filtros e a deduplicação de ingestão. Uma exceção de gravação
é registrada e não encerra a coleta de leituras posteriores. Cancelamento é
propagado. Não há promessa de repetir a amostra que falhou: o deduplicador e
a semântica do StateFlow permanecem os existentes. O envio usa o repositório
atual e sua pausa persistida por falta de autorização.

## Verificação e alcance

Seis testes novos cobrem encerramento/reabertura do observador de tela,
preferência ligada/desligada, filtros e repetição, falha de armazenamento,
cancelamento durante gravação e cancelamento lançado pelo gravador. Usam dados
sintéticos apenas no processo de teste. Nenhuma leitura sintética é inserida
no tablet.

Evidências do SHA candidato, verificações, APK e teste físico em
`C:/CDev/Next2U-Patient-Delivery/2026-09-24-background-recording/`.
Comando final: `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:compileReleaseKotlin :app:lintDebug :app:lintRelease -Pandroid.builder.sdkDownload=false --offline --console=plain --continue`.

**REAL local:** gravação no aplicativo paciente enquanto seu processo e a
conexão permanecem ativos. O Android ainda pode encerrar o processo; não se
garante coleta ininterrupta, recuperação retroativa, precisão clínica ou
recebimento pela equipe. Web Profissional, Tablet ACS e WhatsApp/SM Click sem
alterações. Identidade, autorização, sincronização entre canais e homologação
do Core continuam dependentes dos contratos e acessos correspondentes:
**BACKEND CONTRACT REQUIRED** onde ainda não confirmados.

## RECOMMENDATIONS

Revisar a composição com a pilha existente antes de publicar. Evidência local
é **SELF_REVIEW_ONLY**, não CI nem revisão independente. Sem merge ou deploy.
Validação prolongada de autonomia e sobrevivência do serviço exige outra janela
de observação; o teste desta entrega exercita encerramento da tela com serviço
ativo. Preservar dados e restaurar as preferências após testes físicos.
