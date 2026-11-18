# BLE em background no iOS

VeepooBleSDK **é dono** do `CBCentralManager` (`VPBleCentralManage.centralManager`).
Não existe API do vendor para passar `CBCentralManagerOptionRestoreIdentifierKey`.
Um sidecar com o mesmo restore id **não** via as ligações do VE30 — o iOS só
restaura o manager que fez o `connect`. Por isso o sidecar antigo não relançava
a sessão depois do silêncio em background.

Este app **injeta** o restore id no `CBCentralManager` criado pelo SDK
(`N2UBleRestore.m`) e, se a injeção falhar, substitui o manager no
`didFinishLaunching` por um com:

- restore id `br.com.next2u.saude.paciente.ble`
- delegate `VeepooCentralForwarder` (encaminha para o SDK e intercepta
  `willRestoreState` / disconnect)
- `connectPeripheral:options:` com `NotifyOnConnection` /
  `NotifyOnDisconnection` / `NotifyOnNotification` (QA1866), para o iOS
  relançar o processo nas notificações GATT

A UI (`VeepooSession.shared`) nasce aí, **antes** do store abrir, para o iOS
conseguir entregar `willRestoreState` (TN3115).

## Causa do silêncio (Sérgio, 14/11/2026, build 15)

O envio ao vivo funcionava com o app aberto. A última leitura chegou ao Core
às 17:30 BRT; depois, com o iPhone bloqueado e o app em segundo plano, nada
por mais de uma hora — o painel marca Offline após 50 min.

O que o código do build 15 fazia, e por que parava:

1. **Upload ao vivo** no máximo a cada 60 s; **histórico** no máximo a cada
   5 min; ciclo pontual FC→SpO₂ com pausa de 5–30 min.
2. Em segundo plano tentava FC contínua, mas o teste Veepoo **termina**
   (estado ≥ 2) e o código ia para SpO₂. O SpO₂ é longo; se o iOS suspendesse
   no meio, o watchdog de 3 s **não corre** e não havia mais notify GATT.
3. Os callbacks do SDK/CoreBluetooth saltavam com `Task { @MainActor }` **sem**
   `beginBackgroundTask` na hora. A janela de wake acabava e o trabalho
   agendado era descartado.
4. O POST usava o Darwin/Ktor em sessão *foreground*: morria com a suspensão.
5. `BGAppRefresh` (`earliestBeginDate` 15 min) só esvaziava a fila — que
   estava vazia, porque nenhuma leitura nova tinha sido gravada. O iOS pode
   adiar esse slot por muito mais que 15 min.

## Passos e pressão (build 17)

O build 16 enviava FC/SpO₂, mas `recordAndFlush` gravava **sistólica 0, diastólica 0, passos 0**. O Core omite PA se sys/dia não são > 0; passos 0 ia no JSON mesmo assim.

O que o build 17 faz, **sem** quebrar a FC contínua em segundo plano:

| Peça | Comportamento |
|---|---|
| Passos | `veepooSDK_readStepDataWithDayNumber:0` ~1×/min quando não há SpO₂/PA em curso. Depois do histórico, também o total do dia na base do SDK (`veepooSDKGetStepDataWithDate`). Guarda o último valor com horário. |
| Pressão ao vivo | Estágio próprio no ciclo de **primeiro plano**: FC → SpO₂ → `veepooSDKTestBloodStart` (modo 0) se a última PA válida tem ≥ 30 min. O teste dura ~50–55 s; não corre em paralelo com FC/SpO₂. |
| Pressão em segundo plano | **Não** interrompe a FC contínua (o teste longo mataria o notify GATT, o mesmo problema do SpO₂ no build 15). Só numa oportunidade `BGAppRefresh`/`BGProcessing` (`performBackgroundOpportunity`), depois do histórico, se a PA estiver vencida; em seguida reabre a FC. Histórico `origin` continua a mapear sys/dia. |
| Ingestão | `recordAndFlush` manda a última PA válida e os últimos passos > 0. Passos desconhecidos **não** vão no JSON (não se fabrica 0). PA só se sys e dia estiverem na faixa. |
| UI | Início: cartões Passos e Pressão (pt-BR), sem texto técnico. |

## O que está configurado (build 16)

