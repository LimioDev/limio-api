package com.limio.api.modulos.anuncio.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.limio.api.comum.midia.LinkTemporario;
import com.limio.api.comum.portas.ResumoCategoria;
import com.limio.api.comum.portas.ResumoPerfilEmpregador;
import com.limio.api.comum.records.PageResponse;
import com.limio.api.modulos.anuncio.ItemDoMural;
import com.limio.api.modulos.anuncio.actions.mapper.MuralMapper;
import com.limio.api.modulos.anuncio.records.MuralItemResponse;

class MuralMapperTest {

    private final MuralMapper mapper = new MuralMapper();

    private ItemDoMural item(LinkTemporario foto, double km) {
        return new ItemDoMural(UUID.randomUUID(), "Trocar chuveiro", new ResumoCategoria(UUID.randomUUID(), "Elétrica", "Reparos", true),
                foto, new ResumoPerfilEmpregador("Mari", "https://bucket/mari", Instant.now(), null, 0),
                Instant.parse("2026-11-05T13:00:00Z"), "Cambuí", km);
    }

    @Test
    void dadoItemComFoto_quandoMapeia_entaoTrazSoOsCamposDoMuralComDistanciaEmUmaCasa() {
        ItemDoMural item = item(new LinkTemporario("https://bucket/foto", Instant.now()), 2.4567);

        PageResponse<MuralItemResponse> resposta = mapper.toResponse(new PageImpl<>(List.of(item), PageRequest.of(0, 1), 9));

        MuralItemResponse mapeado = resposta.content().get(0);
        assertThat(mapeado.fotoUrl()).isEqualTo("https://bucket/foto");
        assertThat(mapeado.distanciaKm()).isEqualTo(2.5);
        assertThat(mapeado.empregador().nomeExibicao()).isEqualTo("Mari");
        assertThat(mapeado.categoria().nome()).isEqualTo("Elétrica");
        assertThat(resposta.totalElementos()).isEqualTo(9);
    }

    @Test
    void dadoItemSemFoto_quandoMapeia_entaoFotoNula() {
        MuralItemResponse mapeado = mapper.toResponse(new PageImpl<>(List.of(item(null, 1.0)), PageRequest.of(0, 20), 1))
                .content().get(0);

        assertThat(mapeado.fotoUrl()).isNull();
    }
}
