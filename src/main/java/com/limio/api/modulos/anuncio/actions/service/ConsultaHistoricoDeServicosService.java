package com.limio.api.modulos.anuncio.actions.service;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.portas.ConsultaHistoricoDeServicos;
import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoService;

/** Implementa {@link ConsultaHistoricoDeServicos} para o histórico resumido do perfil público ({@code modulos.perfil}, ETI-105). */
@Component
public class ConsultaHistoricoDeServicosService implements ConsultaHistoricoDeServicos {

    private final AnuncioService anuncioService;
    private final AplicacaoService aplicacaoService;

    public ConsultaHistoricoDeServicosService(AnuncioService anuncioService, AplicacaoService aplicacaoService) {
        this.anuncioService = anuncioService;
        this.aplicacaoService = aplicacaoService;
    }

    @Override
    @Transactional(readOnly = true)
    public int contarConcluidos(UUID usuarioId, PapelUsuario papel) {
        long total = switch (papel) {
            case EMPREGADOR -> anuncioService.contarConcluidosDoContratante(usuarioId);
            case PRESTADOR -> aplicacaoService.contarConcluidasDoPrestador(usuarioId);
        };
        return Math.toIntExact(total);
    }
}
