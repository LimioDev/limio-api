package com.limio.api.modulos.auth.actions.mapper;

import org.springframework.stereotype.Component;

import com.limio.api.comum.base.BaseMapper;
import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.modulos.auth.records.CadastroRequest;
import com.limio.api.modulos.auth.records.PapelAtivoResponse;
import com.limio.api.modulos.auth.records.UsuarioResponse;
import com.limio.api.modulos.auth.usuario.Usuario;
import com.limio.api.modulos.auth.usuario.enums.StatusConta;

/** Anti-corruption layer entre {@link Usuario} e os records HTTP do módulo. Chamado só pelo controller. */
@Component
public class UsuarioMapper implements BaseMapper<Usuario, CadastroRequest, UsuarioResponse> {

    @Override
    public Usuario toEntity(CadastroRequest request) {
        return Usuario.builder()
                .nomeCompleto(request.nomeCompleto())
                .email(request.email())
                .telefone(request.telefone())
                .cpf(request.cpf())
                .dataNascimento(request.dataNascimento())
                .cidadeUf(request.cidadeUf())
                .papelAtivo(PapelUsuario.EMPREGADOR)
                .statusConta(StatusConta.ATIVA)
                .build();
    }

    @Override
    public UsuarioResponse toResponse(Usuario entity) {
        return new UsuarioResponse(
                entity.getId(),
                entity.getNomeCompleto(),
                entity.getEmail(),
                entity.getPapelAtivo(),
                entity.getCriadoEm());
    }

    public PapelAtivoResponse toPapelAtivoResponse(Usuario entity) {
        return new PapelAtivoResponse(entity.getPapelAtivo());
    }
}
