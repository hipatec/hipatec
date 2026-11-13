# Aplicação autorizada das migrações no Neon

## Resultado conferido

Hermes aplicou em 2026-11-13, após autorização explícita do usuário, as quatro migrações abaixo na branch `production` do projeto Hipatec. A identidade foi confirmada no console antes da submissão. Nenhum dado foi importado ou criado como fixture no destino.

Antes do primeiro DDL: snapshot manual presente no console (`production at 2026-11-13 20:27:29 UTC (manual)`), preflight de ausência das novas tabelas/colunas e compatibilidade de comprimento de descrição. O snapshot foi confirmado como existente; não foi realizado ensaio de restauração desse snapshot. Ensaios de migração e preservação foram realizados previamente em PostgreSQL local isolado.

## Artefatos aplicados, sem reescrever seus bytes

- `20261113_create_evento.sql` — SHA256 `fb60802465bff3bf4de83d3512573da9baee8cd3729d9ded06516cd16adb9aca`.
- `20261113_create_curso_extend_mentoria.sql` — SHA256 `c42d16856b3b890c7a646b68e3a45d3c04d2be3c0f918a143a1f90dd02449f69`.
- `20261113_create_denuncia.sql` — SHA256 `5719ca61a007724f24d9aafd98255ac32850fff5c57ca2584334c21430896285`.
- `20261113_denuncia_evidencia.sql` — SHA256 `16824909a9e30b98869e3a1c31013b45c32e1768821e3ef8df88b9cebff7410d`.

Cada script conserva suas próprias transações. Uma consulta de recibo foi anexada depois do script, sem mudar suas instruções. A UI não apresentou grade estruturada nos lotes DDL; a confirmação foi feita por novas consultas independentes ao catálogo, não pelo clique em Run. Uma divergência inicial entre DOM renderizado e SQL abortou antes de executar: CodeMirror duplicava visualmente linhas vazias. A verificação passou a comparar o documento real do editor byte a byte (ignorando apenas whitespace periférico).

## Verificação independente após DDL

- Tabelas públicas passaram de dez para treze: novas `evento`, `curso` e `denuncia`.
- `mentoria` recebeu `area`, `formato` e `requisicao_id`; descrição confirmada como VARCHAR(4000).
- `denuncia.evidencia` confirmada como VARCHAR(5000), com `autor_perfil_id` presente.
- Constraints UNIQUE de idempotência de evento/curso/mentoria e pessoa/alvo de denúncia verificadas no catálogo; definições de FKs, CHECKs e índices capturadas para comparação.
- Todas as dez tabelas anteriores conservaram contagens e fingerprints agregados de linhas. Nenhum valor de credencial ou linha pessoal foi exibido ou versionado.
- As três tabelas novas ficaram vazias. Não foi executado smoke de escrita contra produção.

## Limites

Esta confirmação é de schema e preservação, não de implantação da aplicação ou teste de Cloudinary/Firebase remoto. Regras RTDB e transição de escritores antigos continuam condições separadas do rollout. Não há Flyway/Liquibase: migrações continuam manuais versionadas, com `ddl-auto=none`.

Rollback não destrutivo: conservar as novas tabelas e dados e retornar a versão de aplicação anterior quando necessário; não executar DROP para desfazer publicação. Snapshot é recurso de recuperação disponível, sem ensaio de restauração executado nesta rodada.

Evidências sanitizadas detalhadas foram mantidas fora do Git no scratch privado do Hermes. Controle funcional: backend issue #17 e issue de feedback frontend #36; não fechar critérios de validação pendentes apenas por schema pronto.
