-- Retrato da cidade do anúncio (ETI-79): o detalhe do mural mostra "Bairro · Cidade/UF" e não pode ler o
-- endereço de uma linha (só o dono e quem for escolhido veem). Coluna nova, como a uf_snapshot (V21).
ALTER TABLE anuncio ADD COLUMN cidade_snapshot VARCHAR(50);

-- Anúncio criado antes desta coluna: a cidade é o trecho entre a última vírgula e " - UF" do endereço de uma
-- linha ("..., bairro, cidade - UF"). Sem casar, fica NULL e a tela mostra só a UF.
UPDATE anuncio
SET cidade_snapshot = btrim(substring(endereco_completo_snapshot FROM ', ([^,]+) - [A-Z]{2}$'))
WHERE cidade_snapshot IS NULL AND endereco_completo_snapshot IS NOT NULL;
