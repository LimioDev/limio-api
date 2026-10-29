package com.limio.api.modulos.perfil.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.limio.api.comum.localizacao.enums.Uf;
import com.limio.api.comum.portas.EnderecoInformado;
import com.limio.api.comum.portas.EnderecoLocalizado;
import com.limio.api.modulos.perfil.actions.service.ConsultaEnderecoService;
import com.limio.api.modulos.perfil.actions.service.Geocodificador;
import com.limio.api.modulos.perfil.endereco.EnderecoParaGeocodificar;
import com.limio.api.modulos.perfil.endereco.EnderecoSalvo;
import com.limio.api.modulos.perfil.endereco.EnderecoSalvoService;
import com.limio.api.modulos.perfil.excecao.EnderecoNaoLocalizadoException;
import com.limio.api.modulos.perfil.excecao.EnderecoSalvoNaoEncontradoException;
import com.limio.api.modulos.perfil.geo.Coordenadas;

@ExtendWith(MockitoExtension.class)
class ConsultaEnderecoServiceTest {

    private static final UUID USUARIO_ID = UUID.randomUUID();
    private static final UUID ENDERECO_ID = UUID.randomUUID();

    @Mock
    private EnderecoSalvoService enderecoSalvoService;

    @Mock
    private Geocodificador geocodificador;

    private ConsultaEnderecoService service;

    @BeforeEach
    void montarService() {
        service = new ConsultaEnderecoService(enderecoSalvoService, geocodificador);
    }

    @Test
    void dadoEnderecoSalvoComComplemento_quandoBusca_entaoUmaLinhaECoordenadasDoProprioEndereco() {
        EnderecoSalvo salvo = EnderecoSalvo.builder()
                .apelido("Casa").cep("05422000").rua("Rua dos Pinheiros").numero("100").complemento("Apto 12")
                .bairroRegiao("Pinheiros").cidade("São Paulo").uf(Uf.SP).latitude(-23.56).longitude(-46.68)
                .build();
        when(enderecoSalvoService.buscarEnderecoAtivoDoUsuarioOuFalhar(ENDERECO_ID, USUARIO_ID)).thenReturn(salvo);

        EnderecoLocalizado localizado = service.buscarSalvo(USUARIO_ID, ENDERECO_ID);

        assertThat(localizado.bairroRegiao()).isEqualTo("Pinheiros");
        assertThat(localizado.uf()).isEqualTo(Uf.SP);
        assertThat(localizado.enderecoCompleto())
                .isEqualTo("Rua dos Pinheiros, 100 - Apto 12, Pinheiros, São Paulo - SP");
        assertThat(localizado.latitude()).isEqualTo(-23.56);
        assertThat(localizado.longitude()).isEqualTo(-46.68);
    }

    @Test
    void dadoEnderecoSalvoDeOutroUsuario_quandoBusca_entaoPropagaO404DoPerfil() {
        when(enderecoSalvoService.buscarEnderecoAtivoDoUsuarioOuFalhar(ENDERECO_ID, USUARIO_ID))
                .thenThrow(new EnderecoSalvoNaoEncontradoException());

        assertThatThrownBy(() -> service.buscarSalvo(USUARIO_ID, ENDERECO_ID))
                .isInstanceOf(EnderecoSalvoNaoEncontradoException.class);
    }

    @Test
    void dadoEnderecoAvulsoSemComplemento_quandoLocaliza_entaoGeocodificaRuaNumeroCidadeEUf() {
        EnderecoInformado avulso = new EnderecoInformado("01310100", "Avenida Paulista", "1000", null,
                "Bela Vista", "São Paulo", Uf.SP);
        when(geocodificador.localizarEndereco(
                new EnderecoParaGeocodificar("Avenida Paulista", "1000", "São Paulo", Uf.SP)))
                .thenReturn(Optional.of(new Coordenadas(-23.5614, -46.6559)));

        EnderecoLocalizado localizado = service.localizar(avulso);

        assertThat(localizado.enderecoCompleto()).isEqualTo("Avenida Paulista, 1000, Bela Vista, São Paulo - SP");
        assertThat(localizado.bairroRegiao()).isEqualTo("Bela Vista");
        assertThat(localizado.uf()).isEqualTo(Uf.SP);
        assertThat(localizado.latitude()).isEqualTo(-23.5614);
    }

    @Test
    void dadoEnderecoAvulsoNaoAchado_quandoLocaliza_entaoEnderecoNaoLocalizado() {
        when(geocodificador.localizarEndereco(new EnderecoParaGeocodificar("Rua X", "1", "Lugar", Uf.SP)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.localizar(
                new EnderecoInformado("00000000", "Rua X", "1", null, "Centro", "Lugar", Uf.SP)))
                .isInstanceOf(EnderecoNaoLocalizadoException.class);
    }
}
