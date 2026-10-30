package com.limio.api.modulos.perfil.actions.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.midia.LinkTemporario;
import com.limio.api.comum.portas.ConsultaFotoPerfil;
import com.limio.api.comum.portas.ConsultaPerfilEmpregador;
import com.limio.api.comum.portas.ResumoPerfilEmpregador;
import com.limio.api.modulos.perfil.empregador.PerfilEmpregador;
import com.limio.api.modulos.perfil.empregador.PerfilEmpregadorService;

/**
 * Implementa {@link ConsultaPerfilEmpregador} para o painel do Empregador ({@code modulos.anuncio}, TICKET-0052) e o
 * cabeçalho do chat ({@code modulos.chat}, ETI-101) e o mural do Prestador (ETI-79).
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
        PerfilEmpregador perfil = perfilEmpregadorService.garantirPerfil(usuarioId).perfil();
        return resumoDe(perfil, linkDaFoto(usuarioId));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, ResumoPerfilEmpregador> buscarResumosEmLote(Collection<UUID> usuarioIds) {
        Map<UUID, PerfilEmpregador> perfis = perfilEmpregadorService.buscarPerfisOuIniciais(usuarioIds);
        Map<UUID, Midia> fotos = midiaService.buscarFotosPerfilAtivas(perfis.keySet());
        Map<UUID, ResumoPerfilEmpregador> resumos = new HashMap<>();
        perfis.forEach((usuarioId, perfil) ->
                resumos.put(usuarioId, resumoDe(perfil, Optional.ofNullable(fotos.get(usuarioId)).flatMap(this::linkDa))));
        return resumos;
    }

    private ResumoPerfilEmpregador resumoDe(PerfilEmpregador perfil, Optional<LinkTemporario> foto) {
        return new ResumoPerfilEmpregador(perfil.getNomeExibicao(),
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
                foto.map(LinkTemporario::expiraEm).orElse(null),
                perfil.getMediaAvaliacoes(),
                perfil.getQtdAvaliacoes());
    }

    /** Sem storage o painel continua de pé, só sem a foto (a consulta da própria foto segue respondendo 503). */
    private Optional<LinkTemporario> linkDaFoto(UUID usuarioId) {
        return midiaService.buscarFotoPerfilAtiva(usuarioId).flatMap(this::linkDa);
    }

    private Optional<LinkTemporario> linkDa(Midia foto) {
        try {
            return Optional.of(armazenamentoService.gerarLink(foto.getUrlS3()));
        } catch (ArmazenamentoIndisponivelException e) {
            log.warn("link da foto de perfil indisponível; resumo do empregador segue sem foto");
            return Optional.empty();
        }
    }
}
