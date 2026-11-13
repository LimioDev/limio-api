-- Evidência de check-out (ETI-90): fotos que o Prestador envia ao terminar o serviço, ligadas ao anúncio.
--
-- A V20 só deixava anuncio_id preenchido em FOTO_ANUNCIO. A evidência de check-out também pertence a um
-- anúncio (coluna crua, sem FK: anuncio é de outro módulo, ADR-0001 §1.6), então a regra passa a ser:
-- anuncio_id é obrigatório nos dois tipos ligados a anúncio e proibido nos demais (FOTO_PERFIL).
ALTER TABLE midia DROP CONSTRAINT ck_midia_anuncio_so_em_foto_de_anuncio;
ALTER TABLE midia ADD CONSTRAINT ck_midia_anuncio_so_em_midia_de_anuncio
    CHECK ((tipo IN ('FOTO_ANUNCIO', 'EVIDENCIA_CHECKOUT')) = (anuncio_id IS NOT NULL));

-- Consulta das evidências ativas de um anúncio (validação do Empregador). O índice da V20
-- (idx_midia_fotos_anuncio_ativas) continua cobrindo só FOTO_ANUNCIO.
CREATE INDEX idx_midia_evidencias_checkout_ativas
    ON midia (anuncio_id)
    WHERE tipo = 'EVIDENCIA_CHECKOUT' AND expurgo_em IS NULL;
