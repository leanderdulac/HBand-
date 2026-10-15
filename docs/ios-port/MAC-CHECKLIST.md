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

## 6. Background BLE

Ver `docs/ios-port/BACKGROUND-BLE.md`. Resumo no aparelho:

1. Parear o VE30, deixar FC a enviar. Ir à tela inicial **sem** force-quit.
2. Envios / Ajustes → **Diagnóstico BLE** (último evento, ligação, HTTP).
3. Esperar 20–40 min em background: os envios devem continuar ou retomar.
4. **Force-quit não relança.** Bluetooth off também para. Reinstalar perde o UUID.
5. Religar o app deve `retrievePeripherals` no último UUID.

## 7. Visual (Início / Histórico / tema / logo)

1. Launch screen: fundo `#F5F8FC` e o wordmark `next2u SAÚDE` (mesmo PNG do Android).
2. Ícone do app: o pulso laranja do launcher Android, fundo branco, todos os sizes.
3. Início: logo 48 pt à esquerda, «Olá!», nome, «Meu relógio», cartões de FC/SpO₂ com sparkline, «Registros de hoje», «Ver histórico».
4. Histórico: seletor, resumo 7 dias, «Ver gráfico», chips 7/30/90. Caption **«não é limiar clínico»**. Sem faixa pintada.
5. Ajustes → Aparência: Claro (padrão), Escuro azul, Escuro índigo, Escuro grafite + ciano, Seguir o sistema.

## 8. Envios (fase D)

1. Aba Envios: badge com pendentes; lista até 30; «Aguardando envio» / «Falha no envio».
2. Retry item / enviar / apagar com o diálogo de confirmação Android.
3. «Ver dados técnicos» abre o JSON (`client_reading_id`).
4. BGTask `br.com.next2u.saude.paciente.ingest` é oportunista — **não** promete 15 min.

## 9. Ajustes (fase E)

1. Meu perfil grava no store cifrado e reaparece depois de matar o app.
2. Cartão **Envio contínuo neste iPhone** — sem «Liberar o app», sem Samsung/Multilaser.
3. Avisos de batimentos usam o texto congelado (`AlertCopy`). Limites cadastrados, não faixa clínica.
4. «Opções de notificação deste aparelho» abre Ajustes do iOS.

## 10. Store cifrado (fase A)

1. Na abertura deve aparecer «Abrindo seus registros» e só então as cinco abas.
2. Depois de um batimento real, Relógio e Envios mostram «N medições gravadas neste iPhone».
3. Mate o app e reabra: o número de medições **permanece** (ficheiro cifrado + Keychain).
4. Se o store falhar: a tela «Não foi possível abrir seus registros» (cópia Android) e **sem** coleta/envio. Não limpe dados.
5. `Secrets.local.xcconfig` em `/tmp/hband-ios` **nunca** entra no git.

Rebuild `shared.framework` after pulling this branch (`./iosApp/Scripts/embed-shared.sh`).

## O que este ambiente Linux não verificou

- Compilação Xcode / Swift vs nomes exatos do SDK (`DeviceConnectState`, etc.)
- Ligação GATT no VE30
- HTTP 200 no Core com chave real
- Restauração de verdade em background

Ajuste fino de nomes Swift do SDK, se o Xcode reclamar, é esperado
nesta fase (o vendor mistura `VP*` e `Ble*`).
