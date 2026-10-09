package com.limio.api.modulos.auth.verificacaocontato;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.limio.api.comum.base.BaseService;
import com.limio.api.modulos.auth.verificacaocontato.enums.FinalidadeVerificacao;

/** Camada técnica de persistência de {@link VerificacaoContato} — CRUD genérico (herdado) + finders da entity. */
@Component
public class VerificacaoContatoService extends BaseService<VerificacaoContato, UUID, VerificacaoContatoRepository> {

    public VerificacaoContatoService(VerificacaoContatoRepository repository) {
        super(repository);
    }

    public Optional<VerificacaoContato> buscarPorTokenHashEFinalidade(String tokenHash, FinalidadeVerificacao finalidade) {
        return repository.findByTokenHashAndFinalidade(tokenHash, finalidade);
    }

    /** Quantos tokens desta finalidade foram emitidos pro usuário desde {@code desde} — base do rate limit. */
    public long contarDesde(UUID usuarioId, FinalidadeVerificacao finalidade, Instant desde) {
        return repository.countByUsuarioIdAndFinalidadeAndCriadoEmAfter(usuarioId, finalidade, desde);
    }
}
