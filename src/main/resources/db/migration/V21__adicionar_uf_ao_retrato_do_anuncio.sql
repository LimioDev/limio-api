-- Retrato da UF do anúncio (ETI-59): a tela 16 mostra "Bairro · UF", e o mural também só pode mostrar a
-- região. Coluna nova em vez de mudar a V20, que já foi aplicada.
ALTER TABLE anuncio ADD COLUMN uf_snapshot VARCHAR(2);

-- Anúncio criado antes desta coluna: a UF é o fim do endereço de uma linha ("..., cidade - UF").
UPDATE anuncio
SET uf_snapshot = substring(endereco_completo_snapshot FROM ' - ([A-Z]{2})$')
WHERE uf_snapshot IS NULL AND endereco_completo_snapshot IS NOT NULL;
