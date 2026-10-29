package com.limio.api.modulos.chat.actions.usecase;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import com.limio.api.modulos.chat.enums.PapelNoChat;
import com.limio.api.modulos.chat.excecao.ChatNaoEncontradoException;
import com.limio.api.modulos.chat.mensagem.Mensagem;
import com.limio.api.modulos.chat.mensagem.MensagemService;

/**
 * Abre o chat de uma candidatura (ETI-101): cabeçalho, somente leitura e uma página do histórico. Só as duas partes
 * enxergam o chat; para qualquer outra pessoa ele não existe. Não marca nada como lido: isso é
 * {@link ConfirmarMensagensUseCase}, chamado pelo app ao exibir a conversa.
 */
@Service
public class AbrirChatUseCase {

    public static final int TAMANHO_PAGINA_MAXIMO = 100;

    private final ChatService chatService;
    private final MensagemService mensagemService;
    private final ConsultaCandidatura consultaCandidatura;
    private final ConsultaDadosUsuario consultaDadosUsuario;
    private final ConsultaPerfilEmpregador consultaPerfilEmpregador;
    private final ConsultaFotoPerfil consultaFotoPerfil;

    public AbrirChatUseCase(ChatService chatService, MensagemService mensagemService,
            ConsultaCandidatura consultaCandidatura, ConsultaDadosUsuario consultaDadosUsuario,
            ConsultaPerfilEmpregador consultaPerfilEmpregador, ConsultaFotoPerfil consultaFotoPerfil) {
        this.chatService = chatService;
        this.mensagemService = mensagemService;
        this.consultaCandidatura = consultaCandidatura;
        this.consultaDadosUsuario = consultaDadosUsuario;
        this.consultaPerfilEmpregador = consultaPerfilEmpregador;
        this.consultaFotoPerfil = consultaFotoPerfil;
    }

    // sem readOnly: ConsultaPerfilEmpregador cria o perfil do Empregador que ainda não tem um (mesma regra do painel)
    @Transactional
    public ChatAberto executar(UUID aplicacaoId, UUID usuarioId, int pagina, int tamanho) {
        Chat chat = chatService.buscarDaCandidaturaDoParticipante(aplicacaoId, usuarioId);
        ResumoCandidatura candidatura = consultaCandidatura.buscarPorId(aplicacaoId)
                .orElseThrow(ChatNaoEncontradoException::new);
        OutraParte outraParte = outraParte(chat, usuarioId);

        int paginaValida = Math.max(pagina, 0);
        int tamanhoValido = Math.clamp(tamanho, 1, TAMANHO_PAGINA_MAXIMO);
        Slice<Mensagem> historico = mensagemService.buscarHistorico(chat.getId(), paginaValida, tamanhoValido);
        return new ChatAberto(chat, outraParte, candidatura, historico.getContent(), paginaValida, tamanhoValido,
                historico.hasNext());
    }

    /**
     * Empregador aparece pelo nome de exibição do perfil; Prestador, pelo nome do cadastro. Conta encerrada (porta
     * de {@code auth} vazia) vira "Usuário excluído" sem consultar mais nada.
     */
    private OutraParte outraParte(Chat chat, UUID usuarioId) {
        UUID outraParteId = chat.outraParte(usuarioId);
        PapelNoChat papel = chat.papelDaOutraParte(usuarioId);
        Optional<DadosUsuario> dados = consultaDadosUsuario.buscarPorId(outraParteId);
        if (dados.isEmpty())
            return OutraParte.contaEncerrada(papel);
        return switch (papel) {
            case EMPREGADOR -> {
                ResumoPerfilEmpregador perfil = consultaPerfilEmpregador.buscarResumo(outraParteId);
                yield new OutraParte(perfil.nomeExibicao(), perfil.fotoUrl(), perfil.fotoExpiraEm(), papel);
            }
            case CANDIDATO -> {
                Optional<FotoPerfil> foto = consultaFotoPerfil.buscarFoto(outraParteId);
                yield new OutraParte(dados.get().nomeCompleto(), foto.map(FotoPerfil::url).orElse(null),
                        foto.map(FotoPerfil::expiraEm).orElse(null), papel);
            }
        };
    }
}
