-- Título do anúncio (ETI-59): o Figma da tela de criar anúncio tem campo de
-- título, que faltava no contrato. Linhas antigas recebem o começo da
-- descrição pra poder virar NOT NULL.
ALTER TABLE anuncio ADD COLUMN titulo VARCHAR(100);

UPDATE anuncio SET titulo = LEFT(descricao, 100) WHERE titulo IS NULL;

ALTER TABLE anuncio ALTER COLUMN titulo SET NOT NULL;
