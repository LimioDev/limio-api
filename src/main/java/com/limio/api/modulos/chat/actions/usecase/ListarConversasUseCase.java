package com.limio.api.modulos.chat.actions.usecase;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import com.limio.api.modulos.chat.ConversaResumo.UltimaMensagem;
import com.limio.api.modulos.chat.ConversasListadas;
import com.limio.api.modulos.chat.OutraParte;
import com.limio.api.modulos.chat.enums.MotivoSomenteLeitura;
import com.limio.api.modulos.chat.enums.PapelNoChat;

/**
 * Tela "Conversas" (ETI-103): os chats do papel ativo, do mais recente para o mais antigo, com a última mensagem e
 * quantas o usuário ainda não leu. Empregador vê os chats dos anúncios dele; Prestador, os das candidaturas dele. A
 * pessoa vem sempre do token, nunca da requisição, e o papel só escolhe a coluna filtrada: o chat do outro papel não
 * aparece. Chat somente leitura continua na lista, é o histórico.
 *
 * <p>Título, status, nome e foto vêm das portas em lote: uma consulta por porta, qualquer que seja o tamanho da
 * página. Conta encerrada (ausente do lote) vira "Usuário excluído", sem foto, mas mantém o {@code usuarioId}: o perfil
 * público dela responde "Conta encerrada" (ETI-136).
 */
@Service
public class ListarConversasUseCase {

    private final ChatService chatService;
    private final ConsultaCandidatura consultaCandidatura;
    private final ConsultaDadosUsuario consultaDadosUsuario;
    private final ConsultaPerfilEmpregador consultaPerfilEmpregador;
    private final ConsultaFotoPerfil consultaFotoPerfil;

    public ListarConversasUseCase(ChatService chatService, ConsultaCandidatura consultaCandidatura,
            ConsultaDadosUsuario consultaDadosUsuario, ConsultaPerfilEmpregador consultaPerfilEmpregador,
            ConsultaFotoPerfil consultaFotoPerfil) {
        this.chatService = chatService;
        this.consultaCandidatura = consultaCandidatura;
        this.consultaDadosUsuario = consultaDadosUsuario;
        this.consultaPerfilEmpregador = consultaPerfilEmpregador;
        this.consultaFotoPerfil = consultaFotoPerfil;
    }

    @Transactional(readOnly = true)
    public ConversasListadas executar(UUID usuarioId, PapelUsuario papelAtivo, int pagina, int tamanho) {
        Page<ConversaProjecao> linhas = switch (papelAtivo) {
            case EMPREGADOR -> chatService.listarDoContratante(usuarioId, pagina, tamanho);
            case PRESTADOR -> chatService.listarDoPrestador(usuarioId, pagina, tamanho);
        };
        long totalComNaoLidas = switch (papelAtivo) {
            case EMPREGADOR -> chatService.contarDoContratanteComNaoLidas(usuarioId);
            case PRESTADOR -> chatService.contarDoPrestadorComNaoLidas(usuarioId);
        };
        if (linhas.isEmpty()) {
            return new ConversasListadas(
                    new PageImpl<>(List.<ConversaResumo>of(), linhas.getPageable(), linhas.getTotalElements()),
                    totalComNaoLidas);
        }

        Map<UUID, ResumoCandidatura> candidaturas = consultaCandidatura.buscarResumosEmLote(
                linhas.stream().map(ConversaProjecao::getCandidaturaId).toList());
        Map<UUID, OutraParte> outrasPartes = outrasPartes(linhas.getContent(), papelAtivo);

        Page<ConversaResumo> conversas = linhas.map(linha -> resumoDe(linha, usuarioId, papelAtivo, candidaturas,
                outrasPartes));
        return new ConversasListadas(conversas, totalComNaoLidas);
    }

    private ConversaResumo resumoDe(ConversaProjecao linha, UUID usuarioId, PapelUsuario papelAtivo,
            Map<UUID, ResumoCandidatura> candidaturas, Map<UUID, OutraParte> outrasPartes) {
        ResumoCandidatura candidatura = candidaturas.get(linha.getCandidaturaId());
        if (candidatura == null) {
            // chat nunca existe sem a candidatura (aplicacao não é apagada): ausência aqui é dado corrompido
            throw new IllegalStateException("candidatura do chat não existe: " + linha.getCandidaturaId());
        }
        UUID outraParteId = outraParteId(linha, papelAtivo);
        OutraParte outraParte = outrasPartes.getOrDefault(outraParteId,
                OutraParte.contaEncerrada(outraParteId, papelDaOutraParte(papelAtivo)));
        UltimaMensagem ultimaMensagem = linha.getUltimaMensagemEm() == null ? null
                : new UltimaMensagem(linha.getUltimaMensagem(), linha.getUltimaMensagemEm(),
                        usuarioId.equals(linha.getUltimaMensagemRemetenteId()));
        MotivoSomenteLeitura motivo = linha.getMotivoSomenteLeitura() == null ? null
                : MotivoSomenteLeitura.valueOf(linha.getMotivoSomenteLeitura());
        return new ConversaResumo(linha.getChatId(), linha.getCandidaturaId(), outraParte,
                candidatura.anuncioTitulo(), candidatura.status(), ultimaMensagem, linha.getNaoLidas(),
                linha.getSomenteLeitura(), motivo);
    }

    private Map<UUID, OutraParte> outrasPartes(List<ConversaProjecao> linhas, PapelUsuario papelAtivo) {
        List<UUID> ids = linhas.stream().map(linha -> outraParteId(linha, papelAtivo)).distinct().toList();
        PapelNoChat papel = papelDaOutraParte(papelAtivo);
        Map<UUID, OutraParte> outrasPartes = new HashMap<>();
        switch (papelAtivo) {
            case EMPREGADOR -> {
                Map<UUID, DadosUsuario> dados = consultaDadosUsuario.buscarPorIds(ids);
                Map<UUID, LinkTemporario> fotos = consultaFotoPerfil.buscarFotosEmLote(dados.keySet());
                dados.forEach((id, usuario) -> outrasPartes.put(id,
                        outraParte(id, usuario.nomeCompleto(), Optional.ofNullable(fotos.get(id)), papel)));
            }
            case PRESTADOR -> consultaPerfilEmpregador.buscarResumosEmLote(ids).forEach((id, perfil) ->
                    outrasPartes.put(id, outraParte(id, perfil, papel)));
        }
        return outrasPartes;
    }

    private static OutraParte outraParte(UUID usuarioId, String nome, Optional<LinkTemporario> foto,
            PapelNoChat papel) {
        return new OutraParte(usuarioId, nome, foto.map(LinkTemporario::url).orElse(null),
                foto.map(LinkTemporario::expiraEm).orElse(null), papel);
    }

    private static OutraParte outraParte(UUID usuarioId, ResumoPerfilEmpregador perfil, PapelNoChat papel) {
        return new OutraParte(usuarioId, perfil.nomeExibicao(), perfil.fotoUrl(), perfil.fotoExpiraEm(), papel);
    }

    private static UUID outraParteId(ConversaProjecao linha, PapelUsuario papelAtivo) {
        return switch (papelAtivo) {
            case EMPREGADOR -> linha.getPrestadorId();
            case PRESTADOR -> linha.getContratanteId();
        };
    }

    private static PapelNoChat papelDaOutraParte(PapelUsuario papelAtivo) {
        return switch (papelAtivo) {
            case EMPREGADOR -> PapelNoChat.CANDIDATO;
            case PRESTADOR -> PapelNoChat.EMPREGADOR;
        };
    }
}
