# Ensaio Android nativo do cartão e FileProvider

## OBSERVED FACTS / escopo

Base produtiva520e27633f3650ed84e6320b9382d47755eb9b4c preservada, sem modificação
em src/main, schema, dependências, manifest ou configuração. Acrescenta teste de
instrumentação e runner de laboratório sobre o build storageLab já existente.
O teste JVM de FileProvider é ignorado no Windows por caminhos não Android;
essa limitação não deve ser ocultada removendo o guard ou substituindo o provider.

Novo AVD, somente imagem SDK já instalada, pacote .storagelab sem INTERNET,
Application neutra e inicializadores operacionais removidos. Nenhum banco é
iniciado. O runner recusa aparelho físico, AVD incorreto, APK não lab/com internet,
SHA divergente, fonte suja, pacote já instalado ou pasta de evidência existente.
Sem apagar/recriar dados. Dois processos separados, dados sintéticos preservados.

Fase gerar: duas imagens com gerador produtivo, totais ausentes e conhecidos;
confere resumo, nomes distintos, URI content, autoridade, MIME, nome/tamanho por
query, bytes via ContentResolver iguais ao arquivo e PNG1080x1350 decodificável.
Confere provider não exportado com suporte a concessões e recusa caminho fora
das raízes configuradas. Salva hashes/caminhos sintéticos e PID no próprio app.

Fase reabrir: processo diferente lê as mesmas URIs, compara hashes/tamanho e
evidência original sem regravar/gerar imagens. Repete recusa de raiz não configurada.
Nada é enviado a outro aplicativo ou destinatário; não abre chooser.

## RECOMMENDATIONS / limites

Teste LOCAL/DEMO; candidato PROPOSED / CONCEPTUAL para incorporação. Resultados,
APK hashes, fonte e revisão distinta serão vinculados na entrega externa. Não
presumir execução bem-sucedida apenas pela existência deste código.

O recorte comprova, se aprovado, renderização e provider real em filesystem
Android/API35 do emulador. Usa UID do próprio aplicativo: não valida concessão
temporária a UID externo, chooser, apps destinatários, revogação, acessibilidade,
aparelho físico ou todas as versões Android. Reabertura imediata não garante
retenção ilimitada de cache. Assinatura/package lab não são versão distribuível.

Sem capacidade REAL adicional, aceite clínico, publicação, distribuição ou
atualização de APK normal. Testes locais não são CI. Contratos centrais não
confirmados continuam BACKEND CONTRACT REQUIRED. Core/Web/ACS/SM Click, filas,
IDs/chaves, consentimento, destinatários e envio isolado do Leandro não mudam.
