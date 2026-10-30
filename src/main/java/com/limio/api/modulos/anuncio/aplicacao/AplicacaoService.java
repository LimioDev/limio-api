package com.limio.api.modulos.anuncio.aplicacao;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.limio.api.comum.base.BaseService;
import com.limio.api.modulos.anuncio.enums.StatusAplicacao;

/** Camada técnica de persistência de {@link Aplicacao} — CRUD genérico (herdado) + finders da entity. */
@Component
public class AplicacaoService extends BaseService<Aplicacao, UUID, AplicacaoRepository> {

    private static final List<StatusAplicacao> STATUS_ABERTOS =
            List.of(StatusAplicacao.ENVIADA, StatusAplicacao.ESCOLHIDA, StatusAplicacao.AGUARDANDO_VALIDACAO);

    /** Derivado de {@link StatusAplicacao#isEscolhida()} — fonte única, não duplica a lista aqui. */
    private static final List<StatusAplicacao> STATUS_ESCOLHIDA =
            Arrays.stream(StatusAplicacao.values()).filter(StatusAplicacao::isEscolhida).toList();

    public AplicacaoService(AplicacaoRepository repository) {
        super(repository);
    }

    /** Algum prestador já foi escolhido pro anúncio (ver {@link Aplicacao#isEscolhida()}). */
    public boolean existeEscolhida(UUID anuncioId) {
        return repository.existsByAnuncioIdAndStatusIn(anuncioId, STATUS_ESCOLHIDA);
    }

    /** Candidaturas ainda sem resposta do Empregador, da mais antiga pra mais nova. */
    public List<Aplicacao> buscarEnviadasDoAnuncio(UUID anuncioId) {
        return repository.findByAnuncioIdAndStatusOrderByDataAplicacaoAscIdAsc(anuncioId, StatusAplicacao.ENVIADA);
    }

    /** Candidaturas ainda abertas (não recusadas/concluídas/encerradas) em que {@code usuarioId} é o prestador. */
    public List<Aplicacao> buscarAbertasDoPrestador(UUID usuarioId) {
        return repository.findByPrestadorIdAndStatusIn(usuarioId, STATUS_ABERTOS);
    }

    /**
     * Candidaturas de {@code prestadorId} escolhidas no período ({@code escolhidaEm} entre {@code inicio}, incluso, e
     * {@code fimExclusivo}), qualquer que seja o status atual, da escolha mais recente pra mais antiga.
     */
    public List<Aplicacao> buscarEscolhidasDoPrestadorEntre(UUID prestadorId, Instant inicio, Instant fimExclusivo) {
        return repository.findByPrestadorIdAndEscolhidaEmGreaterThanEqualAndEscolhidaEmLessThanOrderByEscolhidaEmDesc(
                prestadorId, inicio, fimExclusivo);
    }

    /**
     * Todas as candidaturas ainda abertas de um anúncio (inclui {@code
     * ENVIADA} e {@code AGUARDANDO_VALIDACAO}, não só {@code ESCOLHIDA}) —
     * pra fechar e avisar todo mundo quando o anúncio encerra.
     */
    public List<Aplicacao> buscarAbertasDoAnuncio(UUID anuncioId) {
        return repository.findByAnuncioIdAndStatusIn(anuncioId, STATUS_ABERTOS);
    }

    /** Mesma busca que {@link #buscarAbertasDoAnuncio}, em lote pra vários anúncios de uma vez (evita N+1). */
    public List<Aplicacao> buscarAbertasDosAnuncios(List<UUID> anuncioIds) {
        if (anuncioIds.isEmpty()) {
            return List.of();
        }
        return repository.findByAnuncioIdInAndStatusIn(anuncioIds, STATUS_ABERTOS);
    }
}
