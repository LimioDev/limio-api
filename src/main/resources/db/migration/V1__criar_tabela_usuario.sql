CREATE TABLE usuario (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nome_completo          VARCHAR(255)      NOT NULL,
    email                  VARCHAR(255)      NOT NULL,
    telefone               VARCHAR(11)       NOT NULL,
    senha_hash             VARCHAR(255)      NOT NULL,
    cpf                    VARCHAR(11)       NOT NULL,
    data_nascimento        DATE              NOT NULL,
    cidade_uf              VARCHAR(255)      NOT NULL,
    papel_ativo            VARCHAR(20)       NOT NULL CHECK (papel_ativo IN ('CONTRATANTE', 'PRESTADOR', 'ADMIN')),
    status_conta           VARCHAR(30)       NOT NULL CHECK (status_conta IN ('ATIVA', 'PENDENTE_VERIFICACAO', 'BLOQUEADA')),
    telefone_confirmado_em TIMESTAMPTZ,
    email_confirmado_em    TIMESTAMPTZ,
    criado_em              TIMESTAMPTZ       NOT NULL DEFAULT now(),
    atualizado_em          TIMESTAMPTZ       NOT NULL DEFAULT now(),
    CONSTRAINT uk_usuario_email UNIQUE (email),
    CONSTRAINT uk_usuario_cpf UNIQUE (cpf)
);

CREATE INDEX idx_usuario_email ON usuario (email);
CREATE INDEX idx_usuario_cpf ON usuario (cpf);
