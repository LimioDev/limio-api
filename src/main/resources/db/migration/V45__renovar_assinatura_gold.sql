-- Renovação mensal do GOLD (ETI-85): cobrança da renovação, novas tentativas e volta ao FREE.
-- V41 está na PR #51 (ETI-71) e V42–V44 no painel admin (ETI-116/117); V45 evita colidir com elas.

-- proxima_tentativa_em: quando a próxima cobrança da renovação pode sair, depois de uma recusa ou de o gateway não
--   responder. Nula = cobrar assim que data_fim_ciclo passar. A rotina de renovação lê esta coluna (a "fila").
-- renovacao_cancelada_em: o Prestador cancelou a renovação (US27, card "Gerenciar a assinatura GOLD"). Ao passar
--   data_fim_ciclo, a assinatura é encerrada em vez de cobrada.
-- encerrada_em: quando a assinatura voltou para FREE (status ENCERRADA), por tentativas esgotadas ou fim do ciclo
--   com a renovação cancelada.
ALTER TABLE assinatura_gold
    ADD COLUMN proxima_tentativa_em   TIMESTAMPTZ,
    ADD COLUMN renovacao_cancelada_em TIMESTAMPTZ,
    ADD COLUMN encerrada_em           TIMESTAMPTZ;

-- inicio_ciclo_cobrado: início do ciclo que a cobrança de renovação paga (o data_fim_ciclo da assinatura quando
--   ela saiu). Nulo = primeira cobrança, a da assinatura (ETI-81). Um aviso que chega atrasado do gateway só avança
--   o ciclo se ainda for o ciclo em aberto.
-- tentativa: número da tentativa dentro do ciclo (1, 2, ...). Linhas da ETI-81 são a primeira.
ALTER TABLE cobranca
    ADD COLUMN inicio_ciclo_cobrado TIMESTAMPTZ,
    ADD COLUMN tentativa            INTEGER NOT NULL DEFAULT 1,
    ADD CONSTRAINT ck_cobranca_tentativa CHECK (tentativa > 0);

-- A rotina busca as assinaturas ativas vencidas; o webhook e a rotina buscam as cobranças da assinatura por status.
CREATE INDEX ix_assinatura_gold_ativa_fim_ciclo ON assinatura_gold (data_fim_ciclo) WHERE status = 'ATIVA';
CREATE INDEX ix_cobranca_assinatura_status ON cobranca (assinatura_gold_id, status);
CREATE INDEX ix_cobranca_gateway_cobranca_id ON cobranca (gateway_cobranca_id);
