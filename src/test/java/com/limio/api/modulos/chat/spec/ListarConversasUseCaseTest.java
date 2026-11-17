package com.limio.api.modulos.chat.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.limio.api.comum.midia.LinkTemporario;
import com.limio.api.comum.portas.ConsultaCandidatura;
import com.limio.api.comum.portas.ConsultaDadosUsuario;
import com.limio.api.comum.portas.ConsultaFotoPerfil;
import com.limio.api.comum.portas.ConsultaPerfilEmpregador;
import com.limio.api.comum.portas.DadosUsuario;
import com.limio.api.comum.portas.ResumoCandidatura;
import com.limio.api.comum.portas.ResumoPerfilEmpregador;
import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.modulos.chat.ChatService;
import com.limio.api.modulos.chat.ConversaProjecao;
import com.limio.api.modulos.chat.ConversaResumo;
import com.limio.api.modulos.chat.ConversasListadas;
import com.limio.api.modulos.chat.OutraParte;
import com.limio.api.modulos.chat.actions.usecase.ListarConversasUseCase;
import com.limio.api.modulos.chat.enums.MotivoSomenteLeitura;
import com.limio.api.modulos.chat.enums.PapelNoChat;

@ExtendWith(MockitoExtension.class)
class ListarConversasUseCaseTest {

    private static final UUID EMPREGADOR_ID = UUID.randomUUID();
    private static final UUID PRESTADOR_ID = UUID.randomUUID();
    private static final UUID CANDIDATURA_ID = UUID.randomUUID();
    private static final Instant ENVIADA_EM = Instant.parse("2026-11-10T12:00:00Z");

    @Mock
    private ChatService chatService;

    @Mock
    private ConsultaCandidatura consultaCandidatura;

    @Mock
    private ConsultaDadosUsuario consultaDadosUsuario;

    @Mock
    private ConsultaPerfilEmpregador consultaPerfilEmpregador;

    @Mock
    private ConsultaFotoPerfil consultaFotoPerfil;

    private ListarConversasUseCase useCase;

    @BeforeEach
    void montarUseCase() {
        useCase = new ListarConversasUseCase(chatService, consultaCandidatura, consultaDadosUsuario,
                consultaPerfilEmpregador, consultaFotoPerfil);
    }

    private record Linha(UUID chatId, UUID candidaturaId, UUID contratanteId, UUID prestadorId, boolean somenteLeitura,
            String motivoSomenteLeitura, String ultimaMensagem, Instant ultimaMensagemEm,
            UUID ultimaMensagemRemetenteId, long naoLidas) implements ConversaProjecao {

        @Override
        public UUID getChatId() {
            return chatId;
        }

        @Override
        public UUID getCandidaturaId() {
            return candidaturaId;
        }

        @Override
        public UUID getContratanteId() {
            return contratanteId;
        }

        @Override
        public UUID getPrestadorId() {
            return prestadorId;
        }

        @Override
        public boolean getSomenteLeitura() {
            return somenteLeitura;
        }

        @Override
        public String getMotivoSomenteLeitura() {
            return motivoSomenteLeitura;
        }

        @Override
        public String getUltimaMensagem() {
            return ultimaMensagem;
        }

        @Override
        public Instant getUltimaMensagemEm() {
            return ultimaMensagemEm;
        }

        @Override
        public UUID getUltimaMensagemRemetenteId() {
            return ultimaMensagemRemetenteId;
        }

        @Override
        public long getNaoLidas() {
            return naoLidas;
        }
    }

    private static Linha aberta(long naoLidas, UUID remetente) {
        return new Linha(UUID.randomUUID(), CANDIDATURA_ID, EMPREGADOR_ID, PRESTADOR_ID, false, null, "Combinado!",
                ENVIADA_EM, remetente, naoLidas);
    }

    private static ResumoCandidatura candidatura(String status) {
        return new ResumoCandidatura(CANDIDATURA_ID, status, UUID.randomUUID(), EMPREGADOR_ID, PRESTADOR_ID,
                "Instalar 3 tomadas", "Elétrica");
    }

