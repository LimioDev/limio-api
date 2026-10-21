package com.limio.api.modulos.perfil.actions.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import com.limio.api.comum.base.BaseMapper;
import com.limio.api.modulos.perfil.endereco.EnderecoSalvo;
import com.limio.api.modulos.perfil.records.EnderecoSalvoRequest;
import com.limio.api.modulos.perfil.records.EnderecoSalvoResponse;

@Component
public class EnderecoSalvoMapper implements BaseMapper<EnderecoSalvo, EnderecoSalvoRequest, EnderecoSalvoResponse> {

    @Override
    public EnderecoSalvo toEntity(EnderecoSalvoRequest request) {
        return EnderecoSalvo.builder()
                .apelido(request.apelido())
                .cep(request.cep())
                .rua(request.rua())
                .numero(request.numero())
                .complemento(request.complemento())
                .bairroRegiao(request.bairroRegiao())
                .cidade(request.cidade())
                .uf(request.uf())
                .latitude(request.latitude())
                .longitude(request.longitude())
                .padrao(request.padrao())
                .build();
    }

    @Override
    public EnderecoSalvoResponse toResponse(EnderecoSalvo endereco) {
        return new EnderecoSalvoResponse(
                endereco.getId(),
                endereco.getApelido(),
                endereco.getCep(),
                endereco.getRua(),
                endereco.getNumero(),
                endereco.getComplemento(),
                endereco.getBairroRegiao(),
                endereco.getCidade(),
                endereco.getUf(),
                endereco.getLatitude(),
                endereco.getLongitude(),
                endereco.isPadrao());
    }

    public List<EnderecoSalvoResponse> toResponse(List<EnderecoSalvo> enderecos) {
        return enderecos.stream().map(this::toResponse).toList();
    }
}
