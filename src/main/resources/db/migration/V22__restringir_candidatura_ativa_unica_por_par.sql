-- Clique duplo / corrida (ETI-75): o usecase já confere antes de gravar, mas só o índice garante sob
-- concorrência. Só uma candidatura "ativa" (ENVIADA/ESCOLHIDA/AGUARDANDO_VALIDACAO) por par
-- anúncio+prestador — RECUSADA/CONCLUIDA/ENCERRADA não contam, então o prestador pode se candidatar de
-- novo depois de recusado (mesma regra do card).
CREATE UNIQUE INDEX uk_aplicacao_par_ativo ON aplicacao (anuncio_id, prestador_id)
    WHERE status IN ('ENVIADA', 'ESCOLHIDA', 'AGUARDANDO_VALIDACAO');
