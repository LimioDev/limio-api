-- Primeira implementação do módulo anuncio (TICKET-0049). Colunas de FK pra
-- usuario_id são cruas (sem FOREIGN KEY), igual o resto do projeto segue
-- desde o ADR-0001 §1.6 — módulo não referencia tabela de outro módulo.
CREATE TABLE anuncio (
    id                           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    contratante_id               UUID         NOT NULL,
    anuncio_origem_id            UUID,
    descricao                    TEXT         NOT NULL,
    data_hora_pretendida         TIMESTAMPTZ  NOT NULL,
    status                       VARCHAR(30)  NOT NULL,
    bairro_regiao_snapshot       VARCHAR(255),
    endereco_completo_snapshot   VARCHAR(255),
    latitude_snapshot            DOUBLE PRECISION,
    longitude_snapshot           DOUBLE PRECISION,
    tamanho_local                VARCHAR(100),
    numero_pessoas               INTEGER,
    observacoes                  TEXT,
    prazo_validacao_horas        INTEGER,
    iniciado_em                  TIMESTAMPTZ,
    checkout_em                  TIMESTAMPTZ,
    validado_em                  TIMESTAMPTZ,
    encerrado_em                 TIMESTAMPTZ,
    criado_em                    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    atualizado_em                TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_anuncio_contratante_id ON anuncio (contratante_id);
CREATE INDEX idx_anuncio_contratante_status ON anuncio (contratante_id, status);

CREATE TABLE aplicacao (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    anuncio_id           UUID         NOT NULL,
    prestador_id         UUID         NOT NULL,
    status               VARCHAR(30)  NOT NULL,
    data_aplicacao       TIMESTAMPTZ  NOT NULL,
    escolhida_em         TIMESTAMPTZ,
    recusada_em          TIMESTAMPTZ,
    encerrada_em         TIMESTAMPTZ,
    motivo_encerramento  TEXT,
    criado_em            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    atualizado_em        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_aplicacao_prestador_id ON aplicacao (prestador_id);
CREATE INDEX idx_aplicacao_prestador_status ON aplicacao (prestador_id, status);
CREATE INDEX idx_aplicacao_anuncio_status ON aplicacao (anuncio_id, status);

-- No máximo uma candidatura ESCOLHIDA por anúncio — o código assume isso (ver
-- AplicacaoService/AplicacaoRepository), mas nada impedia duas linhas antes
-- deste índice.
CREATE UNIQUE INDEX idx_aplicacao_escolhida_unica ON aplicacao (anuncio_id) WHERE status = 'ESCOLHIDA';

CREATE TABLE reagendamento (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    anuncio_id      UUID         NOT NULL,
    data_anterior   TIMESTAMPTZ  NOT NULL,
    data_proposta   TIMESTAMPTZ  NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    justificativa   TEXT,
    respondido_em   TIMESTAMPTZ,
    criado_em       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    atualizado_em   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_reagendamento_anuncio_status ON reagendamento (anuncio_id, status);
