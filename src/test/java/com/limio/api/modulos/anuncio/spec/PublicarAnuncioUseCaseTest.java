package com.limio.api.modulos.anuncio.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionOperations;

import com.limio.api.comum.localizacao.enums.Uf;
import com.limio.api.comum.portas.ConsultaCategoria;
import com.limio.api.comum.portas.ConsultaDadosUsuario;
import com.limio.api.comum.portas.ConsultaEndereco;
import com.limio.api.comum.portas.DadosUsuario;
import com.limio.api.comum.portas.EnderecoInformado;
import com.limio.api.comum.portas.EnderecoLocalizado;
import com.limio.api.comum.portas.ResumoCategoria;
import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.EnderecoDoAnuncio;
import com.limio.api.modulos.anuncio.actions.usecase.PublicarAnuncioUseCase;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;
import com.limio.api.modulos.anuncio.eventos.AnuncioPublicadoEvent;
import com.limio.api.modulos.anuncio.excecao.CategoriaIndisponivelException;
import com.limio.api.modulos.anuncio.excecao.ContatosNaoConfirmadosException;
import com.limio.api.modulos.anuncio.excecao.DataNoPassadoException;
import com.limio.api.modulos.anuncio.excecao.SomenteEmpregadorPublicaException;

@ExtendWith(MockitoExtension.class)
class PublicarAnuncioUseCaseTest {

    private static final UUID USUARIO_ID = UUID.randomUUID();
    private static final UUID CATEGORIA_ID = UUID.randomUUID();
    private static final UUID ENDERECO_SALVO_ID = UUID.randomUUID();
    private static final int PRAZO_VALIDACAO_HORAS = 24;
    private static final EnderecoLocalizado LOCALIZADO = new EnderecoLocalizado("Pinheiros", Uf.SP,
            "Rua dos Pinheiros, 100, Pinheiros, São Paulo - SP", -23.56, -46.68);
    private static final EnderecoInformado AVULSO = new EnderecoInformado("05422000", "Rua dos Pinheiros", "100",
            null, "Pinheiros", "São Paulo", Uf.SP);

    @Mock
    private AnuncioService anuncioService;

    @Mock
    private ConsultaDadosUsuario consultaDadosUsuario;

    @Mock
    private ConsultaCategoria consultaCategoria;

    @Mock
    private ConsultaEndereco consultaEndereco;

    @Mock
    private ApplicationEventPublisher eventos;

    private PublicarAnuncioUseCase useCase;

    @BeforeEach
    void montarUseCase() {
        useCase = new PublicarAnuncioUseCase(anuncioService, consultaDadosUsuario, consultaCategoria,
                consultaEndereco, eventos, TransactionOperations.withoutTransaction(), PRAZO_VALIDACAO_HORAS);
    }

    private static Anuncio candidato(Instant dataHoraPretendida) {
        return Anuncio.builder()
                .categoriaId(CATEGORIA_ID)
                .descricao("Faxina completa")
                .dataHoraPretendida(dataHoraPretendida)
                .build();
    }

    private static Anuncio candidato() {
        return candidato(Instant.now().plus(Duration.ofDays(2)));
    }

    private void contatos(Instant emailConfirmadoEm, Instant telefoneConfirmadoEm) {
        when(consultaDadosUsuario.buscarPorId(USUARIO_ID)).thenReturn(Optional.of(new DadosUsuario("Maria Souza",
                "maria@example.com", "+5511999998888", emailConfirmadoEm, telefoneConfirmadoEm)));
    }

    private void categoria(boolean ativa) {
        when(consultaCategoria.buscarPorId(CATEGORIA_ID))
                .thenReturn(Optional.of(new ResumoCategoria(CATEGORIA_ID, "Limpeza", "Limpeza", ativa)));
    }

    private void salvarDevolvendoComId() {
        when(anuncioService.salvar(any())).thenAnswer(chamada -> {
            Anuncio anuncio = chamada.getArgument(0);
            ReflectionTestUtils.setField(anuncio, "id", UUID.randomUUID());
            return anuncio;
        });
    }

    @Test
    void dadoEnderecoSalvo_quandoPublica_entaoGravaPublicadoComRetratoDoEnderecoEPublicaEvento() {
        contatos(Instant.now(), Instant.now());
        categoria(true);
        when(consultaEndereco.buscarSalvo(USUARIO_ID, ENDERECO_SALVO_ID)).thenReturn(LOCALIZADO);
        salvarDevolvendoComId();

        Anuncio publicado = useCase.executar(USUARIO_ID, PapelUsuario.EMPREGADOR, candidato(),
                new EnderecoDoAnuncio.Salvo(ENDERECO_SALVO_ID));

        assertThat(publicado.getStatus()).isEqualTo(StatusAnuncio.PUBLICADO);
        assertThat(publicado.getContratanteId()).isEqualTo(USUARIO_ID);
        assertThat(publicado.getEnderecoSalvoId()).isEqualTo(ENDERECO_SALVO_ID);
        assertThat(publicado.getBairroRegiaoSnapshot()).isEqualTo("Pinheiros");
        assertThat(publicado.getEnderecoCompletoSnapshot()).isEqualTo(LOCALIZADO.enderecoCompleto());
        assertThat(publicado.getLatitudeSnapshot()).isEqualTo(-23.56);
        assertThat(publicado.getLongitudeSnapshot()).isEqualTo(-46.68);
        assertThat(publicado.getPrazoValidacaoHoras()).isEqualTo(PRAZO_VALIDACAO_HORAS);
        ArgumentCaptor<AnuncioPublicadoEvent> evento = ArgumentCaptor.forClass(AnuncioPublicadoEvent.class);
        verify(eventos).publishEvent(evento.capture());
        assertThat(evento.getValue().anuncioId()).isEqualTo(publicado.getId());
    }

