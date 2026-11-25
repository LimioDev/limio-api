package com.limio.api.modulos.moderacao.denuncia;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.limio.api.comum.base.BaseService;
import com.limio.api.modulos.moderacao.enums.MotivoDenuncia;
import com.limio.api.modulos.moderacao.enums.StatusResolucao;

/** Camada técnica de persistência de {@link Denuncia} — CRUD genérico (herdado) + finders da entity. */
@Component
public class DenunciaService extends BaseService<Denuncia, UUID, DenunciaRepository> {

    /** Derivado de {@link StatusResolucao#isEmAberto()} — fonte única, não duplica a lista aqui. */
    private static final List<StatusResolucao> STATUS_EM_ABERTO =
            Arrays.stream(StatusResolucao.values()).filter(StatusResolucao::isEmAberto).toList();

    public DenunciaService(DenunciaRepository repository) {
        super(repository);
    }

    /** Grava já: o {@code numero} só existe depois do insert, e a resposta do registro precisa dele. */
    public Denuncia salvarAgora(Denuncia denuncia) {
        return repository.saveAndFlush(denuncia);
    }

    /**
     * A denúncia ainda em aberto de {@code denuncianteId} com o mesmo alvo (denunciado e contexto) e o mesmo motivo,
     * se houver. São poucas por par denunciante/denunciado, por isso o contexto é comparado aqui e não na consulta
     * (evita comparar no SQL colunas que podem ser nulas).
     */
    public Optional<Denuncia> buscarEmAbertoIgual(UUID denuncianteId, AlvoDenuncia alvo, MotivoDenuncia motivo) {
        return repository.findByDenuncianteIdAndDenunciadoIdAndStatusResolucaoIn(denuncianteId, alvo.denunciadoId(),
                STATUS_EM_ABERTO).stream()
                .filter(denuncia -> denuncia.repete(alvo, motivo))
                .findFirst();
    }
}
