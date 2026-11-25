package com.limio.api.modulos.anuncio.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;

/** Data do desfecho do anúncio (ETI-71). */
class DesfechoDominioTest {

    private static final Instant VALIDADO_EM = Instant.parse("2026-10-12T17:40:00Z");
    private static final Instant ENCERRADO_EM = Instant.parse("2026-10-03T12:00:00Z");

    @Test
    void dadoConcluido_quandoConsultaDesfecho_entaoDataDaValidacao() {
        Anuncio anuncio = Anuncio.builder().status(StatusAnuncio.CONCLUIDO).validadoEm(VALIDADO_EM).build();

        assertThat(anuncio.desfechoEm()).isEqualTo(VALIDADO_EM);
    }

    @Test
    void dadoEncerrado_quandoConsultaDesfecho_entaoDataDoEncerramento() {
        Anuncio anuncio = Anuncio.builder().status(StatusAnuncio.ENCERRADO).encerradoEm(ENCERRADO_EM).build();

        assertThat(anuncio.desfechoEm()).isEqualTo(ENCERRADO_EM);
    }

    @ParameterizedTest
    @EnumSource(value = StatusAnuncio.class, names = { "PUBLICADO", "EM_ANDAMENTO", "AGUARDANDO_VALIDACAO" })
    void dadoAnuncioAindaAberto_quandoConsultaDesfecho_entaoNaoHa(StatusAnuncio status) {
        assertThat(Anuncio.builder().status(status).build().desfechoEm()).isNull();
    }
}
