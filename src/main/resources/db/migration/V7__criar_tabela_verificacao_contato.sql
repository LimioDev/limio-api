-- Token de uso único por canal/finalidade (TICKET-0033: recuperação de senha;
-- confirmação de e-mail entra quando a UC05 for implementada). Compartilhada
-- com o Cadastro (UC01). Só o hash do token é gravado, nunca o valor em texto
-- puro (mesma lógica do refresh token da sessão).
CREATE TABLE verificacao_contato (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id    UUID         NOT NULL,
    canal         VARCHAR(20)  NOT NULL,
    finalidade    VARCHAR(30)  NOT NULL,
    token_hash    VARCHAR(64)  NOT NULL,
    expira_em     TIMESTAMPTZ  NOT NULL,
    consumido_em  TIMESTAMPTZ,
    criado_em     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    atualizado_em TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_verificacao_contato_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id),
    CONSTRAINT uk_verificacao_contato_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_verificacao_contato_usuario_id ON verificacao_contato (usuario_id);
-- Base do rate limit de solicitação (3/hora por conta e finalidade).
CREATE INDEX idx_verificacao_contato_usuario_finalidade_criado_em
    ON verificacao_contato (usuario_id, finalidade, criado_em);
