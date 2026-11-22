package com.limio.api.comum.portas;

/**
 * Porta de leitura do total de assinaturas GOLD para o dashboard do administrador ({@code modulos.administracao},
 * ETI-117). Só consulta; quem implementa é o {@code financeiro}.
 */
public interface ConsultaTotaisDeAssinaturas {

    /** Assinaturas GOLD com status {@code ATIVA}. Pendentes de pagamento e canceladas ficam de fora. */
    long contarGoldAtivas();
}
