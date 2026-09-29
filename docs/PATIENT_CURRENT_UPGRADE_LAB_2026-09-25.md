# Substituição sintética 5c80 → 466d

## OBSERVED FACTS

Escopo autorizado por Rafael: ensaio isolado de conservação de dados. Base limpa
466d79a8ef0229adb72f36f56a9743a5db99960f; PR5 GitHub ainda DRAFT, HEAD
e9a80ef386d207a1bc6fe66bef3969eafa84aae5 e base
9a239d9113bc671624643acc975b3e10042f4a57. Conta sem escrita no paciente.
PR5 é ancestral integral da base local; PRs históricos1/2/4 não são incorporados
novamente. Autorização atual específica permite este laboratório do paciente;
restrições históricas da Web não ampliam nem substituem esse pedido.

Somente teste Android, runner host e este documento são adicionados. Nenhuma
fonte produtiva, schema, contrato, assinatura ou configuração é alterada.
O aplicativo anterior é o APK arquivado do SHA
5c80c1b22c3c4c2d5ac20a2fda017ae1aa019de1 (hash b1b8046ed1bfbf23c8179b28d6e2aa8af21888ed5b0f42d1c21c48846a11e8b5).
O aplicativo posterior é o APK arquivado do SHA
466d79a8ef0229adb72f36f56a9743a5db99960f (hash 7b36f18fc9ae1c8785d09fa2ef529a8cd442c93e6934526315e56eb95545cb73).
O APK de instrumentação novo é separado e exercita os dois aplicativos exatos.

Runner exclusivo emulator-5590/Next2U_Upgrade466d_Lab_20260925: exige pacotes
ausentes inicialmente, hashes fixos, assinatura debug compatível, manifestos
isolados equivalentes e ausência de INTERNET. Instala versão antiga/teste,
semeia uma vez, reabre, substitui somente o app com install -r -t, verifica e
encerra/reabre o processo. Quatro PIDs distintos são exigidos. Não contém clear,
uninstall, downgrade, exclusão de chave/fila nem reparo. Recusa reexecução no AVD
semeado; falha conserva estado/evidência, sem rollback automático.

Fixture usa entidades/DAOs existentes: duas linhas em cada tabela histórica,
um perfil CURRENT_USER explicitamente sintético e quatro itens da fila com
estados PENDING, FAILED401, FAILED403 e SYNCED. Compara todas as colunas, tipos,
valores, IDs/payloads, tentativas e horários, schema7, digest de proteção da chave
e reabertura SQLCipher. Baseline externo ao SQLite fica no armazenamento privado
do próprio pacote, é preservado e comparado com digest também pelo host.

## Verificação e limites

Resultados, SHA do harness, comandos, APKs, hashes e revisão independente:
C:/CDev/Next2U-Pilot-2026-09-25-upgrade-466d/. A existência deste roteiro não
declara sucesso de execução; consultar resultados efetivos. Checks locais não
são CI; SELF_REVIEW_ONLY não substitui revisor distinto. Sem push/merge automático.

Classificação DEMO/local sintética; candidato PROPOSED / CONCEPTUAL. Ambos os
apps usam versionCode1/schema7: substituição de pacote de laboratório, sem
incremento de versão de release ou migração. Application simples, sem startup
operacional, BLE, UI ou transporte. SYNCED é confirmação local sintética, não
aceitação backend; 401/403 são marcadores persistidos, não sessão autenticada.
Não comprova atualização do piloto, assinatura de distribuição, Keystore físico,
energia/disco, perda de chave, instalação interrompida, hardware VE30, deduplicação
remota ou visibilidade entre canais. Não há envio, nem mesmo transporte simulado.

Web Profissional, ACS e WhatsApp/SM Click permanecem inalterados; não há entidades
centrais novas nem mudança de donos/IDs/contratos. Backend/conta/contratos ACS e
teste integrado continuam BACKEND CONTRACT REQUIRED onde não confirmados.
Incorporação, distribuição, integração e merge continuam humanos. Piloto não pronto.
