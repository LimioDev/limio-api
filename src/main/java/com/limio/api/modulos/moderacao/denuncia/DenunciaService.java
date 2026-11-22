package com.limio.api.modulos.moderacao.denuncia;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import com.limio.api.comum.base.BaseService;
import com.limio.api.modulos.moderacao.enums.MotivoDenuncia;
import com.limio.api.modulos.moderacao.enums.StatusResolucao;

/** Camada técnica de persistência de {@link Denuncia} — CRUD genérico (herdado) + finders da entity. */
@Component
public class DenunciaService extends BaseService<Denuncia, UUID, DenunciaRepository> {

    public DenunciaService(DenunciaRepository repository) {
        super(repository);
    }

    /** Grava já: o {@code numero} só existe depois do insert, e a resposta do registro precisa dele. */
    public Denuncia salvarAgora(Denuncia denuncia) {
        return repository.saveAndFlush(denuncia);
    }

    public long contarPorStatus(StatusResolucao status) {
        return repository.countByStatusResolucao(status);
    }

    public Optional<Denuncia> buscarPorId(UUID id) {
        return repository.findById(id);
    }

    public Optional<Denuncia> buscarParaDecidir(UUID id) {
        return repository.findByIdParaDecidir(id);
    }

    /** Fila: só as abertas, com um dos motivos, do prazo mais curto para o mais longo (empate: a mais antiga). */
    public Page<Denuncia> buscarAbertas(Collection<MotivoDenuncia> motivos, int pagina, int tamanho) {
        return repository.findByStatusResolucaoAndMotivoIn(StatusResolucao.ABERTA, motivos,
                PageRequest.of(pagina, tamanho, Sort.by("prazoAnalise", "numero")));
    }

    public List<Denuncia> buscarOutrasContraODenunciado(Denuncia denuncia) {
        return repository.findByDenunciadoIdAndIdNotOrderByCriadoEmDesc(denuncia.getDenunciadoId(), denuncia.getId());
    }
}
