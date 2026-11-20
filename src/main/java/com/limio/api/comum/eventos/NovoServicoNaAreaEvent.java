package com.limio.api.comum.eventos;

import java.time.Instant;
import java.util.UUID;

/**
 * Pedido de aviso "Novo serviço na sua área" para um Prestador compatível com um anúncio recém-publicado (ETI-65).
 * Um evento por Prestador. Quem envia é o {@code notificacao} (ETI-112), respeitando os canais ligados; até lá
 * ninguém escuta.
 *
 * <p>Privacidade (UC): só bairro e distância, nunca endereço completo, latitude nem longitude. {@code anuncioId} é o
 * que o app usa para abrir o detalhe do anúncio no mural (tela 30) ao tocar no aviso.
 *
 * @param destinatarioId usuário Prestador que recebe o aviso
 * @param anuncioTitulo  título do anúncio (o Figma da tela 43 mostra "Troca de chuveiro em Perdizes, a 3,4 km")
 * @param categoriaNome  nome da categoria do anúncio
 * @param bairroRegiao   bairro/região do retrato do endereço
 * @param distanciaKm    distância da base do Prestador até o anúncio, em km, sem arredondar
 */
public record NovoServicoNaAreaEvent(
        UUID destinatarioId,
        UUID anuncioId,
        String anuncioTitulo,
        String categoriaNome,
        String bairroRegiao,
        double distanciaKm,
        Instant ocorridoEm) {
}
