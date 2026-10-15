# Paridade Android ↔ iOS — next2u SAÚDE

Análise **somente leitura**. Nenhuma tela iOS foi alterada neste documento.

| Lado | Ref | HEAD |
|---|---|---|
| Android (fonte de verdade do piloto) | `app/` em `main` | `1ac9de0` — PR #17 (Cuidado claro) + PR #19 (cartão de bateria) |
| iOS (este branch) | `iosApp/` + `:shared` | `4f3b0f7` — esqueleto + P0 Relógio + vendor Veepoo |

**Por que o iOS não é idêntico:** o iOS desta PR é um **esqueleto de port** (fases 1–2). Cinco abas e tokens de cor já existem; a maior parte da UI, da persistência e dos serviços de fundo do Android ainda não foi portada. Parte da diferença é **deliberada** (iOS não tem FGS / isenção de bateria Android). Parte é **ainda não feita**.

Classificação usada abaixo:

| Classe | Significado |
|---|---|
| **Idêntico** | Mesmo comportamento e cópia visível ao paciente |
| **Simplificado** | Existe um stub / P0; falta o restante |
| **Intencional** | iOS não pode (ou não deve) copiar o mecanismo Android |
| **Ausente** | Não há tela, fluxo nem persistência equivalente |

Esforço: tamanho do trabalho (S / M / L / XL) = quantas superfícies SwiftUI + o que precisa entrar em `:shared`, sem prazo em dias.

---

## 1. Navegação e casca

| Item | Android (`main`) | iOS (branch) | Classe | Esforço |
|---|---|---|---|---|
| Cinco destinos Início / Histórico / Relógio / Envios / Ajustes | `PatientNavigationBar` + rail em tablet | `TabView` com os mesmos rótulos | **Simplificado** — nomes iguais; sem badge da fila, sem rail, sem voltar à Início no gesto de voltar | S |
| Layout adaptativo (barra vs grade vs rail ≥600 dp) | `PatientAdaptiveLayout` | `TabView` padrão do sistema | **Intencional** no phone; rail em iPad ainda **ausente** | M (iPad) |
| Cabeçalho “Olá, {nome}” + atalho de perfil | `HomeWelcomeHeader` só na Início | Não existe | **Ausente** | S |
| Tipografia sênior (headline 32, botão 56 dp, sem encolher label) | `Type.kt` + medição de label na barra | 28/17 pt e botão 56 no Relógio/Ajustes; Início/Histórico/Envios são placeholder | **Simplificado** | S–M (aplicar em todas as telas) |
| Tela de abertura do armazenamento | `StorageStartupScreen` (SQLCipher / “Abrindo seus registros”) | App abre direto | **Ausente** (depende da persistência cifrada) | M |
| Snackbar / aviso em faixa | `PatientNotificationEffect` | Não | **Ausente** | S |

---

## 2. Início (home)

A `DashboardTab` no Android é a tela mais densa do app. No iOS é um parágrafo placeholder.

