-- Preferência de canal de notificação do usuário (ETI-34): no máximo um
-- registro por usuário. usuario_id é coluna crua, sem relação com a tabela
-- usuario (ADR-0001 §1.6 — módulo notificacao não se acopla a auth).
CREATE TABLE preferencia_notificacao (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id    UUID        NOT NULL,
    push          BOOLEAN     NOT NULL,
    email         BOOLEAN     NOT NULL,
    sms           BOOLEAN     NOT NULL,
    whatsapp      BOOLEAN     NOT NULL,
    criado_em     TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_preferencia_notificacao_usuario_id UNIQUE (usuario_id)
);
