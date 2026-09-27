# Inspetor de registros em telas baixas

## OBSERVED FACTS

Baseline `fdb0cf4c85cc1a670e882c1ee471a09988dfcd17`, PR6 OPEN/DRAFT,
base PR5 `e9a80ef386d207a1bc6fe66bef3969eafa84aae5`, confirmados no GitHub.
Clone isolado e fontes/entregas anteriores preservados.

Dois testes reproduziram o botão “Fechar Inspetor” invisível em tela640×320,
com fontes1 e2. Usam JsonPayloadModal produtivo e um registro sintético em
memória; nenhum VM, DAO, WorkManager, BLE, dispositivo ou HTTP operacional.
O conteúdo tinha caixa fixa280dp antes do botão, consumindo a altura disponível.

A correção reserva a ação de fechar fora do conteúdo rolável. Título,
identificação e payload compartilham a rolagem; o JSON continua integral e
somente leitura. Diálogo aproveita a largura disponível até960dp com margem,
como os diálogos existentes de perfil/cartão. Botão tem altura mínima48dp.
onDismiss continua sendo a única ação; comportamento padrão de voltar/toque
fora preservado pelo Dialog. Não adiciona envio, edição, exclusão ou refresh.

Cinco casos cobrem tela baixa normal/ampliada, retrato estreito, tablet e
rolagem até o final do registro mantendo ação/ID/payload. As duas asserções
de reprodução permanecem, ampliadas com limites e preservação do conteúdo.
Capturas são geradas em execução focal recordRoborazziDebug separada, sem
atualizar imagens versionadas ou repetir suíte inteira desnecessariamente.

## Alcance e RECOMMENDATIONS

Apenas apresentação de suporte do Paciente. O inspetor continua acessado
pelos detalhes DEBUG da fila. Release compila o mesmo componente, sem nova
rota para abrir suporte. Web Profissional, Tablet ACS, Core e WhatsApp/SM Click
não recebem entidades, permissões ou contratos. IDs/dados, fila, processador,
DAO/schema, transporte, configuração e sincronização/offline intactos.

Testes **LOCAL/DEMO**, candidato **PROPOSED / CONCEPTUAL** para integração;
não comprovam capacidade **REAL** ou aceite físico. Contratos centrais ainda
pendentes continuam **BACKEND CONTRACT REQUIRED**. FontScale/renderização JVM
não substituem aparelho real. A captura usa conteúdo sintético; não contém
dados de pacientes. O trabalho não cria limites/truncamento de payload.

Evidências, SHA final, checks/ambiente, patch/bundle, APK não instalado e
pareceres distintos na entrega externa
`C:/CDev/Next2U-Pilot-2026-09-27-inspector-layout`. Manter gates humanos antes
de integrar/distribuir; CI ausente mantém PR DRAFT. Histórico de tarefas já
rotulava ação explicitamente como envio e não foi alterado nesta etapa.
