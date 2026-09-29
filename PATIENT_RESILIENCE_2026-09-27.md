# Recuperação de permissões e compartilhamento

## OBSERVED FACTS

Baseline `8ec3e550ec4a665c863b11131e626f81fc006446`.
Bloco consolidado de cinco defeitos, reproduzidos antes da edição produtiva:

| Fluxo | Evidência anterior | Comportamento corrigido |
|---|---|---|
| Permissão pendente | Segunda solicitação substituía a ação da primeira | Pedido admitido permanece; outro toque aguarda sua resposta |
| Abertura da solicitação | Exceção escapava da tela | Estado pendente é liberado e nova tentativa explícita fica disponível |
| Configurações de permissões | Falha de abertura escapava da tela | Orientação local sem inventar concessão nem iniciar busca/conexão |
| Imagem da prévia | Arquivo removido ou vazio ainda era enviado ao chooser | Verifica arquivo legível, regular e não vazio antes do envio; preserva prévia/texto e oferece preparar outro cartão |
| Restauração de feedback | Recibo de cópia de A reaparecia na prévia B | Estado completo do diálogo isolado por URI e resumo do snapshot |

Relógio: três reproduções falharam. Compartilhamento: três falharam e o controle
de arquivo disponível passou. Fixtures e XML originais preservados na entrega.
Seis novos testes de relógio e nove de recuperação do cartão cobrem negativa,
retry, cliques repetidos, troca de ação após falha, diretório/vazio/ausente,
canRead falso determinístico, intenção original, troca de prévia em composição
e restauração variando URI/resumo separadamente. Testes anteriores mantêm a
cobertura de restauração da mesma prévia e perda da ação de permissão pendente.
Um caso visual adicional exercita aviso de imagem indisponível e ações em tela
baixa/fontScale2. Texto comprido continua rolável; não se exige exibição simultânea.
Os arquivos temporários de um byte dos testes antigos apenas permitem alcançar
o chooser interceptado; não são chamados de PNG válido.

Sem alteração de política de permissões: mesmas permissões e callbacks BLE.
Retorno de permissão limpa o estado antes de executar a ação admitida. Falha de
Settings não equivale a negativa/concessão. Share mantém URI/resumo/MIME/read flag
do snapshot; não regenera nem apaga dados. Abrir chooser não confirma entrega.
Verificar arquivo não valida PNG, FileProvider ou leitura futura; a corrida com
remoção posterior continua possível. Clipboard e registros dos ensaios são artificiais.

Auditoria independente adicional de perfil, respiração, hidratação e startup não
demonstrou outro defeito neste recorte; não se introduz persistência/controlador
novo sem evidência. CSV permanece com sua implementação e cobertura anteriores;
isso não equivale a validar todos os cenários de falha possíveis.

## RECOMMENDATIONS / alcance

App Paciente: recuperação local da interface. Web, ACS e WhatsApp/SM Click intactos;
sem entidades, IDs, contratos, DAO/schema, transporte, política offline ou sincronização
novos. Sem intervenção no trabalho de leitura isolada do Leandro.
LOCAL/DEMO nos ensaios; PROPOSED / CONCEPTUAL para aceite; nenhuma capacidade REAL
nova comprovada. Garantias centrais BACKEND CONTRACT REQUIRED. Testes JVM/Robolectric
não comprovam aparelho físico, BLE, destinatário, process death real, TalkBack ou CI.
SHA, seis tarefas finais, revisões distintas e conferência remota registrados na
entrega externa. Manter PR DRAFT; sem merge, instalação, distribuição ou aceite do piloto.
