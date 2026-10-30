-- Mural do Prestador (ETI-79): a consulta filtra só anúncios PUBLICADO, então os índices são parciais
-- (não carregam anúncios concluídos/encerrados, que são a maioria com o tempo).

-- Caixa envolvente (latitude/longitude) aplicada antes do cálculo de distância exata.
CREATE INDEX idx_anuncio_mural_geo
    ON anuncio (latitude_snapshot, longitude_snapshot)
    WHERE status = 'PUBLICADO';

-- Filtro por categoria + janela de data ("Quando").
CREATE INDEX idx_anuncio_mural_categoria_data
    ON anuncio (categoria_id, data_hora_pretendida)
    WHERE status = 'PUBLICADO';
