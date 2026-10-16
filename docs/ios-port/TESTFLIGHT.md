# TestFlight — next2u SAÚDE (iOS)

Rascunho interno. **Não publicar na App Store** até a chave Gemini sair do binário
e a ficha de privacidade / política estiverem prontas.

## Pré-requisitos no Mac

1. Xcode 27, Team **H87XXMXA5G**, bundle `br.com.next2u.saude.paciente`.
2. `iosApp/Config/Secrets.local.xcconfig` (gitignored) com a chave real do Core
   e, se o piloto usar o cartão de IA, `NEXT2U_GEMINI_API_KEY`. **Nunca commitar.**
3. Rebuild do KMP e fatias vendor:

```bash
./iosApp/Scripts/embed-shared.sh
./iosApp/Scripts/strip-vendor-archs.sh
```

## Archive + export (sem upload)

```bash
./iosApp/Scripts/archive-testflight.sh
```

Gera `iosApp/build/Next2UPaciente.xcarchive` e um IPA em
`iosApp/build/testflight-export` com `ExportOptions.plist`
(`method=app-store-connect`, `destination=export`). Envie pelo Organizer
(Distribute App → App Store Connect → Upload) ou Transporter.

Versão: `CFBundleShortVersionString` = `0.1.0`, `CFBundleVersion` = `1`
(primeiro TestFlight). Incremente o build a cada envio.

## Assinatura Release

- `CODE_SIGN_STYLE = Automatic`
- `DEVELOPMENT_TEAM = H87XXMXA5G`
- `PRODUCT_BUNDLE_IDENTIFIER = br.com.next2u.saude.paciente`
- `ITSAppUsesNonExemptEncryption = NO`

## Fatias vendor (App Store)

`strip-vendor-archs.sh` deixa só **arm64** device nos `.framework` / `.a`.
Não há `.xcframework` neste tree; fatias de simulador não são mantidas.

Conferir no Mac:

```bash
lipo -info iosApp/Vendor/GRDFUSDK.framework/GRDFUSDK
lipo -info iosApp/Vendor/JL_BLEKit.framework/JL_BLEKit
lipo -info iosApp/Vendor/JLDialUnit.framework/JLDialUnit
lipo -info iosApp/Vendor/ZipZap.framework/ZipZap
lipo -info iosApp/Vendor/ABParTool.framework/ABParTool
lipo -info iosApp/Vendor/VeepooBleSDK.framework/VeepooBleSDK
lipo -info iosApp/Vendor/FMDB/libFMDB.a
```

Esperado: só `arm64` (ou «Non-fat file … arm64» / archive de objetos arm64).
`armv7`, `i386` e `x86_64` quebram o upload.

## Info.plist / privacidade no binário

- Bluetooth always + peripheral (pt-BR): conexão do relógio, inclusive em
  segundo plano (`bluetooth-central`).
- Localização when-in-use (pt-BR): **não** coletamos GPS; o texto existe só
  se o iOS exigir para o scan BLE.
- Background: `bluetooth-central` (sessão Veepoo), `fetch` + `processing`
  (fila de envio `br.com.next2u.saude.paciente.ingest`). Não promete 15 min.
- `PrivacyInfo.xcprivacy`: saúde, nome, observações/perfil, telefone de
  emergência, `patient_id`, identificador do relógio. Sem tracking.
  Required-reason: UserDefaults `CA92.1`, file timestamp `C617.1`
  (store local + SDKs Veepoo/GRD).
- Ícone: todos os sizes iPhone/iPad + 1024, PNG RGB **sem alpha**.

## Itens que ficam no App Store Connect (não estão no git)

Estes **não** entram neste PR. Preencher antes do primeiro TestFlight
externo / App Store pública:

1. **URL da política de privacidade** (App Privacy + ficha da app).
2. **Nutrition labels** no Connect, alinhadas ao `PrivacyInfo.xcprivacy`
   (Health, Name, Other User Content, Phone Number, User ID, Device ID;
   purposes = App Functionality; not used for tracking).
3. **Notas para o revisor:** o app depende de um relógio **HBand VE30**
   (senha padrão 0000). Sem o relógio, Relógio/Início não mostram medições
   ao vivo. Fechar o H Band oficial ao testar. Bluetooth e Atualização em
   Segundo Plano ligados. Não force-quit.
4. **Chave Gemini:** hoje vai no build (`NEXT2U_GEMINI_API_KEY` /
   `Secrets.local.xcconfig`). **Mover para o servidor antes da App Store
   pública.** Placeholder no xcconfig commitado (`YOUR_GEMINI_API_KEY`)
   cai no fallback por regras, igual ao Android.
5. Conta interna TestFlight + testers Next2U. Ad Hoc só se alguém não
   puder usar TestFlight (UDID).

## Veepoo

Este PR **não** chama selectors novos. O rename do relógio continua
`veepooSDKSettingDeviceName(with:resultBlock:)` (ObjC
`veepooSDKSettingDeviceNameWithString:resultBlock:` em
`VPPeripheralBaseManage.h`). Grep antes de qualquer Swift novo.
