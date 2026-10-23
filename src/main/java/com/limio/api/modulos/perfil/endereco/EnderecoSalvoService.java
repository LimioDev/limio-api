package com.limio.api.modulos.perfil.endereco;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import com.limio.api.comum.base.BaseService;
import com.limio.api.modulos.perfil.excecao.EnderecoPadraoConflitoException;
import com.limio.api.modulos.perfil.excecao.EnderecoSalvoNaoEncontradoException;

@Component
public class EnderecoSalvoService extends BaseService<EnderecoSalvo, UUID, EnderecoSalvoRepository> {

    private static final String CONSTRAINT_PADRAO_UNICO = "uk_endereco_salvo_padrao";

    public EnderecoSalvoService(EnderecoSalvoRepository repository) {
        super(repository);
    }

    public List<EnderecoSalvo> listarEnderecoAtivos(UUID perfilEmpregadorId) {
        return repository.findAllByPerfilEmpregadorIdAndRemovidoEmIsNullOrderByPadraoDescApelidoAsc(
                perfilEmpregadorId);
    }

    public EnderecoSalvo buscarEnderecoAtivoOuFalhar(UUID id, UUID perfilEmpregadorId) {
        return repository.findByIdAndPerfilEmpregadorIdAndRemovidoEmIsNull(id, perfilEmpregadorId)
                .orElseThrow(EnderecoSalvoNaoEncontradoException::new);
    }

    public void desmarcarEnderecoPadrao(UUID perfilEmpregadorId) {
        repository.desmarcarPadrao(perfilEmpregadorId, Instant.now());
    }

    @Override
    public EnderecoSalvo salvar(EnderecoSalvo endereco) {
        try {
            return repository.saveAndFlush(endereco);
        } catch (DataIntegrityViolationException e) {
            if (violaUnicidadeDoPadrao(e)) {
                throw new EnderecoPadraoConflitoException();
            }
            throw e;
        }
    }

    private static boolean violaUnicidadeDoPadrao(Throwable erro) {
        for (Throwable causa = erro; causa != null; causa = causa.getCause()) {
            if (causa instanceof ConstraintViolationException violacao
                    && CONSTRAINT_PADRAO_UNICO.equalsIgnoreCase(violacao.getConstraintName())) {
                return true;
            }
        }
        return false;
    }
}
