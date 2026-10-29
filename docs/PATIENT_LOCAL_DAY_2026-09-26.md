# Dia local de hidratação e respiração

## OBSERVED FACTS

Base local4b465af95108d8467b965b1c8fddf3c755d80a2a. O MainViewModel capturava
a data uma única vez na criação. Manter a sessão aberta na virada do dia fazia
inserções e reset continuarem usando o dia anterior. A consulta do total diário
também permanecia presa a essa data. São registros locais, não eventos Core.

LocalWellnessRecords concentra os mesmos DAOs e ações; cada salvamento obtém
um instante e deriva dele data e timestamp. A data é a do salvamento local,
inclusive para respiração. O reset resolve a data atual ao executar a ação.
O total de respiração continua acumulado, sem filtro diário novo.

O total de água troca de consulta ao receber sinais Android de mudança de data,
hora, fuso ou minuto e ao voltar a ser observado. Sinais só invalidam: a data não
vem do payload de broadcast. O receiver existe enquanto o fluxo é observado e
é removido no cancelamento. O compartilhamento da tela descarta o valor ao
deixar de ser observado; a troca de dia inicia em zero enquanto Room consulta,
evitando reapresentar o total anterior como pertencente ao novo dia.

Mantido SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) e fuso atual do
aparelho, conforme o formato local existente. Nenhum histórico é reformatado,
reclassificado ou corrigido retroativamente. Schema8, entidades, migrações,
IDs e filas permanecem iguais. Não se assume dia de24h, data UTC ou fuso territorial.

Reprodução: extração da semântica anterior com relógio injetável e Room sintético;
7 falhas em8 testes. Não se iniciou o MainViewModel completo, que possui consumidores
BLE/rede/IA fora do escopo. Fixture preservado na entrega externa. Os mesmos testes
exercitam a correção; testes adicionais cobrem sinais/lifecycle sob Robolectric36.
Resultados finais, SHAs, comandos e revisão distinta ficam na entrega externa.

## RECOMMENDATIONS / limites

PROPOSED / CONCEPTUAL para incorporação; evidência LOCAL/DEMO, sem atestar nova
capacidade REAL. Relógio do aparelho não ganha autoridade clínica, territorial,
de recebimento ou reconciliação: essas garantias centrais continuam BACKEND
CONTRACT REQUIRED quando não confirmadas. Mudança manual de hora/fuso pode
mudar o dia de novos registros; antigos conservam a sua data original.

Não há novo agendamento/worker. A atualização da consulta depende da entrega
de eventos do sistema/retomada da observação; não promete execução pontual em
segundo plano ou cronologia autoritativa. Os testes locais não são execução
em aparelho, CI nem ensaio de atualização do APK final.

App Paciente: diário local apenas. Web, ACS e WhatsApp/SM Click sem alteração,
novo contrato ou permissão. Nada enviado ao Core; envio isolado/pausa inicial
continua atribuído a Leandro. Sem publicação, merge, distribuição ou instalação.

Referência técnica consultada: https://developer.android.com/develop/background-work/background-tasks/broadcasts
