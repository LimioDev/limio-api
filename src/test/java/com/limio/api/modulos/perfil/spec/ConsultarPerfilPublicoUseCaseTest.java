package com.limio.api.modulos.perfil.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.limio.api.comum.localizacao.enums.Uf;
import com.limio.api.comum.midia.LinkTemporario;
import com.limio.api.comum.portas.AvaliacaoRecente;
import com.limio.api.comum.portas.ConsultaAvaliacoes;
import com.limio.api.comum.portas.ConsultaDadosUsuario;
import com.limio.api.comum.portas.ConsultaFotoPerfil;
import com.limio.api.comum.portas.ConsultaHistoricoDeServicos;
import com.limio.api.comum.portas.DadosUsuario;
import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.modulos.perfil.PerfilPublico;
import com.limio.api.modulos.perfil.actions.usecase.ConsultarPerfilPublicoUseCase;
import com.limio.api.modulos.perfil.categoria.Categoria;
import com.limio.api.modulos.perfil.empregador.PerfilEmpregador;
import com.limio.api.modulos.perfil.empregador.PerfilEmpregadorService;
import com.limio.api.modulos.perfil.excecao.PerfilPublicoNaoEncontradoException;
import com.limio.api.modulos.perfil.geo.Coordenadas;
import com.limio.api.modulos.perfil.perfilprestador.PerfilPrestador;
import com.limio.api.modulos.perfil.perfilprestador.PerfilPrestadorService;

@ExtendWith(MockitoExtension.class)
class ConsultarPerfilPublicoUseCaseTest {

    private static final UUID USUARIO_ID = UUID.randomUUID();
    private static final DadosUsuario MARIA = new DadosUsuario("Maria da Silva", "maria@example.com", null, null, null);
    private static final DadosUsuario RAFAEL = new DadosUsuario("Rafael da Silva Souza", "rafael@example.com", null,
            null, null);

    @Mock
    private ConsultaDadosUsuario consultaDadosUsuario;

    @Mock
    private ConsultaFotoPerfil consultaFotoPerfil;

    @Mock
    private ConsultaAvaliacoes consultaAvaliacoes;

    @Mock
    private ConsultaHistoricoDeServicos consultaHistoricoDeServicos;

    @Mock
    private PerfilEmpregadorService perfilEmpregadorService;

    @Mock
    private PerfilPrestadorService perfilPrestadorService;

    private ConsultarPerfilPublicoUseCase useCase;

    @BeforeEach
    void montarUseCase() {
        useCase = new ConsultarPerfilPublicoUseCase(consultaDadosUsuario, consultaFotoPerfil, consultaAvaliacoes,
                consultaHistoricoDeServicos, perfilEmpregadorService, perfilPrestadorService);
    }

    private static Categoria eletrica() {
        Categoria categoria = Categoria.builder().nome("Elétrica").grupo("Casa e reparos").ordem(1).ativa(true).build();
        ReflectionTestUtils.setField(categoria, "id", UUID.randomUUID());
        return categoria;
    }

    @Test
    void dadoUsuarioInexistente_quandoConsulta_entaoLancaPerfilNaoEncontrado() {
        when(consultaDadosUsuario.buscarPorId(USUARIO_ID)).thenReturn(Optional.empty());
        when(consultaDadosUsuario.existe(USUARIO_ID)).thenReturn(false);

        assertThatThrownBy(() -> useCase.executar(USUARIO_ID, PapelUsuario.EMPREGADOR))
                .isInstanceOf(PerfilPublicoNaoEncontradoException.class);
        verify(perfilEmpregadorService, never()).buscarPorUsuario(USUARIO_ID);
    }

