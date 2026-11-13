# Revisão do banco para os incrementos locais

## Escopo e evidência

Em 2026-11-13, Hermes inspecionou pelo console Neon o projeto Hipatec, branch identificada como produção, exclusivamente por SELECT de metadados e agregados. Nenhum DDL, DML, export de registros, credencial ou migração foi executado. Resultado observado: PostgreSQL 18.6. A inspeção é um checkpoint, não uma garantia de ausência de drift posterior.

## Estado observado

Existem dez tabelas públicas: `estudante`, `mentora`, `perfil`, `mentoria`, `onboarding_respostas`, `recuperacao_senha`, `seguimento`, `inscricao_mentoria`, `evento_mentoria` e `vaga`.

A tabela **`evento` não existe**. `evento_mentoria` é um domínio distinto, com vínculo obrigatório a mentoria; não substitui eventos pessoais/comunitários.

As quatro tabelas previstas em `20261110_add_missing_product_entities.sql` já existem, com índices, FKs e constraints de domínio visíveis no catálogo. Não reaplicar esse arquivo indiscriminadamente: `IF NOT EXISTS` não reconcilia divergência de tabela existente.

As PKs de estudante, mentora, perfil e mentoria são identity. Estão presentes FKs de perfil, mentoria, onboarding e relações. Há UNIQUE de seguimento origem/destino e CHECK contra autosseguimento.

Os agregados consultados não apontaram colisão de e-mail entre papéis, duplicidade case-insensitive dentro de cada papel, perfil com vínculo inválido, órfãos nas relações verificadas ou divergência entre contadores legados de perfil e seguimento. Isso não substitui constraints contra futuras corridas.

Todas as três contas observadas usam o formato `{bcrypt}$2…` esperado pelo login atual. Não foram lidos/exibidos valores de senha/hash. As rotinas `login_estudante` e `login_mentora` não existem; a compatibilidade com futuras contas legadas não está coberta. Não converter credenciais nem criar rotinas antigas automaticamente.

## Mudança necessária para eventos

Aplicar, após autorização específica e recuperação qualificada, os bytes revisados de:

`database/migrations/20261113_create_evento.sql`

O script é aditivo/transacional: tabela `evento`, identity, FK para `perfil(id)`, data civil DATE, horário TIME opcional, fuso `America/Sao_Paulo`, limites/checks, UNIQUE de proprietário+chave idempotente e índices de proprietário/data e visibilidade/data. Não migra dados de `evento_mentoria` nem faz backfill de autoria.

A execução foi comprovada em H2 arquivo local com preservação e nova sessão/reinício. **Execução PostgreSQL ainda não realizada.** Compatibilidade estática não equivale a teste no PostgreSQL.

## Outros incrementos

- Contadores de seguidores/seguindo consultam relações existentes; não exigem coluna nova.
- Configurações, e-mail e senha usam colunas existentes. E-mail só é devolvido ao proprietário exato; unicidade entre duas tabelas exige manter a proteção do código e revisar os demais caminhos de cadastro.
- Curtidas, comentários e publicações usam Firebase, não novas tabelas PostgreSQL. Testes locais com fixture de posts não comprovam persistência no provedor real.
- Republicações ainda têm limitações locais explicitamente documentadas; criação de tabelas SQL não resolve esse contrato automaticamente.
- Curso/mentoria com os novos formulários ainda têm integração funcional pendente; não criar schema especulativo nesta revisão.

## Gates de implantação

1. Confirmar novamente projeto/branch/schema-alvo e drift, sem dados pessoais.
2. Qualificar backup/restauração ou mecanismo de recuperação adequado antes do primeiro DDL.
3. Testar a migração em PostgreSQL isolado compatível; verificar constraints e preservação de tabelas anteriores.
4. Aplicar a migração de eventos somente em janela e alvo autorizados, antes de liberar a nova API; verificar tabela/constraints/índices por catálogo.
5. Executar smoke autorizado de API e interface. Não interpretar push/commit como autorização de DDL.

`spring.jpa.hibernate.ddl-auto=none` permanece. Não existe executor Flyway/Liquibase: SQL versionado não é aplicado automaticamente. O script `hipatec/sql/sprint2-autenticacao.sql` é histórico de SQL Server e não deve ser executado no Neon.

## Rastreabilidade

Eventos: https://github.com/hipatec/hipatec/issues/17 . Detalhe do contrato e laboratório: [EVENTOS-LOCAIS.md](EVENTOS-LOCAIS.md). As issues permanecem abertas onde houver homologação pendente.
