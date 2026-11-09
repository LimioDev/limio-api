package com.limio.api.modulos.anuncio.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.limio.api.comum.midia.LinkTemporario;
import com.limio.api.comum.portas.ConsultaAvaliacoes;
import com.limio.api.comum.portas.ConsultaChat;
import com.limio.api.comum.portas.ConsultaDadosUsuario;
import com.limio.api.comum.portas.ConsultaEspecialidades;
import com.limio.api.comum.portas.ConsultaFotoPerfil;
import com.limio.api.comum.portas.ConsultaPlanoPrestador;
import com.limio.api.comum.portas.DadosUsuario;
import com.limio.api.comum.portas.ResumoAvaliacoes;
import com.limio.api.comum.portas.enums.Plano;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.CandidatoDoAnuncio;
import com.limio.api.modulos.anuncio.actions.usecase.ListarCandidatosUseCase;
import com.limio.api.modulos.anuncio.aplicacao.Aplicacao;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoService;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;
import com.limio.api.modulos.anuncio.enums.StatusAplicacao;
import com.limio.api.modulos.anuncio.excecao.AnuncioNaoEncontradoException;

@ExtendWith(MockitoExtension.class)
class ListarCandidatosUseCaseTest {

    private static final UUID DONO_ID = UUID.randomUUID();
    private static final UUID ANUNCIO_ID = UUID.randomUUID();
    private static final LinkTemporario LINK = new LinkTemporario("https://bucket/assinado",
            Instant.parse("2026-10-27T18:00:00Z"));

    @Mock
    private AnuncioService anuncioService;

    @Mock
    private AplicacaoService aplicacaoService;

    @Mock
    private ConsultaDadosUsuario consultaDadosUsuario;

    @Mock
    private ConsultaFotoPerfil consultaFotoPerfil;

    @Mock
    private ConsultaPlanoPrestador consultaPlanoPrestador;

    @Mock
    private ConsultaEspecialidades consultaEspecialidades;

    @Mock
    private ConsultaAvaliacoes consultaAvaliacoes;

    @Mock
    private ConsultaChat consultaChat;

    private ListarCandidatosUseCase useCase;