| Cartão / bloco | Android | iOS | Classe | Esforço |
|---|---|---|---|---|
| Meu relógio (`DeviceControlCard`: estado, auto-envio, checagem, buscar, desligar) | Sim | Não (pedaços no Relógio) | **Ausente** na Início | M |
| Aviso de bateria baixa do VE30 | `LowBatteryWarningCard` | Só “Bateria do relógio: N%” no Relógio | **Ausente** como cartão de status | S |
| Grade de vitais + sparkline | `TelemetryGauges` → `VitalCard` + `Sparkline` (FC, SpO₂, PA, temp; acentos PR #17) | Não | **Ausente** | L (UI + série local) |
| Resumo do dia | `DailyHealthSummaryCard` | Não | **Ausente** | M |
| “Ver histórico” | Vai para a aba 1 | Não | **Ausente** | S |
| Estado da fila / sync | `SyncStatusIndicator` | Linha de HTTP no Relógio/Envios | **Ausente** na Início | M |
| P1 “Medições do relógio” | `AdvancedDetectCard` (ECG, glicose, composição, emoção, fadiga, respiração) se `hasAdvancedDetect` | Não | **Ausente** (depois do probe no VE30) | L |
| Água no dia a dia | `HydrationCard` | Não | **Ausente** | M |
| Exercício de respiração | `BreathingExerciseCard` | Não | **Ausente** | M |
| Sono | `SleepAnalysisCard` | Não | **Ausente** | M |
| Compartilhar registros | `PatientShareRecords` + diálogo | Não | **Ausente** | M |
| Resumo IA (ver §7) | Condicional ao flag | Não | **Ausente** | M |
| Bloco DEBUG (log de sync, API) | Só debug | Não | **Ausente** (ok no piloto) | S |

**Paridade visual da Início** só depois de haver métricas persistidas (Room no Android). Hoje o iOS não grava série temporal — só a fila JSON de ingestão.

---

## 3. Histórico e TrendChart

| Item | Android | iOS | Classe | Esforço |
|---|---|---|---|---|
| Título “Histórico” + seletor de métrica | `RechartsSensorDashboard` (resumo 7 dias, batimentos, PA, SpO₂, temp, passos/calorias) | Placeholder explicando 7/30/90 dias | **Ausente** | L |
| `TrendChart` (linha, períodos 7 / 30 / 90, chips) | Canvas Compose | Não | **Ausente** | L |
| Resumo de 7 dias | `RechartsSevenDaySummaryCard` | Não | **Ausente** | M |
| Faixa esperada | `ExpectedRangePlaceholders` — **sempre** “não é limiar clínico”; min/max nulos até R2/R3 | Texto do placeholder cita a regra; sem gráfico | Cópia da regra: **simplificado**. Gráfico: **ausente** | S (caption) + L (gráfico) |
| Lista das últimas 15 leituras | Sim | Não | **Ausente** | S |
| Falha de leitura local + retry | `LocalReadNotice` | Não | **Ausente** | S |

---

## 4. Relógio (pareamento / onboarding BLE)

Não há onboarding de “primeiro uso” separado no Android: o pareamento **é** a aba Relógio (`PatientWatchScreen`) + permissões no momento do toque.

| Item | Android | iOS | Classe | Esforço |
|---|---|---|---|---|
| Busca / lista / conectar / desligar | Completo + permissões Android 12 + “conectar por MAC” + falhas de scan | `WatchScreen` + `VeepooSession` (scan, senha `0000`, FC, SpO₂, bateria) | **Simplificado** — P0 funciona no iPhone; falta MAC manual, mensagens de permissão alinhadas, scan failure tipado | M |
| Handshake senha `0000` + sensores vivos | FC, SpO₂, PA, temp, HRV em revezamento | FC + SpO₂; sem PA/temp/HRV contínuos | **Simplificado** | M |
| Histórico do relógio (origin / sono / HRV) | `VeepooHistorySync` | Leitura diária só para liberar o PPG | **Ausente** como produto | L |
| Auto-reconnect persistido | Preferência em Ajustes | `automaticConnection` do SDK + último endereço | **Simplificado** | S |
| Serviço em primeiro plano / boot | `HBandBleService` + `HBandBootReceiver` | `bluetooth-central` + restore sidecar | **Intencional** — ver §10 | — |
| Não abrir o H Band oficial ao mesmo tempo | Aviso no Android em vários pontos | Aviso no Relógio iOS | **Idêntico** (cópia próxima) | — |

---

## 5. Envios (fila offline)

| Item | Android | iOS | Classe | Esforço |
|---|---|---|---|---|
| Contrato Core (`client_reading_id`, lote ≤50, recibo seletivo, chave) | `IngestPayload` / `IngestReconciler` / Room | `:shared` `IngestRuntime` + arquivo JSON | Contrato: **idêntico** no núcleo. UI e durabilidade: **simplificado** | — / L (SQLCipher) |
| Lista pendente / falha / enviado (30 itens) | `QueueInspector` | Placeholder + último HTTP | **Ausente** | M |
| Badge na aba Envios | Contagem real | Não | **Ausente** | S |
| Retry item / retry todos / apagar / limpar sincronizados / JSON | Sim + `JsonPayloadModal` | Não | **Ausente** | M |
| WorkManager 15 min + one-shot | `HBandIngestWorker` | `BGTaskScheduler` declarado, **não agendado** | **Intencional** o mecanismo; flush periódico: **ausente** | M (BGTask, com limites iOS) |
| Auto-envio das leituras vivas | Toggle na Início | Flush após cada FC/SpO₂ se a chave serve | **Simplificado** | S |

---

## 6. Ajustes

| Item | Android (`SettingsTab`) | iOS (`SettingsPlaceholder`) | Classe | Esforço |
|---|---|---|---|---|
| Tema Claro / Escuro azul / índigo / grafite / Seguir o sistema | `ThemeSelectorCard`, default Claro, tokens PR #17 | Mesmas opções e hex principais | **Simplificado** — seletor existe; faltam `primaryContainer`, cores de status e vitais no `PatientPalette` | S |
| Meu perfil (nome, idade, sexo, altura, peso, metas, emergência, notas, `patient_id`) | `UserProfileDialog` + Room | Não | **Ausente** | L |
| Cartão de bateria PR #19 (“Envio contínuo”, “Liberar o app”, Samsung / Multilaser) | `BatteryOptimizationSettingsCard` + prompt após parear | Não | **Intencional** o *pedido de isenção* (API Android). **Ausente** um cartão iOS honesto (Background App Refresh + “não feche o app”) | M (texto novo, revisão clínica/produto) |
| Reconectar relógio | Switch persistido | Não na UI (SDK auto) | **Simplificado** | S |
| Opções do relógio (auto-medida, SpO₂ auto, wear, histórico, alarme, aviso FC no band, sedentarismo, virar pulso, achar relógio) | `BandSdkSettingsCard` | Não | **Ausente** | L |
| Avisos de batimentos (limites, switch, sliders, cópia) | Seção completa | Não | **Ausente** | M |
| Permissão de notificação do aparelho | `PatientNotificationSettings` | Não | **Ausente** (Settings URL iOS) | S |
| Diagnóstico de ingestão / teste de API / apagar tudo | DEBUG + reset com diálogo | URL/chave no Relógio (`Secrets.local.xcconfig` + override) | Diagnóstico: **ausente**. Segredo de build: **intencional** (melhor que campo visível) | S–M |

---

## 7. Avisos clínicos e notificações

Não há uma “tela de alerta” isolada no Android. Os avisos de FC são:

1. Seção **Avisos de batimentos** em Ajustes (limites cadastrados, não “faixa segura”).
2. Notificação local + snackbar com cópia **congelada** (`AlertCopy` em `:shared` e o equivalente Android).
3. Caption dos gráficos: “não é limiar clínico”.

| Item | Android | iOS | Classe | Esforço |
|---|---|---|---|---|
| Cópia congelada (`TITLE_HIGH` / `TITLE_LOW` / corpo / snackbar) | Canal “Avisos de batimentos” | `AlertCopy` no KMP; **nenhuma UI/UNUserNotification** | Núcleo: **idêntico**. Entrega: **ausente** | M |
| Limites persistidos + toggle | DataStore / prefs | Não | **Ausente** | S |
| “Faixa esperada — não é limiar clínico” | Em todo `TrendChart` / `VitalCard` | Só no texto do placeholder de Histórico | **Simplificado** | S (obrigatório ao portar gráficos) |
| Canal BLE de sessão | strings.xml “Sessão Bluetooth” | Sem notificação persistente (iOS não tem FGS) | **Intencional** | — |

**Não reescrever** `AlertCopy` sem revisão clínica.

---

## 8. IA (Gemini / `AI_INSIGHT_ENABLED`)

| Item | Android | iOS | Classe | Esforço |
|---|---|---|---|---|
| Flag `AI_INSIGHT_ENABLED` | `BuildConfig`; `false` no piloto = cartão “Resumo automático em validação”, geração só em DEBUG | Flag e cliente **ausentes** | **Ausente** | M |
| `true` = destaque na Início + geração automática | `AiInsightHighlightCard` + `PatientInsightReview` | Não | **Ausente** | M |
| Chave Gemini | Build / override; placeholder recusado | Não | **Ausente** | S |
| Cópia de guarda (“ainda precisa de revisão…”) | Congelada no cartão | Não | **Ausente** (copiar literal) | S |

---

## 9. Cópia pt-BR

Quase todo o texto do paciente está **hardcoded em Kotlin**, não em `strings.xml` (`strings.xml` só cobre o canal BLE). O iOS tem frases soltas em Swift.

| Área | Android | iOS | Classe |
|---|---|---|---|
| Destinos das abas | Início, Histórico, Relógio, Envios, Ajustes | Iguais | **Idêntico** |
| Temas | Claro, Escuro azul, Escuro índigo, Escuro grafite + ciano, Seguir o sistema | Iguais | **Idêntico** |
| Relógio P0 | “Buscar relógio”, avisos de permissão longos | “Buscar relógio”, “Desligar relógio”, frases de sessão | **Simplificado** |
| Início / Histórico / Envios / Ajustes (resto) | Dezenas de strings de produto | Placeholders curtos | **Ausente** |
| `AlertCopy` / faixa esperada / guarda da IA | Congeladas | Só no KMP ou no placeholder | **Ausente** na UI |

Paridade de tom: portar **literalmente** do Compose (e de `AlertCopy`), não reescrever.

---

## 10. Plataforma — o que nunca será “igual”

| Android | iOS equivalente honesto | Por quê |
|---|---|---|
| `HBandBleService` FGS `connectedDevice` | `UIBackgroundModes=bluetooth-central` + restore id | Sem FGS; notify GATT pode acordar o app; force-quit não relança (TN3115 / iOS 26 AccessorySetupKit) |
| PR #19 `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` + texto Samsung/Multilaser | Background App Refresh + “não deslize o app para fechar” + sem diálogo de “liberar bateria” | Essa API **não existe** no iOS. Copiar o cartão Android seria mentira |
| WorkManager 15 min com rede | `BGTaskScheduler` oportunista | Sem garantia de 15 min |
| SQLCipher + Android Keystore | SQLCipher / Keychain, ou CryptoKit + ficheiro | Mesma *regra* (não apagar fila num upgrade); outra implementação |
| Permissões BLE 12+ / localização | `NSBluetoothAlwaysUsageDescription` (+ localização só se o iOS exigir) | Intencional |
| SDK `vpprotocol-2.3.80.15` | `VeepooBleSDK` **2.2.107.15** | Gap de vendor; P1 só após probe no VE30 |

Telemetria 24 h no iPhone é **melhor esforço**, não o FGS Android. O relógio deve guardar medidas; o iPhone sincroniza ao abrir, ao notify ou no restore.

---

## 11. O que já está alinhado (não é o app inteiro)

- Nomes das cinco abas e da marca (next2u SAÚDE).
- Tokens Claro / três escuros (hex do PR #17) no seletor.
- Contrato de ingestão no `:shared` (id, lote, recibo, recusa de placeholder).
- `AlertCopy` no KMP (ainda sem fio na UI).
- P0 BLE oficial (scan / `0000` / FC / SpO₂) no iPhone físico.
- Segredo Core via `Secrets.local.xcconfig` (commit `4f3b0f7`) — não vai para o git.

Isso **não** torna a experiência idêntica. O paciente no iPhone ainda não vê a Início do PR #17.

---

## 12. Maiores buracos (ordem de impacto no paciente)

1. **Início vazia** — sem vitais, sparkline, resumo do dia, relógio, sync.
2. **Sem série local** — Histórico, sparklines e IA dependem de `hband_sensor_metrics` (Room). iOS só tem fila JSON.
3. **Histórico / TrendChart** — tela inteira ausente.
4. **Envios** — sem lista, badge, retry, JSON.
5. **Ajustes** — sem perfil, avisos de FC, cartão de continuidade, opções do VE30.
6. **Notificações + `AlertCopy`** — cópia pronta, entrega ausente.
7. **Bem-estar** — água, respiração, sono, partilha.
8. **IA Gemini** — flag e cartões ausentes.
9. **P1 / histórico do band** — Android completo; iOS P0 só.
10. **Fundo 24 h** — não copiável; falta o cartão iOS honesto no lugar do PR #19.

---

## 13. Plano proposto até a paridade (visual + funcional)

Ordem pensada para o paciente ver a Início cedo, sem fingir FGS.

| Fase | O quê | Tamanho | Dependências |
|---|---|---|---|
| **A — Persistência** | Métricas, fila, perfil, hidratação/respiração em store cifrado (SQLCipher+Keychain ou equivalente); migrações que **não** apagam dados; tela “Abrindo seus registros” | XL | Base de tudo abaixo |
| **B — Início visual** | Header, `DeviceControlCard`, `VitalCard`+`Sparkline`, resumo do dia, sync, “Ver histórico”. Cores de status/vitais do PR #17. Cópia literal | L | A |
| **C — Histórico** | `RechartsSensorDashboard` + `TrendChart` 7/30/90 + caption “não é limiar clínico” | L | A |
| **D — Envios** | `QueueInspector` + badge + modal JSON; `BGTask` de flush (sem prometer 15 min) | M | A |
| **E — Ajustes paciente** | Perfil; avisos de FC com `AlertCopy`; Settings de notificação; switch de reconectar; **cartão iOS de continuidade** (não o texto Samsung) | L | A + permissão UNUserNotification |
| **F — Relógio P0→P0+** | PA/temp/HRV no revezamento; MAC manual; mensagens de permissão; auto-reconnect na UI | M | SDK 2.2.x no VE30 |
| **G — Bem-estar + partilha** | Água, respiração, sono, share | L | A |
| **H — IA** | Mesmo `AI_INSIGHT_ENABLED` e as duas cópias de guarda; cliente Gemini | M | A + secret no Mac |
| **I — P1 / histórico do band** | Só com `DeviceCapabilities` reais no VE30 iOS | L | Probe físico; gap 2.2 vs 2.3 |
| **J — iPad rail** | `PatientNavigationRail` | M | B–E no phone primeiro |

**Fora do plano de “ficar igual”:** isenção de bateria Android, FGS sticky, WorkManager pontual, canal de notificação persistente de BLE.

**Aceite de paridade visual:** lado a lado no mesmo `patient_id`, tema Claro, fonte aumentada — Início / Histórico / Envios / Ajustes reconhecíveis. Relógio pode continuar com layout iOS nativo se os *passos* (buscar, senha, FC) forem os mesmos.

**Aceite de paridade funcional:** mesma regra de ingestão, mesmos avisos de FC (texto), mesmo perfil, mesma fila recuperável após kill do app. Fundo 24 h documentado como melhor esforço.

---

## 14. Referências de código

Android: `HomeScreen.kt`, `PatientNavigationBar.kt`, `SettingsTab.kt`, `PatientWatchScreen.kt`, `RechartsSensorDashboard.kt`, `TrendChart.kt`, `VitalCard.kt`, `BatteryOptimizationCard.kt`, `GeminiHealthInsightCard.kt`, `QueueInspector.kt`, `theme/Color.kt`, `theme/ExpectedRangePlaceholders.kt`.

iOS: `ContentView.swift`, `WatchScreen.swift`, `VeepooSession.swift`, `Theme.swift`.

Shared: `ThemeTokens.kt` (`AlertCopy`), `IngestRuntime.kt`.