| Peça | Onde |
|---|---|
| `UIBackgroundModes` = `bluetooth-central`, `fetch`, `processing` | `iosApp/Next2UPaciente/Info.plist` — só o que a revisão da App Store justifica: sessão BLE do relógio, `BGAppRefresh` e `BGProcessing` de reserva. Sem `audio`, `location`, `voip` ou `remote-notification`. |
| Restore identifier | `br.com.next2u.saude.paciente.ble` no manager do Veepoo |
| Notify que acorda o processo | `CBConnectPeripheralOptionNotifyOn*` em todo `connect` (swizzle + pending connect) |
| Reconnect | `willRestoreState` + `didDisconnect` → `connect` pendente do iOS (`connectionTimeout = .max`). Sem scan de 1 s. Scan com backoff só em **primeiro plano** e só se o UUID for desconhecido. |
| FC contínua em segundo plano | Enquanto o relógio está ligado, o teste de FC **recomeça** ao terminar. Não troca para SpO₂ nem para PA com o iPhone bloqueado. SpO₂ e PA ao vivo entram no ciclo em primeiro plano; em fundo, SpO₂ pelo histórico e PA só na oportunidade BGTask (build 17). |
| Wake lock | `BleWakeLock` / `BleMain.run`: `beginBackgroundTask` **síncrono** no callback BLE, depois o trabalho no main actor (sem `Task` solto). |
| Upload que sobrevive à suspensão | `URLSessionConfiguration.background` id `br.com.next2u.saude.paciente.ingest.http` (`IngestURLSession`). `handleEventsForBackgroundURLSession` no `AppDelegate`. A fila Ktor continua a fonte da verdade; o Core deduplica por `client_reading_id`. |
| Fallback | `BGAppRefresh` + `BGProcessing`: religa, lê o histórico do relógio, reabre a FC, envia. **Não** promete 15 min. |

## Heartbeat / last_seen (contrato do Core)

O Core (`POST api/v1/wearables/batch-ingest` e o ingest avulso) **exige**
`heart_rate` em 20–250 (`IngestPayload.isIngestibleJson`). Campos extras passam
(`extra=allow`, ex.: `device_label`), mas um corpo **sem FC** é recusado. Não
há endpoint de heartbeat nem campo `last_seen`.

Este app **não** reenvia a última FC para fingir presença — isso inventaria
sinal clínico (o Android também não publica telemetria de keep-alive).
Enquanto o relógio estiver ligado, a FC contínua e o histórico automático
alimentam o Core. Sem amostra nova, o painel marca Offline após 50 min.

Para um heartbeat verdadeiro o Core precisaria, **sem** mudar o contrato das
leituras:

- `POST api/v1/wearables/heartbeat` com `patient_id`, `device_id`,
  `connected_at`; **ou**
- aceitar `ingest_source=companion_keepalive` **sem** `heart_rate`, usado só
  para `last_seen` no painel; **ou**
- o painel usar o estado da ligação BLE em vez da idade da última leitura.

## Limites (não são bugs do app)

1. **Force-quit** (deslizar o app para fechar) **não** relança. O iOS deixa de
   restaurar o `CBCentralManager`. Tem de abrir o next2u SAÚDE outra vez.
   [TN3115](https://developer.apple.com/documentation/technotes/tn3115-bluetooth-state-restoration-app-relaunch-rules).
2. **Bluetooth desligado** (Control Center ou Ajustes) corta a sessão. Não há
   pending connect enquanto o rádio está off.
3. **Reinstalação** apaga `UserDefaults` (UUID do VE30). É preciso parear de
   novo. O Keychain do store sobrevive em alguns casos; o UUID BLE não.
4. Não há FGS Android. Notify GATT *pode* acordar o processo; o sistema pode
   mesmo assim suspender depois de alguns minutos sem eventos.
5. Reinício com passcode: sem BLE até o primeiro unlock.
6. `BGTaskScheduler` **não** promete 15 min. O caminho principal continua o
   notify GATT + URLSession background. Force-quit **para** os envios.
7. Exceção de bateria (PR #19) **não existe** no iOS. Ligar Atualização em
   Segundo Plano; não inventar «liberar bateria».
8. No **iOS 26**, alguns toggles do Control Center só relançam apps com
   AccessorySetupKit (fora deste PR).

## Como testar com o iPhone bloqueado por 1 h

1. Parear o VE30 / Veepoo no next2u SAÚDE. Confirmar batimentos na Início e
   um HTTP 200 (Debug: Envios). **Não** force-quit; só ir para a tela inicial.
2. Bloquear o iPhone. Deixar o relógio no pulso, Bluetooth ligado, o app **não**
   deslizado para fechar. Atualização em Segundo Plano ligada para o next2u.
3. Esperar **60 min**. No painel, a última leitura desse `device_id` deve
   renovar pelo menos a cada ~15–20 min (FC ao vivo ou histórico). Não deve
   ficar Offline (limiar 50 min) se o relógio permaneceu ligado.
4. Abrir o app: a Início mostra as medições; a fila não deve ter crescido
   sem envio. Console.app: subsystem `br.com.next2u.saude.paciente`,
   categoria `ble`; linhas `URLSession background: HTTP`.
5. Desligar o Bluetooth: os envios param. Religar: pending connect / restore
   deve voltar.
6. **Force-quit**: o app **não** volta sozinho. Abrir o app religa o último UUID.

## Impacto no piloto

Telemetria 24 h como no Android continua **melhor esforço** (não há FGS). Com
este build o caminho esperado, relógio ligado e app só em segundo plano, é
FC por notify GATT + POST em URLSession background, com BGTask e histórico
como rede de segurança.
