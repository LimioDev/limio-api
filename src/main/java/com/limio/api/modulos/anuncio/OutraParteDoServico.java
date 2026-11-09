package com.limio.api.modulos.anuncio;

import com.limio.api.comum.midia.LinkTemporario;

/**
 * A outra parte do serviço combinado, do ponto de vista de quem consulta (ETI-86). Nome e telefone saem
 * completos — contato já liberado, diferente do candidato resumido de antes da escolha. {@code gold} só é
 * calculado quando quem consulta é o Empregador (vendo o Prestador); {@code null} no sentido contrário.
 * Todos os campos saem {@code null} se a conta já foi encerrada.
 */
public record OutraParteDoServico(String nome, LinkTemporario foto, String telefone, Boolean gold) {

    public static final OutraParteDoServico CONTA_ENCERRADA = new OutraParteDoServico(null, null, null, null);
}
