-- Uma linha por falha de login (TICKET-0032), base do rate limit por e-mail.
-- Chave é o e-mail digitado, não usuario_id: falha com e-mail inexistente também
-- conta, senão o bloqueio revelaria quais e-mails têm conta. Não está no DER.
CREATE TABLE tentativa_login (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(255) NOT NULL,
    ip            VARCHAR(45),
    criado_em     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    atualizado_em TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_tentativa_login_email_criado_em ON tentativa_login (email, criado_em DESC);
