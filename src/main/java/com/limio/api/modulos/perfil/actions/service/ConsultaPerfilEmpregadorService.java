package com.limio.api.modulos.perfil.actions.service;

import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.limio.api.comum.midia.ArmazenamentoService;
import com.limio.api.comum.midia.LinkTemporario;
import com.limio.api.comum.midia.Midia;
import com.limio.api.comum.midia.MidiaService;
import com.limio.api.comum.midia.excecao.ArmazenamentoIndisponivelException;
import com.limio.api.comum.portas.ConsultaPerfilEmpregador;
import com.limio.api.comum.portas.ResumoPerfilEmpregador;
import com.limio.api.modulos.perfil.empregador.PerfilEmpregadorService;

/** Implementa {@link ConsultaPerfilEmpregador} para o painel do Empregador ({@code modulos.anuncio}, TICKET-0052). */
@Component
public class ConsultaPerfilEmpregadorService implements ConsultaPerfilEmpregador {

    private static final Logger log = LoggerFactory.getLogger(ConsultaPerfilEmpregadorService.class);

    private final PerfilEmpregadorService perfilEmpregadorService;
    private final MidiaService midiaService;
    private final ArmazenamentoService armazenamentoService;

    public ConsultaPerfilEmpregadorService(PerfilEmpregadorService perfilEmpregadorService,
            MidiaService midiaService, ArmazenamentoService armazenamentoService) {
        this.perfilEmpregadorService = perfilEmpregadorService;
        this.midiaService = midiaService;
        this.armazenamentoService = armazenamentoService;
    }

    @Override
    public ResumoPerfilEmpregador buscarResumo(UUID usuarioId) {
        String nomeExibicao = perfilEmpregadorService.garantirPerfil(usuarioId).perfil().getNomeExibicao();
        Optional<LinkTemporario> foto = linkDaFoto(usuarioId);
        return new ResumoPerfilEmpregador(nomeExibicao,
                foto.map(LinkTemporario::url).orElse(null),
                foto.map(LinkTemporario::expiraEm).orElse(null));
    }

    /** Sem storage o painel continua de pé, só sem a foto (a consulta da própria foto segue respondendo 503). */
    private Optional<LinkTemporario> linkDaFoto(UUID usuarioId) {
        try {
            return midiaService.buscarFotoPerfilAtiva(usuarioId)
                    .map(Midia::getUrlS3)
                    .map(armazenamentoService::gerarLink);
        } catch (ArmazenamentoIndisponivelException e) {
            log.warn("link da foto de perfil indisponível; resumo do empregador segue sem foto");
            return Optional.empty();
        }
    }
}
