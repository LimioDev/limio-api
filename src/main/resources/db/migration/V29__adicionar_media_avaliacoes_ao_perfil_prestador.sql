-- Média e quantidade de avaliações do Prestador (ETI-107), com a mesma regra de perfil_empregador (V10):
-- media_avaliacoes é NULL enquanto não há nenhuma avaliação (RB11, nunca 0,0). Quem grava é o módulo perfil, ao
-- receber o AvaliacaoPublicadaEvent de moderacao.
ALTER TABLE perfil_prestador
    ADD COLUMN media_avaliacoes NUMERIC(2,1),
    ADD COLUMN qtd_avaliacoes   INTEGER NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_perfil_prestador_media CHECK (media_avaliacoes IS NULL OR media_avaliacoes BETWEEN 1 AND 5),
    ADD CONSTRAINT ck_perfil_prestador_qtd CHECK (qtd_avaliacoes >= 0);
