# Piloto — preservação local e leitura de histórico

## OBSERVED FACTS

Baseline local preservado: `17ab0620bdb46e1c726f47b67246bc897a278d8f`.
Branch isolada: `codex/patient-pilot-preservation`. GitHub consultado antes da
criação: PR5 DRAFT `e9a80ef386d207a1bc6fe66bef3969eafa84aae5`, base PR4
`9a239d9113bc671624643acc975b3e10042f4a57`, main
`f35d12b26c5a2305004271c2a05068782a1c9fc8`; conta sem push. Não é merge
completo do PR5, publicação ou candidato pronto para instalar no aparelho com dados.

Foram portados do PR5 apenas os argumentos de consulta do histórico VE30 e
seus testes: dia inicial 0, posição inicial 1, capacidade no argumento próprio.
Comparação com `OperaterActivity` do SDK oficial no commit
`add57a049a916c210c5463a66b097ad94265e481` confirma a ordem usada pelo exemplo.
Sem trocar SDK/binários, tratamento local de cancelamento, estado de progresso,
coleta da aplicação, fila, autorização ou transações existentes.

O banco v6 podia ser recriado por fallback destrutivo em incompatibilidade;
um arquivo SQLite não criptografado e seus auxiliares também eram apagados na
abertura. Agora a falta de migração interrompe a abertura. A detecção de SQLite
legado também interrompe a abertura conservando os arquivos. Não há conversão,
limpeza ou migração de dados. Uma instalação nessas condições precisa de
recuperação explícita antes de voltar a operar; não foi criada tela de recuperação.
A aplicação pode falhar ao iniciar nessas condições. Preservar arquivos não
equivale a tornar os dados legados novamente acessíveis.

Versão do banco continua 6. A configuração SQLCipher/chave e seu fallback
preexistente quando a biblioteca nativa não carrega não foram redesenhados.
Os testes usam SQLite sintético, não comprovam criptografia, falha de energia
ou reinício físico/process death no Android. Nenhum aparelho foi modificado.

## Verificação

`DatabasePreservationTest`: reabertura do arquivo após falha de envio; pausa
401 após reabertura e tentativa manual 503; rejeição de upgrade/downgrade sem
migração conservando registro e versão; bloqueio de SQLite legado conservando
arquivo, WAL, SHM e journal. Usam a política de schema do builder de produção,
sem o factory SQLCipher e sem rede real. A suíte existente continua cobrindo
atomicidade, cancelamento e concorrência. `VeepooHistoryReadSettingsTest` verifica
os argumentos; leitura física VE30 permanece pendente.

Executar testes completos, assembleDebug, compileReleaseKotlin, lintDebug e
lintRelease com o cache Android existente. SHA candidato, contexto, resultados
e revisão independente ficam na entrega externa
`C:/CDev/Next2U-Pilot-2026-09-24/continuation/`.

## RECOMMENDATIONS / composição pendente

Não aplicar substituição integral de WearableRepository do PR5: preservar
LocalWriteTransaction, pausa de autorização persistida e gate compartilhado.
Relatório independente `contract-review.md` identifica confirmação indevida de
respostas ambíguas, lote misto de pacientes, identidade/tempo e migração v6→v7
a exercitar antes da integração do envio. A semântica de métricas/ausência também
difere entre Android e Core; confirmar com Leandro antes de homologar medições.

Core PR14 foi observado DRAFT em `90c3a1d334834f5d7620ee1c7b4e80f938c44154`,
base `51478b6413e336145cca21687b96daf9627b6634`. Documento de idempotência
marcado CONFIRMED nessa composição não comprova versão acordada/implantada.
Enviar fila acumulada continua bloqueado até validação acompanhada do backend.

**PROPOSED / CONCEPTUAL:** candidato local; verificações locais não são CI nem
homologação REAL. **BACKEND CONTRACT REQUIRED:** integração ainda não alinhada
e comprovada. ACS permanece **DEMO**. Patient/device/IDs e APIs não mudaram.
Web Profissional, ACS celular/tablet e WhatsApp/SM Click não recebem mudança;
visibilidade/permissões/sincronização continuam dependentes do backend central.
Autorrevisão do autor é **SELF_REVIEW_ONLY**; revisão distinta deve identificar o
SHA efetivo. Integração final e merge pertencem ao humano.
