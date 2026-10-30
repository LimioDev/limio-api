package com.limio.api.modulos.anuncio.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.limio.api.comum.portas.ConsultaDadosUsuario;
import com.limio.api.comum.portas.ConsultaPerfilEmpregador;
import com.limio.api.comum.portas.DadosUsuario;
import com.limio.api.comum.portas.ResumoPerfilEmpregador;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioNoPainel;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.PainelEmpregador;
import com.limio.api.modulos.anuncio.actions.usecase.ConsultarPainelEmpregadorUseCase;
import com.limio.api.modulos.anuncio.aplicacao.Aplicacao;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoService;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;
import com.limio.api.modulos.anuncio.enums.StatusAplicacao;

@ExtendWith(MockitoExtension.class)
class ConsultarPainelEmpregadorUseCaseTest {

    private static final UUID CONTRATANTE_ID = UUID.randomUUID();
    private static final UUID PRESTADOR_ID = UUID.randomUUID();
    private static final ResumoPerfilEmpregador PERFIL = new ResumoPerfilEmpregador("Maria", null, null, null, 0);

    @Mock
    private AnuncioService anuncioService;

    @Mock
    private AplicacaoService aplicacaoService;

    @Mock
    private ConsultaPerfilEmpregador consultaPerfilEmpregador;

    @Mock
    private ConsultaDadosUsuario consultaDadosUsuario;

    private ConsultarPainelEmpregadorUseCase useCase;

    @BeforeEach
    void montarUseCase() {
        useCase = new ConsultarPainelEmpregadorUseCase(anuncioService, aplicacaoService, consultaPerfilEmpregador,
                consultaDadosUsuario);
        when(consultaPerfilEmpregador.buscarResumo(CONTRATANTE_ID)).thenReturn(PERFIL);
    }

    private Anuncio anuncio() {
        Anuncio anuncio = Anuncio.builder()
                .contratanteId(CONTRATANTE_ID)
                .titulo("Faxina")
                .descricao("Faxina")
                .dataHoraPretendida(Instant.now().plus(Duration.ofDays(3)))
                .status(StatusAnuncio.PUBLICADO)
                .build();
        ReflectionTestUtils.setField(anuncio, "id", UUID.randomUUID());
        return anuncio;
    }

    private Aplicacao aplicacao(Anuncio anuncio, UUID prestadorId, StatusAplicacao status) {
        return Aplicacao.builder().anuncioId(anuncio.getId()).prestadorId(prestadorId).status(status).build();
    }

    @Test
    void dadoSemAnuncios_quandoConsulta_entaoListaVaziaEContadoresZerados() {
        when(anuncioService.buscarAbertosDoContratantePorData(CONTRATANTE_ID)).thenReturn(List.of());
        when(aplicacaoService.buscarAbertasDosAnuncios(List.of())).thenReturn(List.of());

        PainelEmpregador painel = useCase.executar(CONTRATANTE_ID);

        assertThat(painel.perfil()).isEqualTo(PERFIL);
        assertThat(painel.anuncios()).isEmpty();
        assertThat(painel.anunciosAbertos()).isZero();
        assertThat(painel.novosCandidatos()).isZero();
        verifyNoInteractions(consultaDadosUsuario);
    }

    @Test
    void dadoAnunciosComCandidatos_quandoConsulta_entaoContaSoAsEnviadasPorAnuncioEMantemAOrdem() {
        Anuncio primeiro = anuncio();
        Anuncio segundo = anuncio();
        when(anuncioService.buscarAbertosDoContratantePorData(CONTRATANTE_ID)).thenReturn(List.of(primeiro, segundo));
        when(aplicacaoService.buscarAbertasDosAnuncios(List.of(primeiro.getId(), segundo.getId()))).thenReturn(List.of(
                aplicacao(segundo, UUID.randomUUID(), StatusAplicacao.ENVIADA),
                aplicacao(segundo, UUID.randomUUID(), StatusAplicacao.ENVIADA),
                aplicacao(primeiro, UUID.randomUUID(), StatusAplicacao.ENVIADA)));

        PainelEmpregador painel = useCase.executar(CONTRATANTE_ID);

        assertThat(painel.anuncios()).extracting(AnuncioNoPainel::anuncio).containsExactly(primeiro, segundo);
        assertThat(painel.anuncios()).extracting(AnuncioNoPainel::candidatos).containsExactly(1, 2);
        assertThat(painel.anuncios()).extracting(AnuncioNoPainel::prestadorEscolhido).containsOnlyNulls();
        assertThat(painel.anunciosAbertos()).isEqualTo(2);
        assertThat(painel.novosCandidatos()).isEqualTo(3);
    }

    @Test
    void dadoPrestadorEscolhido_quandoConsulta_entaoDevolveNomeAbreviadoSemContarComoCandidato() {
        Anuncio anuncio = anuncio();
        when(anuncioService.buscarAbertosDoContratantePorData(CONTRATANTE_ID)).thenReturn(List.of(anuncio));
        when(aplicacaoService.buscarAbertasDosAnuncios(List.of(anuncio.getId())))
                .thenReturn(List.of(aplicacao(anuncio, PRESTADOR_ID, StatusAplicacao.ESCOLHIDA)));
        when(consultaDadosUsuario.buscarPorId(PRESTADOR_ID))
                .thenReturn(Optional.of(new DadosUsuario("Rafael Souza", "rafael@example.com", null, null, null)));

        AnuncioNoPainel item = useCase.executar(CONTRATANTE_ID).anuncios().getFirst();

        assertThat(item.prestadorEscolhido()).isEqualTo("Rafael S.");
        assertThat(item.candidatos()).isZero();
    }

    @Test
    void dadoAguardandoValidacao_quandoConsulta_entaoTambemMostraOPrestador() {
        Anuncio anuncio = anuncio();
        when(anuncioService.buscarAbertosDoContratantePorData(CONTRATANTE_ID)).thenReturn(List.of(anuncio));
        when(aplicacaoService.buscarAbertasDosAnuncios(List.of(anuncio.getId())))
                .thenReturn(List.of(aplicacao(anuncio, PRESTADOR_ID, StatusAplicacao.AGUARDANDO_VALIDACAO)));
        when(consultaDadosUsuario.buscarPorId(PRESTADOR_ID))
                .thenReturn(Optional.of(new DadosUsuario("Rafael Souza", "rafael@example.com", null, null, null)));

        assertThat(useCase.executar(CONTRATANTE_ID).anuncios().getFirst().prestadorEscolhido()).isEqualTo("Rafael S.");
    }

    @Test
    void dadoPrestadorEscolhidoComContaEncerrada_quandoConsulta_entaoSemNome() {
        Anuncio anuncio = anuncio();
        when(anuncioService.buscarAbertosDoContratantePorData(CONTRATANTE_ID)).thenReturn(List.of(anuncio));
        when(aplicacaoService.buscarAbertasDosAnuncios(List.of(anuncio.getId())))
                .thenReturn(List.of(aplicacao(anuncio, PRESTADOR_ID, StatusAplicacao.ESCOLHIDA)));
        when(consultaDadosUsuario.buscarPorId(any())).thenReturn(Optional.empty());

        assertThat(useCase.executar(CONTRATANTE_ID).anuncios().getFirst().prestadorEscolhido()).isNull();
    }
}
