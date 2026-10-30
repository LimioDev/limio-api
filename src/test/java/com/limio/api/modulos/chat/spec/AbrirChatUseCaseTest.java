package com.limio.api.modulos.chat.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;
import org.springframework.test.util.ReflectionTestUtils;

import com.limio.api.comum.midia.LinkTemporario;
import com.limio.api.comum.portas.ConsultaCandidatura;
import com.limio.api.comum.portas.ConsultaDadosUsuario;
import com.limio.api.comum.portas.ConsultaFotoPerfil;
import com.limio.api.comum.portas.ConsultaPerfilEmpregador;
import com.limio.api.comum.portas.DadosUsuario;
import com.limio.api.comum.portas.ResumoCandidatura;
import com.limio.api.comum.portas.ResumoPerfilEmpregador;
import com.limio.api.modulos.chat.Chat;
import com.limio.api.modulos.chat.ChatAberto;
import com.limio.api.modulos.chat.ChatService;
import com.limio.api.modulos.chat.OutraParte;
import com.limio.api.modulos.chat.actions.usecase.AbrirChatUseCase;
import com.limio.api.modulos.chat.enums.PapelNoChat;
import com.limio.api.modulos.chat.excecao.ChatNaoEncontradoException;
import com.limio.api.modulos.chat.mensagem.MensagemService;

@ExtendWith(MockitoExtension.class)
class AbrirChatUseCaseTest {

    private static final UUID APLICACAO_ID = UUID.randomUUID();
    private static final UUID ANUNCIO_ID = UUID.randomUUID();
    private static final UUID CHAT_ID = UUID.randomUUID();
    private static final UUID CONTRATANTE_ID = UUID.randomUUID();
    private static final UUID PRESTADOR_ID = UUID.randomUUID();
    private static final Instant EXPIRA = Instant.now().plusSeconds(600);

    @Mock
    private ChatService chatService;

    @Mock
    private MensagemService mensagemService;

    @Mock
    private ConsultaCandidatura consultaCandidatura;

    @Mock
    private ConsultaDadosUsuario consultaDadosUsuario;

    @Mock
    private ConsultaPerfilEmpregador consultaPerfilEmpregador;

    @Mock
    private ConsultaFotoPerfil consultaFotoPerfil;

    private AbrirChatUseCase useCase;

    @BeforeEach
    void montarUseCase() {
        useCase = new AbrirChatUseCase(chatService, mensagemService, consultaCandidatura, consultaDadosUsuario,
                consultaPerfilEmpregador, consultaFotoPerfil);
    }

    private void candidaturaEnviada() {
        when(consultaCandidatura.buscarPorId(APLICACAO_ID)).thenReturn(Optional.of(new ResumoCandidatura(APLICACAO_ID,
                "ENVIADA", ANUNCIO_ID, CONTRATANTE_ID, PRESTADOR_ID, "Instalar 3 tomadas", "Elétrica")));
    }

    private void chatDaCandidatura() {
        candidaturaEnviada();
        Chat chat = Chat.builder()
                .aplicacaoId(APLICACAO_ID)
                .anuncioId(ANUNCIO_ID)
                .contratanteId(CONTRATANTE_ID)
                .prestadorId(PRESTADOR_ID)
                .build();
        ReflectionTestUtils.setField(chat, "id", CHAT_ID);
        when(chatService.garantirDaCandidatura(APLICACAO_ID, ANUNCIO_ID, CONTRATANTE_ID, PRESTADOR_ID)).thenReturn(chat);
    }

    private void historicoVazio(UUID antesDe, int tamanho) {
        when(mensagemService.buscarHistorico(CHAT_ID, antesDe, tamanho))
                .thenReturn(new SliceImpl<>(List.of(), PageRequest.ofSize(tamanho), false));
    }

    private static DadosUsuario dados(String nome) {
        return new DadosUsuario(nome, "x@example.com", null, null, null);
    }

