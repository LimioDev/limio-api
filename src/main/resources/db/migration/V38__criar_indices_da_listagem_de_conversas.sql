-- Listagem de conversas do usuário por papel ativo (ETI-103). Empregador filtra por contratante_id e Prestador por
-- prestador_id; até aqui o chat só tinha índice por aplicacao_id e anuncio_id.
CREATE INDEX idx_chat_contratante_id ON chat (contratante_id);
CREATE INDEX idx_chat_prestador_id ON chat (prestador_id);

-- Mensagens não lidas por chat (contagem por item e total de conversas com não lidas). Parcial: só as pendentes,
-- que são poucas perto do histórico; remetente_id entra porque a contagem descarta as mensagens do próprio usuário.
CREATE INDEX idx_mensagem_nao_lidas ON mensagem (chat_id, remetente_id) WHERE lida_em IS NULL;
