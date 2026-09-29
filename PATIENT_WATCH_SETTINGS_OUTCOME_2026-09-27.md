# Resultado das configurações do relógio e navegação do histórico

## OBSERVED FACTS

Baseline `c8912ad241e7276170a00dcc94af1bc96c3d7e04`.
As três opções do relógio agora distinguem solicitação pendente, retorno recebido,
alteração não confirmada e configuração ainda desconhecida na conexão atual.
O valor anterior fica indicado até um retorno relevante do SDK. A preferência
salva continua representando a intenção do usuário; não é recibo do dispositivo.

O cliente de medição automática não altera os objetos da última observação para
montar o pedido. Copia os onze campos existentes do SDK, preservando parâmetros e
política de envio. Falha parcial, retorno nulo, vazio ou sem o tipo solicitado não
produzem sucesso completo. O callback explícito de sucesso do SDK permanece aceito
como ACK, sem alegar leitura física posterior. Uma falha pode deixar parte do pedido
aplicada no relógio; a interface informa essa incerteza, sem prometer rollback.

Os retornos de SpO2 e detecção de uso precisam trazer status reconhecido e relevante.
READ_SUCCESS de detecção de uso não contém o valor da opção, portanto não o confirma.
ACK de escrita não avança o horário de última leitura. Toques repetidos enquanto a
mesma opção está pendente não duplicam o pedido; após falha, cabe tentativa explícita.
Cancelamento, timeout e retorno de cliente antigo não fabricam sucesso.

A leitura inicial e as três escritas de configuração compartilham exclusão local.
Isso impede a leitura inicial de enviar preferência antiga depois de uma alteração
explícita. Identidade do cliente é reavaliada após a espera. Revisões por opção,
capturadas antes de agendar a leitura inicial, impedem a aplicação de observação
antiga sobre pedido mais recente. Não é serialização global do SDK: histórico,
sensores e P1 permanecem fora desse mecanismo.

A navegação dos sete dias limita o índice no próprio callback. Toques rápidos antes
da atualização visual deixam de acessar índices fora de 0 a 6. A orientação para
acompanhar a busca aponta para Ajustes → Opções do relógio, onde o estado é exibido.
Datas, agregação de leituras e cálculos permanecem inalterados.

Reproduções anteriores à correção: oito falhas de configurações e um controle
aprovado; dois erros de índice no histórico. Testes adicionais durante o refinamento
reproduziram a captura tardia de revisão e a inversão de comandos de detecção de uso.
Fixtures, deltas correspondentes, XML e contexto de execução ficam na entrega externa.
São 25 testes novos: cinco do cliente, quatorze do gerenciador, quatro da interface
e dois da navegação rápida. O caso visual usa tela 640×320 e fonte ampliada, com
rolagem para alcançar separadamente o aviso e a opção de nova tentativa.

## RECOMMENDATIONS / alcance

App Paciente: alteração local de coordenação de configurações e apresentação.
Web, ACS e WhatsApp/SM Click não receberam mudanças. Sem entidades, IDs, permissões,
API, schema, DAO, fila, política offline ou transporte novos. Preservados os parâmetros
do protocolo e os callbacks existentes. O trabalho de leitura isolada do Leandro não
foi acionado ou alterado.

LOCAL/DEMO nos ensaios; PROPOSED / CONCEPTUAL para aceite do candidato. SDK interceptado,
Application neutra e estados artificiais não comprovam firmware, BLE físico, valor
persistido no relógio, TalkBack ou CI. Nenhuma nova capacidade REAL comprovada;
garantias centrais continuam BACKEND CONTRACT REQUIRED. A semântica dos callbacks
existentes não substitui validação física da configuração no relógio do piloto.
SHA completo, seis tarefas finais, revisões distintas, capturas e conferência remota
registrados na entrega externa. Manter PR DRAFT; sem merge, instalação, distribuição
ou declaração de prontidão final do piloto.
