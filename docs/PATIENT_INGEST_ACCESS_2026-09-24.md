# Configuração de ingestão e diagnóstico local

## OBSERVED FACTS

Continuação autorizada por Rafael, em `codex/patient-ingest-access`, sobre
`60ce6da3e186d3901b077927fcb908144adccd49`. A composição preserva os candidatos
anteriores, banco7, IDs estáveis, gravação conjunta, recibos por item, pausa
persistida e contadores VE30. É conciliação seletiva do PR5, não merge completo.
PR5 consultado no GitHub: OPEN/DRAFT, HEAD
`e9a80ef386d207a1bc6fe66bef3969eafa84aae5`, base
`9a239d9113bc671624643acc975b3e10042f4a57`. Conta somente leitura.
Core14 OPEN fora de DRAFT, HEAD `90c3a1d334834f5d7620ee1c7b4e80f938c44154`,
base `51478b6413e336145cca21687b96daf9627b6634`; implantação não comprovada.

### Mudanças verificáveis

- Configuração inválida bloqueia POST de ingestão antes de reclassificar itens,
  zerar tentativas ou alterar qualquer campo salvo. Chave ausente, placeholder,
  identificador padrão de paciente e caracteres inválidos não são acesso válido.
  A presença de outra string não comprova autorização do servidor.
- HTTPS obrigatório, sem credenciais na URL, query ou fragmento. A origem do
  diagnóstico exclui o caminho; redirects desativados. Não há logging no novo
  cliente nem exibição de mensagens brutas do health.
- URL/chave são capturadas juntas, uma vez por tentativa da fila. Uma edição
  afeta tentativas futuras, não troca chave no transporte de um lote em curso.
  Uma revogação não cancela retroativamente tentativa iniciada; não há garantia
  nova sobre transações já recebidas pelo servidor.
- Os três construtores produtivos usam a captura (app, worker, ViewModel).
  Mudança de configuração não limpa a pausa HTTP401/403. Retomada manual mantém
  a lógica anterior de recibos, sem sucesso presumido.
- O diagnóstico, dentro das ferramentas debug existentes, deriva das linhas
  persistidas e configuração atual: carregando, aguardando, falhos, concluídos,
  desconhecidos e pausa. Não guarda payload, erro bruto, identidade ou chave.
- O botão de conexão passa de POST sintético smokeHeart a GET `api/health`,
  rota já existente. Disponibilidade não comprova autorização/ingestão.
  O indicador antigo “SEGURO” passa a “ACESSÍVEL”.

### Limites de segurança e evidência

Não foi implementada uma nova arquitetura de credenciais. Permanecem os
SharedPreferences e fallback BuildConfig legados; override explicitamente vazio
não reativa o fallback. `commit()` é verificado antes de publicar configuração
em memória, mas isso não é cofre de segredos nem teste de falha física de disco.
Não foram copiadas chaves reais/.env. A política de provisionamento, rotação e
distribuição de APK requer Leandro. Não foi portado o gate de release do PR5 que
exigiria chave embutida. Nenhum novo formulário de provisionamento foi criado.

GET health continua possível sem chave, inclusive nos caminhos preexistentes
de atualização manual; bloqueio descrito acima se refere aos POSTs de ingestão.
Helper legado `com.healthtech.companion.net.HealthtechRepository` permanece no
repositório, sem chamadores no app após remoção do smoke do ViewModel. Outros
controles debug de simulação/limpeza não fazem parte deste delta e não devem
ser usados em aparelhos com registros do piloto.

Testes usam dados sintéticos, Room local e transporte simulado, sem backend,
VE30 físico ou app instalado. Verificam preservação campo a campo, reabertura,
pausa, rotação, projeção e UI em320dp/fonte2. Não comprovam encerramento do SO,
atualização instalada, SQLCipher em aparelho, falha física nem serviço publicado.
Resultados e SHA final estão no pacote externo `ingest-access`, para não
atribuir resultados futuros a este documento. Autorrevisão: SELF_REVIEW_ONLY;
revisão distinta local exigida antes da entrega. Checks locais não são CI.

### Quatro canais

Paciente: melhora local implementada, REAL de ponta a ponta não comprovado.
ACS: DEMO local, sem novas APIs, offline operacional ou autenticação presumida.
Web: sem alterações; hospedagem tratada em outra tarefa.
WhatsApp/SM Click: sem alterações ou comprovação de recebimento/visibilidade;
depende do estado central autorizado.
Core/Identity, serviços compartilhados: sem alterações; contratos, permissões
e implantação são BACKEND CONTRACT REQUIRED.
IDs canônicos, domínio de cadastro e vínculo territorial não são redefinidos.

## RECOMMENDATIONS

Leandro deve incorporar/reconciliar a composição em branch própria e revisar
o novo SHA/base. Não substituir WearableRepository ou resumo salvo por versões
anteriores. Acordar contrato e ambiente publicado, identidade sintética permitida,
métricas/unidades e provisionamento antes de liberar qualquer fila acumulada.
Depois executar ensaios integrados de nova leitura, reenvio, lote parcial,
consulta, reinício e recuperação sem perda. Integração e merge são humanos.
