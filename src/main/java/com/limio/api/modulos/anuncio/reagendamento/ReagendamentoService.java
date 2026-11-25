package com.limio.api.modulos.anuncio.reagendamento;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.limio.api.comum.base.BaseService;
import com.limio.api.modulos.anuncio.enums.StatusReagendamento;

/** Camada técnica de persistência de {@link Reagendamento} — CRUD genérico (herdado) + finder da entity. */
@Component
public class ReagendamentoService extends BaseService<Reagendamento, UUID, ReagendamentoRepository> {

    public ReagendamentoService(ReagendamentoRepository repository) {
        super(repository);
    }

    public List<Reagendamento> buscarPendentesDosAnuncios(List<UUID> anuncioIds) {
        if (anuncioIds.isEmpty()) {
            return List.of();
        }
        return repository.findByAnuncioIdInAndStatus(anuncioIds, StatusReagendamento.PENDENTE);
    }

    /**
     * O serviço deixou de estar combinado: toda proposta pendente dos anúncios perde o efeito. A data vigente não muda.
     * Mesma assinatura do ponto único que o ETI-99 (#49) introduz; aqui ainda usa o {@code cancelarSemEfeito} da
     * develop, e o #49 troca a implementação pelo estado {@code SEM_EFEITO}.
     */
    public void encerrarPendentesSemEfeito(List<UUID> anuncioIds, Instant agora) {
        for (Reagendamento pendente : buscarPendentesDosAnuncios(anuncioIds)) {
            pendente.cancelarSemEfeito(agora);
            salvar(pendente);
        }
    }

    public Optional<Reagendamento> buscarUltimoAceito(UUID anuncioId) {
        return repository.findFirstByAnuncioIdAndStatusOrderByRespondidoEmDesc(anuncioId, StatusReagendamento.ACEITO);
    }

    /** Quantos reagendamentos já foram aceitos pro anúncio (ETI-86); conta contra o limite de 2 (ETI-99/ETI-100). */
    public int contarAceitos(UUID anuncioId) {
        return repository.findByAnuncioIdInAndStatus(List.of(anuncioId), StatusReagendamento.ACEITO).size();
    }
}
