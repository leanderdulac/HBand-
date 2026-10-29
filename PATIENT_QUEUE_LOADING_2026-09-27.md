# Fila: carregamento distinto de resultado vazio

## OBSERVED FACTS

Baseline: `7b6c097863af430402c8186f2a58c92820463d49`, PR #6 OPEN/DRAFT,
base PR #5 `e9a80ef386d207a1bc6fe66bef3969eafa84aae5`, confirmados no GitHub.
Clone isolado; originais e entregas anteriores preservados.

Dois testes locais reproduziram mensagens prematuras de fila sem pendências e
lista vazia. O ensaio usou a expressão `stateIn` antiga extraída do MainViewModel,
uma emissão sintética suspensa e o QueueInspector de produção. Não iniciou o
MainViewModel operacional, Room real, WorkManager, BLE ou backend. A fixture
final usa o novo helper; não é byte a byte idêntica à fixture de reprodução.

A apresentação agora começa em null e aguarda uma resposta do DAO. Lista e
três contagens são calculadas juntos. Home usa esse resultado para painel,
fila e acessibilidade da navegação. A observação específica da tela descarta
replay ao perder o último assinante; as observações antigas de diagnóstico
continuam separadas. Há uma observação adicional de Room enquanto a tela está
ativa, sem nova consulta de rede ou gravação.

Durante carregamento, a fila não apresenta lista vazia, contagens nem ações de
envio, repetição ou exclusão. Uma confirmação de exclusão aberta é descartada
ao voltar para carregamento. A resposta vazia confirmada continua exibindo
lista vazia; respostas com registros preservam IDs, payload e comportamento
existente. O indicador de navegação usa “Carregando fila do aplicativo” enquanto
a contagem é desconhecida. O status do painel preserva a precedência anterior
de sincronização, autorização, serviço, falha e pendência depois da resposta.

## Alcance e evidência

Candidato **PROPOSED / CONCEPTUAL** para integração; testes sintéticos locais
**DEMO**, sem nova comprovação **REAL** do piloto. Fluxo/Compose em JVM não
comprovam aparelho físico, consulta Room operacional ou ciclo Activity completo.
Checks, SHA final, patch/bundle, APK e parecer de agente distinto ficam na entrega
externa `C:/CDev/Next2U-Pilot-2026-09-27-queue-loading`. Checks locais não são CI;
o PR permanece DRAFT até os gates aplicáveis. APK não instalado.

Paciente: apenas observação/apresentação. Web Profissional e Tablet ACS: sem
mudança de contratos, entidades ou permissões. WhatsApp/SM Click: sem alterações,
tratado em outro chat. Donos, reading_id, patient_id, device_id, IDs locais,
schemas, DAO, processador, transporte, admissão concorrente, offline e backend
preservados. Contratos centrais ainda pendentes continuam **BACKEND CONTRACT REQUIRED**.

## RECOMMENDATIONS e limites

Manter revisão humana e gates de integração/distribuição. A fila exibida não
confirma recebimento pela equipe. Não se introduz snapshot atômico entre fila,
saúde da API, sincronização e logs. Logs e diagnósticos mantêm suas políticas
anteriores; saúde ainda não consultada mantém sua interpretação preexistente
após carregar a fila. Erro da consulta não ganha novo modelo de erro/retry.
Os testes nativos anteriores permanecem vinculados aos próprios SHAs e não
foram repetidos para esta mudança de apresentação.