    private void empregadorVe(Linha... linhas) {
        when(chatService.listarDoContratante(EMPREGADOR_ID, 0, 20))
                .thenReturn(new PageImpl<>(List.of(linhas), PageRequest.of(0, 20), linhas.length));
    }

    private void prestadorVe(Linha... linhas) {
        when(chatService.listarDoPrestador(PRESTADOR_ID, 0, 20))
                .thenReturn(new PageImpl<>(List.of(linhas), PageRequest.of(0, 20), linhas.length));
    }

    private void prestadorCadastrado(String nome, LinkTemporario foto) {
        when(consultaDadosUsuario.buscarPorIds(List.of(PRESTADOR_ID)))
                .thenReturn(Map.of(PRESTADOR_ID, new DadosUsuario(nome, "p@example.com", null, null)));
        when(consultaFotoPerfil.buscarFotosEmLote(anyCollection()))
                .thenReturn(foto == null ? Map.of() : Map.of(PRESTADOR_ID, foto));
    }

    @Test
    void dadoPapelEmpregador_quandoLista_entaoVeOsChatsDosAnunciosComOPrestadorComoOutraParte() {
        Instant expiraEm = Instant.now();
        empregadorVe(aberta(0, PRESTADOR_ID));
        when(chatService.contarDoContratanteComNaoLidas(EMPREGADOR_ID)).thenReturn(0L);
        when(consultaCandidatura.buscarResumosEmLote(List.of(CANDIDATURA_ID)))
                .thenReturn(Map.of(CANDIDATURA_ID, candidatura("ENVIADA")));
        prestadorCadastrado("Rafael Souza", new LinkTemporario("https://bucket/rafael", expiraEm));

        ConversasListadas listadas = useCase.executar(EMPREGADOR_ID, PapelUsuario.EMPREGADOR, 0, 20);

        ConversaResumo item = listadas.conversas().getContent().getFirst();
        assertThat(item.outraParte()).isEqualTo(
                new OutraParte("Rafael Souza", "https://bucket/rafael", expiraEm, PapelNoChat.CANDIDATO));
        assertThat(item.tituloAnuncio()).isEqualTo("Instalar 3 tomadas");
        assertThat(item.statusCandidatura()).isEqualTo("ENVIADA");
        verify(chatService, never()).listarDoPrestador(EMPREGADOR_ID, 0, 20);
        verifyNoInteractions(consultaPerfilEmpregador);
    }

    @Test
    void dadoPapelPrestador_quandoLista_entaoVeOsChatsDasCandidaturasComOEmpregadorComoOutraParte() {
        Instant expiraEm = Instant.now();
        prestadorVe(aberta(0, EMPREGADOR_ID));
        when(chatService.contarDoPrestadorComNaoLidas(PRESTADOR_ID)).thenReturn(0L);
        when(consultaCandidatura.buscarResumosEmLote(List.of(CANDIDATURA_ID)))
                .thenReturn(Map.of(CANDIDATURA_ID, candidatura("ESCOLHIDA")));
        when(consultaPerfilEmpregador.buscarResumosEmLote(List.of(EMPREGADOR_ID))).thenReturn(Map.of(EMPREGADOR_ID,
                new ResumoPerfilEmpregador("Mari", "https://bucket/mari", expiraEm, null, 0)));

        ConversasListadas listadas = useCase.executar(PRESTADOR_ID, PapelUsuario.PRESTADOR, 0, 20);

        ConversaResumo item = listadas.conversas().getContent().getFirst();
        assertThat(item.outraParte()).isEqualTo(
                new OutraParte("Mari", "https://bucket/mari", expiraEm, PapelNoChat.EMPREGADOR));
        assertThat(item.statusCandidatura()).isEqualTo("ESCOLHIDA");
        verify(chatService, never()).listarDoContratante(PRESTADOR_ID, 0, 20);
        verifyNoInteractions(consultaDadosUsuario, consultaFotoPerfil);
    }

