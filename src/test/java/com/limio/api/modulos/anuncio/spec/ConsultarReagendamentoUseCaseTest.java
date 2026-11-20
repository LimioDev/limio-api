package com.limio.api.modulos.anuncio.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.ReagendamentoConsultado;
import com.limio.api.modulos.anuncio.actions.usecase.ConsultarReagendamentoUseCase;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoService;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;
import com.limio.api.modulos.anuncio.excecao.ReagendamentoNaoEncontradoException;
import com.limio.api.modulos.anuncio.reagendamento.Reagendamento;
import com.limio.api.modulos.anuncio.reagendamento.ReagendamentoService;

/** Consultar a proposta (ETI-99): quem enxerga e qual é a data vigente devolvida. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConsultarReagendamentoUseCaseTest {

    private static final UUID ANUNCIO_ID = UUID.randomUUID();
    private static final UUID REAGENDAMENTO_ID = UUID.randomUUID();
    private static final UUID CONTRATANTE_ID = UUID.randomUUID();
    private static final UUID PRESTADOR_ID = UUID.randomUUID();
    private static final UUID ESTRANHO_ID = UUID.randomUUID();
    private static final Instant ANTIGA = Instant.parse("2026-11-14T15:00:00Z");
    private static final Instant VIGENTE = Instant.parse("2026-11-20T15:00:00Z");

    @Mock
    private AnuncioService anuncioService;

    @Mock
    private AplicacaoService aplicacaoService;

    @Mock
    private ReagendamentoService reagendamentoService;

    private ConsultarReagendamentoUseCase useCase;
    private Reagendamento proposta;

    @BeforeEach
    void montarUseCase() {
        useCase = new ConsultarReagendamentoUseCase(anuncioService, aplicacaoService, reagendamentoService);
        Anuncio anuncio = Anuncio.builder()
                .contratanteId(CONTRATANTE_ID)
                .titulo("Instalar 3 tomadas")
                .dataHoraPretendida(VIGENTE)
                .status(StatusAnuncio.PUBLICADO)
                .build();
        ReflectionTestUtils.setField(anuncio, "id", ANUNCIO_ID);
        // proposta antiga: foi feita sobre ANTIGA e, depois, outra aceita trocou a vigente para VIGENTE
        proposta = Reagendamento.propor(ANUNCIO_ID, CONTRATANTE_ID, ANTIGA, ANTIGA.plus(Duration.ofDays(2)),
                "Imprevisto", ANTIGA.plus(Duration.ofHours(24)), false);
        ReflectionTestUtils.setField(proposta, "id", REAGENDAMENTO_ID);
        when(reagendamentoService.buscarPorId(REAGENDAMENTO_ID)).thenReturn(Optional.of(proposta));
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.of(anuncio));
        when(aplicacaoService.buscarPrestadorDoServico(ANUNCIO_ID)).thenReturn(Optional.of(PRESTADOR_ID));
    }

    @Test
    void dadoEmpregador_quandoConsulta_entaoVeAPropostaComADataVigenteDoAnuncio() {
        ReagendamentoConsultado consultado = useCase.executar(CONTRATANTE_ID, REAGENDAMENTO_ID);

        assertThat(consultado.reagendamento()).isSameAs(proposta);
        assertThat(consultado.dataVigente()).isEqualTo(VIGENTE);
    }

    @Test
    void dadoPrestadorEscolhido_quandoConsulta_entaoVeAProposta() {
        assertThat(useCase.executar(PRESTADOR_ID, REAGENDAMENTO_ID).reagendamento()).isSameAs(proposta);
    }

    @Test
    void dadoProponenteQueDeixouDeSerParte_quandoConsulta_entaoAindaVeAPropostaQueFez() {
        when(aplicacaoService.buscarPrestadorDoServico(ANUNCIO_ID)).thenReturn(Optional.empty());
        ReflectionTestUtils.setField(proposta, "proponenteId", PRESTADOR_ID);

        assertThat(useCase.executar(PRESTADOR_ID, REAGENDAMENTO_ID).reagendamento()).isSameAs(proposta);
    }

    @Test
    void dadoUsuarioQueNaoEParte_quandoConsulta_entao404() {
        assertThatThrownBy(() -> useCase.executar(ESTRANHO_ID, REAGENDAMENTO_ID))
                .isInstanceOf(ReagendamentoNaoEncontradoException.class);
    }

    @Test
    void dadaPropostaInexistente_quandoConsulta_entao404() {
        when(reagendamentoService.buscarPorId(REAGENDAMENTO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(CONTRATANTE_ID, REAGENDAMENTO_ID))
                .isInstanceOf(ReagendamentoNaoEncontradoException.class);

        verifyNoInteractions(anuncioService, aplicacaoService);
    }
}
