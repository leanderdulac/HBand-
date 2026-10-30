# BLE em background no iOS

VeepooBleSDK **é dono** do `CBCentralManager` (`VPBleCentralManage.centralManager`).
Não existe API do vendor para passar `CBCentralManagerOptionRestoreIdentifierKey`.
Um sidecar com o mesmo restore id **não** via as ligações do VE30 — o iOS só
restaura o manager que fez o `connect`. Por isso o sidecar antigo não relançava
a sessão depois do silêncio em background.

Este app **substitui** o manager do SDK, no `didFinishLaunching`, por um
`CBCentralManager` com:

- restore id `br.com.next2u.saude.paciente.ble`
- delegate `VeepooCentralForwarder` (encaminha para o SDK e intercepta
  `willRestoreState` / disconnect)

A UI (`VeepooSession.shared`) nasce aí, **antes** do store abrir, para o iOS
conseguir entregar `willRestoreState` (TN3115).

## O que está configurado

| Peça | Onde |
|---|---|
| `UIBackgroundModes` = `bluetooth-central`, `fetch`, `processing` | `iosApp/Next2UPaciente/Info.plist` |
| Restore identifier | `br.com.next2u.saude.paciente.ble` no manager do Veepoo (não num sidecar vazio) |
| Pending connect sem timeout do app | `central.connect(peripheral, options:)` + `connectionTimeout = .max` (o 30 s do vendor cancelava a pending connect) |
| Notify que acorda o processo | `CBConnectPeripheralOptionNotifyOnConnectionKey` / `Disconnection` / `Notification` |
| Reconnect no launch / foreground | UUID em `UserDefaults` `last_veepoo_uuid` + `retrievePeripherals` |
| Upload a partir do callback BLE | `recordAndFlush` em cada FC/SpO₂; `beginBackgroundTask` em volta da rede |
| Sem timers de rotação | FC↔SpO₂ muda no próprio notify ( `asyncAfter` congela em background) |
| Diagnóstico | cartão em Envios e Ajustes + `os_log` categoria `ble` |

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
6. `BGTaskScheduler`: `BGAppRefresh` (`br.com.next2u.saude.paciente.ingest.refresh`,
   `earliestBeginDate` ~15 min) é o slot preferido; `BGProcessing`
   (`br.com.next2u.saude.paciente.ingest`) fica de reserva. **Não** promete 15 min.
   O flush principal continua o callback BLE. Force-quit **para** os envios.
7. Exceção de bateria (PR #19) **não existe** no iOS. Ligar Atualização em
   Segundo Plano; não inventar «liberar bateria».
8. No **iOS 26**, alguns toggles do Control Center só relançam apps com
   AccessorySetupKit (fora deste PR).

## O que Leandro deve testar

1. Parear o VE30, deixar FC a enviar (~12 s). Confirmar HTTP 200 em Envios.
2. Ir à tela inicial **sem** deslizar o app para fechar. Esperar 20–40 min.
   Os envios devem continuar ou retomar sozinhos quando o relógio notificar.
3. Abrir Envios / Ajustes: cartão **Diagnóstico BLE** com último evento,
   estado da ligação e último HTTP. No Console.app: subsystem
   `br.com.next2u.saude.paciente`, categoria `ble`.
4. Desligar o Bluetooth do iPhone: o cartão deve dizer que o rádio está off
   e os envios param. Religar: pending connect / restore deve voltar.
5. **Force-quit** (fechar pela lista): o app **não** deve voltar sozinho.
   Abrir o app deve religar o último UUID.
6. Reinstalar o app: **não** espera-se restore automático — parear outra vez.

## Impacto no piloto

Telemetria 24 h como no Android continua **melhor esforço**. O relógio deve
guardar medidas automáticas; o iPhone sincroniza ao notify, ao restore, ou
ao abrir. Compare a fila iOS com a Android no mesmo `patient_id`.
