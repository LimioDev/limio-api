-- Cancelamento de anúncio (ETI-63): "Decisão pendente" do card resolvida reaproveitando ENCERRADO
-- (sem status novo) e gravando o motivo em texto livre — mesmo padrão que aplicacao.motivo_encerramento
-- já usa desde o TICKET-0049. Serve também pros futuros ETI-66 (expirado) e ETI-94/95 (cancelamento com
-- e sem estorno), cada um com seu próprio texto, sem crescer o enum StatusAnuncio.
ALTER TABLE anuncio ADD COLUMN motivo_encerramento TEXT;
