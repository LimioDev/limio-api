package com.limio.api.modulos.anuncio.actions.usecase;

import static java.util.stream.Collectors.toSet;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.portas.ConsultaAvaliacoes;
import com.limio.api.comum.portas.ConsultaDadosUsuario;
import com.limio.api.comum.portas.DadosUsuario;
import com.limio.api.comum.portas.SituacaoAvaliacao;
import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.comum.texto.NomeHelper;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.CriterioDoHistorico;
import com.limio.api.modulos.anuncio.ItemHistorico;
import com.limio.api.modulos.anuncio.actions.helper.PeriodoHistoricoHelper;
import com.limio.api.modulos.anuncio.actions.helper.PeriodoHistoricoHelper.Periodo;
import com.limio.api.modulos.anuncio.aplicacao.Aplicacao;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoService;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;
import com.limio.api.modulos.anuncio.enums.StatusHistorico;

/**
 * Histórico de anúncios e serviços do usuário logado, com filtros (ETI-71, UC23). O papel ativo do token decide a
 * visão: o Empregador vê os anúncios dele; o Prestador, os anúncios em que ele se candidatou. Só estados finais
 * (concluído, cancelado, expirado), do desfecho mais recente para o mais antigo. O escopo é sempre o id do token:
 * não existe id vindo do cliente.
 *
 * <p>Por página: uma consulta de anúncios, uma de candidaturas, uma de nomes e duas de avaliações, sem N+1. A nota
 * recebida e a janela de 7 dias vêm da porta {@link ConsultaAvaliacoes}; a regra continua só na moderação.
 *
 * <p>Nome da contraparte: quem foi escolhido vê o nome completo do outro; o Prestador que não foi escolhido vê o do
 * Empregador abreviado ("Maria S."), como no mural. Conta encerrada aparece como {@link NomeHelper#CONTA_ENCERRADA}.
 */
@Service
public class ListarHistoricoUseCase {

    private static final Logger log = LoggerFactory.getLogger(ListarHistoricoUseCase.class);

    private final AnuncioService anuncioService;
    private final AplicacaoService aplicacaoService;
    private final ConsultaDadosUsuario consultaDadosUsuario;
    private final ConsultaAvaliacoes consultaAvaliacoes;

    public ListarHistoricoUseCase(AnuncioService anuncioService, AplicacaoService aplicacaoService,
            ConsultaDadosUsuario consultaDadosUsuario, ConsultaAvaliacoes consultaAvaliacoes) {
        this.anuncioService = anuncioService;
        this.aplicacaoService = aplicacaoService;
        this.consultaDadosUsuario = consultaDadosUsuario;
        this.consultaAvaliacoes = consultaAvaliacoes;
    }

    @Transactional(readOnly = true)
    public Page<ItemHistorico> executar(UUID usuarioId, PapelUsuario papel, StatusHistorico status,
            LocalDate dataInicio, LocalDate dataFim, int pagina, int tamanho) {
        Periodo periodo = PeriodoHistoricoHelper.de(dataInicio, dataFim);
        CriterioDoHistorico criterio = new CriterioDoHistorico(status, periodo.de(), periodo.ate());
        return switch (papel) {
            case EMPREGADOR -> doEmpregador(usuarioId, criterio, pagina, tamanho);
            case PRESTADOR -> doPrestador(usuarioId, criterio, pagina, tamanho);
        };
    }

    private Page<ItemHistorico> doEmpregador(UUID empregadorId, CriterioDoHistorico criterio, int pagina,
            int tamanho) {
        Page<Anuncio> anuncios = anuncioService.buscarHistoricoDoContratante(empregadorId, criterio, pagina,
                tamanho);
        Map<UUID, UUID> escolhidoPorAnuncio = prestadorEscolhidoPorAnuncio(anuncios.getContent());
        Map<UUID, DadosUsuario> prestadores = consultaDadosUsuario
                .buscarPorIds(Set.copyOf(escolhidoPorAnuncio.values()));
        Map<UUID, SituacaoAvaliacao> avaliacoes = situacoes(empregadorId, anuncios.getContent(),
                escolhidoPorAnuncio::containsKey);

        return montar(anuncios, anuncio -> {
            UUID prestadorId = escolhidoPorAnuncio.get(anuncio.getId());
            return prestadorId == null ? null : nomeCompleto(prestadores.get(prestadorId));
        }, avaliacoes);
    }

