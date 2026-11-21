package com.limio.api.modulos.anuncio;

import java.math.BigDecimal;

import com.limio.api.comum.midia.LinkTemporario;

/**
 * A outra parte do serviço combinado, do ponto de vista de quem consulta (ETI-86). Nome e telefone saem
 * completos — contato já liberado, diferente do candidato resumido de antes da escolha. {@code gold} só é
 * calculado quando quem consulta é o Empregador (vendo o Prestador); {@code null} no sentido contrário. A reputação
 * ({@code qtdAvaliacoes}/{@code mediaAvaliacoes}, ETI-138) segue a mesma regra do {@code gold}.
 * Todos os campos saem {@code null} se a conta já foi encerrada.
 */
public record OutraParteDoServico(String nome, LinkTemporario foto, String telefone, Boolean gold,
        Integer qtdAvaliacoes, BigDecimal mediaAvaliacoes) {

    public static final OutraParteDoServico CONTA_ENCERRADA =
            new OutraParteDoServico(null, null, null, null, null, null);
}
