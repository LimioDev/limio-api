package com.limio.api.modulos.anuncio;

import java.util.List;

/**
 * O que o Empregador vê antes de confirmar a validação (ETI-92): as fotos do check-out, se houver, e o
 * nome do Prestador escolhido. {@code nomePrestador} é {@code null} se a conta do Prestador já foi
 * encerrada.
 */
public record ValidacaoDoServico(Anuncio anuncio, List<FotoDoCheckout> fotosCheckout, String nomePrestador) {
}
