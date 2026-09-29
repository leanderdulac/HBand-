# Apresentação da última verificação do serviço

## OBSERVED FACTS

Baseline `564665741a34d796e9af9f05b8b6b295dbeb95c3`, PR6 OPEN/DRAFT sobre
PR5 `e9a80ef386d207a1bc6fe66bef3969eafa84aae5`, confirmados no GitHub antes
do clone isolado. Três testes locais reproduziram: painel classifica estado
inicial como OFFLINE; suporte mostra DESCONECTADO sem consulta concluída;
suporte associa consulta ao endpoint de ingestão embora HealthTechApiService
execute GET api/health. Os testes usam mapper/composable produtivos e estados
sintéticos; não inicializam VM/Room operacional nem executam chamadas HTTP.

O estado inicial ApiHealthState possui lastCheckTime=0. O repositório existente
preenche esse campo ao concluir a consulta, inclusive em falha, e preserva a
resposta anterior quando uma tentativa é cancelada. A correção consome essa
evidência já existente; não cria campo, contrato ou fluxo de transporte.

Painel deixa de inferir falha do serviço sem verificação concluída; mantém os
fatos locais da fila, incluindo fila carregando, pendência, falha e bloqueio
de acesso persistido. Falha observada do serviço continua sendo apresentada,
agora explicitamente como resultado da última verificação.

Suporte apresenta “Ainda não verificado”, resposta, recusa HTTP401/403 ou falha.
Exibe data/hora local da conclusão identificada como “Horário no celular”, não
como tempo oficial do servidor ou das leituras. Layout em coluna e botão de
48dp mínimo acomodam texto ampliado. O hostname fixo e o endpoint de ingestão
foram retirados porque não eram evidência do alvo da consulta atual. O horário
não é substituído pela hora da renderização. Resultado anterior continua
identificado como histórico enquanto outra consulta aguarda resposta.

## Alcance e limites

Mudança de apresentação apenas. Nenhum DAO, schema, worker, BLE, cliente HTTP,
configuração, credencial, política de retry, envio, processamento ou admissão
concorrente foi alterado. Não há novo estado de consulta em andamento, limite
de idade do resultado ou tratamento de ordem de respostas concorrentes. Troca
de configuração mantém comportamento preexistente; o cabeçalho não atribui o
resultado a um host que não acompanha esse estado. O painel não comprova
autorização do ingest nem recebimento de registros pela equipe.

ApiHeader continua na seção DEBUG para suporte; o mapeamento do painel e sua
mensagem também se aplicam ao código release. Paciente/VE30: apresentação local.
Web Profissional, Tablet ACS e WhatsApp/SM Click: sem alteração de entidades,
IDs, permissões, contratos ou sincronização. SM Click permanece em outro chat.

Testes **LOCAL/DEMO**, candidato **PROPOSED / CONCEPTUAL** para integração; sem
nova comprovação **REAL**. Contratos centrais pendentes permanecem **BACKEND
CONTRACT REQUIRED**. Entrega, SHA final/checks, capturas, patch/bundle e revisões
em `C:/CDev/Next2U-Pilot-2026-09-27-health-presentation`. Fixture final amplia a
baseline e usa renderização nativa Robolectric; as três asserções iniciais
permanecem. Não é execução de Android/Activity/VM operacional, backend ou hardware.

## RECOMMENDATIONS

Manter gates humanos e CI aplicável antes da integração/distribuição. Consulta
de saúde não comprova ingestão. Resultado histórico não é garantia de conexão
presente. Nenhum APK desta etapa será instalado automaticamente.
