package com.limio.api.modulos.perfil.actions.service;

import java.util.Collection;
import java.util.HashMap;
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

/**
 * Implementa {@link ConsultaFotoPerfil}: cabeçalho do chat ({@code modulos.chat}), candidatos do anúncio
 * ({@code modulos.anuncio}) e o resumo do Empregador ({@link ConsultaPerfilEmpregadorService}).
 */
@Component
public class ConsultaFotoPerfilService implements ConsultaFotoPerfil {

    private static final Logger log = LoggerFactory.getLogger(ConsultaFotoPerfilService.class);

    private final MidiaService midiaService;
    private final ArmazenamentoService armazenamentoService;

    public ConsultaFotoPerfilService(MidiaService midiaService, ArmazenamentoService armazenamentoService) {
        this.midiaService = midiaService;
        this.armazenamentoService = armazenamentoService;
    }

    /** Sem storage quem consulta continua de pé, só sem a foto (a consulta da própria foto segue respondendo 503). */
    @Override
    public Optional<LinkTemporario> buscarFoto(UUID usuarioId) {
        try {
            return midiaService.buscarFotoPerfilAtiva(usuarioId)
                    .map(Midia::getUrlS3)
                    .map(armazenamentoService::gerarLink);
        } catch (ArmazenamentoIndisponivelException e) {
            log.warn("link da foto de perfil indisponível; consulta segue sem foto");
            return Optional.empty();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, LinkTemporario> buscarFotosEmLote(Collection<UUID> usuarioIds) {
        if (usuarioIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, LinkTemporario> links = new HashMap<>();
        midiaService.buscarFotosPerfilAtivas(usuarioIds).forEach((usuarioId, foto) -> {
            try {
                links.put(usuarioId, armazenamentoService.gerarLink(foto.getUrlS3()));
            } catch (ArmazenamentoIndisponivelException e) {
                log.warn("link da foto de perfil indisponível; consulta segue sem foto");
            }
        });
        return links;
    }
}
