package com.limio.api.modulos.moderacao.actions.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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
import com.limio.api.modulos.moderacao.avaliacao.ResumoAvaliacaoProjecao;

/**
 * Implementa {@link ConsultaAvaliacoes} para os candidatos do anúncio ({@code modulos.anuncio}, ETI-67) e o perfil
 * público ({@code modulos.perfil}, ETI-105). Avaliador com conta encerrada aparece como "Conta encerrada" (mesmo
 * literal de {@code Usuario.encerrar}/{@code PerfilEmpregador.anonimizar}) em vez de desaparecer da lista — a nota
 * e o comentário continuam valendo.
 */
@Component
public class ConsultaAvaliacoesService implements ConsultaAvaliacoes {

    private static final String AVALIADOR_CONTA_ENCERRADA = "Conta encerrada";

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
        return avaliacaoRepository
                .findByAvaliadoIdAndPapelAvaliadoOrderByPublicadaEmDesc(avaliadoId, papel, PageRequest.of(0, limite))
                .stream()
                .map(this::toRecente)
                .toList();
    }

    /** Mesmo arredondamento (comercial, uma casa) de {@code AvaliacaoService.recalcular}. */
    private static ResumoAvaliacoes resumoDe(ResumoAvaliacaoProjecao projecao) {
        Double media = projecao.getMedia();
        return new ResumoAvaliacoes(projecao.getQtd(),
                media == null ? null : BigDecimal.valueOf(media).setScale(1, RoundingMode.HALF_UP));
    }

    private AvaliacaoRecente toRecente(Avaliacao avaliacao) {
        String nome = consultaDadosUsuario.buscarPorId(avaliacao.getAvaliadorId())
                .map(DadosUsuario::nomeCompleto)
                .map(NomeHelper::abreviar)
                .orElse(AVALIADOR_CONTA_ENCERRADA);
        return new AvaliacaoRecente(nome, avaliacao.getNota(), avaliacao.getComentario(), avaliacao.getPublicadaEm());
    }
}
