package com.limio.api.modulos.anuncio.enums;

/**
 * Diagrama de classes declara o enum sem valores ("a definir pelo grupo").
 * Cada valor abaixo corresponde a um marco de timestamp que {@link
 * com.limio.api.modulos.anuncio.Anuncio} já tem — nenhum estado sem rastro
 * no diagrama (TICKET-0049).
 */
public enum StatusAnuncio {
    PUBLICADO,
    EM_ANDAMENTO,
    AGUARDANDO_VALIDACAO,
    CONCLUIDO,
    ENCERRADO;

    /** Status ainda "vivo" — ainda não chegou num estado final. Fonte única pra {@code isAberto()} e pros finders. */
    public boolean isAberto() {
        return switch (this) {
            case PUBLICADO, EM_ANDAMENTO, AGUARDANDO_VALIDACAO -> true;
            case CONCLUIDO, ENCERRADO -> false;
        };
    }
}
