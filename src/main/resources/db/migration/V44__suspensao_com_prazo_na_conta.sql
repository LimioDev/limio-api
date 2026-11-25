-- Suspensão com prazo (ETI-116, módulo auth). A moderação suspende a conta até uma data: a conta fica BLOQUEADA e
-- suspensa_ate guarda o fim. A rotina de auth (auth.suspensao.cron) volta a conta para ATIVA quando o prazo passa.
-- BLOQUEADA sem suspensa_ate é bloqueio sem prazo (linha ajustada à mão), e a rotina não mexe nela.
ALTER TABLE usuario ADD COLUMN suspensa_ate TIMESTAMPTZ;

CREATE INDEX idx_usuario_suspensa_ate ON usuario (suspensa_ate) WHERE suspensa_ate IS NOT NULL;
