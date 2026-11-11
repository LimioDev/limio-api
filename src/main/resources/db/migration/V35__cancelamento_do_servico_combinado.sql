-- Cancelamento do serviço combinado pelo Empregador (ETI-94).
--
-- anuncio.tipo_cancelamento: NORMAL ou TARDIO (carência pós-reagendamento, RB36). Nulo em todo anúncio que não foi
-- cancelado depois da escolha (o cancelamento antes da escolha e a saída de conta seguem só com motivo_encerramento).
ALTER TABLE anuncio ADD COLUMN tipo_cancelamento VARCHAR(20);

-- aplicacao.cota_estornada_em: quando o estorno da cota FREE aconteceu. Marca de idempotência (uma escolha é
-- estornada uma vez só) e permite às contagens por candidatura (barreira de candidatura, "onde você usou sua cota")
-- ignorarem a escolha devolvida. Nulo = a escolha ainda conta (ou nunca consumiu cota).
ALTER TABLE aplicacao ADD COLUMN cota_estornada_em TIMESTAMPTZ;
