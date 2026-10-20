package com.limio.api.modulos.anuncio.aplicacao;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.limio.api.comum.base.BaseService;
import com.limio.api.modulos.anuncio.enums.StatusAplicacao;

/** Camada técnica de persistência de {@link Aplicacao} — CRUD genérico (herdado) + finders da entity. */
@Component
public class AplicacaoService extends BaseService<Aplicacao, UUID, AplicacaoRepository> {

    private static final List<StatusAplicacao> STATUS_ABERTOS =
            List.of(StatusAplicacao.ENVIADA, StatusAplicacao.ESCOLHIDA, StatusAplicacao.AGUARDANDO_VALIDACAO);

    public AplicacaoService(AplicacaoRepository repository) {
        super(repository);
    }

    /** Candidaturas ainda abertas (não recusadas/concluídas/encerradas) em que {@code usuarioId} é o prestador. */
    public List<Aplicacao> buscarAbertasDoPrestador(UUID usuarioId) {
        return repository.findByPrestadorIdAndStatusIn(usuarioId, STATUS_ABERTOS);
    }

    /** A candidatura escolhida de um anúncio, se houver — pra saber quem avisar quando ele encerra. */
    public Optional<Aplicacao> buscarEscolhidaDoAnuncio(UUID anuncioId) {
        return repository.findByAnuncioIdAndStatus(anuncioId, StatusAplicacao.ESCOLHIDA);
    }
}
