-- Administrador é ator próprio (tabela administrador, módulo administracao), nunca um usuario
-- (decisão do tech lead, 2026-10-26). Nenhum fluxo da API grava ADMIN (cadastro usa EMPREGADOR e a troca
-- de papel recusava ADMIN); o UPDATE só cobre linha inserida à mão.
UPDATE usuario SET papel_ativo = 'EMPREGADOR' WHERE papel_ativo = 'ADMIN';

ALTER TABLE usuario DROP CONSTRAINT usuario_papel_ativo_check;
ALTER TABLE usuario ADD CONSTRAINT usuario_papel_ativo_check
    CHECK (papel_ativo IN ('EMPREGADOR', 'PRESTADOR'));
