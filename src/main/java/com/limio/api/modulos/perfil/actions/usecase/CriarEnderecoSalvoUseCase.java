package com.limio.api.modulos.perfil.actions.usecase;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.modulos.perfil.empregador.PerfilEmpregador;
import com.limio.api.modulos.perfil.empregador.PerfilEmpregadorService;
import com.limio.api.modulos.perfil.endereco.EnderecoSalvo;
import com.limio.api.modulos.perfil.endereco.EnderecoSalvoService;

@Service
public class CriarEnderecoSalvoUseCase {

    private final PerfilEmpregadorService perfilEmpregadorService;
    private final EnderecoSalvoService enderecoSalvoService;

    public CriarEnderecoSalvoUseCase(PerfilEmpregadorService perfilEmpregadorService,
            EnderecoSalvoService enderecoSalvoService) {
        this.perfilEmpregadorService = perfilEmpregadorService;
        this.enderecoSalvoService = enderecoSalvoService;
    }

    @Transactional
    public EnderecoSalvo executar(UUID usuarioId, EnderecoSalvo novo) {
        PerfilEmpregador perfil = perfilEmpregadorService.garantirPerfilParaAtualizar(usuarioId);
        if (novo.isPadrao()) {
            enderecoSalvoService.desmarcarEnderecoPadrao(perfil.getId());
        }
        novo.setPerfilEmpregadorId(perfil.getId());
        return enderecoSalvoService.salvar(novo);
    }
}
