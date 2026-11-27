package com.limio.api.modulos.financeiro.assinaturagold.cobranca;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.limio.api.comum.base.BaseService;
import com.limio.api.modulos.financeiro.assinaturagold.cobranca.enums.StatusCobranca;

/** Camada técnica de persistência de {@link Cobranca} — CRUD genérico (herdado) + finders da entity. */
@Component
public class CobrancaService extends BaseService<Cobranca, UUID, CobrancaRepository> {

    public CobrancaService(CobrancaRepository repository) {
        super(repository);
    }

    /**
     * A cobrança de um aviso do gateway: pela chave de idempotência (o {@code externalReference} que o LIMIO mandou)
     * e, sem ela, pelo id da cobrança no gateway.
     */
    public Optional<UUID> buscarIdDoAviso(String chaveIdempotencia, String gatewayCobrancaId) {
        Optional<UUID> pelaChave = chaveIdempotencia == null ? Optional.empty()
                : repository.findIdByChaveIdempotencia(chaveIdempotencia);
        if (pelaChave.isPresent() || gatewayCobrancaId == null) {
            return pelaChave;
        }
        return repository.findIdByGatewayCobrancaId(gatewayCobrancaId);
    }

    public Optional<UUID> buscarAssinaturaId(UUID cobrancaId) {
        return repository.findAssinaturaGoldIdById(cobrancaId);
    }

    /** A cobrança de renovação ainda sem resposta do gateway, se houver (a última, se por acaso houver duas). */
    public Optional<Cobranca> buscarPendente(UUID assinaturaId) {
        return repository.findFirstByAssinaturaGoldIdAndStatusOrderByCriadoEmDesc(assinaturaId,
                StatusCobranca.PENDENTE);
    }

    /** Quantas cobranças do ciclo o gateway já recusou: a próxima tentativa é esse número mais um. */
    public int contarRecusadasDoCiclo(UUID assinaturaId, Instant inicioCicloCobrado) {
        return (int) repository.countByAssinaturaGoldIdAndInicioCicloCobradoAndStatus(assinaturaId,
                inicioCicloCobrado, StatusCobranca.RECUSADA);
    }
}