    @Test
    void dadoEnderecoAvulso_quandoPublica_entaoLocalizaPeloGeocodificadorESemEnderecoSalvo() {
        contatos(Instant.now(), Instant.now());
        categoria(true);
        when(consultaEndereco.localizar(AVULSO)).thenReturn(LOCALIZADO);
        salvarDevolvendoComId();

        Anuncio publicado = useCase.executar(USUARIO_ID, PapelUsuario.EMPREGADOR, candidato(),
                new EnderecoDoAnuncio.Avulso(AVULSO));

        assertThat(publicado.getEnderecoSalvoId()).isNull();
        assertThat(publicado.getEnderecoCompletoSnapshot()).isEqualTo(LOCALIZADO.enderecoCompleto());
        verify(consultaEndereco, never()).buscarSalvo(any(), any());
    }

    @Test
    void dadoPapelPrestador_quandoPublica_entaoRecusaSemConsultarNada() {
        assertThatThrownBy(() -> useCase.executar(USUARIO_ID, PapelUsuario.PRESTADOR, candidato(),
                new EnderecoDoAnuncio.Avulso(AVULSO)))
                .isInstanceOf(SomenteEmpregadorPublicaException.class);
        verifyNoInteractions(consultaDadosUsuario, consultaEndereco, anuncioService, eventos);
    }

    @Test
    void dadoDataNoPassado_quandoPublica_entaoRecusa() {
        assertThatThrownBy(() -> useCase.executar(USUARIO_ID, PapelUsuario.EMPREGADOR,
                candidato(Instant.now().minus(Duration.ofMinutes(1))), new EnderecoDoAnuncio.Avulso(AVULSO)))
                .isInstanceOf(DataNoPassadoException.class);
        verifyNoInteractions(anuncioService, eventos);
    }

    @Test
    void dadoTelefoneNaoConfirmado_quandoPublica_entaoRecusa() {
        contatos(Instant.now(), null);

        assertThatThrownBy(() -> useCase.executar(USUARIO_ID, PapelUsuario.EMPREGADOR, candidato(),
                new EnderecoDoAnuncio.Avulso(AVULSO)))
                .isInstanceOf(ContatosNaoConfirmadosException.class);
        verifyNoInteractions(consultaEndereco, anuncioService);
    }

    @Test
    void dadoEmailNaoConfirmado_quandoPublica_entaoRecusa() {
        contatos(null, Instant.now());

        assertThatThrownBy(() -> useCase.executar(USUARIO_ID, PapelUsuario.EMPREGADOR, candidato(),
                new EnderecoDoAnuncio.Avulso(AVULSO)))
                .isInstanceOf(ContatosNaoConfirmadosException.class);
    }

    @Test
    void dadoContaEncerrada_quandoPublica_entaoRecusaComoContatosNaoConfirmados() {
        when(consultaDadosUsuario.buscarPorId(USUARIO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(USUARIO_ID, PapelUsuario.EMPREGADOR, candidato(),
                new EnderecoDoAnuncio.Avulso(AVULSO)))
                .isInstanceOf(ContatosNaoConfirmadosException.class);
    }

    @Test
    void dadoCategoriaInativa_quandoPublica_entaoRecusaSemGeocodificar() {
        contatos(Instant.now(), Instant.now());
        categoria(false);

        assertThatThrownBy(() -> useCase.executar(USUARIO_ID, PapelUsuario.EMPREGADOR, candidato(),
                new EnderecoDoAnuncio.Avulso(AVULSO)))
                .isInstanceOf(CategoriaIndisponivelException.class);
        verifyNoInteractions(consultaEndereco, anuncioService);
    }

    @Test
    void dadoCategoriaInexistente_quandoPublica_entaoRecusa() {
        contatos(Instant.now(), Instant.now());
        when(consultaCategoria.buscarPorId(CATEGORIA_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(USUARIO_ID, PapelUsuario.EMPREGADOR, candidato(),
                new EnderecoDoAnuncio.Avulso(AVULSO)))
                .isInstanceOf(CategoriaIndisponivelException.class);
    }
}
