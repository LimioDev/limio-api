-- ETI-134: SMS sai dos canais de notificação. O canal nunca chegou a disparar nada (ETI-112 não
-- implementado) e a confirmação de telefone por SMS (UC04) nunca existiu — e-mail é o único canal
-- de confirmação de contato.
ALTER TABLE preferencia_notificacao DROP COLUMN sms;
