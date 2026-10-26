package com.limio.api.modulos.auth.tentativalogin;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.base.BaseService;
import com.limio.api.comum.persistencia.TravaTransacional;
import com.limio.api.comum.persistencia.enums.EscopoTrava;

/** Camada técnica de persistência de {@link TentativaLogin} — CRUD genérico (herdado) + finder da entity. */
@Component
public class TentativaLoginService extends BaseService<TentativaLogin, UUID, TentativaLoginRepository> {

    private final TravaTransacional travaTransacional;

    public TentativaLoginService(TentativaLoginRepository repository, TravaTransacional travaTransacional) {
        super(repository);
        this.travaTransacional = travaTransacional;
    }

    /** Instantes das últimas tentativas do e-mail, da mais recente pra mais antiga. */
    public List<Instant> ultimasFalhas(String email, int quantidade) {
        return repository.findByEmailOrderByCriadoEmDesc(email, Limit.of(quantidade)).stream()
                .map(TentativaLogin::getCriadoEm)
                .toList();
    }

    /**
     * Confere o bloqueio e já grava a tentativa, atomicamente por e-mail: com o
     * lock, requisições simultâneas pro mesmo e-mail passam aqui uma de cada vez
     * e cada uma enxerga as anteriores. Sem isso, uma rajada paralela passava
     * inteira pela checagem antes de qualquer falha ser gravada.
     *
     * @return vazio se {@code bloqueado} disser que o e-mail está bloqueado (nada é gravado)
     */
    @Transactional
    public Optional<TentativaLogin> registrarSeLiberado(String email, String ip, int quantidade,
            Predicate<List<Instant>> bloqueado) {
        travaTransacional.travar(EscopoTrava.LOGIN, email);
        if (bloqueado.test(ultimasFalhas(email, quantidade))) {
            return Optional.empty();
        }
        return Optional.of(repository.save(TentativaLogin.builder().email(email).ip(ip).build()));
    }

    /** Apaga falhas de login mais antigas que a retenção (rotina diária — ver {@code LimparRetencaoUseCase}). */
    @Transactional
    public void limparAntigas(Instant limite) {
        repository.apagarAntesDe(limite);
    }
}
