package com.limio.api.modulos.auth.verificacaocontato;

import java.time.Instant;
import java.util.List;
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

    /** Dono do token, sem carregar o token. Primeiro passo de quem consome: ver {@link #buscarParaConsumir}. */
    public Optional<UUID> buscarUsuarioIdPorTokenHash(String tokenHash, FinalidadeVerificacao finalidade) {
        return repository.findUsuarioIdByTokenHashAndFinalidade(tokenHash, finalidade);
    }

    /**
     * Token com {@code SELECT ... FOR UPDATE}: dois consumos simultâneos do mesmo link se enfileiram e o
     * segundo já vê {@code consumidoEm}. Quem chama trava a conta antes
     * ({@code UsuarioService.buscarParaAtualizarOuFalhar}) — mesma ordem (conta, depois token) da troca de
     * e-mail, senão as duas podem se travar mutuamente.
     */
    public Optional<VerificacaoContato> buscarParaConsumir(String tokenHash, FinalidadeVerificacao finalidade) {
        return repository.findParaConsumir(tokenHash, finalidade);
    }

    /** Encerramento de conta (UC10): nenhum token da conta tem uso depois, e o {@code destino_hash} identifica o e-mail. */
    public void apagarDoUsuario(UUID usuarioId) {
        repository.apagarDoUsuario(usuarioId);
    }

    /** Quantos tokens desta finalidade foram emitidos pro usuário desde {@code desde} — base do rate limit. */
    public long contarDesde(UUID usuarioId, FinalidadeVerificacao finalidade, Instant desde) {
        return repository.countByUsuarioIdAndFinalidadeAndCriadoEmAfter(usuarioId, finalidade, desde);
    }

    public List<VerificacaoContato> pendentesDoUsuario(UUID usuarioId, FinalidadeVerificacao finalidade) {
        return repository.findByUsuarioIdAndFinalidadeAndConsumidoEmIsNull(usuarioId, finalidade);
    }
}
