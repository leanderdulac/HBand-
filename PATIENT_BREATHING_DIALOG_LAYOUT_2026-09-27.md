# Confirmações de respiração em tela baixa

## OBSERVED FACTS

Baseline `a10913f29164e469c5d0336b8ff5fa8b1169d4c5`. Dois testes no card produtivo,
640×320/fontScale2, reproduziram explicações cortadas nas confirmações de retry e
descarte. TextLayoutResult indicou overflow; capturas mostram a frase de duplicação
incompleta e o limite do descarte sem seu final. Recibos sintéticos, sem escrita.

Extraído BreathingConfirmationDialog para os dois usos. Título e explicação completos
ficam em área rolável; as duas decisões ficam fora da rolagem, com altura mínima de
48dp. Largura máxima 560dp e margem16dp. Textos, callbacks, fechamento externo e
enabled=!anySaving preservados. Sem alteração de timer, token, controller, recibos,
VM, schema, IDs, DAOs, dados, filas ou transportes.

Evidência: testes de layout/callback no card com recibos simulados, e ensaios focais
JVM/Robolectric com inspeção das imagens antes/depois de rolar. Verificações vinculadas
ao SHA da entrega; não emulador/aparelho ou startup operacional do MainViewModel.

## RECOMMENDATIONS / limites

LOCAL/DEMO, candidato PROPOSED/CONCEPTUAL. Não é disponibilidade REAL nem aprovação
de merge/distribuição. PR permanece DRAFT. Garantias centrais não confirmadas seguem
BACKEND CONTRACT REQUIRED. Só apresentação do App Paciente muda; Web/ACS/SM Click sem
nova entidade, contrato, permissão ou sincronização. Risco anterior de duplicação
após retry manual de commit incerto permanece e agora seu texto completo é acessível.
Sem garantia de todos os tamanhos de tela/dispositivos ou recuperação transacional.
