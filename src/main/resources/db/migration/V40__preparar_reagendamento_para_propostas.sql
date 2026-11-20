-- ETI-99: a proposta de reagendamento passa a guardar quem propôs, até quando a contraparte pode responder e se
-- foi feita depois da data vigente (no máximo uma por serviço nessa situação).
-- A tabela não tinha nenhum fluxo gravando nela (V13 só a criou), então as colunas NOT NULL entram sem backfill.
ALTER TABLE reagendamento
    ADD COLUMN proponente_id               UUID        NOT NULL,
    ADD COLUMN expira_em                   TIMESTAMPTZ NOT NULL,
    ADD COLUMN proposta_apos_data_vigente  BOOLEAN     NOT NULL DEFAULT false;

-- No máximo uma proposta pendente por serviço; rede de segurança do banco sob a trava do anúncio.
CREATE UNIQUE INDEX idx_reagendamento_pendente_unico ON reagendamento (anuncio_id) WHERE status = 'PENDENTE';

-- Job de expiração: só olha as pendentes, em ordem de vencimento.
CREATE INDEX idx_reagendamento_pendente_expira ON reagendamento (expira_em) WHERE status = 'PENDENTE';
