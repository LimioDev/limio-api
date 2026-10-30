package com.limio.api.comum.portas;

import java.util.UUID;

import com.limio.api.comum.portas.enums.Plano;

/**
 * Porta de leitura do plano do Prestador para módulos que não podem importar {@code modulos.financeiro} (hoje o
 * {@code perfil}, na consulta da cota do mês — US24). Só consulta; quem implementa é o {@code financeiro}, dono
 * da assinatura GOLD.
 */
public interface ConsultaPlanoPrestador {

    /**
     * @param prestadorId id do usuário Prestador
     * @return o plano em vigor agora
     */
    Plano planoAtual(UUID prestadorId);
}
