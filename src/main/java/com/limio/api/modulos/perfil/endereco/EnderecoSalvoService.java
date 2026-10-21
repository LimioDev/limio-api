package com.limio.api.modulos.perfil.endereco;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.limio.api.comum.base.BaseService;
import com.limio.api.modulos.perfil.excecao.EnderecoSalvoNaoEncontradoException;

@Component
public class EnderecoSalvoService extends BaseService<EnderecoSalvo, UUID, EnderecoSalvoRepository> {

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
}
