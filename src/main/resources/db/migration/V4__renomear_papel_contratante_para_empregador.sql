-- Nome correto de produto é "empregador", não "contratante" (TICKET-0044).
UPDATE usuario SET papel_ativo = 'EMPREGADOR' WHERE papel_ativo = 'CONTRATANTE';

ALTER TABLE usuario DROP CONSTRAINT usuario_papel_ativo_check;
ALTER TABLE usuario ADD CONSTRAINT usuario_papel_ativo_check
    CHECK (papel_ativo IN ('EMPREGADOR', 'PRESTADOR', 'ADMIN'));