    @BeforeEach
    void montarUseCase() {
        useCase = new ListarCandidatosUseCase(anuncioService, aplicacaoService, consultaDadosUsuario,
                consultaFotoPerfil, consultaPlanoPrestador, consultaEspecialidades, consultaAvaliacoes, consultaChat);
        Anuncio anuncio = Anuncio.builder()
                .contratanteId(DONO_ID)
                .categoriaId(UUID.randomUUID())
                .descricao("Faxina")
                .dataHoraPretendida(Instant.now().plus(Duration.ofDays(2)))
                .status(StatusAnuncio.PUBLICADO)
                .build();
        ReflectionTestUtils.setField(anuncio, "id", ANUNCIO_ID);
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.of(anuncio));
        lenient().when(consultaEspecialidades.listarNomes(org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(Map.of());
        lenient().when(consultaAvaliacoes.resumir(org.mockito.ArgumentMatchers.anyCollection())).thenReturn(Map.of());
        lenient().when(consultaChat.buscarIdsPorCandidatura(org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(Map.of());
        lenient().when(consultaFotoPerfil.buscarFoto(org.mockito.ArgumentMatchers.any())).thenReturn(Optional.empty());
        lenient().when(consultaPlanoPrestador.planoAtual(org.mockito.ArgumentMatchers.any())).thenReturn(Plano.FREE);
    }

    private Aplicacao enviada(UUID prestadorId) {
        Aplicacao aplicacao = Aplicacao.builder().anuncioId(ANUNCIO_ID).prestadorId(prestadorId)
                .status(StatusAplicacao.ENVIADA).build();
        ReflectionTestUtils.setField(aplicacao, "id", UUID.randomUUID());
        return aplicacao;
    }

    private void prestador(UUID id, String nome) {
        when(consultaDadosUsuario.buscarPorId(id))
                .thenReturn(Optional.of(new DadosUsuario(nome, "p@example.com", "11999990000", null, null)));
    }

    @Test
    void dadoAnuncioDeOutraPessoa_quandoLista_entao404() {
        assertThatThrownBy(() -> useCase.executar(UUID.randomUUID(), ANUNCIO_ID))
                .isInstanceOf(AnuncioNaoEncontradoException.class);
    }

    @Test
    void dadoAnuncioSemCandidatos_quandoLista_entaoListaVazia() {
        when(aplicacaoService.buscarEnviadasDoAnuncio(ANUNCIO_ID)).thenReturn(List.of());

        assertThat(useCase.executar(DONO_ID, ANUNCIO_ID)).isEmpty();
    }

    @Test
    void dadoCandidatoSemAvaliacoes_quandoLista_entaoMediaAusenteEQuantidadeZero() {
        UUID prestadorId = UUID.randomUUID();
        Aplicacao aplicacao = enviada(prestadorId);
        when(aplicacaoService.buscarEnviadasDoAnuncio(ANUNCIO_ID)).thenReturn(List.of(aplicacao));
        prestador(prestadorId, "Rafael Souza");

        List<CandidatoDoAnuncio> candidatos = useCase.executar(DONO_ID, ANUNCIO_ID);

        assertThat(candidatos).hasSize(1);
        CandidatoDoAnuncio candidato = candidatos.get(0);
        assertThat(candidato.candidaturaId()).isEqualTo(aplicacao.getId());
        assertThat(candidato.nome()).isEqualTo("Rafael S.");
        assertThat(candidato.qtdAvaliacoes()).isZero();
        assertThat(candidato.mediaAvaliacoes()).isNull();
        assertThat(candidato.gold()).isFalse();
        assertThat(candidato.foto()).isNull();
        assertThat(candidato.especialidades()).isEmpty();
        assertThat(candidato.chatId()).isNull();
    }

    @Test
    void dadoCandidatoCompleto_quandoLista_entaoMontaTodosOsCampos() {
        UUID prestadorId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        Aplicacao aplicacao = enviada(prestadorId);
        when(aplicacaoService.buscarEnviadasDoAnuncio(ANUNCIO_ID)).thenReturn(List.of(aplicacao));
        prestador(prestadorId, "Rafael Souza");
        when(consultaFotoPerfil.buscarFoto(prestadorId)).thenReturn(Optional.of(LINK));
        when(consultaPlanoPrestador.planoAtual(prestadorId)).thenReturn(Plano.GOLD);
        when(consultaEspecialidades.listarNomes(List.of(prestadorId)))
                .thenReturn(Map.of(prestadorId, List.of("Eletricista", "Encanador")));
        when(consultaAvaliacoes.resumir(List.of(prestadorId)))
                .thenReturn(Map.of(prestadorId, new ResumoAvaliacoes(12, new BigDecimal("4.6"))));
        when(consultaChat.buscarIdsPorCandidatura(List.of(aplicacao.getId())))
                .thenReturn(Map.of(aplicacao.getId(), chatId));

        CandidatoDoAnuncio candidato = useCase.executar(DONO_ID, ANUNCIO_ID).get(0);

        assertThat(candidato.foto()).isEqualTo(LINK);
        assertThat(candidato.gold()).isTrue();
        assertThat(candidato.especialidades()).containsExactly("Eletricista", "Encanador");
        assertThat(candidato.qtdAvaliacoes()).isEqualTo(12);
        assertThat(candidato.mediaAvaliacoes()).isEqualByComparingTo("4.6");
        assertThat(candidato.chatId()).isEqualTo(chatId);
    }

    @Test
    void dadoCandidatoComContaEncerrada_quandoLista_entaoFicaFora() {
        UUID ativo = UUID.randomUUID();
        UUID encerrado = UUID.randomUUID();
        when(aplicacaoService.buscarEnviadasDoAnuncio(ANUNCIO_ID))
                .thenReturn(List.of(enviada(encerrado), enviada(ativo)));
        when(consultaDadosUsuario.buscarPorId(encerrado)).thenReturn(Optional.empty());
        prestador(ativo, "Ana Lima");

        List<CandidatoDoAnuncio> candidatos = useCase.executar(DONO_ID, ANUNCIO_ID);

        assertThat(candidatos).extracting(CandidatoDoAnuncio::nome).containsExactly("Ana L.");
    }
}
