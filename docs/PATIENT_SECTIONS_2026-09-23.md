# Seções expansíveis — clareza em smartphones e tablets

## OBSERVED FACTS

Continuação de UI/UX autorizada nesta conversa. Preflight limpo em
`69b97b56a38b4fbcb4052edb0f82ca51078e7c83`, branch
`codex/patient-responsive-devices`, remoto `leanderdulac/HBand-`.
GitHub confirmou main `f35d12b26c5a2305004271c2a05068782a1c9fc8` e PR #4
DRAFT com HEAD `9a239d9113bc671624643acc975b3e10042f4a57` e base
`04938c46fdba3ab59789943f41b7a48ea5b4b72e`. PRs #1/#2 mantêm a pilha conhecida.
Ancestralidade local conferida, sem operação Git pendente ou conflito novo.
Permissão GitHub somente leitura; entrega local sem push/merge.

Reconciliação de escopo: a restrição genérica de alterar o mobile em
PROJECT_RULES rege o projeto Web. O pedido explícito atual autoriza esta melhoria
na apresentação do app Paciente, em seu checkout nativo e sobre o handoff
FRONTEND_UI_HANDOFF. Não autoriza mudanças em BLE/Veepoo, contratos ou backend.

## Mudança

PatientSection alinha o título no início e acrescenta seta para abrir/fechar.
O texto pode ocupar várias linhas sem reduzir a fonte; a seta não cria um
segundo alvo de toque nem um anúncio duplicado para leitor de tela.
A seção preserva o estado após restauração e continua usando o botão inteiro.
Atividades em andamento mantêm a seção aberta e sem seta de recolher; o aviso
de acessibilidade usa “atividade”, válido também para sincronização/localização
do relógio, além de medições. Callbacks e regras de expansão são preservados.

Arquivos: componente compartilhado, testes de leitura e este registro.
Web Profissional, Tablet ACS e WhatsApp/SM Click sem impacto. IDs, permissões,
atualização de dados, concorrência e comportamento offline sem alteração.
Nenhuma nova capacidade **REAL** declarada. Fixtures de testes são **DEMO**;
integração central e garantias pendentes continuam **BACKEND CONTRACT REQUIRED**.

## Verificação

Fonte e testes verificados em `262c1a393faf050f255cf05e8bc9cb2d7b7c3a26`.
Execução local Windows/Codex em 23/09/2026, JDK 21.0.12.1, Gradle 9.3.1,
cache existente offline. Comando: `:app:testDebugUnitTest :app:assembleDebug
:app:compileReleaseKotlin :app:lintDebug -Pandroid.builder.sdkDownload=false
--offline --console=plain --continue` pelo wrapper Gradle.

- Testes focados: 6 PASS antes do commit, mesmo conteúdo confirmado no candidato.
- Suíte completa: 181 casos, **180 PASS, 0 falhas, 1 SKIP** pela limitação
  conhecida de caminhos Android/FileProvider no Windows.
- APK DEBUG e compilação Kotlin release: **PASS**.
- Lint: **FAIL**, `MissingPermission` em HBandBleManager.kt:1640 e
  `InvalidFragmentVersionForActivityResult` em MainActivity.kt:24; 48 avisos.
  Bloqueios herdados, sem supressão. O comando conjunto terminou com falha;
  resultado global não é PASS.

APK `next2u-paciente-sections-262c1a3.apk` instalado por atualização no M8 WIFI
Android 13, sem limpeza de dados. Hash SHA-256 do arquivo e da cópia extraída
do pacote instalado: `6426D1D47514785E0ABB017343F2183AB3666D12E0996DB04F1C3C5988A61140`.
Conferência visual de Ajustes aberto/fechado e Envios com fonte 1.6 em retrato.
Em paisagem com fonte 1.6, XML e toque confirmaram preservação da seção aberta
e fechamento; captura salva, sem concluir a inspeção visual dessa imagem.
Fonte 1.0, orientação do usuário 0 e rotação automática 1 restauradas e lidas
novamente. Nenhuma busca/conexão, medição ou envio foi disparado.
ACS permanece 1.0.6 (16), última atualização `2026-09-15 13:35:21`.

Evidências e APK: `C:/CDev/Next2U-Patient-Delivery/2026-09-23-sections`.
O commit posterior altera somente este registro, sem mudança em app/.
Aplicam-se Gradle/JDK e testes nativos; pnpm não se aplica a este checkout Kotlin.
Sem dependências novas. **SELF_REVIEW_ONLY**, não CI nem revisão independente.

## RECOMMENDATIONS

Confirmar compreensão com usuários e leitura falada completa com TalkBack.
Testes de componentes e inspeção no M8 não substituem ensaio em smartphone físico.
