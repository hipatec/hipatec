-- Preserva evidência obtida pelo servidor para análise mesmo se a autora excluir o conteúdo.
BEGIN;
ALTER TABLE denuncia ADD COLUMN evidencia VARCHAR(5000);
ALTER TABLE denuncia ADD COLUMN autor_perfil_id INTEGER;
COMMIT;
-- Denúncias anteriores continuam válidas e não recebem evidência inventada.
