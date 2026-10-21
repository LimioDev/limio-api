package com.limio.api.modulos.perfil.actions.usecase;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.modulos.perfil.empregador.PerfilEmpregador;
import com.limio.api.modulos.perfil.empregador.PerfilEmpregadorService;
import com.limio.api.modulos.perfil.endereco.EnderecoSalvo;
import com.limio.api.modulos.perfil.endereco.EnderecoSalvoService;

@Service
public class MarcarEnderecoSalvoPadraoUseCase {

    private final PerfilEmpregadorService perfilEmpregadorService;
    private final EnderecoSalvoService enderecoSalvoService;

    public MarcarEnderecoSalvoPadraoUseCase(PerfilEmpregadorService perfilEmpregadorService,
            EnderecoSalvoService enderecoSalvoService) {
        this.perfilEmpregadorService = perfilEmpregadorService;
        this.enderecoSalvoService = enderecoSalvoService;
    }

    @Transactional
    public EnderecoSalvo executar(UUID usuarioId, UUID enderecoId) {
        PerfilEmpregador perfil = perfilEmpregadorService.garantirPerfilParaAtualizar(usuarioId);
        EnderecoSalvo endereco = enderecoSalvoService.buscarEnderecoAtivoOuFalhar(enderecoId, perfil.getId());
        if (endereco.isPadrao()) {
            return endereco;
        }
        enderecoSalvoService.desmarcarEnderecoPadrao(perfil.getId());
        EnderecoSalvo atualizado = enderecoSalvoService.buscarEnderecoAtivoOuFalhar(enderecoId, perfil.getId());
        atualizado.setPadrao(true);
        return enderecoSalvoService.salvar(atualizado);
    }
}
