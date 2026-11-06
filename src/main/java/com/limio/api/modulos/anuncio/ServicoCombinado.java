package com.limio.api.modulos.anuncio;

import java.time.Instant;

import com.limio.api.comum.portas.ResumoCategoria;
import com.limio.api.comum.portas.ResumoChatDoServico;
import com.limio.api.modulos.anuncio.reagendamento.Reagendamento;

/**
 * O serviço combinado visto por uma das duas partes (ETI-86): o {@code Anuncio} depois da escolha, com os
 * contatos liberados. {@code escolhidaEm} vem da candidatura escolhida, não do anúncio.
 *
 * <p>{@code reagendamentosAceitos}/{@code limiteReagendamentos} e {@code propostaPendente} dependem da tabela
 * {@code reagendamento} (já existe, TICKET-0049) mas de nenhum usecase que a preencha ainda (ETI-99/ETI-100) —
 * nesta sprint sempre voltam zero/vazio, como o próprio card prevê.
 *
 * @param chat {@code null} se o chat da candidatura ainda não existe
 */
public record ServicoCombinado(
        Anuncio anuncio,
        ResumoCategoria categoria,
        Instant escolhidaEm,
        Instant prazoCancelamentoEm,
        OutraParteDoServico outraParte,
        int reagendamentosAceitos,
        int limiteReagendamentos,
        Reagendamento propostaPendente,
        ResumoChatDoServico chat) {
}