    @Test
    void dadoEmpregadorAbrindo_quandoAbre_entaoVeOCandidatoPeloNomeDoCadastroEComFoto() {
        chatDaCandidatura();
        historicoVazio(null, 30);
        when(consultaDadosUsuario.buscarPorId(PRESTADOR_ID)).thenReturn(Optional.of(dados("Rafael Souza")));
        when(consultaFotoPerfil.buscarFoto(PRESTADOR_ID)).thenReturn(Optional.of(new LinkTemporario("https://foto", EXPIRA)));

        ChatAberto aberto = useCase.executar(APLICACAO_ID, CONTRATANTE_ID, null, 30);

        assertThat(aberto.outraParte())
                .isEqualTo(new OutraParte("Rafael Souza", "https://foto", EXPIRA, PapelNoChat.CANDIDATO));
        assertThat(aberto.candidatura().anuncioTitulo()).isEqualTo("Instalar 3 tomadas");
        verifyNoInteractions(consultaPerfilEmpregador);
    }

    @Test
    void dadoPrestadorAbrindo_quandoAbre_entaoVeOEmpregadorPeloNomeDeExibicaoSemCriarPerfil() {
        chatDaCandidatura();
        historicoVazio(null, 30);
        when(consultaPerfilEmpregador.buscarResumoSemCriar(CONTRATANTE_ID))
                .thenReturn(Optional.of(new ResumoPerfilEmpregador("Marina Bertoldi", null, null, null, 0)));

        ChatAberto aberto = useCase.executar(APLICACAO_ID, PRESTADOR_ID, null, 30);

        assertThat(aberto.outraParte()).isEqualTo(new OutraParte("Marina Bertoldi", null, null, PapelNoChat.EMPREGADOR));
        verifyNoInteractions(consultaFotoPerfil, consultaDadosUsuario);
    }

    @Test
    void dadaOutraParteComContaEncerrada_quandoAbre_entaoUsuarioExcluidoSemFoto() {
        chatDaCandidatura();
        historicoVazio(null, 30);
        when(consultaDadosUsuario.buscarPorId(PRESTADOR_ID)).thenReturn(Optional.empty());

        ChatAberto aberto = useCase.executar(APLICACAO_ID, CONTRATANTE_ID, null, 30);

        assertThat(aberto.outraParte()).isEqualTo(OutraParte.contaEncerrada(PapelNoChat.CANDIDATO));
        assertThat(aberto.outraParte().nome()).isEqualTo("Usuário excluído");
        verifyNoInteractions(consultaFotoPerfil, consultaPerfilEmpregador);
    }

    @Test
    void dadaReferenciaDeMensagem_quandoAbre_entaoBuscaAsAnterioresAEla() {
        UUID antesDe = UUID.randomUUID();
        chatDaCandidatura();
        historicoVazio(antesDe, 10);
        when(consultaDadosUsuario.buscarPorId(PRESTADOR_ID)).thenReturn(Optional.empty());

        ChatAberto aberto = useCase.executar(APLICACAO_ID, CONTRATANTE_ID, antesDe, 10);

        verify(mensagemService).buscarHistorico(CHAT_ID, antesDe, 10);
        assertThat(aberto.tamanho()).isEqualTo(10);
        assertThat(aberto.temMais()).isFalse();
    }

    @Test
    void dadoQuemNaoEParte_quandoAbre_entaoChatNaoEncontradoSemCriarChat() {
        UUID terceiro = UUID.randomUUID();
        candidaturaEnviada();

        assertThatThrownBy(() -> useCase.executar(APLICACAO_ID, terceiro, null, 30))
                .isInstanceOf(ChatNaoEncontradoException.class);
        verifyNoInteractions(chatService, consultaDadosUsuario, mensagemService);
    }

    @Test
    void dadaCandidaturaInexistente_quandoAbre_entaoChatNaoEncontrado() {
        when(consultaCandidatura.buscarPorId(APLICACAO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(APLICACAO_ID, CONTRATANTE_ID, null, 30))
                .isInstanceOf(ChatNaoEncontradoException.class);
        verifyNoInteractions(chatService);
    }
}
