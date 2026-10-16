package com.limio.api.modulos.perfil.actions.usecase;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.portas.ConsultaDadosUsuario;
import com.limio.api.comum.portas.DadosUsuario;
import com.limio.api.modulos.perfil.actions.helper.NomeExibicaoInicial;
import com.limio.api.modulos.perfil.empregador.MeuPerfilEmpregador;
import com.limio.api.modulos.perfil.empregador.PerfilEmpregador;
import com.limio.api.modulos.perfil.empregador.PerfilEmpregadorService;
import com.limio.api.modulos.perfil.excecao.PerfilEmpregadorNaoEncontradoException;

@Service
public class ConsultarMeuPerfilUseCase {

    private final ConsultaDadosUsuario consultaDadosUsuario;
    private final PerfilEmpregadorService perfilEmpregadorService;

    public ConsultarMeuPerfilUseCase(ConsultaDadosUsuario consultaDadosUsuario,
            PerfilEmpregadorService perfilEmpregadorService) {
        this.consultaDadosUsuario = consultaDadosUsuario;
        this.perfilEmpregadorService = perfilEmpregadorService;
    }

    @Transactional
    public MeuPerfilEmpregador executar(UUID usuarioId) {
        DadosUsuario dados = consultaDadosUsuario.buscarPorId(usuarioId)
                .orElseThrow(PerfilEmpregadorNaoEncontradoException::new);
        PerfilEmpregador perfil = perfilEmpregadorService.buscarOuCriar(usuarioId,
                NomeExibicaoInicial.de(dados.nomeCompleto()));
        return new MeuPerfilEmpregador(perfil, dados);
    }
}
