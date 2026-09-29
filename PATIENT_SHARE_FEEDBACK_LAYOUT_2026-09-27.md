# Compartilhamento: conteúdo acessível após feedback

## OBSERVED FACTS

Baseline `a7c23d39757dbec42e58993a28166c24b2d1e594`.
Em 640x320/fontScale2, feedback fixo deixava apenas 36 pontos visíveis de botões
com 56 pontos de altura, mesmo após rolagem. Duas reproduções estritas falharam.
A primeira versão do teste passou porque assertIsDisplayed aceita visibilidade
parcial; fixture/XML/imagens foram preservados e a exigência corrigida antes de
editar produção. Não tratar esses dois PASS iniciais como prova de acessibilidade.

Feedback agora participa da área rolável existente. Título e fechar continuam
fora dela. Cada chamada de feedback solicita scrollTo(0), inclusive mensagem
igual repetida; a rolagem não executa ações de Android. Mensagens, liveRegion,
callbacks, intents, URI, permissões, snapshot e restauração de texto permanecem.
A posição de rolagem usa rememberScrollState; retorno de ação visível não implica
que todo o cartão ou aviso caiba ao mesmo tempo em qualquer tamanho de tela.

Cinco testes exercitam componente produtivo com Application neutra, bitmap/texto
artificiais e ContextWrapper que intercepta chooser/clipboard: erros em tela baixa,
retrato/fontScale2, erro repetido após rolar e restauração sem repetir efeitos.
Verificam altura integral das ações nas fixtures e retorno completo do feedback.
Nenhum chooser real aberto ou conteúdo copiado/enviado nesses testes.

## RECOMMENDATIONS / alcance

App Paciente: layout e reposicionamento de feedback. Web, ACS e WhatsApp/SM Click
intactos; sem mudanças em entidades, IDs, contratos, persistência, transporte,
permissões, concorrência/sincronização ou offline. Não há novo destinatário nem
suposição de entrega. LOCAL/DEMO nos ensaios; PROPOSED / CONCEPTUAL para aceite;
sem nova capacidade REAL. Garantias centrais BACKEND CONTRACT REQUIRED.
Não comprova Android físico, TalkBack, MainViewModel operacional ou CI. Evidências
vinculadas ao SHA exato e revisão distinta na entrega externa. Manter PR DRAFT,
sem merge, instalação, distribuição ou aceite do piloto.
