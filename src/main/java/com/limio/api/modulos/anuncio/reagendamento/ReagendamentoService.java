package com.limio.api.modulos.anuncio.reagendamento;

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

    public Optional<Reagendamento> buscarUltimoAceito(UUID anuncioId) {
        return repository.findFirstByAnuncioIdAndStatusOrderByRespondidoEmDesc(anuncioId, StatusReagendamento.ACEITO);
    }

    /** Quantos reagendamentos já foram aceitos pro anúncio (ETI-86); conta contra {@code anuncio.reagendamento.limite}. */
    public int contarAceitos(UUID anuncioId) {
        return (int) repository.countByAnuncioIdAndStatus(anuncioId, StatusReagendamento.ACEITO);
    }

    /** Há proposta aguardando resposta; enquanto houver, a conclusão por silêncio não pode concluir o serviço. */
    public boolean existePendente(UUID anuncioId) {
        return repository.existsByAnuncioIdAndStatus(anuncioId, StatusReagendamento.PENDENTE);
    }

    /** Já houve proposta feita com a data vigente passada, qualquer que tenha sido o desfecho. */
    public boolean existePropostaAposDataVigente(UUID anuncioId) {
        return repository.existsByAnuncioIdAndPropostaAposDataVigenteTrue(anuncioId);
    }
}
