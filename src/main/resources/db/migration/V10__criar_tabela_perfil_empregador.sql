-- Perfil de exibição do Empregador (TICKET-0048). usuario_id é coluna crua, sem FK pra
-- usuario: perfil é outro módulo e não referencia o de auth (ADR-0001 §1.6). Média e
-- quantidade de avaliações ficam aqui; media_avaliacoes é NULL enquanto não há nenhuma
-- avaliação (RB11 — nunca 0,0). Sem coluna de foto até existir Midia.
CREATE TABLE perfil_empregador (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id       UUID         NOT NULL,
    nome_exibicao    VARCHAR(80)  NOT NULL,
    media_avaliacoes NUMERIC(2,1),
    qtd_avaliacoes   INTEGER      NOT NULL DEFAULT 0,
    criado_em        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    atualizado_em    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_perfil_empregador_usuario_id UNIQUE (usuario_id),
    CONSTRAINT ck_perfil_empregador_media CHECK (media_avaliacoes IS NULL OR media_avaliacoes BETWEEN 1 AND 5),
    CONSTRAINT ck_perfil_empregador_qtd CHECK (qtd_avaliacoes >= 0)
);
