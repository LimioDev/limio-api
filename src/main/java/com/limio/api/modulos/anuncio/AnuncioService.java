package com.limio.api.modulos.anuncio;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import com.limio.api.comum.base.BaseService;
import com.limio.api.modulos.anuncio.enums.CausaEncerramento;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;

/** Camada técnica de persistência de {@link Anuncio} — CRUD genérico (herdado) + finder da entity. */
@Component
public class AnuncioService extends BaseService<Anuncio, UUID, AnuncioRepository> {

    /** Derivado de {@link StatusAnuncio#isAberto()} — fonte única, não duplica a lista aqui. */
    private static final List<StatusAnuncio> STATUS_ABERTOS =
            Arrays.stream(StatusAnuncio.values()).filter(StatusAnuncio::isAberto).toList();

    /** Mais conservador que 1° de latitude real (≈110,6 km): a caixa envolvente nunca corta anúncio válido. */
    private static final double KM_POR_GRAU = 110.0;

    /** Valor qualquer pro {@code IN} quando não há filtro de categoria (a query o ignora com o boolean ao lado). */
    private static final Set<UUID> SEM_CATEGORIA = Set.of(new UUID(0, 0));

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

    /** Mesma busca que {@link #buscarPorId}, em lote (evita N+1). Id sem anúncio fica de fora. */
    public List<Anuncio> buscarPorIds(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return repository.findAllById(ids);
    }

    /** Quantos anúncios {@code contratanteId} já concluiu como contratante (histórico do perfil público, ETI-105). */
    public long contarConcluidosDoContratante(UUID contratanteId) {
        return repository.countByContratanteIdAndStatus(contratanteId, StatusAnuncio.CONCLUIDO);
    }

    private static final List<StatusAnuncio> STATUS_FINAIS = List.of(StatusAnuncio.CONCLUIDO, StatusAnuncio.ENCERRADO);

    private static final List<CausaEncerramento> TODAS_AS_CAUSAS = List.of(CausaEncerramento.values());

    public Page<Anuncio> buscarHistoricoDoContratante(UUID contratanteId, CriterioDoHistorico criterio, int pagina,
            int tamanho) {
        Instant de = criterio.de() != null ? criterio.de() : Instant.EPOCH;
        Instant ate = criterio.ate() != null ? criterio.ate() : Instant.EPOCH;
        return repository.buscarHistoricoDoContratante(contratanteId, statusFinais(criterio),
                causasAceitas(criterio), criterio.de() == null, de, criterio.ate() == null, ate,
                PageRequest.of(pagina, tamanho));
    }

    public Page<Anuncio> buscarHistoricoDoPrestador(UUID prestadorId, CriterioDoHistorico criterio, int pagina,
            int tamanho) {
        Instant de = criterio.de() != null ? criterio.de() : Instant.EPOCH;
        Instant ate = criterio.ate() != null ? criterio.ate() : Instant.EPOCH;
        return repository.buscarHistoricoDoPrestador(prestadorId, statusFinais(criterio),
                causasAceitas(criterio), criterio.de() == null, de, criterio.ate() == null, ate,
                PageRequest.of(pagina, tamanho));
    }

    private static List<StatusAnuncio> statusFinais(CriterioDoHistorico criterio) {
        if (criterio.status() == null) {
            return STATUS_FINAIS;
        }
        return switch (criterio.status()) {
            case CONCLUIDO -> List.of(StatusAnuncio.CONCLUIDO);
            case CANCELADO, EXPIRADO -> List.of(StatusAnuncio.ENCERRADO);
        };
    }

    private static List<CausaEncerramento> causasAceitas(CriterioDoHistorico criterio) {
        if (criterio.status() == null) {
            return TODAS_AS_CAUSAS;
        }
        return switch (criterio.status()) {
            case CONCLUIDO -> TODAS_AS_CAUSAS;
            case CANCELADO -> List.of(CausaEncerramento.CANCELAMENTO);
            case EXPIRADO -> List.of(CausaEncerramento.EXPIRACAO);
        };
    }

    public List<UUID> buscarIdsParaExpirar(Instant agora, int lote) {
        return repository.buscarIdsParaExpirar(agora, lote);
    }

    /**
     * Página do mural: anúncios PUBLICADO, sem candidatura ESCOLHIDA, de outro contratante, compatíveis com
     * o {@code criterio} (especialidade/"Outros" e raio) e dentro dos filtros opcionais. Distância, filtros,
     * ordenação e total saem do banco. Sem especialidades não há o que casar: página vazia, sem consulta.
     * O {@code pageable} não deve trazer {@code Sort} — a ordem é fixa na query.
     */
    public Page<AnuncioDoMuralProjecao> buscarMural(CriterioDoMural criterio, Pageable pageable) {
        if (criterio.especialidadeIds().isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }
        double deltaLatitude = criterio.raioKm() / KM_POR_GRAU;
        double deltaLongitude = criterio.raioKm()
                / (KM_POR_GRAU * Math.max(Math.cos(Math.toRadians(criterio.latitudeBase())), 0.01));
        boolean semFiltroCategoria = criterio.categoriasFiltro().isEmpty();
        Collection<UUID> categoriasFiltro = semFiltroCategoria ? SEM_CATEGORIA : criterio.categoriasFiltro();
        return repository.buscarMural(criterio.prestadorId(), criterio.especialidadeIds(),
                criterio.categoriaOutrosId(), criterio.possuiOutros(),
                criterio.latitudeBase(), criterio.longitudeBase(),
                criterio.latitudeBase() - deltaLatitude, criterio.latitudeBase() + deltaLatitude,
                criterio.longitudeBase() - deltaLongitude, criterio.longitudeBase() + deltaLongitude,
                criterio.raioKm(), semFiltroCategoria, categoriasFiltro, criterio.de(),
                criterio.ate() == null, criterio.ate() != null ? criterio.ate() : criterio.de(), pageable);
    }
}
