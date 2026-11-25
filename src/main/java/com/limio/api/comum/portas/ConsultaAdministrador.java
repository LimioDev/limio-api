package com.limio.api.comum.portas;

import java.util.UUID;

/**
 * Porta de leitura do administrador do painel web para o {@code LeitorTokenAcesso} ({@code comum/seguranca}, ETI-117),
 * que não pode importar {@code modulos.administracao}. Só consulta; quem implementa é o {@code administracao}.
 */
@FunctionalInterface
public interface ConsultaAdministrador {

    /** O administrador ainda existe: token de um administrador removido (ou de um id qualquer) não abre o painel. */
    boolean existe(UUID administradorId);
}
