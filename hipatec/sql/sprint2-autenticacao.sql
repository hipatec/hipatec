-- SQL Server: executar no banco de desenvolvimento com backup, antes de iniciar a nova API.
-- Não executa aprovações nem remove contas. Duplicidades devem ser resolvidas pela equipe.
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF EXISTS (SELECT email FROM estudante GROUP BY email HAVING COUNT(*) > 1)
    THROW 50001, 'Existem emails duplicados em estudante. Revisar antes da migracao.', 1;
IF EXISTS (SELECT email FROM mentora GROUP BY email HAVING COUNT(*) > 1)
    THROW 50002, 'Existem emails duplicados em mentora. Revisar antes da migracao.', 1;
IF COL_LENGTH('mentora', 'aprovada') IS NULL
    ALTER TABLE mentora ADD aprovada bit NOT NULL CONSTRAINT DF_mentora_aprovada DEFAULT 0 WITH VALUES;
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UX_estudante_email' AND object_id = OBJECT_ID('estudante'))
    CREATE UNIQUE INDEX UX_estudante_email ON estudante(email);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UX_mentora_email' AND object_id = OBJECT_ID('mentora'))
    CREATE UNIQUE INDEX UX_mentora_email ON mentora(email);
COMMIT;
