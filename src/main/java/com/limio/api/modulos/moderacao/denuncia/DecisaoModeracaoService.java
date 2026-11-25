package com.limio.api.modulos.moderacao.denuncia;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.limio.api.comum.base.BaseService;

/**
 * Camada técnica de persistência de {@link DecisaoModeracao} e das {@link Sancao}s que ela aplica — as duas só existem
 * juntas, na decisão de uma denúncia (ETI-116).
 */
@Component
public class DecisaoModeracaoService extends BaseService<DecisaoModeracao, UUID, DecisaoModeracaoRepository> {

    private final SancaoRepository sancaoRepository;

    public DecisaoModeracaoService(DecisaoModeracaoRepository repository, SancaoRepository sancaoRepository) {
        super(repository);
        this.sancaoRepository = sancaoRepository;
    }

    /** Grava já: a sanção aponta para o id da decisão. */
    public DecisaoModeracao salvarAgora(DecisaoModeracao decisao) {
        return repository.saveAndFlush(decisao);
    }

    public Sancao salvarSancao(Sancao sancao) {
        return sancaoRepository.save(sancao);
    }

    public Optional<DecisaoModeracao> buscarDaDenuncia(UUID denunciaId) {
        return repository.findByDenunciaId(denunciaId);
    }

    public List<Sancao> buscarSancoesDaDecisao(UUID decisaoId) {
        return sancaoRepository.findByDecisaoId(decisaoId);
    }

    /** Histórico do denunciado, da mais recente para a mais antiga. */
    public List<Sancao> buscarSancoesDoUsuario(UUID usuarioId) {
        return sancaoRepository.findByUsuarioIdOrderByAplicadaEmDesc(usuarioId);
    }
}
