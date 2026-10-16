package com.limio.api.modulos.perfil.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.limio.api.comum.portas.DadosUsuario;
import com.limio.api.modulos.perfil.actions.mapper.PerfilEmpregadorMapper;
import com.limio.api.modulos.perfil.empregador.MeuPerfilEmpregador;
import com.limio.api.modulos.perfil.empregador.PerfilEmpregador;
import com.limio.api.modulos.perfil.records.PerfilEmpregadorResponse;

class PerfilEmpregadorMapperTest {

    private final PerfilEmpregadorMapper mapper = new PerfilEmpregadorMapper();

    @Test
    void deveJuntarCamposDoPerfilEDoUsuario() {
        Instant emailConfirmado = Instant.parse("2026-10-01T10:00:00Z");
        PerfilEmpregador perfil = PerfilEmpregador.builder().usuarioId(UUID.randomUUID()).nomeExibicao("Maria")
                .mediaAvaliacoes(new BigDecimal("4.5")).qtdAvaliacoes(2).build();
        DadosUsuario dados = new DadosUsuario("Maria da Silva", "maria@example.com", "+5511999998888", emailConfirmado,
                null);

        PerfilEmpregadorResponse response = mapper.toResponse(new MeuPerfilEmpregador(perfil, dados));

        assertThat(response).isEqualTo(new PerfilEmpregadorResponse("Maria", new BigDecimal("4.5"), 2,
                "maria@example.com", "+5511999998888", emailConfirmado, null));
    }

    @Test
    void deveManterNuloSemAvaliacaoENaoConfirmado() {
        PerfilEmpregador perfil = PerfilEmpregador.builder().usuarioId(UUID.randomUUID()).nomeExibicao("Maria")
                .build();
        DadosUsuario dados = new DadosUsuario("Maria", "m@example.com", "+5511999998888", null, null);

        PerfilEmpregadorResponse response = mapper.toResponse(new MeuPerfilEmpregador(perfil, dados));

        assertThat(response.mediaAvaliacoes()).isNull();
        assertThat(response.qtdAvaliacoes()).isZero();
        assertThat(response.emailConfirmadoEm()).isNull();
        assertThat(response.telefoneConfirmadoEm()).isNull();
    }
}
