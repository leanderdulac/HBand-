# VE30 — candidato isolado de ensaio físico

## OBSERVED FACTS / escopo

A instalação Paciente existente pode conter fila/dados a preservar. O `storageLab`
desativa a interface e o Bluetooth, portanto não serve ao ensaio de rádio.
`-PbleLab=true` prepara somente debug, com pacote próprio
`com.aistudio.hbandhealthtech.pxq97m.blelab` e nome **VE30 Ensaio**. Mantém o
SDK/BLE, UI e armazenamento do candidato; não migra nem acessa o pacote normal.
A faixa permanente informa **ENSAIO / SEM ENVIO**. Não há gerador de medições.

O manifesto compilado remove INTERNET, desativa a retomada por boot e o
inicializador Firebase, e exclui backup/transferência de dados. As autoridades
FileProvider também usam o sufixo. Workers e controles de rede do aplicativo
continuam existentes; os dois clientes HTTP recusam as requisições antes de DNS,
como IOException recuperável, evitando crash do dispatcher sem INTERNET.
O Android também nega rede direta a esse UID mesmo com Wi-Fi ligado.
Isso não impede um compartilhamento explícito por outro aplicativo: não usar
exportação/compartilhamento no ensaio. Não preencher credenciais em Ajustes.

Configuração com chaves reais ou google-services.json impede o build de laboratório.
Arquivos `.properties` na raiz também são recusados, salvo `gradle.properties`
e `local.properties` de infraestrutura. Isso inclui `debug.properties` e o
overlay sem nome `.properties`, que o Secrets Plugin lê mesmo sem flavors.
Essas entradas poderiam sobrepor os placeholders após a validação.
As opções storageLab/bleLab são mutuamente exclusivas. Qualquer tarefa Release
com bleLab é recusada. O build normal continua separado, sem faixa de ensaio.

## Preparação e verificação

Em checkout isolado com configuração descartável e chaves placeholder:

```powershell
./gradlew.bat assembleDebug lintDebug testDebugUnitTest -PbleLab=true
./tools/Test-BleLabApk.ps1 -Apk ./app/build/outputs/apk/debug/app-debug.apk -ApkAnalyzer <SDK>/cmdline-tools/<version>/bin/apkanalyzer.bat
```

Registrar SHA completo, tarefas/contexto, hash APK, assinatura e resultado do
verificador antes de instalar. O verificador é somente leitura. Verificar que o
pacote `.blelab` ainda não existe no dispositivo; se existir, preservar e examinar
seus dados antes de qualquer atualização. Nunca usar `pm clear`, desinstalar,
substituir o APK normal ou copiar a fila/credenciais do aplicativo existente.

## Roteiro e limites do ensaio

1. Confirmar M8 e relógio reservado; registrar hashes dos apps preservados sem
   abrir seus bancos. Instalar exclusivamente APK `.blelab` previamente verificado.
2. Conferir package/UID, ausência de INTERNET e faixa identificadora no aparelho.
   Usar apenas perfil fictício; o ID local não é um paciente mestre confirmado.
3. Buscar dispositivos e confirmar qual é o relógio reservado antes de conectar.
   Não selecionar um vizinho apenas por nome. Registrar identificação privadamente.
4. Exercitar conexão, origem das leituras, bateria e desconexão. Leituras ausentes
   continuam ausentes, sem números simulados. O SDK existente pode sincronizar
   hora/configuração e iniciar coleta no relógio; este não é um teste passivo.
5. Desconectar e encerrar apenas o laboratório; conferir apps preservados e guardar
   evidência limitada. Não enviar fila nem trocar configurações da rede do tablet.

O ensaio comprova apenas os passos efetivamente observados na versão identificada.
Não certifica precisão clínica, vínculo mestre, ingestão Core ou aceite integrado.
Integração central permanece **BACKEND CONTRACT REQUIRED** no alcance pendente;
capacidade de laboratório preparada é **PROPOSED / CONCEPTUAL** até validação.
Web, ACS e SM Click não mudam. Não é uma versão para atendimento de pacientes.

## Integração da fonte

Esta entrega parte de PR7, ainda pendente de integração. Conforme instrução de
Rafael, **somente Leandro decide e executa merges neste repositório**. CI e revisão
do delta não aprovam automaticamente a cadeia anterior ou publicação operacional.
