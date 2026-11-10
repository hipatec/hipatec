-- Additive schema for the current MVP. No existing table or row is changed.
-- Apply only after reviewing the production schema snapshot.

CREATE TABLE IF NOT EXISTS inscricao_mentoria (
    id BIGSERIAL PRIMARY KEY,
    estudante_id INTEGER NOT NULL,
    mentoria_id INTEGER NOT NULL,
    progresso INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL DEFAULT 'ATIVA',
    data_inscricao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_conclusao TIMESTAMP NULL,
    CONSTRAINT fk_inscricao_mentoria_estudante
        FOREIGN KEY (estudante_id) REFERENCES estudante(id),
    CONSTRAINT fk_inscricao_mentoria_mentoria
        FOREIGN KEY (mentoria_id) REFERENCES mentoria(id),
    CONSTRAINT uk_inscricao_mentoria_estudante_mentoria
        UNIQUE (estudante_id, mentoria_id),
    CONSTRAINT ck_inscricao_mentoria_progresso
        CHECK (progresso BETWEEN 0 AND 100),
    CONSTRAINT ck_inscricao_mentoria_status
        CHECK (status IN ('ATIVA', 'CONCLUIDA', 'CANCELADA'))
);

CREATE INDEX IF NOT EXISTS ix_inscricao_mentoria_estudante
    ON inscricao_mentoria (estudante_id);
CREATE INDEX IF NOT EXISTS ix_inscricao_mentoria_mentoria
    ON inscricao_mentoria (mentoria_id);

CREATE TABLE IF NOT EXISTS evento_mentoria (
    id BIGSERIAL PRIMARY KEY,
    mentoria_id INTEGER NOT NULL,
    titulo VARCHAR(255) NOT NULL,
    descricao TEXT NULL,
    tipo VARCHAR(30) NOT NULL DEFAULT 'MENTORIA',
    inicio TIMESTAMP NOT NULL,
    fim TIMESTAMP NULL,
    localizacao VARCHAR(255) NULL,
    link VARCHAR(1000) NULL,
    CONSTRAINT fk_evento_mentoria_mentoria
        FOREIGN KEY (mentoria_id) REFERENCES mentoria(id),
    CONSTRAINT ck_evento_mentoria_periodo
        CHECK (fim IS NULL OR fim >= inicio)
);

CREATE INDEX IF NOT EXISTS ix_evento_mentoria_inicio
    ON evento_mentoria (inicio);
CREATE INDEX IF NOT EXISTS ix_evento_mentoria_mentoria
    ON evento_mentoria (mentoria_id, inicio);

CREATE TABLE IF NOT EXISTS vaga (
    id BIGSERIAL PRIMARY KEY,
    empresa VARCHAR(255) NOT NULL,
    titulo VARCHAR(255) NOT NULL,
    descricao TEXT NOT NULL,
    modalidade VARCHAR(40) NULL,
    local VARCHAR(255) NULL,
    pais VARCHAR(100) NULL,
    estado VARCHAR(100) NULL,
    cidade VARCHAR(100) NULL,
    area VARCHAR(100) NULL,
    formacao VARCHAR(100) NULL,
    senioridade VARCHAR(50) NULL,
    idiomas VARCHAR(255) NULL,
    contrato VARCHAR(50) NULL,
    publicada_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    url VARCHAR(1000) NULL,
    publicada_por_perfil_id INTEGER NULL,
    ativa BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_vaga_publicada_por_perfil
        FOREIGN KEY (publicada_por_perfil_id) REFERENCES perfil(id)
);

CREATE INDEX IF NOT EXISTS ix_vaga_ativa_publicada_em
    ON vaga (ativa, publicada_em DESC);

CREATE TABLE IF NOT EXISTS seguimento (
    id BIGSERIAL PRIMARY KEY,
    seguidor_perfil_id INTEGER NOT NULL,
    seguido_perfil_id INTEGER NOT NULL,
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_seguimento_seguidor
        FOREIGN KEY (seguidor_perfil_id) REFERENCES perfil(id),
    CONSTRAINT fk_seguimento_seguido
        FOREIGN KEY (seguido_perfil_id) REFERENCES perfil(id),
    CONSTRAINT uk_seguimento_origem_destino
        UNIQUE (seguidor_perfil_id, seguido_perfil_id),
    CONSTRAINT ck_seguimento_nao_autoseguir
        CHECK (seguidor_perfil_id <> seguido_perfil_id)
);

CREATE INDEX IF NOT EXISTS ix_seguimento_seguidor
    ON seguimento (seguidor_perfil_id);
CREATE INDEX IF NOT EXISTS ix_seguimento_seguido
    ON seguimento (seguido_perfil_id);
