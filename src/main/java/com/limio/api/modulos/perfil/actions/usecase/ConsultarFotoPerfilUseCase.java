package com.limio.api.modulos.perfil.actions.usecase;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.modulos.perfil.actions.service.ArmazenamentoService;
import com.limio.api.modulos.perfil.midia.LinkTemporario;
import com.limio.api.modulos.perfil.midia.Midia;
import com.limio.api.modulos.perfil.midia.MidiaService;

/** Link temporário da foto atual do usuário; vazio se ele nunca enviou foto. */
@Service
public class ConsultarFotoPerfilUseCase {

    private final MidiaService midiaService;
    private final ArmazenamentoService armazenamentoService;

    public ConsultarFotoPerfilUseCase(MidiaService midiaService, ArmazenamentoService armazenamentoService) {
        this.midiaService = midiaService;
        this.armazenamentoService = armazenamentoService;
    }

    @Transactional(readOnly = true)
    public Optional<LinkTemporario> executar(UUID usuarioId) {
        return midiaService.buscarFotoPerfilAtiva(usuarioId)
                .map(Midia::getUrlS3)
                .map(armazenamentoService::gerarLink);
    }
}
