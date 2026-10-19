package com.limio.api.modulos.auth.eventos;

/** Publicado quando o e-mail da conta muda de fato; avisa o endereço antigo depois do commit. */
public record EmailContaAlteradoEvent(String emailAntigo, String novoEmail) {
}
