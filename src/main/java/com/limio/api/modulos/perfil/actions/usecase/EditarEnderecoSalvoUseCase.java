package com.limio.api.modulos.perfil.actions.usecase;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.modulos.perfil.empregador.PerfilEmpregador;
import com.limio.api.modulos.perfil.empregador.PerfilEmpregadorService;
import com.limio.api.modulos.perfil.endereco.EnderecoSalvo;
import com.limio.api.modulos.perfil.endereco.EnderecoSalvoService;

@Service
public class EditarEnderecoSalvoUseCase {

    private final PerfilEmpregadorService perfilEmpregadorService;
    private final EnderecoSalvoService enderecoSalvoService;

    public EditarEnderecoSalvoUseCase(PerfilEmpregadorService perfilEmpregadorService,
            EnderecoSalvoService enderecoSalvoService) {
        this.perfilEmpregadorService = perfilEmpregadorService;
        this.enderecoSalvoService = enderecoSalvoService;
    }

    @Transactional
    public EnderecoSalvo executar(UUID usuarioId, UUID enderecoId, EnderecoSalvo novosDados) {
        PerfilEmpregador perfil = perfilEmpregadorService.garantirPerfilParaAtualizar(usuarioId);
        if (novosDados.isPadrao()) {
            enderecoSalvoService.desmarcarEnderecoPadrao(perfil.getId());
        }
        EnderecoSalvo endereco = enderecoSalvoService.buscarEnderecoAtivoOuFalhar(enderecoId, perfil.getId());
        endereco.substituirDadosPor(novosDados);
        return enderecoSalvoService.salvar(endereco);
    }
}
