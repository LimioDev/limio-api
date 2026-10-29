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

import com.limio.api.comum.portas.ConsultaCandidatura;
import com.limio.api.comum.portas.ConsultaDadosUsuario;
import com.limio.api.comum.portas.ConsultaFotoPerfil;
import com.limio.api.comum.portas.ConsultaPerfilEmpregador;
import com.limio.api.comum.portas.DadosUsuario;
import com.limio.api.comum.portas.FotoPerfil;
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

    private void chatDaCandidatura(UUID quemAbre) {
        Chat chat = Chat.daCandidatura(APLICACAO_ID, UUID.randomUUID(), CONTRATANTE_ID, PRESTADOR_ID);
        ReflectionTestUtils.setField(chat, "id", CHAT_ID);
        when(chatService.buscarDaCandidaturaDoParticipante(APLICACAO_ID, quemAbre)).thenReturn(chat);
        when(consultaCandidatura.buscarPorId(APLICACAO_ID))
                .thenReturn(Optional.of(new ResumoCandidatura(APLICACAO_ID, "ENVIADA", "Instalar 3 tomadas", "Elétrica")));
    }

    private void historicoVazio(int pagina, int tamanho) {
        when(mensagemService.buscarHistorico(CHAT_ID, pagina, tamanho))
                .thenReturn(new SliceImpl<>(List.of(), PageRequest.of(pagina, tamanho), false));
    }

    private static DadosUsuario dados(String nome) {
        return new DadosUsuario(nome, "x@example.com", null, null, null);
    }

    @Test
    void dadoEmpregadorAbrindo_quandoAbre_entaoVeOCandidatoPeloNomeDoCadastroEComFoto() {
        chatDaCandidatura(CONTRATANTE_ID);
        historicoVazio(0, 30);
        when(consultaDadosUsuario.buscarPorId(PRESTADOR_ID)).thenReturn(Optional.of(dados("Rafael Souza")));
        when(consultaFotoPerfil.buscarFoto(PRESTADOR_ID)).thenReturn(Optional.of(new FotoPerfil("https://foto", EXPIRA)));

        ChatAberto aberto = useCase.executar(APLICACAO_ID, CONTRATANTE_ID, 0, 30);

        assertThat(aberto.outraParte())
                .isEqualTo(new OutraParte("Rafael Souza", "https://foto", EXPIRA, PapelNoChat.CANDIDATO));
        assertThat(aberto.candidatura().anuncioTitulo()).isEqualTo("Instalar 3 tomadas");
        verifyNoInteractions(consultaPerfilEmpregador);
    }

    @Test
    void dadoPrestadorAbrindo_quandoAbre_entaoVeOEmpregadorPeloNomeDeExibicao() {
        chatDaCandidatura(PRESTADOR_ID);
        historicoVazio(0, 30);
        when(consultaDadosUsuario.buscarPorId(CONTRATANTE_ID)).thenReturn(Optional.of(dados("Marina Bertoldi Costa")));
        when(consultaPerfilEmpregador.buscarResumo(CONTRATANTE_ID))
                .thenReturn(new ResumoPerfilEmpregador("Marina Bertoldi", null, null));

        ChatAberto aberto = useCase.executar(APLICACAO_ID, PRESTADOR_ID, 0, 30);

        assertThat(aberto.outraParte()).isEqualTo(new OutraParte("Marina Bertoldi", null, null, PapelNoChat.EMPREGADOR));
        verifyNoInteractions(consultaFotoPerfil);
    }

    @Test
    void dadaOutraParteComContaEncerrada_quandoAbre_entaoUsuarioExcluidoSemFoto() {
        chatDaCandidatura(CONTRATANTE_ID);
        historicoVazio(0, 30);
        when(consultaDadosUsuario.buscarPorId(PRESTADOR_ID)).thenReturn(Optional.empty());

        ChatAberto aberto = useCase.executar(APLICACAO_ID, CONTRATANTE_ID, 0, 30);

        assertThat(aberto.outraParte()).isEqualTo(OutraParte.contaEncerrada(PapelNoChat.CANDIDATO));
        assertThat(aberto.outraParte().nome()).isEqualTo("Usuário excluído");
        verifyNoInteractions(consultaFotoPerfil, consultaPerfilEmpregador);
    }

    @Test
    void dadaPaginacaoForaDoLimite_quandoAbre_entaoAjustaAoIntervaloAceito() {
        chatDaCandidatura(CONTRATANTE_ID);
        historicoVazio(0, AbrirChatUseCase.TAMANHO_PAGINA_MAXIMO);
        when(consultaDadosUsuario.buscarPorId(PRESTADOR_ID)).thenReturn(Optional.empty());

        ChatAberto aberto = useCase.executar(APLICACAO_ID, CONTRATANTE_ID, -1, 5000);

        verify(mensagemService).buscarHistorico(CHAT_ID, 0, AbrirChatUseCase.TAMANHO_PAGINA_MAXIMO);
        assertThat(aberto.pagina()).isZero();
        assertThat(aberto.tamanho()).isEqualTo(AbrirChatUseCase.TAMANHO_PAGINA_MAXIMO);
    }

    @Test
    void dadoQuemNaoEParte_quandoAbre_entaoChatNaoEncontrado() {
        UUID terceiro = UUID.randomUUID();
        when(chatService.buscarDaCandidaturaDoParticipante(APLICACAO_ID, terceiro))
                .thenThrow(new ChatNaoEncontradoException());

        assertThatThrownBy(() -> useCase.executar(APLICACAO_ID, terceiro, 0, 30))
                .isInstanceOf(ChatNaoEncontradoException.class);
        verifyNoInteractions(consultaCandidatura, consultaDadosUsuario, mensagemService);
    }
}
