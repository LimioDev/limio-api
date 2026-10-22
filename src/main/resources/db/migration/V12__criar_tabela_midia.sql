-- Mídia enviada por usuário (TICKET-0056: foto de perfil). Nasce no módulo perfil, único
-- usuário hoje; quando anúncio/chat precisarem, a tabela já é genérica (tipo) e a entity sobe
-- pra comum/ (ADR-0001: só sobe com 2+ módulos usando). usuario_id é coluna crua, sem FK pra
-- usuario: perfil não referencia o módulo auth (ADR-0001 §1.6).
--
-- url_s3 guarda a CHAVE do objeto no bucket, não uma URL pública: o bucket é privado e o
-- endereço entregue ao app é um link temporário assinado, gerado a cada consulta.
-- expurgo_em preenchido = mídia fora de uso (trocada) e objeto apagado do storage.
CREATE TABLE midia (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id    UUID         NOT NULL,
    tipo          VARCHAR(30)  NOT NULL,
    url_s3        VARCHAR(500) NOT NULL,
    mime          VARCHAR(50)  NOT NULL,
    largura       INTEGER      NOT NULL,
    altura        INTEGER      NOT NULL,
    bytes         BIGINT       NOT NULL,
    exif_removido BOOLEAN      NOT NULL,
    expurgo_em    TIMESTAMPTZ,
    criado_em     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    atualizado_em TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_midia_url_s3 UNIQUE (url_s3),
    CONSTRAINT ck_midia_dimensoes CHECK (largura > 0 AND altura > 0 AND bytes > 0)
);

-- Foto de perfil é uma por usuário (vale pros dois papéis): no máximo uma ativa por vez.
CREATE UNIQUE INDEX uk_midia_foto_perfil_ativa
    ON midia (usuario_id)
    WHERE tipo = 'FOTO_PERFIL' AND expurgo_em IS NULL;
