package com.limio.api.modulos.auth.usuario;

import java.util.Optional;
import java.util.UUID;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import com.limio.api.comum.base.BaseService;
import com.limio.api.comum.excecao.EntidadeNaoEncontradaException;
import com.limio.api.modulos.auth.excecao.ContatoNaoPermitidoException;

/** Camada técnica de persistência de {@link Usuario} — CRUD genérico (herdado) + finders da entity. */
@Component
public class UsuarioService extends BaseService<Usuario, UUID, UsuarioRepository> {

    private static final String CONSTRAINT_EMAIL_UNICO = "uk_usuario_email";

    public UsuarioService(UsuarioRepository repository) {
        super(repository);
    }

    public Optional<Usuario> buscarPorId(UUID id) {
        return repository.findById(id);
    }

    public Usuario buscarParaAtualizarOuFalhar(UUID id) {
        return repository.findByIdParaAtualizar(id).orElseThrow(EntidadeNaoEncontradaException::new);
    }

    public Usuario salvarComEmailUnico(Usuario usuario) {
        try {
            return repository.saveAndFlush(usuario);
        } catch (DataIntegrityViolationException e) {
            if (violaUnicidadeDeEmail(e)) {
                throw new ContatoNaoPermitidoException();
            }
            throw e;
        }
    }

    private static boolean violaUnicidadeDeEmail(Throwable erro) {
        for (Throwable causa = erro; causa != null; causa = causa.getCause()) {
            if (causa instanceof ConstraintViolationException violacao
                    && CONSTRAINT_EMAIL_UNICO.equalsIgnoreCase(violacao.getConstraintName())) {
                return true;
            }
            if (causa.getMessage() != null && causa.getMessage().contains(CONSTRAINT_EMAIL_UNICO)) {
                return true;
            }
        }
        return false;
    }

    /** Vazio é fluxo normal no login (e-mail inexistente vira credencial inválida), por isso não lança. */
    public Optional<Usuario> buscarPorEmail(String email) {
        return repository.findByEmailIgnoreCase(email);
    }

    public boolean existePorEmail(String email) {
        return repository.existsByEmailIgnoreCase(email);
    }

    public boolean existePorCpf(String cpf) {
        return repository.existsByCpf(cpf);
    }
}