    @Test
    void dadoEmpregadorComAvaliacoes_quandoConsulta_entaoDevolveNomeDeExibicaoEMedia() {
        when(consultaDadosUsuario.buscarPorId(USUARIO_ID)).thenReturn(Optional.of(MARIA));
        PerfilEmpregador perfil = PerfilEmpregador.builder().usuarioId(USUARIO_ID).nomeExibicao("Mari")
                .mediaAvaliacoes(new BigDecimal("4.5")).qtdAvaliacoes(8).build();
        when(perfilEmpregadorService.buscarPorUsuario(USUARIO_ID)).thenReturn(Optional.of(perfil));
        when(consultaFotoPerfil.buscarFoto(USUARIO_ID))
                .thenReturn(Optional.of(new LinkTemporario("https://bucket/mari", Instant.EPOCH)));
        when(consultaHistoricoDeServicos.contarConcluidos(USUARIO_ID, PapelUsuario.EMPREGADOR)).thenReturn(12);
        when(consultaAvaliacoes.listarRecentes(USUARIO_ID, PapelUsuario.EMPREGADOR, 5)).thenReturn(
                List.of(new AvaliacaoRecente("Camila R.", 5, "Ótimo!", Instant.EPOCH)));

        PerfilPublico publico = useCase.executar(USUARIO_ID, PapelUsuario.EMPREGADOR);

        assertThat(publico.nomeExibicao()).isEqualTo("Mari");
        assertThat(publico.fotoUrl()).isEqualTo("https://bucket/mari");
        assertThat(publico.mediaAvaliacoes()).isEqualTo(new BigDecimal("4.5"));
        assertThat(publico.qtdAvaliacoes()).isEqualTo(8);
        assertThat(publico.servicosConcluidos()).isEqualTo(12);
        assertThat(publico.especialidades()).isEmpty();
        assertThat(publico.avaliacoesRecentes()).extracting(AvaliacaoRecente::avaliadorNome).containsExactly("Camila R.");
    }

    @Test
    void dadoEmpregadorSemAvaliacoes_quandoConsulta_entaoMediaNulaEQuantidadeZero() {
        when(consultaDadosUsuario.buscarPorId(USUARIO_ID)).thenReturn(Optional.of(MARIA));
        when(perfilEmpregadorService.buscarPorUsuario(USUARIO_ID)).thenReturn(Optional.empty());
        when(consultaFotoPerfil.buscarFoto(USUARIO_ID)).thenReturn(Optional.empty());
        when(consultaHistoricoDeServicos.contarConcluidos(USUARIO_ID, PapelUsuario.EMPREGADOR)).thenReturn(0);
        when(consultaAvaliacoes.listarRecentes(USUARIO_ID, PapelUsuario.EMPREGADOR, 5)).thenReturn(List.of());

        PerfilPublico publico = useCase.executar(USUARIO_ID, PapelUsuario.EMPREGADOR);

        assertThat(publico.nomeExibicao()).isEqualTo("Maria da Silva");
        assertThat(publico.fotoUrl()).isNull();
        assertThat(publico.mediaAvaliacoes()).isNull();
        assertThat(publico.qtdAvaliacoes()).isZero();
        assertThat(publico.avaliacoesRecentes()).isEmpty();
    }

    @Test
    void dadoPrestadorComEspecialidadesEAvaliacoes_quandoConsulta_entaoNomeAbreviadoEEspecialidadesOrdenadas() {
        when(consultaDadosUsuario.buscarPorId(USUARIO_ID)).thenReturn(Optional.of(RAFAEL));
        PerfilPrestador perfil = PerfilPrestador.novo(USUARIO_ID);
        perfil.atualizarAtuacao(Set.of(eletrica()), 10, "Campinas", Uf.SP, new Coordenadas(-22.9, -47.0), List.of());
        perfil.registrarAvaliacoes(new BigDecimal("5.0"), 3);
        when(perfilPrestadorService.buscarPorUsuario(USUARIO_ID)).thenReturn(Optional.of(perfil));
        when(consultaFotoPerfil.buscarFoto(USUARIO_ID)).thenReturn(Optional.empty());
        when(consultaHistoricoDeServicos.contarConcluidos(USUARIO_ID, PapelUsuario.PRESTADOR)).thenReturn(3);
        when(consultaAvaliacoes.listarRecentes(USUARIO_ID, PapelUsuario.PRESTADOR, 5)).thenReturn(List.of());

        PerfilPublico publico = useCase.executar(USUARIO_ID, PapelUsuario.PRESTADOR);

        assertThat(publico.nomeExibicao()).isEqualTo("Rafael S.");
        assertThat(publico.especialidades()).containsExactly("Elétrica");
        assertThat(publico.mediaAvaliacoes()).isEqualTo(new BigDecimal("5.0"));
        assertThat(publico.qtdAvaliacoes()).isEqualTo(3);
    }

