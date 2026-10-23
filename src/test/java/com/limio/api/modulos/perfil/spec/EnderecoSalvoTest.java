package com.limio.api.modulos.perfil.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.limio.api.modulos.perfil.endereco.EnderecoSalvo;

class EnderecoSalvoTest {

    @Test
    void deveNascerAtivoENaoPadraoPorDefault() {
        EnderecoSalvo endereco = EnderecoSalvo.builder().build();

        assertThat(endereco.isRemovido()).isFalse();
        assertThat(endereco.isPadrao()).isFalse();
    }

    @Test
    void deveMarcarComoRemovidoELimparOPadraoAoRemover() {
        EnderecoSalvo endereco = EnderecoSalvo.builder().padrao(true).build();
        Instant quando = Instant.parse("2026-10-20T12:00:00Z");

        endereco.remover(quando);

        assertThat(endereco.isRemovido()).isTrue();
        assertThat(endereco.getRemovidoEm()).isEqualTo(quando);
        assertThat(endereco.isPadrao()).isFalse();
    }
}
