-- Causa do encerramento do anúncio (ETI-71 — histórico com filtros).
--
-- Não existe status próprio para "cancelado" nem "expirado": os dois são ENCERRADO e só se distinguiam pelo texto livre
-- de motivo_encerramento. anuncio.causa_encerramento guarda a classificação: CANCELAMENTO ou EXPIRACAO. Nulo em todo
-- anúncio que não está ENCERRADO.
ALTER TABLE anuncio ADD COLUMN causa_encerramento VARCHAR(20);

-- Backfill dos anúncios já encerrados, a partir dos motivos que o código gravava até aqui. Só "Expirado sem
-- candidatos" (ETI-66) é expiração; os demais (cancelamento antes da escolha, serviço combinado cancelado, saída de
-- conta) são cancelamento.
UPDATE anuncio
   SET causa_encerramento = CASE
           WHEN motivo_encerramento = 'Expirado sem candidatos' THEN 'EXPIRACAO'
           ELSE 'CANCELAMENTO'
       END
 WHERE status = 'ENCERRADO';

ALTER TABLE anuncio
    ADD CONSTRAINT ck_anuncio_causa_encerramento
        CHECK (causa_encerramento IS NULL OR causa_encerramento IN ('CANCELAMENTO', 'EXPIRACAO'));

-- Só ENCERRADO tem causa, e todo ENCERRADO tem causa. Sem isso, um ENCERRADO sem causa entraria na contagem do
-- histórico e sumiria da página.
ALTER TABLE anuncio
    ADD CONSTRAINT ck_anuncio_causa_so_no_encerrado
        CHECK ((status = 'ENCERRADO') = (causa_encerramento IS NOT NULL));
