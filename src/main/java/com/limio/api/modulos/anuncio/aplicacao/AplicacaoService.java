package com.limio.api.modulos.anuncio.aplicacao;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import com.limio.api.comum.base.BaseService;
import com.limio.api.modulos.anuncio.enums.AbaCandidatura;
import com.limio.api.modulos.anuncio.enums.StatusAplicacao;

/** Camada técnica de persistência de {@link Aplicacao} — CRUD genérico (herdado) + finders da entity. */
@Component
public class AplicacaoService extends BaseService<Aplicacao, UUID, AplicacaoRepository> {

    private static final List<StatusAplicacao> STATUS_ABERTOS =
            List.of(StatusAplicacao.ENVIADA, StatusAplicacao.ESCOLHIDA, StatusAplicacao.AGUARDANDO_VALIDACAO);

    /** Derivado de {@link StatusAplicacao#isEscolhida()} — fonte única, não duplica a lista aqui. */
    private static final List<StatusAplicacao> STATUS_ESCOLHIDA =
            Arrays.stream(StatusAplicacao.values()).filter(StatusAplicacao::isEscolhida).toList();

    /** Mais recente primeiro; {@code id} desempata pra paginação estável. */
    private static final Sort MAIS_RECENTES = Sort.by(Sort.Order.desc("dataAplicacao"), Sort.Order.desc("id"));

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

    /** Candidaturas do Prestador que caem na {@code aba} (ETI-77), mais recentes primeiro. */
    public Page<Aplicacao> buscarDoPrestadorPorAba(UUID prestadorId, AbaCandidatura aba, int pagina, int tamanho) {
        return repository.findByPrestadorIdAndStatusIn(prestadorId, aba.status(),
                PageRequest.of(pagina, tamanho, MAIS_RECENTES));
    }

    /**
     * Busca com {@code SELECT ... FOR UPDATE}: quem muda o status (desistir, ETI-77) segura a linha até o fim
     * da transação. A escolha do Empregador (card futuro) precisa usar esta mesma busca pra esperar e reler o
     * status atualizado — sem isso as duas gravações podem se sobrescrever.
     */
    public Optional<Aplicacao> buscarParaAtualizar(UUID id) {
        return repository.findByIdParaAtualizar(id);
    }
}
