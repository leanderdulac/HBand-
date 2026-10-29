# Regressão: confirmação de apagar registros de água

## OBSERVED FACTS

Baseline: `3be7281ea29e2690f02eac82c2443550c6310c2d`.
A hipótese de aviso cortado na confirmação não foi reproduzida. O cartão/diálogo
produtivo passou com entradas e callbacks sintéticos em tela 640x320/fontScale2,
com aviso completo e decisões legíveis. A aplicação não precisou de correção.
A confirmação de que os registros dos outros dias serão mantidos já cabe no
aviso nesse cenário. A segunda captura de paisagem ocorre após a checagem do
aviso; não implica que tenha havido rolagem. Enquadramentos podem diferir na
captura JVM da janela do diálogo.

Quatro casos de regressão foram incorporados:
- Tela baixa/fonte ampliada: aviso completo e confirmação explícita, exatamente
  um callback; abrir/inspecionar não dispara apagamento.
- Retrato/fonte ampliada: aviso e ações visíveis, cancelar sem callback.
- Total fica indisponível durante confirmação: botão desabilitado, tentativa de
  clique sem callback, cancelar disponível; reabrir exige nova confirmação.
- Total retorna na confirmação aberta: habilita botão sem disparar callback
  automaticamente; apenas clique explícito solicita apagamento.

Ensaios usam Application neutra, Compose e Robolectric SDK36. Verificam UI,
layout e callbacks; não executam MainViewModel operacional ou DAO. A preservação
real do banco/histórico não é provada por estes callbacks. Não há alteração de
produção, textos, entidades, IDs, permissões, armazenamento ou transporte.

## RECOMMENDATIONS / alcance

App Paciente: cobertura sintética do comportamento existente. Web, ACS e
WhatsApp/SM Click inalterados, sem novas dependências, contratos, sincronização,
concorrência ou capacidades offline. Ensaios LOCAL/DEMO; candidato PROPOSED /
CONCEPTUAL para aceite. Sem nova capacidade REAL; garantias centrais permanecem
BACKEND CONTRACT REQUIRED. Checks locais não são CI, nem teste físico/TalkBack.
Evidências e revisão distinta ligadas ao SHA exato na entrega externa. Manter
PR em rascunho, sem merge, instalação, distribuição ou aceite do piloto.
