# App principal

**Arquivo histórico, não candidato atual do piloto.** APK recebido na branch
`feature/next2u-patient-ui` em `c1a5a993fe73ade0e374747700a884555cbb1f7d`.
Não inclui por inferência as correções posteriores do PR #6. A instalação no
Galaxy Z Flip5 abaixo é relato da entrega original, não teste desta composição.
Não reinstalar este APK em aparelhos com dados para validar a nova composição.

Este é o APK do HealthSync de tela branca (next2u SAÚDE), o app do paciente.

**[App principal.apk](App%20principal.apk)**

- Pacote: `com.aistudio.hbandhealthtech.pxq97m`
- Versão: 1.0 (`versionCode` 1)
- minSdk 24, targetSdk 36
- Instalado no Galaxy Z Flip5 (`SM-F731B`)

O app só mostra o que a pulseira mediu. Não há lote de métricas, bateria simulada, alerta de teste nem ingestão `smokeHeart`.

Comando registrado na entrega original (não é instrução de atualização do piloto):

```bash
adb install -r "App principal.apk"
```
