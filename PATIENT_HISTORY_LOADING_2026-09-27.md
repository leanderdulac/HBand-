# Histórico: carregamento distinto de resultado vazio

## OBSERVED FACTS

Baseline `3b2c2aeb13ccdd57d708de8c9a605efcc04422a0`.
Quatro falhas reproduzidas em hoje, resumo semanal, sono e medição selecionada:
antes da primeira resposta, a política initialValue=emptyList de allSensorMetrics
levava a UI a afirmar ausência. Reprodução copiou essa política em fixture com
fonte pendente e componentes produtivos; não executou Home/MainViewModel.

Home agora repassa shareSensorMetrics nullable já existente para essas telas.
Remove apenas a coleta visual antiga sem consumidor; MainViewModel e o fluxo
legado dos demais consumidores permanecem intactos. Nenhuma consulta nova criada.
Null mostra carregamento; resposta vazia continua mostrando ausência conforme
regras anteriores. Durante null, valores/gráficos anteriores são ocultados.
Dia, medição e opção de gráfico permanecem na composição e retornam com os dados.
Filtros, cálculos, datas, ausência de fases de sono, valores e ações não mudam.
O comentário do helper foi atualizado para refletir também o uso no histórico.

Oito testes cobrem quatro estados iniciais com helper produtivo, transições
null/vazio/registros/null, preservação de seleção e fonte ampliada. A conexão
Home→componentes é conferida estaticamente. Capturas são JVM, Application neutra.

## RECOMMENDATIONS / limites e canais

App Paciente: apresentação local após observação da fonte existente. Web, ACS e
WhatsApp/SM Click intactos. Nenhuma mudança em entidades, IDs, DAO/schema, donos,
permissões, APIs, transporte, concorrência ou política offline/sincronização.
Não cria estado de erro, snapshot atômico ou garantia de frescor. A política
existente reinicia com null após a última observação terminar; erros da consulta
não são tratados por este recorte. Sem MainViewModel operacional, backend,
aparelho físico ou CI. Ensaios LOCAL/DEMO, candidato PROPOSED / CONCEPTUAL; nenhuma
nova capacidade REAL atestada. Garantias centrais BACKEND CONTRACT REQUIRED.
Evidências/revisão distinta vinculadas ao SHA exato na entrega externa.
Manter PR em rascunho; sem merge, instalação, distribuição ou aceite do piloto.
