# Checklist Mac — P0 VE30 (PR #20, fase 2)

Conta Apple e Xcode já existem. Faça isto **num iPhone físico**
(o `VeepooBleSDK.framework` é só `iPhoneOS/arm64`; o Simulator não liga).

## 1. Framework Kotlin shared

```bash
chmod +x iosApp/Scripts/embed-shared.sh
./iosApp/Scripts/embed-shared.sh
open iosApp/Next2UPaciente.xcodeproj
```

Signing & Capabilities → Team Next2U (`H87XXMXA5G` já está no xcconfig).
Confirmar bundle `br.com.next2u.saude.paciente`.

## 2. Compilar no iPhone

Product → Destination → o iPhone do piloto → Run.
Esperado: as cinco abas, tema Claro, aba Relógio com «Buscar relógio».

## 3. Parear o VE30

1. Feche o app **H Band** oficial (o vendor avisa loop se os dois ligam).
2. Relógio no pulso, Bluetooth ligado, Background App Refresh ligado.
3. Relógio → Buscar relógio → tocar no VE30.
4. Confirme no relógio se aparecer o pedido de ligação (timeout 30 s).
5. Senha padrão **0000** (igual ao Android). A UI deve mostrar
   «Sessão Veepoo pronta».

## 4. Ver frequência cardíaca

Com o relógio no pulso, em ~20 s deve aparecer um valor em bpm
(40 pt, vermelho). Oxigênio entra no revezamento seguinte (~15 s).
Pressão **não** é medida neste P0 (teste de ~50 s, não é barato).

## 5. Confirmar ingestão HTTP 200

1. Em Relógio, cole a URL HTTPS do Core e a chave `X-API-Key` real
   (não commitar).
2. Toque em «Guardar chave e URL».
3. Espere um batimento real (FC no intervalo 20–250).
4. «HTTP do último flush» deve ser **200**.
5. «Último client_reading_id» fica preenchido e **não muda** se a
   mesma linha for reenviada.
6. Confira no Core o `patient_id` `PAT-HBAND-001` (ou o perfil) e o
   mesmo `client_reading_id`.

Se a chave for placeholder, a fila **conserva** a linha e não chama
a rede — isso é correto.

## 6. Background (primeiro passo)

1. Com o relógio ligado, vá à tela inicial (não mate o app).
2. Volte ao next2u SAÚDE. A sessão pode ter sido restaurada
   (`bluetooth-central` + restore id `br.com.next2u.saude.paciente.ble`).
3. **Force-quit não relança.** No iOS 26, force-quit / alguns toggles
   só voltam com AccessorySetupKit (fase 6).
4. Não espere a continuidade do FGS Android.

## O que este ambiente Linux não verificou

- Compilação Xcode / Swift vs nomes exatos do SDK (`DeviceConnectState`, etc.)
- Ligação GATT no VE30
- HTTP 200 no Core com chave real
- Restauração de verdade em background

Ajuste fino de nomes Swift do SDK, se o Xcode reclamar, é esperado
nesta fase (o vendor mistura `VP*` e `Ble*`).
