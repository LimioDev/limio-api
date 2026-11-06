package com.limio.api.modulos.anuncio.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

import com.limio.api.comum.midia.LinkTemporario;
import com.limio.api.comum.portas.ConsultaCategoria;
import com.limio.api.comum.portas.ConsultaChatDoServico;
import com.limio.api.comum.portas.ConsultaDadosUsuario;
import com.limio.api.comum.portas.ConsultaFotoPerfil;
import com.limio.api.comum.portas.ConsultaPlanoPrestador;
import com.limio.api.comum.portas.DadosUsuario;
import com.limio.api.comum.portas.ResumoCategoria;
import com.limio.api.comum.portas.ResumoChatDoServico;
import com.limio.api.comum.portas.enums.Plano;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.OutraParteDoServico;
import com.limio.api.modulos.anuncio.ServicoCombinado;
import com.limio.api.modulos.anuncio.actions.usecase.ConsultarServicoUseCase;
import com.limio.api.modulos.anuncio.aplicacao.Aplicacao;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoService;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;
import com.limio.api.modulos.anuncio.excecao.ServicoNaoEncontradoException;
import com.limio.api.modulos.anuncio.reagendamento.Reagendamento;
import com.limio.api.modulos.anuncio.reagendamento.ReagendamentoService;

@ExtendWith(MockitoExtension.class)
class ConsultarServicoUseCaseTest {

    private static final UUID ANUNCIO_ID = UUID.randomUUID();
    private static final UUID EMPREGADOR_ID = UUID.randomUUID();
    private static final UUID PRESTADOR_ID = UUID.randomUUID();
    private static final UUID OUTRO_PRESTADOR_ID = UUID.randomUUID();
    private static final UUID CANDIDATURA_ID = UUID.randomUUID();
    private static final Instant DATA_PRETENDIDA = Instant.parse("2026-12-01T15:00:00Z");
    private static final Instant ESCOLHIDA_EM = Instant.parse("2026-11-20T10:00:00Z");

    @Mock
    private AnuncioService anuncioService;

    @Mock
    private AplicacaoService aplicacaoService;

    @Mock
    private ReagendamentoService reagendamentoService;

    @Mock
    private ConsultaCategoria consultaCategoria;

    @Mock
    private ConsultaDadosUsuario consultaDadosUsuario;

    @Mock
    private ConsultaFotoPerfil consultaFotoPerfil;

    @Mock
    private ConsultaPlanoPrestador consultaPlanoPrestador;

    @Mock
    private ConsultaChatDoServico consultaChatDoServico;

    private ConsultarServicoUseCase useCase;

    @BeforeEach
    void montarUseCase() {
        useCase = new ConsultarServicoUseCase(anuncioService, aplicacaoService, reagendamentoService,
                consultaCategoria, consultaDadosUsuario, consultaFotoPerfil, consultaPlanoPrestador,
                consultaChatDoServico);
    }

    private Anuncio anuncio(StatusAnuncio status) {
        Anuncio anuncio = Anuncio.builder()
                .contratanteId(EMPREGADOR_ID)
                .titulo("Instalar 3 tomadas")
                .categoriaId(UUID.randomUUID())
                .descricao("Instalar 3 tomadas novas na sala")
                .dataHoraPretendida(DATA_PRETENDIDA)
                .bairroRegiaoSnapshot("Pinheiros")
                .enderecoCompletoSnapshot("Rua dos Pinheiros, 100, Pinheiros, São Paulo - SP")
                .status(status)
                .build();
        ReflectionTestUtils.setField(anuncio, "id", ANUNCIO_ID);
        return anuncio;
    }

    private Aplicacao escolhida() {
        Aplicacao aplicacao = Aplicacao.builder()
                .anuncioId(ANUNCIO_ID)
                .prestadorId(PRESTADOR_ID)
                .escolhidaEm(ESCOLHIDA_EM)
                .build();
        ReflectionTestUtils.setField(aplicacao, "id", CANDIDATURA_ID);
        return aplicacao;
    }

    private void semReagendamentosNemChat() {
        when(reagendamentoService.buscarPendentesDosAnuncios(List.of(ANUNCIO_ID))).thenReturn(List.of());
        when(reagendamentoService.contarAceitos(ANUNCIO_ID)).thenReturn(0);
        when(consultaChatDoServico.buscar(any(), any())).thenReturn(Optional.empty());
    }

    private void categoria() {
        when(consultaCategoria.buscarPorId(any()))
                .thenReturn(Optional.of(new ResumoCategoria(UUID.randomUUID(), "Elétrica", "Reparos", true)));
    }

