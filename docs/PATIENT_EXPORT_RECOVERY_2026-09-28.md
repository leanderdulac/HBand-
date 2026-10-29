# Exportações locais: falha de geração e área de transferência

## OBSERVED FACTS

Incremento sobre `c810e6e62ac29097a01220685db4516291d24e5f`, PR6 empilhada sobre
PR5 `e9a80ef386d207a1bc6fe66bef3969eafa84aae5`. CSV e cartão copiavam texto de
saúde sem a marca de conteúdo sensível do Android. Geração podia deixar arquivo
parcial se a gravação/URI falhasse; PNG ignorava o retorno falso da compressão.

A implementação usa o mesmo tratamento de geração para CSV e PNG: arquivo novo
exclusivo, stream fechado antes da criação de URI/resultado e rejeição de conteúdo
vazio. Falha de escrita, cancelamento ou publicação local do resultado tenta
remover apenas esse arquivo novo, preservando a exceção original. Falha de limpeza
é anexada à exceção; não é garantia de apagamento seguro ou de sucesso em disco
indisponível. Arquivos anteriores e os já entregues ao compartilhamento não são
removidos. PNG rejeita compressão falsa e libera seu bitmap quando a preparação
falha. A imagem bem-sucedida continua pertencendo à prévia existente.

Copiar CSV e copiar resumo usam `ClipDescription.EXTRA_IS_SENSITIVE`, mantendo
texto, MIME e ação explícita do usuário. Essa marca pede ocultação da prévia de
sistema em Android compatível; não criptografa o clipboard, não impede colagem,
leitura autorizada pelo sistema, histórico de teclado ou cópias em terceiros.
Não implementa consentimento central, limpeza temporizada ou revogação.

Sem mudança de banco, fila, modelos, IDs, dados exportados, destinatários,
permissões do FileProvider, dependências, CI ou configuração operacional.
Os callbacks da interface continuam distinguindo preparação de entrega.

## Verificação e alcance

Testes usam arquivos e conteúdo fictícios, Robolectric/JVM: escrita interrompida,
arquivo vazio, falha de publicação local, cancelamento, repetição, concorrência,
preservação de exportações anteriores, marca sensível no SDK24/36 e nos dois
botões reais da interface. O teste do gerador usa provider ausente e confirma
limpeza em tentativas sucessivas. A conferência FileProvider em caminhos Android
mantém sua restrição histórica no Windows; não é substituída por um falso sucesso.

Comandos, SHA final, resultados e parecer independente ficam na PR6 e na entrega
externa `C:/CDev/Next2U-Pilot-2026-09-28-export-recovery/`. Não antecipar aprovação:
testes locais não são CI nem aceite físico. Inspeção do autor é SELF_REVIEW_ONLY.

## RECOMMENDATIONS

Classificação da evidência: LOCAL/DEMO com dados sintéticos. Comportamento do
clipboard visual e receptores reais requer validação no Android alvo. Arquivos
completos retidos continuam sob a política de cache já existente; não foi criada
política de retenção/expiração. Morte abrupta do processo/energia durante escrita
não executa este tratamento de exceção e não foi resolvida aqui.

Somente App Paciente é alterado. Web Profissional, Tablet ACS, WhatsApp/SM Click
e backend não recebem novos consumidores ou comandos. Contratos, offline ACS,
autorização, sincronização e aceite integrado permanecem BACKEND CONTRACT REQUIRED
quando ainda não confirmados. Nenhuma mensagem, instalação física, merge ou deploy.

Referências: [Android Clipboard](https://developer.android.com/develop/ui/views/touch-and-input/copy-paste)
e [Bitmap.compress](https://developer.android.com/reference/android/graphics/Bitmap#compress(android.graphics.Bitmap.CompressFormat,int,java.io.OutputStream)).
