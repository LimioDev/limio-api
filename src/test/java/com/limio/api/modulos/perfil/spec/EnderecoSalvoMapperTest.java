package com.limio.api.modulos.perfil.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.limio.api.comum.localizacao.enums.Uf;
import com.limio.api.modulos.perfil.actions.mapper.EnderecoSalvoMapper;
import com.limio.api.modulos.perfil.endereco.EnderecoSalvo;
import com.limio.api.modulos.perfil.records.EnderecoSalvoRequest;
import com.limio.api.modulos.perfil.records.EnderecoSalvoResponse;

class EnderecoSalvoMapperTest {

    private final EnderecoSalvoMapper mapper = new EnderecoSalvoMapper();

    @Test
    void deveMontarEntidadeSemDonoAPartirDoRequest() {
        var request = new EnderecoSalvoRequest("Casa", "01310100", "Avenida Paulista", "1000", "Apto 12",
                "Bela Vista", "São Paulo", Uf.SP, true);

        EnderecoSalvo endereco = mapper.toEntity(request);

        assertThat(endereco.getPerfilEmpregadorId()).isNull();
        assertThat(endereco.getApelido()).isEqualTo("Casa");
        assertThat(endereco.getCep()).isEqualTo("01310100");
        assertThat(endereco.getRua()).isEqualTo("Avenida Paulista");
        assertThat(endereco.getNumero()).isEqualTo("1000");
        assertThat(endereco.getComplemento()).isEqualTo("Apto 12");
        assertThat(endereco.getBairroRegiao()).isEqualTo("Bela Vista");
        assertThat(endereco.getCidade()).isEqualTo("São Paulo");
        assertThat(endereco.getUf()).isEqualTo(Uf.SP);
        assertThat(endereco.getLatitude()).isZero();
        assertThat(endereco.getLongitude()).isZero();
        assertThat(endereco.isPadrao()).isTrue();
        assertThat(endereco.isRemovido()).isFalse();
    }

    @Test
    void deveDevolverTodosOsCamposNaResposta_semExporRemovidoEm() {
        UUID id = UUID.randomUUID();
        EnderecoSalvo endereco = EnderecoSalvo.builder().apelido("Casa").cep("01310100").rua("Avenida Paulista")
                .numero("1000").complemento(null).bairroRegiao("Bela Vista").cidade("São Paulo").uf(Uf.SP)
                .latitude(-23.5614).longitude(-46.6559).padrao(true).build();
        ReflectionTestUtils.setField(endereco, "id", id);

        EnderecoSalvoResponse resposta = mapper.toResponse(endereco);

        assertThat(resposta).isEqualTo(new EnderecoSalvoResponse(id, "Casa", "01310100", "Avenida Paulista", "1000",
                null, "Bela Vista", "São Paulo", Uf.SP, -23.5614, -46.6559, true));
    }

    @Test
    void deveMapearListaPreservandoAOrdem() {
        EnderecoSalvo a = EnderecoSalvo.builder().apelido("A").build();
        EnderecoSalvo b = EnderecoSalvo.builder().apelido("B").build();

        assertThat(mapper.toResponse(List.of(a, b))).extracting(EnderecoSalvoResponse::apelido)
                .containsExactly("A", "B");
    }
}