    @Test
    void dadoMensagensNaoLidas_quandoLista_entaoItemTrazAContagemEOTotalVemDaConsultaAgregada() {
        empregadorVe(aberta(3, PRESTADOR_ID));
        // o total conta todas as páginas: pode ser maior que a soma dos itens desta
        when(chatService.contarDoContratanteComNaoLidas(EMPREGADOR_ID)).thenReturn(7L);
        when(consultaCandidatura.buscarResumosEmLote(anyCollection()))
                .thenReturn(Map.of(CANDIDATURA_ID, candidatura("ENVIADA")));
        prestadorCadastrado("Rafael Souza", null);

        ConversasListadas listadas = useCase.executar(EMPREGADOR_ID, PapelUsuario.EMPREGADOR, 0, 20);

        assertThat(listadas.conversas().getContent().getFirst().mensagensNaoLidas()).isEqualTo(3);
        assertThat(listadas.totalComNaoLidas()).isEqualTo(7);
    }

    @Test
    void dadoUltimaMensagemDoProprioUsuario_quandoLista_entaoMarcaEnviadaPorMim() {
        empregadorVe(aberta(0, EMPREGADOR_ID));
        when(chatService.contarDoContratanteComNaoLidas(EMPREGADOR_ID)).thenReturn(0L);
        when(consultaCandidatura.buscarResumosEmLote(anyCollection()))
                .thenReturn(Map.of(CANDIDATURA_ID, candidatura("ENVIADA")));
        prestadorCadastrado("Rafael Souza", null);

        ConversaResumo item = useCase.executar(EMPREGADOR_ID, PapelUsuario.EMPREGADOR, 0, 20)
                .conversas().getContent().getFirst();

        assertThat(item.ultimaMensagem())
                .isEqualTo(new ConversaResumo.UltimaMensagem("Combinado!", ENVIADA_EM, true));
    }

    @Test
    void dadoChatSomenteLeitura_quandoLista_entaoContinuaNaListaComMotivoEStatusDaCandidatura() {
        Linha recusada = new Linha(UUID.randomUUID(), CANDIDATURA_ID, EMPREGADOR_ID, PRESTADOR_ID, true,
                "CANDIDATURA_RECUSADA", "Obrigado", ENVIADA_EM, EMPREGADOR_ID, 0);
        prestadorVe(recusada);
        when(chatService.contarDoPrestadorComNaoLidas(PRESTADOR_ID)).thenReturn(0L);
        when(consultaCandidatura.buscarResumosEmLote(anyCollection()))
                .thenReturn(Map.of(CANDIDATURA_ID, candidatura("RECUSADA")));
        when(consultaPerfilEmpregador.buscarResumosEmLote(anyCollection())).thenReturn(Map.of(EMPREGADOR_ID,
                new ResumoPerfilEmpregador("Mari", null, null, null, 0)));

        ConversaResumo item = useCase.executar(PRESTADOR_ID, PapelUsuario.PRESTADOR, 0, 20)
                .conversas().getContent().getFirst();

        assertThat(item.somenteLeitura()).isTrue();
        assertThat(item.motivoSomenteLeitura()).isEqualTo(MotivoSomenteLeitura.CANDIDATURA_RECUSADA);
        assertThat(item.statusCandidatura()).isEqualTo("RECUSADA");
    }

    @Test
    void dadoPrestadorComContaEncerrada_quandoEmpregadorLista_entaoUsuarioExcluidoSemFoto() {
        empregadorVe(aberta(0, PRESTADOR_ID));
        when(chatService.contarDoContratanteComNaoLidas(EMPREGADOR_ID)).thenReturn(0L);
        when(consultaCandidatura.buscarResumosEmLote(anyCollection()))
                .thenReturn(Map.of(CANDIDATURA_ID, candidatura("ENVIADA")));
        when(consultaDadosUsuario.buscarPorIds(List.of(PRESTADOR_ID))).thenReturn(Map.of());
        when(consultaFotoPerfil.buscarFotosEmLote(anyCollection())).thenReturn(Map.of());

        ConversaResumo item = useCase.executar(EMPREGADOR_ID, PapelUsuario.EMPREGADOR, 0, 20)
                .conversas().getContent().getFirst();

        assertThat(item.outraParte()).isEqualTo(OutraParte.contaEncerrada(PapelNoChat.CANDIDATO));
        assertThat(item.outraParte().nome()).isEqualTo("Usuário excluído");
        assertThat(item.outraParte().fotoUrl()).isNull();
    }

