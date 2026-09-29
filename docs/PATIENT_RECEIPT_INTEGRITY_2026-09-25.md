# Integridade da conclusão de envios

## OBSERVED FACTS

Base9f2bc5f1d6b28b234ea7ed6ad93799a0e81eb098 limpa; branch isolada
codex/patient-receipt-integrity, C:/CDev/Next2U-Patient-Receipt-Integrity.
GitHub paciente mainf35d12b26c5a2305004271c2a05068782a1c9fc8; PR5 DRAFT
e9a80ef386d207a1bc6fe66bef3969eafa84aae5 sobre9a239d9113bc671624643acc975b3e10042f4a57.
Conta somente leitura. Core14 aberto em90c3a1d334834f5d7620ee1c7b4e80f938c44154,
implantação/versão contratual acordada não comprovadas. Composição anterior
preservada, incluindo o trabalho do PR5 e correções locais; não reintegrar por cima.

### Achado e correção

WearableRepository.markAllAsLocalSynced ainda convertia todas as linhas em SYNCED,
zerava tentativas e substituía erros inclusive de autorização, sem resposta remota.
MainViewModel mantinha a ação e HomeScreen transmitia um callback até
SyncStatusIndicator. O componente recebia mas NÃO usava esse callback: a interface
atual não oferecia botão para a ação. Não há prova de execução no piloto.

Removidos o método e toda a cadeia interna sem consumidor. Nenhum botão visível
removido e nenhuma mudança visual planejada. Impede reutilizar acidentalmente esse
atalho como conclusão de envio. Nenhum dado existente é convertido, reenviado ou
reparado; não é possível inferir quais linhas históricas tiveram recibos válidos.
Não se deve promover SYNCED legado a prova nova de confirmação central.

O processamento normal continua usando correlação de recibos existente, sem
alterar contrato, transporte, endpoint, schema, chave, migração ou IDs. Exclusão
manual existente não foi acionada ou redesenhada neste delta. Métricas locais não
equivalem a dados recebidos pela equipe; separação da UI permanece.

### Testes novos

- Recibo de lote recebido, primeira linha persistida e segunda gravação falha:
  fechar/reabrir SQLite sintético conserva primeira confirmação e demais linhas
  originais; novo envio contém somente pendentes e conserva IDs/payloads.
- Cancelamento depois da primeira gravação: primeira confirmação permanece,
  restante intacto; gate liberado e segunda tentativa envia somente restante.
- Duas instâncias do repository no mesmo processo/banco: enquanto a primeira
  aguarda resposta, retry da segunda não altera linha FAILED/tentativas e não faz
  novo envio. Depois, a tentativa pode prosseguir normalmente.

Respostas e deduplicação são simuladas. Room/SQLite local, não SQLCipher nativo,
morte de processo, falha física ou backend REAL. Gate é companion object dentro
do processo; não comprova exclusão mútua distribuída ou entre processos.
O código de reconciliador existente é exercitado, não promovido a contrato confirmado.

Checks, SHA final e revisão independente no pacote:
C:/CDev/Next2U-Pilot-2026-09-25-receipt-integrity/.
Autorrevisão SELF_REVIEW_ONLY, resultados locais não são CI. Nenhum APK instalado.
Governança compartilhada, em especial ADR006/008/011/012/013, aplicada; não alterada.

## RECOMMENDATIONS

LOCAL / DEMO: ensaios com dados e transporte sintéticos. BACKEND CONTRACT REQUIRED:
versão contratual/ambiente de ingestão e comprovação de recibos/deduplicação/recovery.
Solicitar Leandro incorporação humana ou Write e entregas contratuais já listadas.
Não liberar fila acumulada antes do aceite. Integração e merge permanecem humanos.
Web, ACS celular/tablet e WhatsApp/SM Click não mudam; ACS segue DEMO. Não se cria
identidade, paciente paralelo, permissão, vínculo ou contrato entre os quatro canais.
