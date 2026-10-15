# Plano de port iOS — App Paciente Next2U (HBand HealthSync)

Fase 1 do port: inventário, decisão de abordagem, riscos e esqueleto.
Este documento não autoriza merge, publicação na App Store nem aceite físico
do VE30 em iPhone. A composição Android em `main` permanece a fonte de
verdade do piloto atual.

Conta Apple Developer e Xcode no Mac de builds Next2U: **já disponíveis**
(Leandro). Não são bloqueios desta fase. Faltam apenas valores de assinatura
(Team ID, bundle ID definitivo, perfis) — o esqueleto usa placeholders.

---

## 1. Contexto

O App Paciente é Android/Kotlin + Jetpack Compose, pacote
`com.aistudio.hbandhealthtech.pxq97m`, marca **next2u SAÚDE**. Coleta
telemetria do relógio HBand/VE30 pelo SDK Veepoo (`vpprotocol` +
`vpbluetooth`), persiste em Room+SQLCipher, envia ao Core HealthTech com
`client_reading_id` e `Idempotency-Key`, e opcionalmente pede um resumo
Gemini (`AI_INSIGHT_ENABLED`).

HEAD de referência ao iniciar este plano: `1ac9de0`
(`Pedir exceção da otimização de bateria…`, PR #19), com o redesign visual
do PR #17 já em `main`.

---

## 2. Inventário módulo a módulo

### 2.1 BLE / SDK HBand–Veepoo

| Peça Android | Onde | Papel |
|---|---|---|
| Binários oficiais | `app/libs/` (`vpprotocol-2.3.80.15.aar`, `vpbluetooth-1.20.aar`, AARs Jieli, `abpartool`, Gson 2.2.4 do vendor) | SDK Apache-2.0, sem alteração, origem [HBandSDK/Android_Ble_SDK](https://github.com/HBandSDK/Android_Ble_SDK) |
| ECG JNI | `app/src/main/jniLibs/{arm64-v8a,armeabi-v7a}/libnative-lib.so` | `JNIChange` / `System.loadLibrary("native-lib")` — **não** vem dentro do AAR |
| Sessão GATT | `HBandBleManager` (~2 400 linhas) | Scan, connect, handshake de senha, HeartData/SpO2/PA/temp/HRV, histórico, P1 |
| Handshake | `VeepooPasswordHandshake` | Timer de confirm do SDK (12 s default, 30 s no app); não derrubar sessão ao vivo se o callback atrasar |
| Capacidades | `DeviceCapabilities` / `VeepooCapabilityProbe` | Flags só depois do probe; nunca assumir ECG, glicose, SpO2 etc. |
| Histórico | `VeepooHistorySync`, `VeepooHistoryMapper`, `VeepooHistoryReadSettings` | Origin / sono / HRV / SpO2; persistência local antes de qualquer HTTP |
| P1 | `VeepooP1Controller`, `VeepooAdvancedMapper`, `VeepooEcgNative` | ECG, glicose, composição, emoção, fadiga, respiração |
| Esporte | `VeepooSportReading` | Contadores do relógio, persistidos à parte da telemetria viva |
| Scan | `BleScanSession`, `BleScanFailure`, `BluetoothDeviceName` | Falhas de permissão/adapter sem inventar dispositivo |

**iOS:** existe SDK oficial, não é necessário reinventar o protocolo no
primeiro ciclo — ver §5.1.

### 2.2 Foreground service e WorkManager

| Peça | Onde | Papel |
|---|---|---|
| FGS | `HBandBleService` (`foregroundServiceType=connectedDevice`) | Mantém a sessão BLE no Doze; o GATT vive no `Application` |
| Boot | `HBandBootReceiver` | `BOOT_COMPLETED` / `MY_PACKAGE_REPLACED` → recoloca FGS e o job |
| Sync | `HBandIngestWorker` + `HBandWorkScheduler` | Periódico 15 min (`KEEP`), one-shot `REPLACE`, rede obrigatória, backoff 30 s |
| Coletor vivo | `LiveReadingRecorder` | Grava local primeiro; HTTP é sinal conflated, nunca bloqueia a coleta |

Não há equivalente iOS a um FGS sticky. A paridade de “relógio sempre
ligado” é o maior gap de produto — ver §5.2.

### 2.3 Room + SQLCipher

Entidades (`AppDatabase`, versão com fila canônica):

- `ingest_queue` — payload JSON, status, retries, `clientReadingId` único
- `hband_sensor_metrics` — série temporal local (Início, Histórico, IA)
- `hydration_logs`, `breathing_sessions` — bem-estar local
- `user_profile` — `patient_id`, contato de emergência, metas
- `advanced_measurements` — P1

Regras já pagas com laboratório: SQLCipher obrigatório (`SqlCipherNative`),
passphrase no Keystore, migrações 6→7 e 7→8 sem recreate, recusa de
versão desconhecida. iOS precisa da mesma honestidade (não apagar leituras
num upgrade).

### 2.4 Cliente de ingestão Core

Contrato observado no código e nos testes (Core `schemas.py` em
`75e5e02c…`, reconciliação seletiva PR14):

| Regra | Implementação Android |
|---|---|
| Chave | Header `X-API-Key`; placeholder `YOUR_HEALTHTECH_API_KEY_HERE` recusado; override em Ajustes não reativa a chave de build se o campo foi limpo |
| URL | HTTPS, sem userinfo, query ou fragmento; redirects desligados |
| Identidade | `client_reading_id` gerado **uma vez** no insert (`IngestReadingIdentity`, `[A-Za-z0-9._:-]{1,128}`); nunca no flush |
| Lote | `POST api/v1/wearables/batch-ingest`, máx. 50, um `patient_id` por corpo |
| Idempotência | Header `Idempotency-Key` = SHA-256(`patient_id` + ids na ordem do lote) |
| Recibo | HTTP 200 sozinho não confirma; exige `client_reading_id` + `patient_id` + `reading_id` + `ingest_status` ∈ {accepted, duplicate} |
| FC | Transporte `[20, 250]`; valor fora do contrato **permanece no aparelho** |
| Opcionais | PA, SpO2, temperatura, HRV só se medidos; nunca 72 / 118/78 / 98 / 36.6 |
| Fontes | `companion_manual`, `ble_sim`, `ble_hband`, `http` — ausência não é inferida |

Este bloco é o mais testado do repositório e o mais caro de divergir no iOS.

### 2.5 Resumo Gemini (`AI_INSIGHT_ENABLED`)

- Flag lida de `.env` / `.env.example` no Gradle; default **off**.
- `false` (piloto PR #6): aviso apenas; revisão manual só em DEBUG.
- `true`: cartão em destaque no Início + geração automática (debounce 10 s,
  intervalo mínimo 15 min).
- Chave: `BuildConfig.GEMINI_API_KEY` ou override; placeholders
  (`YOUR_GEMINI_API_KEY`, `MY_GEMINI_API_KEY`) caem no fallback por regras.
- Modelo atual: `gemini-3.6-flash`. Prompt e fallback em PT-BR.
- Esta fase **não** porta a chave nem o cliente Gemini.

### 2.6 Sistema de tema (PR #17)

Padrão **Claro** (`AppThemeOption.LIGHT`, storage `light`). Tokens em
`Color.kt` / `Theme.kt` / `Type.kt` / `Shape.kt` / `Status.kt`.

| Opção | Chave | Resolução |
|---|---|---|
| Claro | `light` | Azul confiança — primary `#1450A3`, fundo `#F5F8FC` |
| Escuro azul | `dark_blue` | Tokens escuros da paleta A |
| Escuro índigo | `dark_indigo` | Paleta C |
| Escuro grafite + ciano | `dark_graphite` | Paleta D |
| Seguir o sistema | `follow_system` | Claro A ou escuro azul |

Status (normal / atenção / alerta) é o mesmo em todas as paletas.
Tipografia sênior: corpo ≥ 15 sp; valor de sinal `displayMedium` 40/48 Bold;
toque mínimo 48 dp, botão primário 56 dp.

**Gráficos:** `Sparkline` (7 dias, só valores reais > 0) no Início;
`TrendChart` no Histórico (7 d / 30 d / 3 m). Faixa esperada sombreada
**somente se configurada** — hoje os placeholders R2/R3 estão nulos
(`«Faixa esperada: não configurada… — não é limiar clínico»`).

### 2.7 Exceção de bateria (PR #19) — só Android

`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` + `PowerManager`. Prompt **uma vez**
depois de parear o relógio. Cartão em Ajustes; ajuda extra para Samsung
(Multilaser e demais caem no texto genérico). Boot também garante o
`hband_sync_worker`. **Não existe API iOS equivalente**; o port não deve
inventar um diálogo de “exceção de bateria”.

### 2.8 Avisos (texto clínico congelado)

Não há aba própria: limites e interruptor ficam em Ajustes
(“Avisos de batimentos neste aparelho”); o disparo é notificação + snackbar.

Cópia que **não pode ser reescrita** nesta fase nem no port sem validação
do Rafael:

- Canal: «Avisos de batimentos»
- «Batimentos acima do limite cadastrado» / «Batimentos abaixo do limite cadastrado»
- «Batimentos: {n} bpm. Limite cadastrado: {limite} bpm.»
- Snackbar: «Batimentos: {n} bpm. Acima/Abaixo do limite cadastrado de {limite} bpm.»

Testes de aviso **não** usam leitura do relógio
(«Não é uma leitura do relógio.»).

### 2.9 Ajustes e navegação

Cinco destinos: **Início · Histórico · Relógio · Envios · Ajustes**.
Barra inferior em compacto; trilho quando cabe. Ajustes: perfil, reconectar
relógio, aparência (PR #17), avisos, bateria (PR #19), opções do relógio
(P0/P1 via SDK), URL/chave Core, reset local. Envios inspeciona a fila
sem apagar recibo.

---

## 3. O que compartilhar e o que é nativo

| Bloco | Compartilhar (KMP `commonMain`) | Nativo Android | Nativo iOS |
|---|---|---|---|
| Modelos (`HBandTelemetry`, PA, sono, recibo) | Sim | Já existe; migrar depois | Consome o módulo shared |
| Contrato de ingestão (payload, identidade, lote, recibo) | Sim — **prioridade** | Room/WorkManager/Retrofit hoje | SQLCipher/GRDB + BGTask + Ktor Darwin |
| Tokens de tema / tipografia / spacing | Sim (hex + sp/pt) | Compose Material3 | SwiftUI (Dynamic Type) |
| Regras de sparkline / faixa esperada | Sim (números, sem Canvas) | `Sparkline` / `TrendChart` Compose | Charts SwiftUI |
| Política de aviso (limites, enable) | Sim | `NotificationCompat` | UserNotifications |
| Texto clínico de aviso | Recurso compartilhado, sem reescrita | — | — |
| BLE / Veepoo | Não | `VPOperateManager` + AARs | `VeepooBleSDK.framework` + `VPBleCentralManager` |
| Persistência cifrada | SQLDelight mais tarde | Room + SQLCipher | SQLDelight iOS **ou** GRDB+SQLCipher |
| Background | Política comum (quando flushar) | FGS + WorkManager + Doze exemption | `bluetooth-central`, state restoration, `BGTaskScheduler` |
| Gemini | Cliente HTTP comum *depois* | `GeminiHealthAnalyzer` | Mesmo guard `AI_INSIGHT_ENABLED` |
| Exceção de bateria | Não | PR #19 | Sem equivalente; educar Background App Refresh |
| HealthKit | Não | — | Complemento opcional, não substitui o VE30 |
| UI sênior | Tokens só | Compose + TalkBack | SwiftUI + VoiceOver + Dynamic Type |

---

## 4. Abordagens e decisão

### 4.1 Kotlin Multiplatform (domínio + ingest) + SwiftUI

O núcleo Kotlin já tem dezenas de testes contra 422 do Core, identidade
durável e normalização legado. KMP permite copiar essas regras uma vez.
A UI iOS fica em SwiftUI (VoiceOver, Dynamic Type, Safe Area). O SDK
Veepoo iOS é Objective-C e entra por um wrapper Swift, não pelo KMP.

**Custo:** Gradle multi-módulo, XCFramework no Mac, disciplina para não
puxar Android (`JSONObject`, Room) para `commonMain`. Android **não**
migra nesta PR — o `:app` segue intacto.

### 4.2 Compose Multiplatform

Reaproveitaria cartões Compose, mas: (1) o SDK iOS é UIKit/ObjC e o
background BLE é ciclo de vida UIKit; (2) acessibilidade sênior no iOS
ainda é mais previsível em SwiftUI; (3) o time de UI do paciente
(Rafael) validou o Compose *Android*, não o runtime CMP iOS. Alto risco
de regressão visual com pouco ganho no pedaço mais difícil (BLE).

### 4.3 Rewrite nativo 100% Swift

UI e BLE ficam idiomáticos, porém o contrato de ingestão teria de ser
reescrito. Esse contrato já quebrou em 422 por detalhe de float, fonte e
recibo. Duplicá-lo em Swift no piloto é o pior risco de regressão de
backend.

### 4.4 Decisão

**Kotlin Multiplatform no núcleo (modelos + ingestão + tokens + regras
puras) e SwiftUI no app iOS, consumindo o SDK oficial
`HBandSDK/iOS_Ble_SDK`.**

O esqueleto desta PR cria `:shared` (JVM + iOS no Mac) e `iosApp/`
SwiftUI com placeholders de assinatura. O Android **não** depende de
`:shared` ainda.

---

## 5. Riscos

### 5.1 SDK HBand/Veepoo no iOS — existe oficial

**Não** é necessário reimplementar o protocolo GATT no primeiro ciclo.

Evidência pública (consultada em 2026-10-15):

| Item | Valor |
|---|---|
| Repositório | [HBandSDK/iOS_Ble_SDK](https://github.com/HBandSDK/iOS_Ble_SDK) (Apache-2.0, mesmo vendor do Android) |
| Entrada | `VPBleCentralManager.sharedBleManager` (singleton; **sem concorrência** de comandos) |
| Persistência sugerida pelo vendor | `VPPeripheralManage` (DB interno) vs `VPPeripheralAddManage` (app persiste) — o App Paciente deve persistir **fora** do SDK, como no Android |
| Artefato | `VeepooBleSDK.framework` em `iOS_sdk_source/Framework/{2.0.43.15, 2.1.XX.15, 2.2.XX.15}` |
| Wiki | [VeepooSDK iOS API Document](https://github.com/HBandSDK/iOS_Ble_SDK/wiki/VeepooSDK-iOS-API-Document) (doc 1.2.4, 2026-06-17) |
| Aviso do vendor | iOS permite dois apps no mesmo periférico; **não** deixar o app H Band oficial e o Next2U ligados ao mesmo VE30 |

**Gap de versão:** o Android deste repo usa `vpprotocol-2.3.80.15`. As
pastas públicas do iOS vão até **2.2.XX.15**. Antes do primeiro pareamento
VE30 no iPhone, no Mac: abrir o framework, ler `VeepooBleSDKVersion` e
confirmar com o vendor se há drop 2.3.x iOS. Sem isso, capacidades P1
(ECG, glicose, composição) podem faltar ou divergir.

Reimplementar CoreBluetooth só entra se o vendor não cobrir o firmware
do piloto. Não começar por engenharia reversa do AAR.

### 5.2 BLE em background no iOS (vs Android)

O Android atual combina FGS `connectedDevice` + WorkManager 15 min +
exceção de Doze (PR #19). Isso aproxima “telemetria contínua”.

No iOS:

1. `UIBackgroundModes = bluetooth-central` — o sistema acorda o app em
   connect/disconnect e em notificações GATT; **não** deixa o processo
   correr para sempre.
2. State restoration (`CBCentralManagerOptionRestoreIdentifierKey` +
   `centralManager(_:willRestoreState:)`) — o sistema relança o app para
   um evento pendente (scan, connect, notify).
3. [TN3115](https://developer.apple.com/documentation/technotes/tn3115-bluetooth-state-restoration-app-relaunch-rules)
   (atualizado 2025-09-15): force-quit **não** relança. A partir do
   **iOS 26**, force-quit / alguns toggles do Control Center só relançam
   apps que configuraram o acessório com **AccessorySetupKit**.
4. `BGTaskScheduler` (`BGAppRefreshTask` / `BGProcessingTask`) substitui
   o WorkManager para **flush da fila**, não para manter o GATT. Intervalo
   de 15 min **não é garantido**.
5. Reinício com passcode: não há relançamento BLE até o primeiro unlock.

**Impacto no piloto:** o iPhone **não** terá a mesma continuidade do
Android. A estratégia é: (a) medidas automáticas **no relógio**;
(b) sync de histórico ao abrir / ao acordar por notify; (c) fila local
durável; (d) educar a não matar o app e a deixar Background App Refresh
ligado. Telemetria viva 24 h como no FGS Android fica **melhor esforço**.

### 5.3 HealthKit (complemento, não substituto)

Pode espelhar passos, FC e sono *já persistidos pelo Next2U* para o app
Saúde, se o paciente autorizar. **Não** substitui o VE30: PA, SpO2 de
pulseira, P1 e a proveniência `ble_hband` / `is_real_sensor_data` não
existem nesse canal com o mesmo contrato. Fora desta fase; entitlement
`com.apple.developer.healthkit` entra só com o Team ID real.

### 5.4 Distribuição do piloto (TestFlight / Ad Hoc)

Conta Apple Developer e Xcode: **já existem**. O esqueleto usa
placeholders; Leandro preenche no Mac quando for assinar o primeiro build.

**Ainda precisa do Leandro (não nesta fase):**

| Item | Placeholder no repo | Quando preencher |
|---|---|---|
| Team ID (10 caracteres) | `TEAMIDXXXX` em `iosApp/Config/Next2UPaciente.xcconfig` | Primeiro archive no Xcode |
| Bundle ID | `br.com.next2u.saude.paciente` | Confirmar se o ID final é este ou outro sob o time Next2U |
| Signing | Automático (`CODE_SIGN_STYLE=Automatic`) | Xcode cria App ID + perfil ao selecionar o Team |
| Certificados | — | Xcode gerencia Apple Development / Apple Distribution |
| Dispositivos Ad Hoc | — | UDIDs dos iPhones do piloto, se não for TestFlight |
| Testers TestFlight | — | Apple IDs internos (até 100) e, se externo, revisão de beta |

**Passos TestFlight (depois do primeiro binário no Mac):**

1. No Xcode: Signing & Capabilities → Team Next2U → bundle ID acordado.
2. Capability Bluetooth (e HealthKit só se a fase 6 estiver aprovada).
3. Product → Archive → Distribute App → App Store Connect → Upload.
4. App Store Connect → TestFlight → grupo interno Next2U → convidar testers.
5. Externo só se o piloto precisar de gente fora da conta; exige
   export compliance e revisão de beta.
6. Build de piloto: sem chave Gemini real se `AI_INSIGHT_ENABLED` estiver
   off; chave Core só no secret do Mac / Xcode Cloud, **nunca** no git.

**Passos Ad Hoc** (aparelho que não pode usar TestFlight):

1. Registrar o UDID em Certificates, Identifiers & Profiles.
2. Provisioning Ad Hoc com o App ID do paciente.
3. Archive → Distribute → Ad Hoc → exportar IPA + perfil.
4. Instalar via Apple Configurator / Xcode Devices. Vale até o perfil
   expirar ou o UDID sair da lista.

CI Linux **não** gera IPA. O job Android Verify continua só Android +
`:shared:jvmTest`.

---

## 6. Estrutura criada nesta fase

```
docs/ios-port/PLAN.md          ← este documento
shared/                        ← KMP commonMain + jvm + ios (ios só no Mac)
  modelos, ingestão, tokens, testes JVM
iosApp/                        ← SwiftUI, tema Claro, 5 abas, placeholders
  Config/Next2UPaciente.xcconfig
  Next2UPaciente.xcodeproj
```

O módulo `:app` **não** passa a depender de `:shared`. Comportamento
Android, coleta de vitais, tratamento da chave Gemini e texto clínico
dos avisos ficam inalterados.

No Mac (Leandro), próximos comandos — **não** bloqueiam esta PR:

```bash
# XCFramework do núcleo (requer Xcode)
./gradlew :shared:linkReleaseFrameworkIosArm64 \
          :shared:linkReleaseFrameworkIosSimulatorArm64

# Abrir o app e escolher o Team
open iosApp/Next2UPaciente.xcodeproj
# Signing & Capabilities → Team → substituir TEAMIDXXXX
# Confirmar bundle id br.com.next2u.saude.paciente (ou o ID oficial)
```

Vendor do `VeepooBleSDK.framework` (cópia Apache-2.0, como os AARs) fica
para a fase de BLE, com `THIRD_PARTY_NOTICE` equivalente.

---

## 7. Roteiro e critérios de aceite

### Fase 0 — Plano e esqueleto (esta PR)

- [x] `docs/ios-port/PLAN.md` em português
- [x] `:shared` com modelos, contrato de ingestão e tokens
- [x] `iosApp` SwiftUI com tema Claro e as cinco abas
- [ ] CI Android verde (lint, compile, test, assembleDebug) + `:shared:jvmTest`
- [ ] Android sem mudança de comportamento
- [ ] Sem merge

### Fase 1 — Assinatura no Mac

- Team ID e bundle ID reais no xcconfig (fora do git se forem secrets de time)
- Signing automático gera App ID
- Simulator sobe a casca SwiftUI e liga o framework `shared`
- Aceite: archive local sem Veepoo ainda

### Fase 2 — BLE P0 (scan / senha / FC / bateria)

- Vendor `VeepooBleSDK.framework` (versão conferida vs 2.3.80.15)
- Scan, connect, `verifyPassword`, HeartData, bateria, disconnect
- Capability probe sem assumir P1
- Aceite: VE30 físico pareado; `is_real_sensor_data=true` só com GATT real

### Fase 3 — Fila e Core

- Persistência cifrada (SQLDelight ou GRDB+SQLCipher)
- `client_reading_id` no insert; lote ≤ 50; recibo seletivo
- Mesmos testes de contrato do `:shared`
- Aceite: leitura viva ou histórica chega ao Core sem 422; retry não
  regenera id

### Fase 4 — UI sênior (paridade PR #17)

- Tokens iguais; seletor Claro / três escuros / sistema
- Sparklines e TrendChart só com valores reais; faixa não configurada
- Aceite: Dynamic Type XXL e VoiceOver nos cinco destinos

### Fase 5 — Avisos e Ajustes

- Mesma cópia clínica do Android
- Perfil, reconectar, URL/chave Core, reset
- Sem diálogo de “exceção de bateria”
- Aceite: limites 50–100 (ou os persistidos) disparam UserNotifications
  sem reescrever o texto

### Fase 6 — Background e HealthKit opcional

- `bluetooth-central` + restoration id estável + AccessorySetupKit se o
  piloto for iOS 26
- `BGProcessingTask` para flush (substituto honesto do WorkManager)
- HealthKit opt-in, só espelho do que já está no Next2U
- Aceite: histórico do relógio entra após o app ter sido suspenso;
  force-quit documentado como limite de SO; HealthKit desligado não
  quebra o VE30

### Fase 7 — Gemini

- Mesmo `AI_INSIGHT_ENABLED` (default off)
- Chave só no secret do Mac; placeholders recusados
- Aceite: flag off = piloto PR #6; flag on = cartão + fallback

### Fase 8 — TestFlight do piloto

- Upload interno Next2U
- Roteiro: parear VE30, 24 h de uso, comparar fila Android vs iOS no mesmo
  `patient_id` (ids distintos por aparelho)
- Aceite: testers instalam; sem chave no git; sem declaração de
  equivalência contínua ao FGS Android

---

## 8. Fora de escopo desta PR

- Migrar `:app` para depender de `:shared`
- Alterar vitais, Gemini, avisos ou fila Android
- Vendor do framework iOS Veepoo
- IPA / TestFlight / certificados
- HealthKit entitlement
- Aceite físico VE30+iPhone

---

## 9. Referências

- Vendor Android: `app/libs/THIRD_PARTY_NOTICE.md`
- PRs: [#17 redesign](https://github.com/leanderdulac/HBand-/pull/17),
  [#19 bateria](https://github.com/leanderdulac/HBand-/pull/19)
- SDK iOS: https://github.com/HBandSDK/iOS_Ble_SDK
- Core Bluetooth background (arquivo Apple) e TN3115
- CI Android: `docs/PATIENT_CI.md`, `.github/workflows/android-verify.yml`
