package com.limio.api.modulos.auth.cpfbloqueado;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.base.BaseService;
import com.limio.api.modulos.auth.actions.helper.CpfHasher;

/**
 * Camada técnica de persistência de {@link CpfBloqueado} — CRUD genérico
 * (herdado) + regra do cooldown de 12 meses (UC01/UC10).
 */
@Component
public class CpfBloqueadoService extends BaseService<CpfBloqueado, UUID, CpfBloqueadoRepository> {

    private static final int COOLDOWN_MESES = 12;

    private final CpfHasher cpfHasher;

    public CpfBloqueadoService(CpfBloqueadoRepository repository, CpfHasher cpfHasher) {
        super(repository);
        this.cpfHasher = cpfHasher;
    }

    /** @param agora usado como "ainda bloqueado" se existir registro com {@code liberadoEm} depois dele. */
    public boolean estaBloqueado(String cpf, Instant agora) {
        return repository.existsByCpfHmacAndLiberadoEmAfter(cpfHasher.hash(cpf), agora);
    }

    /** UC10 (encerrar conta): bloqueia o CPF por {@value #COOLDOWN_MESES} meses a partir de {@code agora}. */
    public void bloquear(String cpf, Instant agora) {
        salvar(CpfBloqueado.builder()
                .cpfHmac(cpfHasher.hash(cpf))
                .bloqueadoEm(agora)
                .liberadoEm(agora.atOffset(ZoneOffset.UTC).plusMonths(COOLDOWN_MESES).toInstant())
                .build());
    }

    /** Apaga registros já liberados (rotina diária — ver {@code LimparRetencaoUseCase}). */
    @Transactional
    public void limparLiberados(Instant agora) {
        repository.apagarLiberadosAntesDe(agora);
    }
}
