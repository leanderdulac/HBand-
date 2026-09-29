# Integração da remoção de simulações com as proteções do piloto

## OBSERVED FACTS

Composição de fonte do PR #6 `0e2346d70ed9e4e0a06a58d8f21f72799735b83e`
com o PR #4 `c1a5a993fe73ade0e374747700a884555cbb1f7d`, confirmado no GitHub
em 29/09/2026. O commit do PR #4, de Leandro em 28/09, remove geradores de
simulação e traduz a interface. Ele não era ancestral do PR #6. A composição
inicial apresentou 12 conflitos; nenhuma branch anterior foi sobrescrita.

Foram removidos os geradores de lote, bateria e alertas fictícios, suas ações
e callbacks de interface, e o cliente legado `smokeHeart`. Não se apagam dados
históricos nem se reclassifica sua origem. O marcador legado de bateria simulada
permanece reconhecido e é ocultado tanto no debug quanto no release; bateria
real desconhecida continua ausente, e zero continua uma carga válida.

### Reconciliação por conflito

| Arquivo | Decisão |
| --- | --- |
| HBandBleManager | Remover simulações; preservar exclusão mútua, cancelamento, contexto do dispositivo, revisões de configuração e publicação separada dos contadores esportivos do PR #6. |
| WearableRepository | Remover somente o gerador de lote fictício. Preservar gravação conjunta, pausa persistida de autorização, gate compartilhado, seleção de paciente, IDs, recibos e reenvio. |
| HomeScreen / MainViewModel | Remover ações fictícias; preservar recuperação de leitura e notificações, seleção de telas e verificação de saúde do serviço sem ingestão sintética. |
| DeviceControlCard / LowBatteryWarningCard / RechartsSensorDashboard | Retirar ferramentas e callbacks de simulação; conservar conexão, leitura real, estados ausentes/desconectados e recuperação. |
| SettingsTab | Retirar testes de alertas; conservar bloqueio da cópia remota, confirmação de limpeza e ferramentas já restritas a desenvolvimento. |
| QueueInspector / SyncHistoryLog / SyncStatusIndicator | Conservar versão do PR #6, já em português, com estados de falha/leitura/autorização e distinção entre fila vazia e recebimento. Não reintroduzir “Tudo enviado” ou confirmação local forçada. |
| HealthtechRepository | Remover cliente legado de ingestão fictícia; o teste atual de conectividade usa `checkApiHealth`, sem criar leituras. |

O APK recebido em `App principal` é histórico, mantido com identificação explícita;
não é o binário desta composição e não foi instalado. Nenhum schema, SDK, chave,
permissão, endpoint ou contrato foi criado/alterado. Os testes de alertas removidos
foram substituídos por verificações de falha/cancelamento/valores no caminho de
notificação automática existente. Cenários de rádio/recibos continuam sintéticos.

## RECOMMENDATIONS / limites

Classificação: candidato **PROPOSED / CONCEPTUAL**, até checks e revisão aplicáveis
ao SHA resultante. Testes locais/CI não comprovam operação REAL nem aceite físico
VE30. Não liberar a fila existente, instalar APK histórico ou fazer reset para teste.
Aceite de ingestão e integração distribuída: **BACKEND CONTRACT REQUIRED** conforme
contratos/ambiente já registrados, sem nova capacidade inferida deste merge de fonte.

Impacto nos quatro canais: somente App Paciente; Web, ACS (DEMO) e WhatsApp/SM Click
não recebem API, permissão nem sincronização nova. A remoção de simulações não
reconcilia registros já recebidos pelo Core. Merge GitHub e instalação operacional
continuam decisões humanas em composição verificada. A cadeia PR #1/#2/#4/#5/#6
precisa de revisão de suas respectivas bases; esta composição não aprova a cadeia.
