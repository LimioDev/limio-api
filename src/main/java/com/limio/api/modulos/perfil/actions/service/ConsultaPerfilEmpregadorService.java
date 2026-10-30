package com.limio.api.modulos.perfil.actions.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.midia.ArmazenamentoService;
import com.limio.api.comum.midia.LinkTemporario;
import com.limio.api.comum.midia.Midia;
import com.limio.api.comum.midia.MidiaService;
import com.limio.api.comum.midia.excecao.ArmazenamentoIndisponivelException;
import com.limio.api.comum.portas.ConsultaFotoPerfil;
import com.limio.api.comum.portas.ConsultaPerfilEmpregador;
import com.limio.api.comum.portas.ResumoPerfilEmpregador;
import com.limio.api.modulos.perfil.empregador.PerfilEmpregador;
import com.limio.api.modulos.perfil.empregador.PerfilEmpregadorService;

/**
 * Implementa {@link ConsultaPerfilEmpregador} para o painel do Empregador ({@code modulos.anuncio}, TICKET-0052), o
 * cabeçalho do chat ({@code modulos.chat}, ETI-101) e o mural do Prestador (ETI-73).
 */
@Component
public class ConsultaPerfilEmpregadorService implements ConsultaPerfilEmpregador {

    private static final Logger log = LoggerFactory.getLogger(ConsultaPerfilEmpregadorService.class);

    private final PerfilEmpregadorService perfilEmpregadorService;
    private final ConsultaFotoPerfil consultaFotoPerfil;
    private final MidiaService midiaService;
    private final ArmazenamentoService armazenamentoService;

    public ConsultaPerfilEmpregadorService(PerfilEmpregadorService perfilEmpregadorService,
            ConsultaFotoPerfil consultaFotoPerfil, MidiaService midiaService,
            ArmazenamentoService armazenamentoService) {
        this.perfilEmpregadorService = perfilEmpregadorService;
        this.consultaFotoPerfil = consultaFotoPerfil;
        this.midiaService = midiaService;
        this.armazenamentoService = armazenamentoService;
    }

    @Override
    public ResumoPerfilEmpregador buscarResumo(UUID usuarioId) {
        PerfilEmpregador perfil = perfilEmpregadorService.garantirPerfil(usuarioId).perfil();
        return resumoDe(perfil, consultaFotoPerfil.buscarFoto(usuarioId));
    }

    @Override
    public Optional<ResumoPerfilEmpregador> buscarResumoSemCriar(UUID usuarioId) {
        return Optional.ofNullable(perfilEmpregadorService.buscarPerfisOuIniciais(List.of(usuarioId)).get(usuarioId))
                .map(perfil -> resumoDe(perfil, consultaFotoPerfil.buscarFoto(usuarioId)));
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
                foto.map(LinkTemporario::url).orElse(null),
                foto.map(LinkTemporario::expiraEm).orElse(null),
                perfil.getMediaAvaliacoes(),
                perfil.getQtdAvaliacoes());
    }

    /** Só o lote usa: a foto avulsa passa por {@link ConsultaFotoPerfil}. Sem storage, segue sem a foto. */
    private Optional<LinkTemporario> linkDa(Midia foto) {
        try {
            return Optional.of(armazenamentoService.gerarLink(foto.getUrlS3()));
        } catch (ArmazenamentoIndisponivelException e) {
            log.warn("link da foto de perfil indisponível; resumo do empregador segue sem foto");
            return Optional.empty();
        }
    }
}
