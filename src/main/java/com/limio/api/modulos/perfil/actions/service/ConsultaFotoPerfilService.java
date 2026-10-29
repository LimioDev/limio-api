package com.limio.api.modulos.perfil.actions.service;

import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.limio.api.comum.midia.ArmazenamentoService;
import com.limio.api.comum.midia.Midia;
import com.limio.api.comum.midia.MidiaService;
import com.limio.api.comum.midia.excecao.ArmazenamentoIndisponivelException;
import com.limio.api.comum.portas.ConsultaFotoPerfil;
import com.limio.api.comum.portas.FotoPerfil;

/** Implementa {@link ConsultaFotoPerfil} para o cabeçalho do chat ({@code modulos.chat}, ETI-101). */
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
    public Optional<FotoPerfil> buscarFoto(UUID usuarioId) {
        try {
            return midiaService.buscarFotoPerfilAtiva(usuarioId)
                    .map(Midia::getUrlS3)
                    .map(armazenamentoService::gerarLink)
                    .map(link -> new FotoPerfil(link.url(), link.expiraEm()));
        } catch (ArmazenamentoIndisponivelException e) {
            log.warn("link da foto de perfil indisponível; consulta segue sem foto");
            return Optional.empty();
        }
    }
}
