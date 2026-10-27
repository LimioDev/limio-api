-- D10 do TICKET-0048, aprovada pelo tech lead em 2026-10-26: SHA-256 do e-mail para onde o link foi
-- enviado. O token só vale enquanto a conta tiver esse e-mail (confirmação e recuperação de senha).
-- Anulável: tokens emitidos antes desta migration seguem valendo pelas outras regras até expirarem.
-- Nunca o e-mail em claro; e os tokens da conta são apagados no encerramento (UC10).
ALTER TABLE verificacao_contato ADD COLUMN destino_hash VARCHAR(64);
