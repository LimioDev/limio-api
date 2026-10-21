-- TICKET-0034 (ETI-36): encerrar conta anonimiza cpf/data_nascimento/cidade_uf
-- (apaga, não só mascara) — colunas eram NOT NULL desde a V1, não suportavam isso.
-- uk_usuario_cpf continua valendo: Postgres permite múltiplos NULL numa UNIQUE.
ALTER TABLE usuario
    ALTER COLUMN cpf DROP NOT NULL,
    ALTER COLUMN data_nascimento DROP NOT NULL,
    ALTER COLUMN cidade_uf DROP NOT NULL;

-- Trilha de auditoria (retenção de 5 anos, rotina de limpeza apaga o resto).
-- entidade_id não é, por si só, identificação da pessoa depois que a entidade
-- referenciada (ex.: Usuario) foi anonimizada — por isso não é considerado dado
-- pessoal pra fins de "sem identificar o usuário quando possível".
CREATE TABLE log_auditoria (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entidade       VARCHAR(100) NOT NULL,
    entidade_id    UUID,
    tipo_acao      VARCHAR(50)  NOT NULL,
    detalhes_json  TEXT,
    origem         VARCHAR(100),
    ip             VARCHAR(45),
    criado_em      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    atualizado_em  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_log_auditoria_entidade_entidade_id ON log_auditoria (entidade, entidade_id);
CREATE INDEX idx_log_auditoria_criado_em ON log_auditoria (criado_em);
