package com.limio.api.modulos.anuncio.records;

import java.time.Instant;
import java.util.UUID;

import com.limio.api.modulos.anuncio.enums.StatusAnuncio;

/**
 * Resposta de {@code GET /anuncios/{anuncioId}/servico} (ETI-86), pro Empregador dono ou pro Prestador
 * escolhido. {@code reagendamentosAceitos} conta as propostas aceitas, {@code limiteReagendamentos} é o máximo
 * permitido e {@code propostaPendente} é a proposta aguardando resposta, se houver (ETI-99). Também é a resposta de
 * {@code POST /reagendamentos/{id}/responder}.
 */
public record ServicoCombinadoResponse(
        UUID anuncioId,
        StatusAnuncio status,
        String titulo,
        String descricao,
        CategoriaAnuncioResponse categoria,
        Instant dataHoraPretendida,
        String bairroRegiao,
        String enderecoCompleto,
        OutraParteServicoResponse outraParte,
        Instant escolhidaEm,
        Instant iniciadoEm,
        Instant checkoutEm,
        Instant validadoEm,
        Instant prazoCancelamentoEm,
        int reagendamentosAceitos,
        int limiteReagendamentos,
        PropostaReagendamentoResponse propostaPendente,
        UUID chatId,
        int mensagensNaoLidas) {
}
