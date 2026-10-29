-- Criar e publicar anúncio, com fotos (ETI-59). A V13 não é alterada; o título vem na V18 (PR #17).
--
-- categoria_id e endereco_salvo_id são colunas cruas, sem FOREIGN KEY: categoria e endereco_salvo são
-- tabelas do módulo perfil (ADR-0001 §1.6). endereco_salvo_id é nulo quando o anúncio usa endereço avulso;
-- o endereço que vale é sempre o retrato (*_snapshot), nunca o endereço salvo atual.
ALTER TABLE anuncio ADD COLUMN categoria_id UUID;
ALTER TABLE anuncio ADD COLUMN endereco_salvo_id UUID;

-- Ainda não havia rota de criar anúncio: linha antiga só existe se foi inserida à mão. Vai pra "Outros"
-- (categoria protegida, nunca inativa) pra coluna poder ser NOT NULL.
UPDATE anuncio SET categoria_id = (SELECT id FROM categoria WHERE protegida) WHERE categoria_id IS NULL;
ALTER TABLE anuncio ALTER COLUMN categoria_id SET NOT NULL;

-- Foto de anúncio: mídia ligada ao anúncio (coluna crua, anuncio é de outro módulo que comum/midia).
ALTER TABLE midia ADD COLUMN anuncio_id UUID;
ALTER TABLE midia ADD CONSTRAINT ck_midia_anuncio_so_em_foto_de_anuncio
    CHECK ((tipo = 'FOTO_ANUNCIO') = (anuncio_id IS NOT NULL));

CREATE INDEX idx_midia_fotos_anuncio_ativas
    ON midia (anuncio_id)
    WHERE tipo = 'FOTO_ANUNCIO' AND expurgo_em IS NULL;
