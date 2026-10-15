# Next2U Paciente — esqueleto iOS

Casca SwiftUI + P0 Veepoo. Team ID Next2U já está no xcconfig.

## Assinatura

| Chave | Valor | Onde |
|---|---|---|
| `DEVELOPMENT_TEAM` | `H87XXMXA5G` | `Config/Next2UPaciente.xcconfig` |
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
3. Signing & Capabilities → Team Next2U (`H87XXMXA5G` já está no xcconfig).
4. Confirmar o bundle `br.com.next2u.saude.paciente`.
5. Rodar no **iPhone**. Checklist: `docs/ios-port/MAC-CHECKLIST.md`.

SDK Veepoo vendored: **2.2.107.15** (`iosApp/Vendor/`). Android:
`vpprotocol-2.3.80.15`. Ver `iosApp/Vendor/THIRD_PARTY_NOTICE.md`.

## Depois do primeiro archive

Passos TestFlight e Ad Hoc: `docs/ios-port/PLAN.md` §5.4.

O `VeepooBleSDK.framework` **já** está em `iosApp/Vendor/` (2.2.107.15).
