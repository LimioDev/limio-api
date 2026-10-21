package com.limio.api.modulos.perfil.actions.usecase;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.modulos.perfil.empregador.PerfilEmpregadorService;
import com.limio.api.modulos.perfil.endereco.EnderecoSalvo;
import com.limio.api.modulos.perfil.endereco.EnderecoSalvoService;

@Service
public class ListarEnderecosSalvosUseCase {

    private final PerfilEmpregadorService perfilEmpregadorService;
    private final EnderecoSalvoService enderecoSalvoService;

    public ListarEnderecosSalvosUseCase(PerfilEmpregadorService perfilEmpregadorService,
            EnderecoSalvoService enderecoSalvoService) {
        this.perfilEmpregadorService = perfilEmpregadorService;
        this.enderecoSalvoService = enderecoSalvoService;
    }

    @Transactional
    public List<EnderecoSalvo> executar(UUID usuarioId) {
        UUID perfilId = perfilEmpregadorService.garantirPerfil(usuarioId).perfil().getId();
        return enderecoSalvoService.listarEnderecoAtivos(perfilId);
    }
}
