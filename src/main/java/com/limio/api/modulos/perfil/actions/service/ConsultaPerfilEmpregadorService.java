package com.limio.api.modulos.perfil.actions.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.limio.api.comum.midia.LinkTemporario;
import com.limio.api.comum.portas.ConsultaFotoPerfil;
import com.limio.api.comum.portas.ConsultaPerfilEmpregador;
import com.limio.api.comum.portas.ResumoPerfilEmpregador;
import com.limio.api.modulos.perfil.empregador.PerfilEmpregadorService;

/**
 * Implementa {@link ConsultaPerfilEmpregador} para o painel do Empregador ({@code modulos.anuncio}, TICKET-0052) e o
 * cabeçalho do chat ({@code modulos.chat}, ETI-101).
 */
@Component
public class ConsultaPerfilEmpregadorService implements ConsultaPerfilEmpregador {

    private final PerfilEmpregadorService perfilEmpregadorService;
    private final ConsultaFotoPerfil consultaFotoPerfil;

    public ConsultaPerfilEmpregadorService(PerfilEmpregadorService perfilEmpregadorService,
            ConsultaFotoPerfil consultaFotoPerfil) {
        this.perfilEmpregadorService = perfilEmpregadorService;
        this.consultaFotoPerfil = consultaFotoPerfil;
    }

    @Override
    public ResumoPerfilEmpregador buscarResumo(UUID usuarioId) {
        return resumo(usuarioId, perfilEmpregadorService.garantirPerfil(usuarioId).perfil().getNomeExibicao());
    }

    @Override
    public Optional<ResumoPerfilEmpregador> buscarResumoSemCriar(UUID usuarioId) {
        return perfilEmpregadorService.nomeExibicaoSemCriar(usuarioId).map(nome -> resumo(usuarioId, nome));
    }

    private ResumoPerfilEmpregador resumo(UUID usuarioId, String nomeExibicao) {
        Optional<LinkTemporario> foto = consultaFotoPerfil.buscarFoto(usuarioId);
        return new ResumoPerfilEmpregador(nomeExibicao,
                foto.map(LinkTemporario::url).orElse(null),
                foto.map(LinkTemporario::expiraEm).orElse(null));
    }
}
