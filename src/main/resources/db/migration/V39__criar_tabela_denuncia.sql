-- Denúncia de um usuário contra outro (ETI-114, módulo moderacao). denunciante_id, denunciado_id, anuncio_id,
-- candidatura_id e mensagem_id são colunas cruas, sem FOREIGN KEY: moderacao não referencia tabela de outro módulo
-- (ADR-0001 §1.6). evidencia_id aponta pra midia, que é de comum/, por isso tem FK.
--
-- O alvo é o denunciado e, conforme o caso, o contexto em que o problema aconteceu: anúncio (ou serviço), candidatura
-- (o chat é 1:1 por candidatura) ou mensagem. Denúncia de perfil só tem o denunciado. O usecase preenche os contextos
-- de cima: mensagem também grava candidatura e anúncio, e candidatura também grava o anúncio.
--
-- numero é o número da denúncia mostrado ao usuário (protocolo), sequencial e separado do id.
CREATE TABLE denuncia (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    numero            BIGINT        GENERATED ALWAYS AS IDENTITY,
    denunciante_id    UUID          NOT NULL,
    denunciado_id     UUID          NOT NULL,
    anuncio_id        UUID,
    candidatura_id    UUID,
    mensagem_id       UUID,
    motivo            VARCHAR(40)   NOT NULL,
    descricao         VARCHAR(1000) NOT NULL,
    evidencia_id      UUID,
    status_resolucao  VARCHAR(20)   NOT NULL,
    prazo_analise     TIMESTAMPTZ   NOT NULL,
    criado_em         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    atualizado_em     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uk_denuncia_numero UNIQUE (numero),
    CONSTRAINT uk_denuncia_evidencia UNIQUE (evidencia_id),
    CONSTRAINT fk_denuncia_evidencia FOREIGN KEY (evidencia_id) REFERENCES midia (id),
    CONSTRAINT ck_denuncia_partes CHECK (denunciante_id <> denunciado_id)
);

-- Fila do admin (US58): denúncias por status, as de prazo mais curto primeiro.
CREATE INDEX idx_denuncia_status_prazo ON denuncia (status_resolucao, prazo_analise);
-- Denúncias recebidas por um usuário (análise do admin).
CREATE INDEX idx_denuncia_denunciado ON denuncia (denunciado_id);
