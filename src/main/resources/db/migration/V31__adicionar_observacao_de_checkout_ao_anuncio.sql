-- Check-out com foto (ETI-90): o Prestador pode deixar uma observação opcional pro Empregador ler na validação.
-- Texto livre, sem limite definido pelo PO. Nula quando o Prestador não escreveu nada.
ALTER TABLE anuncio ADD COLUMN observacao_checkout TEXT;
