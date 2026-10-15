# Next2U Paciente — esqueleto iOS

Casca SwiftUI da fase 0. Não envia vitais, não liga o Veepoo e não assina
para TestFlight até o Leandro preencher o Team ID no Mac.

## Placeholders (preencher no Mac, não nesta PR)

| Chave | Valor atual | Onde |
|---|---|---|
| `DEVELOPMENT_TEAM` | `TEAMIDXXXX` | `Config/Next2UPaciente.xcconfig` |
| `PRODUCT_BUNDLE_IDENTIFIER` | `br.com.next2u.saude.paciente` | mesmo xcconfig |
| Signing | Automatic | Xcode cria App ID + perfil ao escolher o Team |

## No Mac (Xcode já instalado)

1. Gerar o framework KMP (só neste Mac; o CI Linux não faz isso):

   ```bash
   ./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
   ```

2. Abrir `iosApp/Next2UPaciente.xcodeproj`.
3. Signing & Capabilities → selecionar o Team Next2U → substituir `TEAMIDXXXX`.
4. Confirmar ou trocar o bundle ID.
5. Rodar no Simulator. As cinco abas (Início, Histórico, Relógio, Envios,
   Ajustes) usam os tokens Claro do PR #17.

## Depois do primeiro archive

Passos TestFlight e Ad Hoc: `docs/ios-port/PLAN.md` §5.4.

O `VeepooBleSDK.framework` **não** está vendored aqui. Copiar de
[HBandSDK/iOS_Ble_SDK](https://github.com/HBandSDK/iOS_Ble_SDK) na fase 2,
conferindo a versão contra `vpprotocol-2.3.80.15` do Android.
