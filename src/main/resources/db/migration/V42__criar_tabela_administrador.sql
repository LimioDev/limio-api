-- Administrador do painel web (ETI-117, módulo administracao). Ator próprio, nunca um usuario (decisão do tech lead,
-- 2026-10-26; o papel ADMIN de usuario saiu na V15). O token de acesso do administrador traz o id desta tabela.
--
-- Ainda não há rota de login nem cadastro pela API: isso é do card da entrada do admin (US60). Até lá, o
-- administrador entra por INSERT manual, com senha_hash em BCrypt (o mesmo PasswordEncoder da API).
CREATE TABLE administrador (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nome           VARCHAR(150) NOT NULL,
    email          VARCHAR(255) NOT NULL,
    senha_hash     VARCHAR(255) NOT NULL,
    criado_em      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    atualizado_em  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uk_administrador_email ON administrador (lower(email));
