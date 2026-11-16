-- Envio dos avisos (ETI-112).
--
-- aparelho_push: token de push (Expo) do aparelho logado. Um token é de um aparelho, então é único: se outra conta
-- entrar no mesmo aparelho, o registro passa pra ela. usuario_id cru, sem FK (ADR-0001 §1.6).
CREATE TABLE aparelho_push (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id    UUID         NOT NULL,
    token         VARCHAR(255) NOT NULL,
    plataforma    VARCHAR(10)  NOT NULL,
    criado_em     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    atualizado_em TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_aparelho_push_token UNIQUE (token)
);

CREATE INDEX idx_aparelho_push_usuario ON aparelho_push (usuario_id);

-- envio_notificacao: por qual canal cada notificação saiu (ou falhou). Canal desligado não gera linha. Some junto
-- com a notificação (conta encerrada).
CREATE TABLE envio_notificacao (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    notificacao_id UUID         NOT NULL,
    canal          VARCHAR(20)  NOT NULL,
    status         VARCHAR(20)  NOT NULL,
    ocorrido_em    TIMESTAMPTZ  NOT NULL,
    detalhe        VARCHAR(300),
    criado_em      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    atualizado_em  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_envio_notificacao_notificacao FOREIGN KEY (notificacao_id)
        REFERENCES notificacao (id) ON DELETE CASCADE
);

CREATE INDEX idx_envio_notificacao_notificacao ON envio_notificacao (notificacao_id);
