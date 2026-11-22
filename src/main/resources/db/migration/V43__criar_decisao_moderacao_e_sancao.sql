-- Análise e decisão da denúncia pelo administrador (ETI-116, módulo moderacao).
--
-- A denúncia sai da fila quando é decidida: status_resolucao vira PROCEDENTE ou IMPROCEDENTE e analisada_em guarda
-- o momento da decisão.
ALTER TABLE denuncia ADD COLUMN analisada_em TIMESTAMPTZ;
ALTER TABLE denuncia ADD CONSTRAINT ck_denuncia_status_resolucao
    CHECK (status_resolucao IN ('ABERTA', 'PROCEDENTE', 'IMPROCEDENTE'));
ALTER TABLE denuncia ADD CONSTRAINT ck_denuncia_analisada
    CHECK ((status_resolucao = 'ABERTA') = (analisada_em IS NULL));

-- Uma decisão por denúncia. administrador_id é coluna crua: administrador é do módulo administracao (ADR-0001 §1.6).
CREATE TABLE decisao_moderacao (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    denuncia_id       UUID          NOT NULL,
    veredito          VARCHAR(20)   NOT NULL,
    fundamento        VARCHAR(2000) NOT NULL,
    administrador_id  UUID          NOT NULL,
    decidida_em       TIMESTAMPTZ   NOT NULL,
    criado_em         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    atualizado_em     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uk_decisao_moderacao_denuncia UNIQUE (denuncia_id),
    CONSTRAINT fk_decisao_moderacao_denuncia FOREIGN KEY (denuncia_id) REFERENCES denuncia (id),
    CONSTRAINT ck_decisao_moderacao_veredito CHECK (veredito IN ('PROCEDENTE', 'IMPROCEDENTE'))
);

-- Sanção aplicada ao denunciado por uma decisão procedente. usuario_id e avaliacao_id são crus (usuario é de auth;
-- avaliacao é deste módulo, mas a sanção só guarda qual foi ocultada). O motivo é o fundamento da decisão.
-- inicio_em e fim_em só existem na suspensão.
CREATE TABLE sancao (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    decisao_id    UUID          NOT NULL,
    usuario_id    UUID          NOT NULL,
    tipo          VARCHAR(30)   NOT NULL,
    motivo        VARCHAR(2000) NOT NULL,
    inicio_em     TIMESTAMPTZ,
    fim_em        TIMESTAMPTZ,
    avaliacao_id  UUID,
    aplicada_em   TIMESTAMPTZ   NOT NULL,
    criado_em     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    atualizado_em TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT fk_sancao_decisao FOREIGN KEY (decisao_id) REFERENCES decisao_moderacao (id),
    CONSTRAINT ck_sancao_tipo
        CHECK (tipo IN ('ADVERTENCIA', 'OCULTAR_AVALIACAO', 'SUSPENSAO', 'ENCERRAMENTO_DE_CONTA')),
    CONSTRAINT ck_sancao_suspensao CHECK (
        (tipo = 'SUSPENSAO' AND inicio_em IS NOT NULL AND fim_em IS NOT NULL AND fim_em > inicio_em)
        OR (tipo <> 'SUSPENSAO' AND inicio_em IS NULL AND fim_em IS NULL)),
    CONSTRAINT ck_sancao_avaliacao CHECK ((tipo = 'OCULTAR_AVALIACAO') = (avaliacao_id IS NOT NULL))
);

-- Histórico de sanções do denunciado, na análise.
CREATE INDEX idx_sancao_usuario ON sancao (usuario_id, aplicada_em DESC);

-- Avaliação oculta pela moderação: some do perfil e da média, mas a linha fica.
ALTER TABLE avaliacao ADD COLUMN oculta_em  TIMESTAMPTZ;
ALTER TABLE avaliacao ADD COLUMN oculta_por UUID;
ALTER TABLE avaliacao ADD CONSTRAINT ck_avaliacao_oculta CHECK ((oculta_em IS NULL) = (oculta_por IS NULL));