    @Test
    void dadoEmpregadorComContaEncerrada_quandoPrestadorLista_entaoUsuarioExcluidoSemFoto() {
        prestadorVe(aberta(0, EMPREGADOR_ID));
        when(chatService.contarDoPrestadorComNaoLidas(PRESTADOR_ID)).thenReturn(0L);
        when(consultaCandidatura.buscarResumosEmLote(anyCollection()))
                .thenReturn(Map.of(CANDIDATURA_ID, candidatura("ESCOLHIDA")));
        when(consultaPerfilEmpregador.buscarResumosEmLote(anyCollection())).thenReturn(Map.of());

        ConversaResumo item = useCase.executar(PRESTADOR_ID, PapelUsuario.PRESTADOR, 0, 20)
                .conversas().getContent().getFirst();

        assertThat(item.outraParte()).isEqualTo(OutraParte.contaEncerrada(PapelNoChat.EMPREGADOR));
    }

    @Test
    void dadoChatSemMensagem_quandoLista_entaoUltimaMensagemNula() {
        Linha vazio = new Linha(UUID.randomUUID(), CANDIDATURA_ID, EMPREGADOR_ID, PRESTADOR_ID, false, null, null,
                null, null, 0);
        empregadorVe(vazio);
        when(chatService.contarDoContratanteComNaoLidas(EMPREGADOR_ID)).thenReturn(0L);
        when(consultaCandidatura.buscarResumosEmLote(anyCollection()))
                .thenReturn(Map.of(CANDIDATURA_ID, candidatura("ENVIADA")));
        prestadorCadastrado("Rafael Souza", null);

        ConversaResumo item = useCase.executar(EMPREGADOR_ID, PapelUsuario.EMPREGADOR, 0, 20)
                .conversas().getContent().getFirst();

        assertThat(item.ultimaMensagem()).isNull();
        assertThat(item.mensagensNaoLidas()).isZero();
    }

    @Test
    void dadoPaginaSemConversas_quandoLista_entaoNaoConsultaPortasEMantemOTotalDeNaoLidas() {
        when(chatService.listarDoContratante(EMPREGADOR_ID, 5, 20))
                .thenReturn(new PageImpl<>(List.<ConversaProjecao>of(), PageRequest.of(5, 20), 3));
        when(chatService.contarDoContratanteComNaoLidas(EMPREGADOR_ID)).thenReturn(2L);

        ConversasListadas listadas = useCase.executar(EMPREGADOR_ID, PapelUsuario.EMPREGADOR, 5, 20);

        assertThat(listadas.conversas().getContent()).isEmpty();
        assertThat(listadas.conversas().getTotalElements()).isEqualTo(3);
        assertThat(listadas.totalComNaoLidas()).isEqualTo(2);
        verifyNoInteractions(consultaCandidatura, consultaDadosUsuario, consultaPerfilEmpregador, consultaFotoPerfil);
    }

    @Test
    void dadaCandidaturaAusenteDoLote_quandoLista_entaoFalhaPorDadoCorrompido() {
        empregadorVe(aberta(0, PRESTADOR_ID));
        when(chatService.contarDoContratanteComNaoLidas(EMPREGADOR_ID)).thenReturn(0L);
        when(consultaCandidatura.buscarResumosEmLote(anyCollection())).thenReturn(Map.of());
        prestadorCadastrado("Rafael Souza", null);

        assertThatThrownBy(() -> useCase.executar(EMPREGADOR_ID, PapelUsuario.EMPREGADOR, 0, 20))
                .isInstanceOf(IllegalStateException.class);
    }
}
