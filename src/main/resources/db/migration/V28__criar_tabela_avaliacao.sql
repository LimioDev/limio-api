-- Avaliação entre as partes de um serviço concluído (ETI-107, módulo moderacao). anuncio_id, avaliador_id e
-- avaliado_id são colunas cruas, sem FOREIGN KEY: moderacao não referencia tabela de outro módulo (ADR-0001 §1.6).
--
-- Fora do DER: papel_avaliado, o papel que o avaliado teve no serviço. A média fica por papel (perfil_empregador ou
-- perfil_prestador), e sem esta coluna a soma de quem atua nos dois papéis precisaria consultar o módulo anuncio.
-- comentario é opcional e nunca vem sem nota (regra do usecase).
CREATE TABLE avaliacao (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    anuncio_id      UUID         NOT NULL,
    avaliador_id    UUID         NOT NULL,
    avaliado_id     UUID         NOT NULL,
    papel_avaliado  VARCHAR(20)  NOT NULL,
    nota            INTEGER      NOT NULL,
    comentario      VARCHAR(400),
    publicada_em    TIMESTAMPTZ  NOT NULL,
    criado_em       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    atualizado_em   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- Uma avaliação por pessoa por serviço: segura o clique duplo que passar pela checagem do usecase.
    CONSTRAINT uk_avaliacao_anuncio_avaliador UNIQUE (anuncio_id, avaliador_id),
    CONSTRAINT ck_avaliacao_nota CHECK (nota BETWEEN 1 AND 5),
    CONSTRAINT ck_avaliacao_partes CHECK (avaliador_id <> avaliado_id)
);

-- Recálculo da média de quem foi avaliado, por papel.
CREATE INDEX idx_avaliacao_avaliado_papel ON avaliacao (avaliado_id, papel_avaliado);
