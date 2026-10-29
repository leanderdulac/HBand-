# Disponibilidade da meta de água

## OBSERVED FACTS

Baseline: `8a2344b303e9114252b7e00d7c4707ae5594e05a`.
HomeScreen convertia perfil indisponível em meta zero. HydrationCard apresentava
isso como ausência confirmada de meta e ocultava metas positivas enquanto o
total de água não estava disponível. Duas reproduções locais falharam no baseline.
A primeira usa a projeção exata de Home com perfil sintético, sem executar Home
ou MainViewModel; a segunda executa o cartão produtivo com total pendente.

Home e Dashboard agora preservam a meta nullable. O cartão mostra indisponível
para null, mantém a política anterior de ausência para zero/negativo e mostra
a meta positiva independentemente do carregamento dos registros de água.
Percentual só existe com total conhecido e meta positiva. Nenhum valor de meta
é substituído, recomendado ou calculado. Quantidades e confirmação de apagar
permanecem iguais. O fluxo de perfil não distingue consulta pendente de perfil
ausente: por isso o texto neutro não promete que uma meta aparecerá.

Sete testes sintéticos cobrem estados, transições, quantidades, confirmação e
acesso por rolagem com fonte ampliada. Evidências completas são vinculadas ao
SHA efetivamente executado na entrega externa, com fonte antes/depois.

## Impacto nos quatro canais

- Paciente Android: apresentação da meta local em Home/Dashboard/cartão.
- Web, ACS e Core: nenhum contrato, código ou estado alterado.
- Entidades, IDs, propriedade, DAO/schema, permissões, transporte, sincronização,
  concorrência e política offline não mudam. SM Click permanece fora do escopo.

## RECOMMENDATIONS / classificação

Ensaios LOCAL/DEMO com Application neutra, sem MainViewModel operacional, rede,
BLE ou aparelho físico. Candidato PROPOSED / CONCEPTUAL para aceite; não cria
capacidade REAL comprovada. Cadastro central e propagação de metas permanecem
BACKEND CONTRACT REQUIRED. Checks locais não são CI; auto-revisão não substitui
revisão independente. Manter PR em rascunho, sem merge, instalação ou distribuição.
