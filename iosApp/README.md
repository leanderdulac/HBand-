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

O `VeepooBleSDK.framework` é **só iPhoneOS/arm64**. Use um iPhone físico.

1. Gerar o framework KMP e copiar para `iosApp/Vendor/shared.framework`:

   ```bash
   chmod +x iosApp/Scripts/embed-shared.sh
   ./iosApp/Scripts/embed-shared.sh
   ```

2. Abrir `iosApp/Next2UPaciente.xcodeproj`.
3. Signing & Capabilities → selecionar o Team Next2U → substituir `TEAMIDXXXX`.
4. Confirmar ou trocar o bundle ID.
5. Rodar no **iPhone**. Checklist: `docs/ios-port/MAC-CHECKLIST.md`.

SDK Veepoo vendored: **2.2.107.15** (`iosApp/Vendor/`). Android:
`vpprotocol-2.3.80.15`. Ver `iosApp/Vendor/THIRD_PARTY_NOTICE.md`.

## Depois do primeiro archive

Passos TestFlight e Ad Hoc: `docs/ios-port/PLAN.md` §5.4.

O `VeepooBleSDK.framework` **não** está vendored aqui. Copiar de
[HBandSDK/iOS_Ble_SDK](https://github.com/HBandSDK/iOS_Ble_SDK) na fase 2,
conferindo a versão contra `vpprotocol-2.3.80.15` do Android.
