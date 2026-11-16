-- Notificações do usuário (ETI-110): a lista da tela 43 e o total de não lidas do sino (tela 13). Quem grava é o
-- envio de avisos (ETI-112). usuario_id e anuncio_id são colunas cruas, sem FK (ADR-0001 §1.6: notificacao não se
-- acopla a auth nem a anuncio). tipo sem CHECK: valor novo do enum TipoNotificacao não exige migration.
-- V36 porque a #38 (ETI-94, aberta) usa V34 e V35.
CREATE TABLE notificacao (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id    UUID         NOT NULL,
    anuncio_id    UUID,
    tipo          VARCHAR(40)  NOT NULL,
    titulo        VARCHAR(120) NOT NULL,
    corpo         VARCHAR(500) NOT NULL,
    enviada_em    TIMESTAMPTZ  NOT NULL,
    lida_em       TIMESTAMPTZ,
    criado_em     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    atualizado_em TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Lista do usuário, da mais nova pra mais antiga.
CREATE INDEX idx_notificacao_usuario_enviada ON notificacao (usuario_id, enviada_em DESC, id DESC);

-- Total de não lidas (sino) e "marcar todas como lidas": só as linhas ainda não lidas.
CREATE INDEX idx_notificacao_usuario_nao_lida ON notificacao (usuario_id) WHERE lida_em IS NULL;
