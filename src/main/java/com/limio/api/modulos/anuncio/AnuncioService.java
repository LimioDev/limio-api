package com.limio.api.modulos.anuncio;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.limio.api.comum.base.BaseService;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;

/** Camada técnica de persistência de {@link Anuncio} — CRUD genérico (herdado) + finder da entity. */
@Component
public class AnuncioService extends BaseService<Anuncio, UUID, AnuncioRepository> {

    private static final List<StatusAnuncio> STATUS_ABERTOS =
            List.of(StatusAnuncio.PUBLICADO, StatusAnuncio.EM_ANDAMENTO, StatusAnuncio.AGUARDANDO_VALIDACAO);

    public AnuncioService(AnuncioRepository repository) {
        super(repository);
    }

    /** Anúncios ainda abertos (não concluídos nem encerrados) em que {@code usuarioId} é o contratante. */
    public List<Anuncio> buscarAbertosDoContratante(UUID usuarioId) {
        return repository.findByContratanteIdAndStatusIn(usuarioId, STATUS_ABERTOS);
    }
}
