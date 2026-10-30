-- Chat 1:1 da candidatura (ETI-101). aplicacao_id, anuncio_id e os ids das partes são colunas cruas, sem FOREIGN
-- KEY: o chat não referencia tabela de outro módulo (ADR-0001 §1.6). As partes ficam gravadas no chat para checar
-- acesso sem consultar o módulo anuncio a cada mensagem.
CREATE TABLE chat (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aplicacao_id            UUID         NOT NULL,
    anuncio_id              UUID         NOT NULL,
    contratante_id          UUID         NOT NULL,
    prestador_id            UUID         NOT NULL,
    somente_leitura         BOOLEAN      NOT NULL DEFAULT FALSE,
    motivo_somente_leitura  VARCHAR(40),
    somente_leitura_em      TIMESTAMPTZ,
    criado_em               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    atualizado_em           TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Um chat por candidatura: o evento CandidaturaCriada pode chegar duas vezes, e o índice segura a corrida.
CREATE UNIQUE INDEX uk_chat_aplicacao ON chat (aplicacao_id);
CREATE INDEX idx_chat_anuncio_id ON chat (anuncio_id);

-- texto é o que o remetente escreveu; texto_exibido é o que a outra parte vê. São iguais até o mascaramento de
-- contato (US50) entrar. entregue_em e lida_em ficam nulos até a outra parte confirmar.
CREATE TABLE mensagem (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chat_id         UUID         NOT NULL REFERENCES chat (id),
    remetente_id    UUID         NOT NULL,
    texto           TEXT         NOT NULL,
    texto_exibido   TEXT         NOT NULL,
    data_envio      TIMESTAMPTZ  NOT NULL,
    entregue_em     TIMESTAMPTZ,
    lida_em         TIMESTAMPTZ,
    criado_em       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    atualizado_em   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Histórico paginado do mais novo para o mais antigo.
CREATE INDEX idx_mensagem_chat_data_envio ON mensagem (chat_id, data_envio DESC);
