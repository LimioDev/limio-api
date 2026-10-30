package com.limio.api.modulos.anuncio.actions.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.portas.ConsultaEscolhasDoPrestador;
import com.limio.api.comum.portas.EscolhaDoPrestador;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.aplicacao.Aplicacao;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoService;

/**
 * Implementa {@link ConsultaEscolhasDoPrestador} para a lista "Onde você usou sua cota" ({@code modulos.perfil},
 * US24). Candidatura cujo anúncio sumiu ({@code anuncio_id} sem FK, ADR-0001 §1.6) fica de fora.
 */
@Component
public class ConsultaEscolhasDoPrestadorService implements ConsultaEscolhasDoPrestador {

    private final AplicacaoService aplicacaoService;
    private final AnuncioService anuncioService;

    public ConsultaEscolhasDoPrestadorService(AplicacaoService aplicacaoService, AnuncioService anuncioService) {
        this.aplicacaoService = aplicacaoService;
        this.anuncioService = anuncioService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<EscolhaDoPrestador> listarEscolhidasEntre(UUID prestadorId, Instant inicio, Instant fimExclusivo) {
        List<Aplicacao> escolhidas = aplicacaoService.buscarEscolhidasDoPrestadorEntre(prestadorId, inicio,
                fimExclusivo);
        Map<UUID, Anuncio> anuncios = anuncioService.buscarPorIds(escolhidas.stream().map(Aplicacao::getAnuncioId)
                .distinct().toList()).stream().collect(Collectors.toMap(Anuncio::getId, Function.identity()));

        return escolhidas.stream()
                .filter(aplicacao -> anuncios.containsKey(aplicacao.getAnuncioId()))
                .map(aplicacao -> escolha(aplicacao, anuncios.get(aplicacao.getAnuncioId())))
                .toList();
    }

    private static EscolhaDoPrestador escolha(Aplicacao aplicacao, Anuncio anuncio) {
        return new EscolhaDoPrestador(anuncio.getId(), anuncio.getTitulo(), anuncio.getDataHoraPretendida(),
                aplicacao.getEscolhidaEm(), aplicacao.getStatus().name());
    }
}
