# Recuperação dos avisos locais e duração entre tentativas

## OBSERVED FACTS

Baseline `9f970ddef6d49c1818b06827ba6a604be86ca968`.
Falhas recuperáveis na obtenção do serviço, criação do canal ou postagem da
notificação Android ficam contidas na fronteira de envio. Os métodos de aviso
continuam produzindo o feedback local. Cancelamento e erros fatais não são
convertidos em falha comum nem mascarados. Não há repetição automática.

O intervalo preexistente de oito segundos entre avisos automáticos passa a usar
SystemClock.elapsedRealtime, uma duração monotônica que inclui sono do aparelho.
O relógio civil deixa de governar essa duração. Ausência de tentativa anterior
é representada por null, permitindo o primeiro aviso mesmo com menos de oito
segundos desde o boot. A janela continua compartilhada entre avisos alto/baixo,
é consumida antes da tentativa Android e não é consumida por valores dentro dos
limites, avisos desativados ou botões manuais de teste.

Limites cadastrados, textos, identificação de simulação, canal, IDs, prioridade
e permissões permanecem iguais. A conclusão da chamada Android não comprova
exibição, leitura ou entrega. Permissão negada pode resultar em descarte silencioso;
não se afirma que toda negativa lança exceção. A falha contida não recebe um recibo
falso de entrega. O aviso local continua descrevendo o valor, não entrega externa.

Antes da correção, seis testes produziram três exceções Android reproduzidas,
dois controles aprovados e uma falha de duração: avançar o relógio Android simulado
não avançava o System.currentTimeMillis da JVM usado pelo código anterior.
Isso demonstra a dependência do relógio escolhido no laboratório; o efeito de
adiantar/atrasar a hora civil foi identificado por análise da subtração no código.
Não foi alterada a hora do computador ou de um aparelho. Fixture e XML originais
ficam preservados na entrega externa, sem atribuição ao candidato corrigido.

Dez testes novos verificam falhas de canal/postagem, próxima tentativa, avisos
manuais, limites exatos de 7.999/8.000 ms, janela compartilhada, primeiro aviso
com uptime curto, sono simulado, desativação, limites inclusivos, cancelamento
e erro fatal. Testes existentes preservam os textos e parâmetros Android.

## RECOMMENDATIONS / alcance

O ensaio chama métodos reais do MainViewModel alocado sem seu construtor, com
Application neutra e apenas os campos utilizados injetados. Não executa init,
coletor operacional, banco, BLE, workers, rede ou fluxo completo do aplicativo.
NotificationManager é interceptado; não há notificações reais. A continuidade
do coletor é uma consequência da contenção no caminho chamado, não um ensaio
de startup ou coleta integral. Não houve mudança de layout, portanto não foi
aberto outro ciclo de capturas visuais.

App Paciente: recuperação local. Web Profissional, Tablet ACS e WhatsApp/SM Click
sem alterações. Sem novos registros, IDs, contratos, schema, filas, transporte,
política offline ou critério clínico. Duração local não é relógio central nem
altera timestamps de medições. Preservado o trabalho de leitura isolada do Leandro.
Ensaios LOCAL/DEMO; candidato PROPOSED / CONCEPTUAL para aceite. Nenhuma capacidade
REAL nova atestada; garantias centrais BACKEND CONTRACT REQUIRED. SHA completo,
seis checks finais, revisão distinta e publicação conferida na entrega externa.
Manter PR DRAFT; sem merge, instalação, distribuição ou aceite físico do piloto.
