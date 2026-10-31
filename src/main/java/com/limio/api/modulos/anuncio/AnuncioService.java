package com.limio.api.modulos.anuncio;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.limio.api.comum.base.BaseService;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;

/** Camada técnica de persistência de {@link Anuncio} — CRUD genérico (herdado) + finder da entity. */
@Component
public class AnuncioService extends BaseService<Anuncio, UUID, AnuncioRepository> {

    /** Derivado de {@link StatusAnuncio#isAberto()} — fonte única, não duplica a lista aqui. */
    private static final List<StatusAnuncio> STATUS_ABERTOS =
            Arrays.stream(StatusAnuncio.values()).filter(StatusAnuncio::isAberto).toList();

    public AnuncioService(AnuncioRepository repository) {
        super(repository);
    }

    /** Anúncios ainda abertos (não concluídos nem encerrados) em que {@code usuarioId} é o contratante. */
    public List<Anuncio> buscarAbertosDoContratante(UUID usuarioId) {
        return repository.findByContratanteIdAndStatusIn(usuarioId, STATUS_ABERTOS);
    }

    /** Mesma busca que {@link #buscarAbertosDoContratante}, da data pretendida mais próxima pra mais distante. */
    public List<Anuncio> buscarAbertosDoContratantePorData(UUID usuarioId) {
        return repository.findByContratanteIdAndStatusInOrderByDataHoraPretendidaAsc(usuarioId, STATUS_ABERTOS);
    }

    /**
     * Busca que não lança — {@code anuncio_id} em {@code Aplicacao}/{@code
     * Reagendamento} não tem FK (ADR-0001 §1.6), então um anúncio ausente é
     * um cenário possível, não excepcional. Quem chama decide o que fazer.
     */
    public Optional<Anuncio> buscarPorId(UUID id) {
        return repository.findById(id);
    }

    /**
     * Mesma busca que {@link #buscarPorId}, mas travando a linha até o fim da transação de quem chama. Exige
     * transação aberta.
     */
    public Optional<Anuncio> buscarParaAtualizar(UUID id) {
        return repository.findByIdParaAtualizar(id);
    }

    /** Mesma busca que {@link #buscarPorId}, em lote (evita N+1). Id sem anúncio fica de fora. */
    public List<Anuncio> buscarPorIds(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return repository.findAllById(ids);
    }
}
