package com.limio.api.modulos.anuncio.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import com.limio.api.comum.portas.SituacaoAvaliacao;
import com.limio.api.comum.records.PageResponse;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.ItemHistorico;
import com.limio.api.modulos.anuncio.actions.mapper.HistoricoMapper;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;
import com.limio.api.modulos.anuncio.enums.StatusHistorico;
import com.limio.api.modulos.anuncio.records.HistoricoItemResponse;

class HistoricoMapperTest {

    private static final Instant VALIDADO_EM = Instant.parse("2026-10-12T17:40:00Z");
    private static final Instant ENCERRADO_EM = Instant.parse("2026-10-03T12:00:00Z");

    private final HistoricoMapper mapper = new HistoricoMapper();

    private Anuncio anuncio(String titulo, String descricao, Instant validadoEm, Instant encerradoEm) {
        Anuncio anuncio = Anuncio.builder().contratanteId(UUID.randomUUID()).titulo(titulo).descricao(descricao)
                .dataHoraPretendida(Instant.now()).status(validadoEm != null ? StatusAnuncio.CONCLUIDO : StatusAnuncio.ENCERRADO).build();
        ReflectionTestUtils.setField(anuncio, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(anuncio, "validadoEm", validadoEm);
        ReflectionTestUtils.setField(anuncio, "encerradoEm", encerradoEm);
        return anuncio;
    }

    private HistoricoItemResponse mapear(ItemHistorico item) {
        PageResponse<HistoricoItemResponse> resposta = mapper
                .toResponse(new PageImpl<>(List.of(item), PageRequest.of(0, 20), 1));
        return resposta.content().get(0);
    }

    @Test
    void dadoServicoConcluido_quandoMapear_entaoDataEDataDaValidacaoEAvaliacaoVai() {
        Anuncio anuncio = anuncio("Limpeza", "d", VALIDADO_EM, null);

        HistoricoItemResponse resposta = mapear(
                new ItemHistorico(anuncio, StatusHistorico.CONCLUIDO, "Maria Souza", new SituacaoAvaliacao(5, false)));

        assertThat(resposta.anuncioId()).isEqualTo(anuncio.getId());
        assertThat(resposta.titulo()).isEqualTo("Limpeza");
        assertThat(resposta.data()).isEqualTo(VALIDADO_EM);
        assertThat(resposta.status()).isEqualTo(StatusHistorico.CONCLUIDO);
        assertThat(resposta.outraParte()).isEqualTo("Maria Souza");
        assertThat(resposta.avaliacao().notaRecebida()).isEqualTo(5);
        assertThat(resposta.avaliacao().podeAvaliar()).isFalse();
    }

    @Test
    void dadoAnuncioEncerrado_quandoMapear_entaoDataEDoEncerramentoESemAvaliacao() {
        Anuncio anuncio = anuncio("Faxina", "d", null, ENCERRADO_EM);

        HistoricoItemResponse resposta = mapear(new ItemHistorico(anuncio, StatusHistorico.EXPIRADO, null, null));

        assertThat(resposta.data()).isEqualTo(ENCERRADO_EM);
        assertThat(resposta.outraParte()).isNull();
        assertThat(resposta.avaliacao()).isNull();
    }

    @Test
    void dadoPagina_quandoMapear_entaoCopiaOsDadosDePaginacao() {
        PageResponse<HistoricoItemResponse> resposta = mapper.toResponse(new PageImpl<>(List.of(), PageRequest.of(2, 20), 45));

        assertThat(resposta.content()).isEmpty();
        assertThat(resposta.pagina()).isEqualTo(2);
        assertThat(resposta.totalElementos()).isEqualTo(45);
        assertThat(resposta.totalPaginas()).isEqualTo(3);
    }
}
