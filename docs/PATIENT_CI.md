# Verificações Android no GitHub

Rafael autorizou em 27/09/2026 configurar e executar CI na PR6. O workflow
Android Verify executa em PRs (inclusive bases empilhadas) e push em main.

| Check | Tarefas Gradle |
| --- | --- |
| android-lint | lintDebug, lintRelease |
| android-compile | compileReleaseKotlin, compileDebugAndroidTestKotlin |
| android-test | testDebugUnitTest |
| android-build | assembleDebug |

Cada job confere o HEAD exato informado pelo evento antes/depois da execução.
Em PR, testa o HEAD do candidato, não o merge sintético. Mudança de base exige
reavaliar composição e aplicabilidade. Resultados são vinculados ao run/SHA;
676 PASS/1 skip do Windows não são resultados presumidos do runner Linux.

Runner Ubuntu24.04 descartável, Java21, SDK36.1, build-tools36.0.0, wrapper
Gradle versionado e dependências declaradas pelo projeto. Actions oficiais
fixadas por SHA, token somente leitura e credencial Git não persistida.
Os jobs podem baixar ferramentas/dependências. Não usam configuração operacional:
.env local com chaves vazias e URL loopback, sem google-services.json.
Os hosts diretos conhecidos Core/Gemini são redirecionados para loopback no runner;
isso não é sandbox universal de rede nem proteção para todo código futuro.

O APK debug usa certificado efêmero de CI e não é artefato de distribuição nem
atualização compatível com o M8. Não há upload de APK, release, emulador, teste
conectado ou instalação. Compilar testes instrumentados não significa executá-los.
Não se altera assinatura operacional, contrato, schema, capacidade REAL ou fila.

CI verde não é aceite físico, aprovação formal ou autorização de merge/publicação.
Nenhum ruleset, proteção de branch ou auto-merge é configurado por este workflow.
As evidências permanecem LOCAL/DEMO ou CI sintética no contexto do runner;
integrações pendentes continuam BACKEND CONTRACT REQUIRED.