    private Page<ItemHistorico> doPrestador(UUID prestadorId, CriterioDoHistorico criterio, int pagina,
            int tamanho) {
        Page<Anuncio> anuncios = anuncioService.buscarHistoricoDoPrestador(prestadorId, criterio, pagina, tamanho);
        Set<UUID> anunciosEmQueFoiEscolhido = prestadorEscolhidoPorAnuncio(anuncios.getContent()).entrySet().stream()
                .filter(escolha -> escolha.getValue().equals(prestadorId))
                .map(Map.Entry::getKey)
                .collect(toSet());
        Map<UUID, DadosUsuario> empregadores = consultaDadosUsuario.buscarPorIds(
                anuncios.stream().map(Anuncio::getContratanteId).collect(toSet()));
        Map<UUID, SituacaoAvaliacao> avaliacoes = situacoes(prestadorId, anuncios.getContent(),
                anunciosEmQueFoiEscolhido::contains);

        return montar(anuncios, anuncio -> {
            DadosUsuario empregador = empregadores.get(anuncio.getContratanteId());
            if (empregador != null && !anunciosEmQueFoiEscolhido.contains(anuncio.getId())) {
                return NomeHelper.abreviar(empregador.nomeCompleto());
            }
            return nomeCompleto(empregador);
        }, avaliacoes);
    }

    /** Regra única das duas visões: com mais de um escolhido no mesmo anúncio, vale o mais recente. */
    private Map<UUID, UUID> prestadorEscolhidoPorAnuncio(Collection<Anuncio> anuncios) {
        List<Aplicacao> escolhidas = aplicacaoService
                .buscarEscolhidasDosAnuncios(anuncios.stream().map(Anuncio::getId).toList());
        Map<UUID, UUID> prestadorPorAnuncio = new HashMap<>();
        escolhidas.stream().sorted(Comparator.comparing(Aplicacao::getEscolhidaEm))
                .forEach(aplicacao -> prestadorPorAnuncio.put(aplicacao.getAnuncioId(), aplicacao.getPrestadorId()));
        return prestadorPorAnuncio;
    }

    private Map<UUID, SituacaoAvaliacao> situacoes(UUID usuarioId, Collection<Anuncio> anuncios,
            Predicate<UUID> participou) {
        Map<UUID, Instant> concluidoEm = new HashMap<>();
        for (Anuncio anuncio : anuncios) {
            if (anuncio.getStatus() == StatusAnuncio.CONCLUIDO
                    && participou.test(anuncio.getId())) {
                concluidoEm.put(anuncio.getId(), anuncio.concluidoEm());
            }
        }
        return concluidoEm.isEmpty() ? Map.of() : consultaAvaliacoes.situacaoPorServico(usuarioId, concluidoEm);
    }

    private static String nomeCompleto(DadosUsuario dados) {
        return dados == null ? NomeHelper.CONTA_ENCERRADA : dados.nomeCompleto();
    }

    private Page<ItemHistorico> montar(Page<Anuncio> anuncios,
            Function<Anuncio, String> outraParte, Map<UUID, SituacaoAvaliacao> avaliacoes) {
        List<ItemHistorico> itens = anuncios.stream()
                .map(anuncio -> montar(anuncio, outraParte.apply(anuncio), avaliacoes.get(anuncio.getId())))
                .flatMap(Optional::stream)
                .toList();
        return new PageImpl<>(itens, anuncios.getPageable(), anuncios.getTotalElements());
    }

    private Optional<ItemHistorico> montar(Anuncio anuncio, String outraParte, SituacaoAvaliacao avaliacao) {
        Optional<StatusHistorico> status = StatusHistorico.de(anuncio.getStatus(), anuncio.getCausaEncerramento());
        if (status.isEmpty()) {
            log.warn("anúncio {} em {} sem causa de encerramento; fora do histórico", anuncio.getId(),
                    anuncio.getStatus());
        }
        return status.map(s -> new ItemHistorico(anuncio, s, outraParte, avaliacao));
    }
}