    @Test
    void dadoEmpregadorDono_quandoConsulta_entaoVeOPrestadorComoOutraParte() {
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.of(anuncio(StatusAnuncio.EM_ANDAMENTO)));
        when(aplicacaoService.buscarEscolhidaDoAnuncio(ANUNCIO_ID)).thenReturn(Optional.of(escolhida()));
        categoria();
        semReagendamentosNemChat();
        when(consultaDadosUsuario.buscarPorId(PRESTADOR_ID)).thenReturn(
                Optional.of(new DadosUsuario("Rafael Souza", "rafael@example.com", "+5511999990000", null, null)));
        when(consultaFotoPerfil.buscarFoto(PRESTADOR_ID)).thenReturn(Optional.empty());
        when(consultaPlanoPrestador.planoAtual(PRESTADOR_ID)).thenReturn(Plano.FREE);

        ServicoCombinado servico = useCase.executar(EMPREGADOR_ID, ANUNCIO_ID);

        assertThat(servico.outraParte().nome()).isEqualTo("Rafael Souza");
        assertThat(servico.outraParte().telefone()).isEqualTo("+5511999990000");
        assertThat(servico.outraParte().gold()).isFalse();
        assertThat(servico.escolhidaEm()).isEqualTo(ESCOLHIDA_EM);
        assertThat(servico.prazoCancelamentoEm()).isEqualTo(DATA_PRETENDIDA.minus(Duration.ofHours(24)));
    }

    @Test
    void dadoPrestadorEscolhido_quandoConsulta_entaoVeOEmpregadorSemGold() {
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.of(anuncio(StatusAnuncio.EM_ANDAMENTO)));
        when(aplicacaoService.buscarEscolhidaDoAnuncio(ANUNCIO_ID)).thenReturn(Optional.of(escolhida()));
        categoria();
        semReagendamentosNemChat();
        when(consultaDadosUsuario.buscarPorId(EMPREGADOR_ID)).thenReturn(
                Optional.of(new DadosUsuario("Maria Silva", "maria@example.com", "+5511988880000", null, null)));
        when(consultaFotoPerfil.buscarFoto(EMPREGADOR_ID)).thenReturn(Optional.empty());

        ServicoCombinado servico = useCase.executar(PRESTADOR_ID, ANUNCIO_ID);

        assertThat(servico.outraParte().nome()).isEqualTo("Maria Silva");
        assertThat(servico.outraParte().gold()).isNull();
    }

    @Test
    void dadoPrestadorNaoEscolhido_quandoConsulta_entao404() {
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.of(anuncio(StatusAnuncio.EM_ANDAMENTO)));
        when(aplicacaoService.buscarEscolhidaDoAnuncio(ANUNCIO_ID)).thenReturn(Optional.of(escolhida()));

        assertThatThrownBy(() -> useCase.executar(OUTRO_PRESTADOR_ID, ANUNCIO_ID))
                .isInstanceOf(ServicoNaoEncontradoException.class);
    }

    @Test
    void dadoAnuncioInexistente_quandoConsulta_entao404() {
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(EMPREGADOR_ID, ANUNCIO_ID))
                .isInstanceOf(ServicoNaoEncontradoException.class);
    }

    @Test
    void dadoSemCandidaturaEscolhidaAinda_quandoEmpregadorConsulta_entao404() {
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.of(anuncio(StatusAnuncio.PUBLICADO)));
        when(aplicacaoService.buscarEscolhidaDoAnuncio(ANUNCIO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(EMPREGADOR_ID, ANUNCIO_ID))
                .isInstanceOf(ServicoNaoEncontradoException.class);
    }

    @Test
    void dadoServicoCancelado_quandoPrestadorEscolhidoConsulta_entaoAindaAcessa() {
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.of(anuncio(StatusAnuncio.ENCERRADO)));
        when(aplicacaoService.buscarEscolhidaDoAnuncio(ANUNCIO_ID)).thenReturn(Optional.of(escolhida()));
        categoria();
        semReagendamentosNemChat();
        when(consultaDadosUsuario.buscarPorId(EMPREGADOR_ID)).thenReturn(
                Optional.of(new DadosUsuario("Maria Silva", "maria@example.com", "+5511988880000", null, null)));
        when(consultaFotoPerfil.buscarFoto(EMPREGADOR_ID)).thenReturn(Optional.empty());

        ServicoCombinado servico = useCase.executar(PRESTADOR_ID, ANUNCIO_ID);

        assertThat(servico.anuncio().getStatus()).isEqualTo(StatusAnuncio.ENCERRADO);
        assertThat(servico.outraParte().nome()).isEqualTo("Maria Silva");
    }

    @Test
    void dadoContaDaOutraParteEncerrada_quandoConsulta_entaoOutraParteVazia() {
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.of(anuncio(StatusAnuncio.EM_ANDAMENTO)));
        when(aplicacaoService.buscarEscolhidaDoAnuncio(ANUNCIO_ID)).thenReturn(Optional.of(escolhida()));
        categoria();
        semReagendamentosNemChat();
        when(consultaDadosUsuario.buscarPorId(PRESTADOR_ID)).thenReturn(Optional.empty());

        ServicoCombinado servico = useCase.executar(EMPREGADOR_ID, ANUNCIO_ID);

        assertThat(servico.outraParte()).isEqualTo(OutraParteDoServico.CONTA_ENCERRADA);
    }

    @Test
    void dadoPropostaDeReagendamentoPendente_quandoConsulta_entaoTraz() {
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.of(anuncio(StatusAnuncio.EM_ANDAMENTO)));
        when(aplicacaoService.buscarEscolhidaDoAnuncio(ANUNCIO_ID)).thenReturn(Optional.of(escolhida()));
        categoria();
        when(consultaChatDoServico.buscar(any(), any())).thenReturn(Optional.empty());
        when(consultaDadosUsuario.buscarPorId(PRESTADOR_ID)).thenReturn(
                Optional.of(new DadosUsuario("Rafael Souza", "rafael@example.com", "+5511999990000", null, null)));
        when(consultaFotoPerfil.buscarFoto(PRESTADOR_ID)).thenReturn(Optional.empty());
        when(consultaPlanoPrestador.planoAtual(PRESTADOR_ID)).thenReturn(Plano.FREE);
        Instant dataProposta = DATA_PRETENDIDA.plus(Duration.ofDays(2));
        Reagendamento pendente = Reagendamento.builder()
                .anuncioId(ANUNCIO_ID)
                .dataAnterior(DATA_PRETENDIDA)
                .dataProposta(dataProposta)
                .justificativa("Imprevisto")
                .build();
        when(reagendamentoService.buscarPendentesDosAnuncios(List.of(ANUNCIO_ID))).thenReturn(List.of(pendente));
        when(reagendamentoService.contarAceitos(ANUNCIO_ID)).thenReturn(1);

        ServicoCombinado servico = useCase.executar(EMPREGADOR_ID, ANUNCIO_ID);

        assertThat(servico.propostaPendente()).isSameAs(pendente);
        assertThat(servico.reagendamentosAceitos()).isEqualTo(1);
        assertThat(servico.limiteReagendamentos()).isEqualTo(2);
    }

    @Test
    void dadoChatDaCandidatura_quandoConsulta_entaoTrazChatIdEMensagensNaoLidas() {
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.of(anuncio(StatusAnuncio.EM_ANDAMENTO)));
        when(aplicacaoService.buscarEscolhidaDoAnuncio(ANUNCIO_ID)).thenReturn(Optional.of(escolhida()));
        categoria();
        when(reagendamentoService.buscarPendentesDosAnuncios(List.of(ANUNCIO_ID))).thenReturn(List.of());
        when(reagendamentoService.contarAceitos(ANUNCIO_ID)).thenReturn(0);
        when(consultaDadosUsuario.buscarPorId(PRESTADOR_ID)).thenReturn(
                Optional.of(new DadosUsuario("Rafael Souza", "rafael@example.com", "+5511999990000", null, null)));
        when(consultaFotoPerfil.buscarFoto(PRESTADOR_ID)).thenReturn(Optional.empty());
        when(consultaPlanoPrestador.planoAtual(PRESTADOR_ID)).thenReturn(Plano.FREE);
        UUID chatId = UUID.randomUUID();
        when(consultaChatDoServico.buscar(CANDIDATURA_ID, EMPREGADOR_ID))
                .thenReturn(Optional.of(new ResumoChatDoServico(chatId, 3)));

        ServicoCombinado servico = useCase.executar(EMPREGADOR_ID, ANUNCIO_ID);

        assertThat(servico.chat().chatId()).isEqualTo(chatId);
        assertThat(servico.chat().mensagensNaoLidas()).isEqualTo(3);
    }

    @Test
    void dadoFotoDaOutraParte_quandoConsulta_entaoTraz() {
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.of(anuncio(StatusAnuncio.EM_ANDAMENTO)));
        when(aplicacaoService.buscarEscolhidaDoAnuncio(ANUNCIO_ID)).thenReturn(Optional.of(escolhida()));
        categoria();
        semReagendamentosNemChat();
        when(consultaDadosUsuario.buscarPorId(PRESTADOR_ID)).thenReturn(
                Optional.of(new DadosUsuario("Rafael Souza", "rafael@example.com", "+5511999990000", null, null)));
        LinkTemporario foto = new LinkTemporario("https://bucket/assinado", Instant.parse("2026-10-27T18:00:00Z"));
        when(consultaFotoPerfil.buscarFoto(PRESTADOR_ID)).thenReturn(Optional.of(foto));
        when(consultaPlanoPrestador.planoAtual(PRESTADOR_ID)).thenReturn(Plano.FREE);

        ServicoCombinado servico = useCase.executar(EMPREGADOR_ID, ANUNCIO_ID);

        assertThat(servico.outraParte().foto()).isEqualTo(foto);
    }
}
