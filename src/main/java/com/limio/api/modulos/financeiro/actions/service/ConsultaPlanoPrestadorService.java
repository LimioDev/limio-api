package com.limio.api.modulos.financeiro.actions.service;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.limio.api.comum.portas.ConsultaPlanoPrestador;
import com.limio.api.comum.portas.enums.Plano;

/**
 * Implementa {@link ConsultaPlanoPrestador} para a cota do mês ({@code modulos.perfil}, US24).
 *
 * <p>A assinatura GOLD ({@code assinatura_gold}) ainda não existe: até o card de assinar o GOLD entrar, todo
 * Prestador é FREE. Quando entrar, a mudança é só aqui — quem consulta já trata os dois planos.
 */
@Component
public class ConsultaPlanoPrestadorService implements ConsultaPlanoPrestador {

    @Override
    public Plano planoAtual(UUID prestadorId) {
        return Plano.FREE;
    }
}
