# Confirmação de gravação do perfil local

## OBSERVED FACTS

Baseline ec3f6242fed55507cbff067ee31cc293a28d239b, PR6 sobre PR5
e9a80ef386d207a1bc6fe66bef3969eafa84aae5. Um teste com UserProfileDialog
produtivo e callback pendente reproduziu fechamento imediato e perda do editor
antes de qualquer confirmação. O VM iniciava DAO em launch sem tratar falhas.

Controller volátil no viewModelScope publica SAVING antes de iniciar a escrita;
correlaciona tentativa por token, id local e patientId. Retorno do DAO confirma
SAVED antes do callback existente de identidade local/notificação. Exceção ou
cancelamento sem retorno confirmado produz UNCONFIRMED, inclusive antes do launch.
Não há repetição automática; token já usado não é admitido de novo na instância.
O formulário mantém campos e fecha automaticamente somente pelo recibo exato.
Campos, salvar, cancelar e fechamento externo ficam bloqueados durante a tentativa.
Resultado incerto preserva edição e permite decisão explícita; fechar não promete
desfazer perfil que já tenha sido gravado. Restauração sem recibo não envia de novo.

IDs somente leitura e cópia dos campos originais preservados. Sem alteração de
DAO/REPLACE, schema, filas, chaves, SDK/BLE ou transporte. Callback setPatientId
mantém a atribuição local existente após retorno do DAO, sem envio ao dispositivo.
Testes isolam controller, Room em memória e UI/recibos simulados; não executam
MainViewModel operacional nem escrita real. Pacote de evidências por SHA em
C:/CDev/Next2U-Pilot-2026-09-27-profile-save; checks locais não são CI.

## RECOMMENDATIONS / limites

LOCAL/DEMO nos ensaios; candidato PROPOSED / CONCEPTUAL, sem nova capacidade REAL.
App Paciente corrige confirmação de gravação local existente. Web, ACS e SM Click
não mudam contratos, entidades, permissões ou sincronização. Cadastro central e
conciliação entre canais continuam BACKEND CONTRACT REQUIRED.
Recibo/token não são journal durável, versão do perfil ou deduplicação persistente.
Uma gravação de resultado incerto pode ter ocorrido; retry explícito mantém a
substituição local existente, sem conciliação de edições concorrentes. Android
saved-state não garante recuperação após morte abrupta. Sem instalação/merge ou
aceite físico/publicado do piloto; revisão de agente distinto registrada à parte.
