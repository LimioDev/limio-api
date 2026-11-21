package com.limio.api.modulos.moderacao.actions.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
import com.limio.api.comum.portas.SituacaoAvaliacao;
import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.comum.texto.NomeHelper;
import com.limio.api.modulos.moderacao.actions.helper.JanelaAvaliacaoHelper;
import com.limio.api.modulos.moderacao.avaliacao.Avaliacao;
import com.limio.api.modulos.moderacao.avaliacao.AvaliacaoRepository;
import com.limio.api.modulos.moderacao.avaliacao.AvaliacaoService;
import com.limio.api.modulos.moderacao.avaliacao.ResumoAvaliacaoProjecao;

/**
 * Implementa {@link ConsultaAvaliacoes} para os candidatos do anúncio ({@code modulos.anuncio}, ETI-67) e o perfil
 * público ({@code modulos.perfil}, ETI-105), e a situação de avaliação por serviço no histórico (ETI-71). Avaliador com conta encerrada aparece como
 * {@link NomeHelper#CONTA_ENCERRADA} em vez de desaparecer da lista — a nota e o comentário continuam valendo.
 */
@Component
public class ConsultaAvaliacoesService implements ConsultaAvaliacoes {

    private final AvaliacaoRepository avaliacaoRepository;
    private final ConsultaDadosUsuario consultaDadosUsuario;
    private final Clock relogio;

    public ConsultaAvaliacoesService(AvaliacaoRepository avaliacaoRepository,
            ConsultaDadosUsuario consultaDadosUsuario, Clock relogio) {
        this.avaliacaoRepository = avaliacaoRepository;
        this.consultaDadosUsuario = consultaDadosUsuario;
        this.relogio = relogio;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, SituacaoAvaliacao> situacaoPorServico(UUID usuarioId, Map<UUID, Instant> concluidoEmPorAnuncio) {
        if (concluidoEmPorAnuncio.isEmpty()) {
            return Map.of();
        }
        Set<UUID> anuncioIds = concluidoEmPorAnuncio.keySet();
        Map<UUID, Integer> notasRecebidas = avaliacaoRepository.findByAvaliadoIdAndAnuncioIdIn(usuarioId, anuncioIds)
                .stream().collect(Collectors.toMap(Avaliacao::getAnuncioId, Avaliacao::getNota));
        Set<UUID> jaAvaliados = Set.copyOf(avaliacaoRepository.findAnuncioIdsAvaliadosPor(usuarioId, anuncioIds));
        Instant agora = Instant.now(relogio);

        Map<UUID, SituacaoAvaliacao> situacoes = new HashMap<>();
        concluidoEmPorAnuncio.forEach((anuncioId, concluidoEm) -> {
            boolean podeAvaliar = concluidoEm != null && JanelaAvaliacaoHelper.aberta(concluidoEm, agora)
                    && !jaAvaliados.contains(anuncioId);
            situacoes.put(anuncioId, new SituacaoAvaliacao(notasRecebidas.get(anuncioId), podeAvaliar));
        });
        return situacoes;
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
