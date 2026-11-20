# Third-Party SDK Binaries (iOS)

`VeepooBleSDK.framework` is the official Veepoo/HBand iOS BLE SDK,
vendored unmodified from:

https://github.com/HBandSDK/iOS_Ble_SDK
(path: `iOS_sdk_source/Framework/2.2.XX.15/`)

Licensed under the Apache License, Version 2.0 (`LICENSE` in this folder).

| Item | Value |
|---|---|
| Folder drop | `2.2.XX.15` (newest public tree, 2026-07-02) |
| Embedded `VeepooBleSDKVersion` | **2.2.107.15** (string in the binary) |
| Info.plist short version | `0.0.0.1` (vendor placeholder — ignore) |
| Slice | `iPhoneOS` / `arm64` only — **no Simulator** |
| Android counterpart in this repo | `vpprotocol-2.3.80.15` |

## Version gap vs Android

The App Paciente Android uses `app/libs/vpprotocol-2.3.80.15.aar`.
The newest public iOS framework is **2.2.107.15**. There is no 2.3.x iOS
drop in [HBandSDK/iOS_Ble_SDK](https://github.com/HBandSDK/iOS_Ble_SDK).
P0 (scan / senha `0000` / FC / SpO₂ / bateria) exists on both.
Capacidades P1 (ECG, glicose, composição) podem divergir — confirmar no
VE30 físico antes de declarar paridade.

Não alterar o binário. Atualizar copiando de novo o drop oficial.

`MJExtension` (3.0.15.1) is vendored from the official Veepoo iOS demo
(`iOS_sdk_source/Demo/VeepooBleSDKDemo/Pods/MJExtension` in
[HBandSDK/iOS_Ble_SDK](https://github.com/HBandSDK/iOS_Ble_SDK)), which is
the same tree as `VeepooBleSDK.framework`. Licensed under MIT
(`Vendor/MJExtension/LICENSE`).

The SDK's `DBStoreManager` serializes ECG and sleep models with
`mj_keyValues`, `mj_keyValuesWithIgnoredKeys:` and related `mj_*`
selectors. Those are categories on `NSObject` from MJExtension. The
app already passed `-ObjC`, but MJExtension was not in the target, so
those selectors crashed with SIGABRT (`unrecognized selector`) on
iOS build 23. Linking this drop restores the official demo linkage.

