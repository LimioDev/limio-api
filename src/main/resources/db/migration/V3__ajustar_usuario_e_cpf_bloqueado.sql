-- Alinha usuario/cpf_bloqueado ao DER atualizado (TICKET-0044).

ALTER TABLE usuario
    ADD COLUMN anonimizado_em TIMESTAMPTZ;

-- cpf_bloqueado passa a guardar HMAC-SHA256 do CPF (não mais texto puro) e
-- ganha bloqueado_em/liberado_em explícitos (liberado_em = liberação
-- antecipada do cooldown; nulo = cooldown padrão ainda vale).
ALTER TABLE cpf_bloqueado
    DROP COLUMN cpf,
    ADD COLUMN cpf_hmac VARCHAR(64) NOT NULL,
    ADD COLUMN bloqueado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN liberado_em TIMESTAMPTZ;

ALTER TABLE cpf_bloqueado ALTER COLUMN bloqueado_em DROP DEFAULT;

DROP INDEX IF EXISTS idx_cpf_bloqueado_cpf;
CREATE INDEX idx_cpf_bloqueado_cpf_hmac ON cpf_bloqueado (cpf_hmac);
