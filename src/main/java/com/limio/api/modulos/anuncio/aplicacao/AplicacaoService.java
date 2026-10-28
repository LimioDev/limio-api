package com.limio.api.modulos.anuncio.aplicacao;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import com.limio.api.comum.base.BaseService;
import com.limio.api.modulos.anuncio.enums.StatusAplicacao;
import com.limio.api.modulos.anuncio.excecao.CandidaturaDuplicadaException;

/** Camada técnica de persistência de {@link Aplicacao} — CRUD genérico (herdado) + finders da entity. */
@Component
public class AplicacaoService extends BaseService<Aplicacao, UUID, AplicacaoRepository> {

    private static final List<StatusAplicacao> STATUS_ABERTOS =
            List.of(StatusAplicacao.ENVIADA, StatusAplicacao.ESCOLHIDA, StatusAplicacao.AGUARDANDO_VALIDACAO);

    private static final List<StatusAplicacao> STATUS_ESCOLHIDA =
            List.of(StatusAplicacao.ESCOLHIDA, StatusAplicacao.AGUARDANDO_VALIDACAO);

    private static final String CONSTRAINT_PAR_ATIVO_UNICO = "uk_aplicacao_par_ativo";

    public AplicacaoService(AplicacaoRepository repository) {
        super(repository);
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

    /** O anúncio já tem alguém escolhido (combinado ou aguardando validação) — não aceita nova candidatura (ETI-75). */
    public boolean temCandidaturaEscolhida(UUID anuncioId) {
        return repository.existsByAnuncioIdAndStatusIn(anuncioId, STATUS_ESCOLHIDA);
    }

    /** Já existe candidatura ativa deste Prestador pra este anúncio — recusa de duplicata (ETI-75). */
    public boolean existeAbertaDoPar(UUID anuncioId, UUID prestadorId) {
        return repository.existsByAnuncioIdAndPrestadorIdAndStatusIn(anuncioId, prestadorId, STATUS_ABERTOS);
    }

    /** Quantas vezes {@code prestadorId} foi escolhido entre {@code inicio} (incluso) e {@code fimExclusivo}. */
    public long contarEscolhidasNoPeriodo(UUID prestadorId, Instant inicio, Instant fimExclusivo) {
        return repository.countByPrestadorIdAndEscolhidaEmGreaterThanEqualAndEscolhidaEmLessThan(prestadorId, inicio,
                fimExclusivo);
    }

    /**
     * {@code salvar} de uma candidatura nova: a checagem de duplicata do usecase já cobre o caso normal,
     * mas clique duplo/corrida pode passar os dois pela checagem antes de qualquer insert — só o índice
     * único {@code uk_aplicacao_par_ativo} garante sob concorrência (ETI-75, mesmo idioma de
     * {@code EnderecoSalvoService.salvar}: {@code saveAndFlush} pra a violação estourar aqui, não só no
     * fim da transação).
     */
    public Aplicacao salvarCandidatura(Aplicacao aplicacao) {
        try {
            return repository.saveAndFlush(aplicacao);
        } catch (DataIntegrityViolationException e) {
            if (violaParAtivoUnico(e)) {
                throw new CandidaturaDuplicadaException();
            }
            throw e;
        }
    }

    private static boolean violaParAtivoUnico(Throwable erro) {
        for (Throwable causa = erro; causa != null; causa = causa.getCause()) {
            if (causa instanceof ConstraintViolationException violacao
                    && CONSTRAINT_PAR_ATIVO_UNICO.equalsIgnoreCase(violacao.getConstraintName())) {
                return true;
            }
        }
        return false;
    }
}
