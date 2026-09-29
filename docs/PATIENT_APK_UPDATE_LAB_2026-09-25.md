# Ensaio de substituição do APK preservando a fila cifrada

## OBSERVED FACTS

Base c0fbd3735d314d4a61e44e4b187fbe0b7ade2ae8, limpa. Branch isolada
codex/patient-update-lab em C:/CDev/Next2U-Patient-Update-Lab.
GitHub revalidado: main f35d12b26c5a2305004271c2a05068782a1c9fc8;
PR5 DRAFT e9a80ef386d207a1bc6fe66bef3969eafa84aae5 sobre
9a239d9113bc671624643acc975b3e10042f4a57, conta sem escrita.
PRs1/2/4 históricos não alteram a composição local previamente conciliada.
Core14 aberto em90c3a1d334834f5d7620ee1c7b4e80f938c44154, sem implantação comprovada;
ACS2 depende de ACS1, ambos DRAFT. Nenhuma alteração nesses projetos.

Escopo autorizado por Rafael: preservação/recuperação e preparação/execução de
testes do paciente. Não alterar app instalado com dados do piloto ou liberar fila.
As restrições históricas da Web não são autorização sobre aplicativos; este
trabalho está autorizado pelo pedido atual específico do proprietário.
Governança compartilhada ADR011/012/013 aplicada; nenhuma regra alterada.

### Composição

Somente script de ensaio e este documento. Código produtivo, testes Android,
build, schema7 e chave mantidos idênticos à base. Não cria API/contrato ou fixture
operacional. O AVD Next2U_Storage_Lab_20260925 já contém dois itens sintéticos
semeados pelo f6aaad9fef8bc161600b5db50dfadb4e3118084b, incluindo pausa401.

tools/Test-StorageAndroidLabUpdate.ps1 verifica serial/qemu/boot/nome do AVD,
hashes fixos dos APKs antigos, pacotes, assinaturas e certificado, versionCode1,
ausência de INTERNET e igualdade dos manifestos compilados com a base isolada.
Essa igualdade conserva Application comum, componentes BLE/boot/launcher
desativados, ausência de providers de auto-init e alvo/runner de instrumentação.
APKs instalados devem corresponder aos arquivos antigos; splits não são aceitos.

Executa somente reopen antes da substituição; se falhar não instala. Substitui
somente .storagelab e .storagelab.test com instalação -r, confere hashes instalados
e executa reopen novamente. Não usa seed, clear, uninstall, downgrade, exclusão
de chave ou reparo. Falha parcial preserva estado e registra a etapa; sem rollback
automático. PreflightOnly verifica as pré-condições sem instrumentar/instalar.

### Evidência e alcance

O reopen já existente abre SQLCipher nativo usando Android Keystore e verifica
as duas linhas da fila, IDs/payload/status/tentativas/tempos/erro e digest dos
metadados da chave. Não há métricas/perfil semeados no banco principal: o ensaio
não comprova preservação de todo histórico clínico. Ambos os APKs têm versionCode1
e schema7: substituição assinada, não upgrade de release nem migração6→7.

Resultados, hashes e revisão independente do candidato final:
C:/CDev/Next2U-Pilot-2026-09-25-update-lab/.
Autorrevisão SELF_REVIEW_ONLY. Checks locais não são CI, revisão não autoriza merge.
Ensaios anteriores permanecem associados aos respectivos SHAs.

## RECOMMENDATIONS

Classificação LOCAL / DEMO: laboratório sintético isolado, sem integração REAL.
Não comprova update do app normal, Keystore físico, BLE/VE30, queda de energia,
interrupção do instalador, recuperação de chave perdida ou backend.
Antes de atualizar aparelhos do piloto, identificar APK/assinatura/schema e
procedimento de recuperação com Leandro. Não desinstalar/limpar dados.

Web, ACS celular/tablet e WhatsApp/SM Click sem alteração; IDs/donos e fronteiras
preservados. ACS continua DEMO e contratos/ambiente BACKEND CONTRACT REQUIRED.
Login precisa de conta autorizada e teste de sessão/permissões/logout; ingestão
precisa de contrato acordado e versão implantada. Integração/merge humanos.
