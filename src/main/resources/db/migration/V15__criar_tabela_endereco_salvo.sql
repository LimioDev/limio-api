-- Endereços salvos do Empregador (TICKET-0050). FK pra perfil_empregador é permitida: é o mesmo
-- módulo (a proibição do ADR-0001 §1.6 é entre módulos). Soft delete por removido_em: a linha
-- permanece (anúncio Combinado guarda snapshot, mas o histórico fica no banco). Só um padrão
-- ativo por Empregador, garantido pelo índice único parcial — a aplicação serializa as escritas
-- com lock no perfil, mas o banco é a verdade final. Latitude/longitude vêm do cliente (NOT NULL).
CREATE TABLE endereco_salvo (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    perfil_empregador_id UUID             NOT NULL,
    apelido              VARCHAR(60)      NOT NULL,
    cep                  VARCHAR(8)       NOT NULL,
    rua                  VARCHAR(255)     NOT NULL,
    numero               VARCHAR(20)      NOT NULL,
    complemento          VARCHAR(100),
    bairro_regiao        VARCHAR(100)     NOT NULL,
    cidade               VARCHAR(100)     NOT NULL,
    uf                   VARCHAR(2)       NOT NULL,
    latitude             DOUBLE PRECISION NOT NULL,
    longitude            DOUBLE PRECISION NOT NULL,
    is_padrao            BOOLEAN          NOT NULL DEFAULT false,
    removido_em          TIMESTAMPTZ,
    criado_em            TIMESTAMPTZ      NOT NULL DEFAULT now(),
    atualizado_em        TIMESTAMPTZ      NOT NULL DEFAULT now(),
    CONSTRAINT fk_endereco_salvo_perfil_empregador FOREIGN KEY (perfil_empregador_id) REFERENCES perfil_empregador (id),
    CONSTRAINT ck_endereco_salvo_cep CHECK (cep ~ '^[0-9]{8}$'),
    CONSTRAINT ck_endereco_salvo_uf CHECK (uf IN (
        'AC', 'AL', 'AP', 'AM', 'BA', 'CE', 'DF', 'ES', 'GO', 'MA', 'MT', 'MS', 'MG', 'PA',
        'PB', 'PR', 'PE', 'PI', 'RJ', 'RN', 'RS', 'RO', 'RR', 'SC', 'SP', 'SE', 'TO')),
    CONSTRAINT ck_endereco_salvo_latitude CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_endereco_salvo_longitude CHECK (longitude BETWEEN -180 AND 180)
);

CREATE UNIQUE INDEX uk_endereco_salvo_padrao
    ON endereco_salvo (perfil_empregador_id)
    WHERE is_padrao AND removido_em IS NULL;

CREATE INDEX idx_endereco_salvo_perfil
    ON endereco_salvo (perfil_empregador_id)
    WHERE removido_em IS NULL;
