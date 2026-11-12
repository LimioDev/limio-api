package com.limio.api.modulos.anuncio.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;

/** Regras de domínio da expiração (ETI-66, RF30/RB09), sem Spring nem banco. */
class ExpiracaoDominioTest {

    private static final Instant AGORA = Instant.parse("2026-11-12T12:00:00Z");

    private static Anuncio anuncio(StatusAnuncio status, Instant dataHoraPretendida) {
        return Anuncio.builder().status(status).dataHoraPretendida(dataHoraPretendida).build();
    }

    @Test
    void dadoPublicadoVencidoSemCandidatura_quandoPerguntaSePermiteExpiracao_entaoSim() {
        assertThat(anuncio(StatusAnuncio.PUBLICADO, AGORA.minusSeconds(1)).permiteExpiracao(false, AGORA)).isTrue();
    }

    @Test
    void dadaDataHoraIgualAoAgora_quandoPerguntaSePermiteExpiracao_entaoSim() {
        assertThat(anuncio(StatusAnuncio.PUBLICADO, AGORA).permiteExpiracao(false, AGORA)).isTrue();
    }

    @Test
    void dadaDataHoraNoFuturo_quandoPerguntaSePermiteExpiracao_entaoNao() {
        assertThat(anuncio(StatusAnuncio.PUBLICADO, AGORA.plusSeconds(1)).permiteExpiracao(false, AGORA)).isFalse();
    }

    @Test
    void dadoPublicadoVencidoComCandidatura_quandoPerguntaSePermiteExpiracao_entaoNao() {
        assertThat(anuncio(StatusAnuncio.PUBLICADO, AGORA.minusSeconds(1)).permiteExpiracao(true, AGORA)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = StatusAnuncio.class, names = { "EM_ANDAMENTO", "AGUARDANDO_VALIDACAO", "CONCLUIDO", "ENCERRADO" })
    void dadoAnuncioForaDePublicado_quandoPerguntaSePermiteExpiracao_entaoNao(StatusAnuncio status) {
        assertThat(anuncio(status, AGORA.minusSeconds(1)).permiteExpiracao(false, AGORA)).isFalse();
    }

    @Test
    void dadaExpiracao_quandoExpira_entaoEncerraComMotivoENaoMexeNaData() {
        Instant pretendida = AGORA.minusSeconds(3600);
        Anuncio anuncio = anuncio(StatusAnuncio.PUBLICADO, pretendida);

        anuncio.expirar(AGORA);

        assertThat(anuncio.getStatus()).isEqualTo(StatusAnuncio.ENCERRADO);
        assertThat(anuncio.getEncerradoEm()).isEqualTo(AGORA);
        assertThat(anuncio.getMotivoEncerramento()).isEqualTo("Expirado sem candidatos");
        assertThat(anuncio.getDataHoraPretendida()).isEqualTo(pretendida);
        assertThat(anuncio.isAberto()).isFalse();
    }
}
