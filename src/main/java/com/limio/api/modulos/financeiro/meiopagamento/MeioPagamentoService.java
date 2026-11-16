package com.limio.api.modulos.financeiro.meiopagamento;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.limio.api.comum.base.BaseService;

/** Camada técnica de persistência de {@link MeioPagamento} — CRUD genérico (herdado) + upsert por usuário. */
@Component
public class MeioPagamentoService extends BaseService<MeioPagamento, UUID, MeioPagamentoRepository> {

    public MeioPagamentoService(MeioPagamentoRepository repository) {
        super(repository);
    }

    public Optional<MeioPagamento> buscarPorUsuario(UUID usuarioId) {
        return repository.findByUsuarioId(usuarioId);
    }

    /** Cria o meio de pagamento do usuário ou substitui o token do que já existia. */
    public MeioPagamento salvarOuSubstituir(UUID usuarioId, String gatewayToken, String bandeira, String ultimos4) {
        MeioPagamento meioPagamento = repository.findByUsuarioId(usuarioId)
                .map(existente -> {
                    existente.substituirToken(gatewayToken, bandeira, ultimos4);
                    return existente;
                })
                .orElseGet(() -> MeioPagamento.novo(usuarioId, gatewayToken, bandeira, ultimos4));
        return repository.save(meioPagamento);
    }
}