    @Test
    void dadoPrestadorSemPerfilDeAtuacao_quandoConsulta_entaoSemAvaliacoesESemEspecialidades() {
        when(consultaDadosUsuario.buscarPorId(USUARIO_ID)).thenReturn(Optional.of(RAFAEL));
        when(perfilPrestadorService.buscarPorUsuario(USUARIO_ID)).thenReturn(Optional.empty());
        when(consultaFotoPerfil.buscarFoto(USUARIO_ID)).thenReturn(Optional.empty());
        when(consultaHistoricoDeServicos.contarConcluidos(USUARIO_ID, PapelUsuario.PRESTADOR)).thenReturn(0);
        when(consultaAvaliacoes.listarRecentes(USUARIO_ID, PapelUsuario.PRESTADOR, 5)).thenReturn(List.of());

        PerfilPublico publico = useCase.executar(USUARIO_ID, PapelUsuario.PRESTADOR);

        assertThat(publico.mediaAvaliacoes()).isNull();
        assertThat(publico.qtdAvaliacoes()).isZero();
        assertThat(publico.especialidades()).isEmpty();
    }

    @Test
    void dadoContaEncerrada_quandoConsultaComoEmpregador_entaoNomeContaEncerradaSemFotoMasComHistorico() {
        when(consultaDadosUsuario.buscarPorId(USUARIO_ID)).thenReturn(Optional.empty());
        when(consultaDadosUsuario.existe(USUARIO_ID)).thenReturn(true);
        PerfilEmpregador perfilAnonimizado = PerfilEmpregador.builder().usuarioId(USUARIO_ID)
                .nomeExibicao(PerfilEmpregador.NOME_CONTA_ENCERRADA)
                .mediaAvaliacoes(new BigDecimal("4.0")).qtdAvaliacoes(5).build();
        when(perfilEmpregadorService.buscarPorUsuario(USUARIO_ID)).thenReturn(Optional.of(perfilAnonimizado));
        when(consultaFotoPerfil.buscarFoto(USUARIO_ID)).thenReturn(Optional.empty());
        when(consultaHistoricoDeServicos.contarConcluidos(USUARIO_ID, PapelUsuario.EMPREGADOR)).thenReturn(7);
        when(consultaAvaliacoes.listarRecentes(USUARIO_ID, PapelUsuario.EMPREGADOR, 5)).thenReturn(List.of());

        PerfilPublico publico = useCase.executar(USUARIO_ID, PapelUsuario.EMPREGADOR);

        assertThat(publico.nomeExibicao()).isEqualTo("Conta encerrada");
        assertThat(publico.fotoUrl()).isNull();
        assertThat(publico.mediaAvaliacoes()).isEqualTo(new BigDecimal("4.0"));
        assertThat(publico.qtdAvaliacoes()).isEqualTo(5);
        assertThat(publico.servicosConcluidos()).isEqualTo(7);
    }

    @Test
    void dadoContaEncerradaSemPerfilNuncaAberto_quandoConsultaComoPrestador_entaoNomeContaEncerradaESemEspecialidades() {
        when(consultaDadosUsuario.buscarPorId(USUARIO_ID)).thenReturn(Optional.empty());
        when(consultaDadosUsuario.existe(USUARIO_ID)).thenReturn(true);
        when(perfilPrestadorService.buscarPorUsuario(USUARIO_ID)).thenReturn(Optional.empty());
        when(consultaFotoPerfil.buscarFoto(USUARIO_ID)).thenReturn(Optional.empty());
        when(consultaHistoricoDeServicos.contarConcluidos(USUARIO_ID, PapelUsuario.PRESTADOR)).thenReturn(2);
        when(consultaAvaliacoes.listarRecentes(USUARIO_ID, PapelUsuario.PRESTADOR, 5)).thenReturn(List.of());

        PerfilPublico publico = useCase.executar(USUARIO_ID, PapelUsuario.PRESTADOR);

        assertThat(publico.nomeExibicao()).isEqualTo("Conta encerrada");
        assertThat(publico.especialidades()).isEmpty();
        assertThat(publico.servicosConcluidos()).isEqualTo(2);
    }
}
