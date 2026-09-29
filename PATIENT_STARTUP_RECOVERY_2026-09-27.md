# Recuperação da solicitação inicial de permissões e vibração opcional

## OBSERVED FACTS

Baseline `fc9a38417d385db6815946394dd943ee196d96d5`.
Três falhas locais corrigidas no mesmo bloco:

- Uma exceção recuperável ao abrir a solicitação inicial de permissões deixava
  o efeito de composição falhar. Agora a ação encaminha aviso local sanitizado,
  indicando Relógio para a conexão e Ajustes para notificações.
- O predicado all de um resultado vazio era verdadeiro e podia anunciar concessão
  após uma solicitação interrompida. Agora exige mapa não vazio e todos os valores
  concedidos. O texto de sucesso se refere às permissões solicitadas, sem chamar
  toda solicitação de Bluetooth.
- A consulta ao serviço de vibração ocorria fora dos tratamentos existentes de
  vibrate/cancel. Agora falha recuperável de lookup/defaultVibrator permite operar
  sem vibração, preservando exercício, tempo contado, rascunho e controles.

O gate de armazenamento READY permanece. A tentativa inicial é marcada antes da
ação e preservada na restauração, sem repetição automática após falha. O callback
de erro é atualizado com a composição e fica fora do catch da ação solicitada.
Os novos catches preservam cancelamento e erros fatais. Não mudam permissões
pedidas, concessões, hardware, cadência de vibração, cronômetro ou salvamento.

Baseline: três falhas reproduzidas e dois controles aprovados. A fonte dos dois
composables estava inalterada. Para exercitar diretamente o predicado do callback,
a expressão original foi extraída para função interna delegada sem mudança de
comportamento. Essa instrumentação está registrada em baseline-production-delta.patch;
não se atribui o ensaio à fonte limpa original. Fixtures/XML foram preservados.

Nove testes novos: seis de Compose e três do predicado. Cobrem exceções na abertura,
conteúdo READY preservado, falha notificada uma vez, ausência de retry em restauração
e mudanças de estado, callback atualizado, serviço opcional ausente, tempo contado
preservado após recriação e solicitação explícita de salvar o mesmo tempo. A ausência
de recibo continua exibindo gravação não confirmada. Vazio, negativa parcial e concessão
completa não vazia são diferenciados. Dois rótulos incorretos em testes adicionados
foram corrigidos para os textos já existentes; não eram falhas adicionais da aplicação.

## RECOMMENDATIONS / alcance

Composables e predicado reais, Application neutra, ContextWrapper e callbacks
artificiais. Não executam MainActivity operacional, armazenamento, BLE, diálogo
real de permissões, vibração física, gravação final ou process death real.
A ligação MainActivity → aviso local foi conferida no código. Restauração é
sintética; não se confirma autorização pelo simples retorno de um callback.
Não houve mudança de layout nem nova rodada de capturas.

Somente App Paciente. Web Profissional, Tablet ACS e WhatsApp/SM Click sem mudança.
Sem novos contratos, IDs, entidades, permissões, schema, fila, transporte ou política
offline; trabalho de leitura isolada do Leandro preservado. Ensaios LOCAL/DEMO,
candidato PROPOSED / CONCEPTUAL para aceite; nenhuma capacidade REAL nova atestada.
Garantias centrais continuam BACKEND CONTRACT REQUIRED. SHA completo, seis checks,
revisão distinta e publicação conferida estão na entrega externa. Manter DRAFT;
sem merge, instalação, distribuição ou declaração de prontidão física do piloto.
