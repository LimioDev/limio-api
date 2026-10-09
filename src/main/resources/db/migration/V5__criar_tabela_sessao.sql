-- Uma linha por login em um aparelho (TICKET-0032). Colunas do DER + expira_em,
-- que o DER não tem: validade do refresh token (renovação empurra pra frente).
-- O refresh token nunca é guardado, só o SHA-256 em hex.
CREATE TABLE sessao (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id         UUID         NOT NULL,
    refresh_token_hash VARCHAR(64)  NOT NULL,
    dispositivo        VARCHAR(255),
    ip                 VARCHAR(45),
    ultimo_uso_em      TIMESTAMPTZ  NOT NULL,
    expira_em          TIMESTAMPTZ  NOT NULL,
    revogada_em        TIMESTAMPTZ,
    criado_em          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    atualizado_em      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_sessao_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id),
    CONSTRAINT uk_sessao_refresh_token_hash UNIQUE (refresh_token_hash)
);

CREATE INDEX idx_sessao_usuario_id ON sessao (usuario_id);
