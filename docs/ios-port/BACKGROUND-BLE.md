# BLE em background no iOS — primeiro passo (fase 2)

## O que está configurado

| Peça | Onde |
|---|---|
| `UIBackgroundModes` = `bluetooth-central`, `fetch`, `processing` | `iosApp/Next2UPaciente/Info.plist` |
| Restore identifier | `br.com.next2u.saude.paciente.ble` (`VeepooSession` + `CBCentralManager`) |
| Auto-reconnect do vendor | `VPBleCentralManage.automaticConnection = true` |
| Último endereço | `UserDefaults` `last_veepoo_address` — no restore, o app volta a escanear e liga se achar o VE30 |

O SDK Veepoo **é dono** do `CBCentralManager` interno. Não dá para passar
o restore id a esse manager. O sidecar (`CBCentralManager` nosso) só
recebe `willRestoreState` e dispara o scan/connect Veepoo de novo.

## Limites (não são bugs do app)

1. O iOS não deixa o processo correr para sempre. Não há FGS Android.
2. Notify GATT pode acordar o app; o SDK precisa reenviar senha / FC.
3. [TN3115](https://developer.apple.com/documentation/technotes/tn3115-bluetooth-state-restoration-app-relaunch-rules):
   force-quit **não** relança. No **iOS 26**, force-quit e alguns toggles
   do Control Center só relançam apps que configuraram o acessório com
   **AccessorySetupKit**.
4. Reinício com passcode: sem BLE até o primeiro unlock.
5. `BGTaskScheduler` (id `br.com.next2u.saude.paciente.ingest`) está
   declarado para o flush da fila; **não** está agendado nesta fase.
6. Exceção de bateria (PR #19) **não existe** no iOS. Educar Background
   App Refresh; não inventar diálogo de “liberar bateria”.

## Impacto no piloto

Telemetria contínua 24 h como no Android é **melhor esforço**.
O relógio deve guardar medidas automáticas; o iPhone sincroniza ao
abrir, ao notify, ou após restore. Compare a fila iOS com a Android
no mesmo `patient_id` (ids de aparelho distintos).
