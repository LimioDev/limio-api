package com.limio.api.modulos.financeiro.actions.service;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.limio.api.comum.portas.ConsultaPlanoPrestador;
import com.limio.api.comum.portas.enums.Plano;
import com.limio.api.modulos.financeiro.assinaturagold.AssinaturaGold;
import com.limio.api.modulos.financeiro.assinaturagold.AssinaturaGoldService;

/**
 * Implementa {@link ConsultaPlanoPrestador} para a cota do mês ({@code modulos.perfil}, ETI-79). GOLD é
 * {@code assinatura_gold.status = ATIVA} do usuário (ETI-81); qualquer outro caso (nunca assinou,
 * cancelada) é FREE.
 */
@Component
public class ConsultaPlanoPrestadorService implements ConsultaPlanoPrestador {

    private final AssinaturaGoldService assinaturaGoldService;

    public ConsultaPlanoPrestadorService(AssinaturaGoldService assinaturaGoldService) {
        this.assinaturaGoldService = assinaturaGoldService;
    }

    @Override
    public Plano planoAtual(UUID prestadorId) {
        return assinaturaGoldService.buscarPorUsuario(prestadorId)
                .filter(AssinaturaGold::estaAtiva)
                .map(a -> Plano.GOLD)
                .orElse(Plano.FREE);
    }
}
