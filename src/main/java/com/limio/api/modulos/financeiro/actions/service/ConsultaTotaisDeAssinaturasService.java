package com.limio.api.modulos.financeiro.actions.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.portas.ConsultaTotaisDeAssinaturas;
import com.limio.api.modulos.financeiro.assinaturagold.AssinaturaGoldService;
import com.limio.api.modulos.financeiro.assinaturagold.enums.StatusAssinatura;

/** Implementa {@link ConsultaTotaisDeAssinaturas} para o dashboard do administrador (ETI-117). */
@Component
public class ConsultaTotaisDeAssinaturasService implements ConsultaTotaisDeAssinaturas {

    private final AssinaturaGoldService assinaturaGoldService;

    public ConsultaTotaisDeAssinaturasService(AssinaturaGoldService assinaturaGoldService) {
        this.assinaturaGoldService = assinaturaGoldService;
    }

    @Override
    @Transactional(readOnly = true)
    public long contarGoldAtivas() {
        return assinaturaGoldService.contarPorStatus(StatusAssinatura.ATIVA);
    }
}
