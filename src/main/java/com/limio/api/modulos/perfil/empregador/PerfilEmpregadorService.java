package com.limio.api.modulos.perfil.empregador;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.limio.api.comum.base.BaseService;
import com.limio.api.comum.portas.ConsultaDadosUsuario;
import com.limio.api.comum.portas.DadosUsuario;
import com.limio.api.modulos.perfil.excecao.PerfilEmpregadorNaoEncontradoException;

@Component
public class PerfilEmpregadorService extends BaseService<PerfilEmpregador, UUID, PerfilEmpregadorRepository> {

    private final ConsultaDadosUsuario consultaDadosUsuario;

    public PerfilEmpregadorService(PerfilEmpregadorRepository repository,
            ConsultaDadosUsuario consultaDadosUsuario) {
        super(repository);
        this.consultaDadosUsuario = consultaDadosUsuario;
    }

    public MeuPerfilEmpregador garantirPerfil(UUID usuarioId) {
        DadosUsuario dados = consultaDadosUsuario.buscarPorId(usuarioId)
                .orElseThrow(PerfilEmpregadorNaoEncontradoException::new);
        PerfilEmpregador perfil = buscarOuCriar(usuarioId,
                PerfilEmpregador.nomeExibicaoInicialDe(dados.nomeCompleto()));
        return new MeuPerfilEmpregador(perfil, dados);
    }

    public PerfilEmpregador garantirPerfilParaAtualizar(UUID usuarioId) {
        PerfilEmpregador perfil = garantirPerfil(usuarioId).perfil();
        return repository.findByIdParaAtualizar(perfil.getId())
                .orElseThrow(PerfilEmpregadorNaoEncontradoException::new);
    }

    private PerfilEmpregador buscarOuCriar(UUID usuarioId, String nomeInicial) {
        return repository.findByUsuarioId(usuarioId).orElseGet(() -> {
            repository.inserirSeAusente(usuarioId, nomeInicial);
            return repository.findByUsuarioId(usuarioId).orElseThrow();
        });
    }
}
