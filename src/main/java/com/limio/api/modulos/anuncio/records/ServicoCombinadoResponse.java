package com.limio.api.modulos.anuncio.records;

import java.time.Instant;
import java.util.UUID;

import com.limio.api.modulos.anuncio.enums.StatusAnuncio;

/**
 * Resposta de {@code GET /anuncios/{anuncioId}/servico} (ETI-86), pro Empregador dono ou pro Prestador
 * escolhido. {@code reagendamentosAceitos} e {@code propostaPendente} vêm vazios nesta sprint: a tabela
 * {@code reagendamento} existe (TICKET-0049), mas nenhum usecase cria propostas ainda (ETI-99/ETI-100).
 * {@code candidaturaId} é a candidatura escolhida, o mesmo pros dois lados: é o que {@code GET
 * /candidaturas/{candidaturaId}/chat} aceita, pro "Conversar com (nome)" das telas 25, 36 e 37 (ETI-138).
 */
public record ServicoCombinadoResponse(
        UUID anuncioId,
        UUID candidaturaId,
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
