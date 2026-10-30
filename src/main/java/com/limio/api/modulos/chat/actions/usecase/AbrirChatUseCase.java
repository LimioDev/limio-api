package com.limio.api.modulos.chat.actions.usecase;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.midia.LinkTemporario;
import com.limio.api.comum.portas.ConsultaCandidatura;
import com.limio.api.comum.portas.ConsultaDadosUsuario;
import com.limio.api.comum.portas.ConsultaFotoPerfil;
import com.limio.api.comum.portas.ConsultaPerfilEmpregador;
import com.limio.api.comum.portas.ResumoCandidatura;
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
 *
 * Se o chat ainda não existe (a reação ao {@code CandidaturaCriadaEvent} falhou depois do commit, ou a candidatura é
 * anterior ao módulo), ele é criado aqui: a candidatura nunca fica sem chat.
 */
@Service
public class AbrirChatUseCase {

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

    /**
     * @param antesDe id da mensagem mais antiga que o app já tem, para carregar as anteriores; {@code null} abre nas
     *     mais recentes
     */
    // sem readOnly: cria o chat que faltar
    @Transactional
    public ChatAberto executar(UUID aplicacaoId, UUID usuarioId, UUID antesDe, int tamanho) {
        ResumoCandidatura candidatura = consultaCandidatura.buscarPorId(aplicacaoId)
                .filter(encontrada -> encontrada.participa(usuarioId))
                .orElseThrow(ChatNaoEncontradoException::new);
        Chat chat = chatService.garantirDaCandidatura(aplicacaoId, candidatura.anuncioId(),
                candidatura.contratanteId(), candidatura.prestadorId());

        Slice<Mensagem> historico = mensagemService.buscarHistorico(chat.getId(), antesDe, tamanho);
        return new ChatAberto(chat, outraParte(chat, usuarioId), candidatura, historico.getContent(), tamanho,
                historico.hasNext());
    }

    /**
     * Empregador aparece pelo nome de exibição do perfil; Prestador, pelo nome do cadastro. Conta encerrada (porta
     * vazia) vira "Usuário excluído". Só leitura: abrir o chat nunca cria nada no perfil da outra parte.
     */
    private OutraParte outraParte(Chat chat, UUID usuarioId) {
        UUID outraParteId = chat.outraParte(usuarioId);
        PapelNoChat papel = chat.papelDaOutraParte(usuarioId);
        Optional<OutraParte> encontrada = switch (papel) {
            case EMPREGADOR -> consultaPerfilEmpregador.buscarResumoSemCriar(outraParteId)
                    .map(perfil -> new OutraParte(perfil.nomeExibicao(), perfil.fotoUrl(), perfil.fotoExpiraEm(),
                            papel));
            case CANDIDATO -> consultaDadosUsuario.buscarPorId(outraParteId).map(dados -> {
                Optional<LinkTemporario> foto = consultaFotoPerfil.buscarFoto(outraParteId);
                return new OutraParte(dados.nomeCompleto(), foto.map(LinkTemporario::url).orElse(null),
                        foto.map(LinkTemporario::expiraEm).orElse(null), papel);
            });
        };
        return encontrada.orElseGet(() -> OutraParte.contaEncerrada(papel));
    }
}
