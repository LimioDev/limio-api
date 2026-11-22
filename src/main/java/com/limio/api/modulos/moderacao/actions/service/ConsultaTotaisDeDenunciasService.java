package com.limio.api.modulos.moderacao.actions.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.portas.ConsultaTotaisDeDenuncias;
import com.limio.api.modulos.moderacao.denuncia.DenunciaService;
import com.limio.api.modulos.moderacao.enums.StatusResolucao;

/** Implementa {@link ConsultaTotaisDeDenuncias} para o dashboard do administrador (ETI-117). */
@Component
public class ConsultaTotaisDeDenunciasService implements ConsultaTotaisDeDenuncias {

    private final DenunciaService denunciaService;

    public ConsultaTotaisDeDenunciasService(DenunciaService denunciaService) {
        this.denunciaService = denunciaService;
    }

    @Override
    @Transactional(readOnly = true)
    public long contarAbertas() {
        return denunciaService.contarPorStatus(StatusResolucao.ABERTA);
    }
}
