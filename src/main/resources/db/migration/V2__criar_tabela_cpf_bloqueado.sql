-- Registro de CPF cuja conta foi excluída, usado só pra bloquear
-- recadastro dentro da janela de cooldown de 12 meses (evita burlar cotas).
CREATE TABLE cpf_bloqueado (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cpf           VARCHAR(11) NOT NULL,
    criado_em     TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_cpf_bloqueado_cpf ON cpf_bloqueado (cpf);
