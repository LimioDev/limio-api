package com.limio.api.modulos.moderacao.actions.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.portas.AvaliacaoRecente;
import com.limio.api.comum.portas.ConsultaAvaliacoes;
import com.limio.api.comum.portas.ConsultaDadosUsuario;
import com.limio.api.comum.portas.DadosUsuario;
import com.limio.api.comum.portas.ResumoAvaliacoes;
import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.comum.texto.NomeHelper;
import com.limio.api.modulos.moderacao.avaliacao.Avaliacao;
import com.limio.api.modulos.moderacao.avaliacao.AvaliacaoRepository;
import com.limio.api.modulos.moderacao.avaliacao.AvaliacaoService;
import com.limio.api.modulos.moderacao.avaliacao.ResumoAvaliacaoProjecao;

/**
 * Implementa {@link ConsultaAvaliacoes} para os candidatos do anúncio ({@code modulos.anuncio}, ETI-67) e o perfil
 * público ({@code modulos.perfil}, ETI-105). Avaliador com conta encerrada aparece como
 * {@link NomeHelper#CONTA_ENCERRADA} em vez de desaparecer da lista — a nota e o comentário continuam valendo.
 */
@Component
public class ConsultaAvaliacoesService implements ConsultaAvaliacoes {

    private final AvaliacaoRepository avaliacaoRepository;
    private final ConsultaDadosUsuario consultaDadosUsuario;

    public ConsultaAvaliacoesService(AvaliacaoRepository avaliacaoRepository,
            ConsultaDadosUsuario consultaDadosUsuario) {
        this.avaliacaoRepository = avaliacaoRepository;
        this.consultaDadosUsuario = consultaDadosUsuario;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, ResumoAvaliacoes> resumir(Collection<UUID> prestadorIds) {
        if (prestadorIds.isEmpty()) {
            return Map.of();
        }
        return avaliacaoRepository.resumirPorAvaliados(prestadorIds, PapelUsuario.PRESTADOR).stream()
                .collect(Collectors.toMap(ResumoAvaliacaoProjecao::getAvaliadoId, ConsultaAvaliacoesService::resumoDe));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AvaliacaoRecente> listarRecentes(UUID avaliadoId, PapelUsuario papel, int limite) {
        List<Avaliacao> avaliacoes = avaliacaoRepository
                .findByAvaliadoIdAndPapelAvaliadoOrderByPublicadaEmDesc(avaliadoId, papel, PageRequest.of(0, limite));
        Map<UUID, DadosUsuario> avaliadores = consultaDadosUsuario
                .buscarPorIds(avaliacoes.stream().map(Avaliacao::getAvaliadorId).toList());
        return avaliacoes.stream().map(avaliacao -> toRecente(avaliacao, avaliadores)).toList();
    }

    private static ResumoAvaliacoes resumoDe(ResumoAvaliacaoProjecao projecao) {
        return new ResumoAvaliacoes(projecao.getQtd(), AvaliacaoService.arredondar(projecao.getMedia()));
    }

    private static AvaliacaoRecente toRecente(Avaliacao avaliacao, Map<UUID, DadosUsuario> avaliadores) {
        String nome = Optional.ofNullable(avaliadores.get(avaliacao.getAvaliadorId()))
                .map(DadosUsuario::nomeCompleto)
                .map(NomeHelper::abreviar)
                .orElse(NomeHelper.CONTA_ENCERRADA);
        return new AvaliacaoRecente(nome, avaliacao.getNota(), avaliacao.getComentario(), avaliacao.getPublicadaEm());
    }
}
