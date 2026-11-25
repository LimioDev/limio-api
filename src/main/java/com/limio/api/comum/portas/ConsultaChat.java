package com.limio.api.comum.portas;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Porta de leitura do chat de uma candidatura para módulos que não podem importar {@code modulos.chat} (hoje os
 * candidatos do anúncio — ETI-67 — e a denúncia de mensagem — ETI-114). Só consulta, nunca cria o chat; quem implementa é o {@code chat}.
 */
public interface ConsultaChat {

    /**
     * Em lote, pra não consultar uma candidatura por vez.
     *
     * @return id do chat por id de candidatura; candidatura cujo chat ainda não foi criado não aparece no mapa
     */
    Map<UUID, UUID> buscarIdsPorCandidatura(Collection<UUID> candidaturaIds);

    /**
     * Uma mensagem e as partes do chat dela (hoje a denúncia de mensagem, ETI-114).
     *
     * @param mensagemId id da mensagem
     * @return o resumo, ou vazio se a mensagem não existe
     */
    Optional<ResumoMensagem> buscarMensagem(UUID mensagemId);

    /**
     * Todas as mensagens do chat de uma candidatura, da mais antiga para a mais recente, com o texto original (hoje a
     * análise de denúncia do administrador, ETI-116).
     *
     * @return vazio se a candidatura não tem chat
     */
    List<MensagemOriginal> listarMensagensOriginais(UUID candidaturaId);

    /**
     * Mesmo que {@link #listarMensagensOriginais(UUID)}, para a denúncia de um serviço, que guarda o anúncio e não a
     * candidatura: as conversas do Prestador nesse anúncio, da mais antiga para a mais recente.
     */
    List<MensagemOriginal> listarMensagensOriginaisDoServico(UUID anuncioId, UUID prestadorId);
}
