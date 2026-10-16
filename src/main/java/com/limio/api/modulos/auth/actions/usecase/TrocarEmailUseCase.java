package com.limio.api.modulos.auth.actions.usecase;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.modulos.auth.actions.helper.ExecutarAposCommit;
import com.limio.api.modulos.auth.actions.helper.SenhaHasher;
import com.limio.api.modulos.auth.actions.helper.TokenRecuperacaoHelper;
import com.limio.api.modulos.auth.actions.service.EmailService;
import com.limio.api.modulos.auth.excecao.ContatoNaoPermitidoException;
import com.limio.api.modulos.auth.excecao.SenhaAtualIncorretaException;
import com.limio.api.modulos.auth.excecao.TrocaContatoLimiteExcedidaException;
import com.limio.api.modulos.auth.usuario.Usuario;
import com.limio.api.modulos.auth.usuario.UsuarioService;
import com.limio.api.modulos.auth.verificacaocontato.VerificacaoContato;
import com.limio.api.modulos.auth.verificacaocontato.VerificacaoContatoService;
import com.limio.api.modulos.auth.verificacaocontato.enums.CanalVerificacao;
import com.limio.api.modulos.auth.verificacaocontato.enums.FinalidadeVerificacao;

@Service
public class TrocarEmailUseCase {

    private final UsuarioService usuarioService;
    private final VerificacaoContatoService verificacaoContatoService;
    private final SenhaHasher senhaHasher;
    private final EmailService emailService;
    private final Duration validadeToken;
    private final int maxSolicitacoes;
    private final Duration janelaSolicitacoes;
    private final String frontendUrl;

    public TrocarEmailUseCase(UsuarioService usuarioService,
            VerificacaoContatoService verificacaoContatoService,
            SenhaHasher senhaHasher,
            EmailService emailService,
            @Value("${auth.confirmacao-email.validade-token}") Duration validadeToken,
            @Value("${auth.confirmacao-email.max-solicitacoes}") int maxSolicitacoes,
            @Value("${auth.confirmacao-email.janela-solicitacoes}") Duration janelaSolicitacoes,
            @Value("${app.frontend.url}") String frontendUrl) {
        this.usuarioService = usuarioService;
        this.verificacaoContatoService = verificacaoContatoService;
        this.senhaHasher = senhaHasher;
        this.emailService = emailService;
        this.validadeToken = validadeToken;
        this.maxSolicitacoes = maxSolicitacoes;
        this.janelaSolicitacoes = janelaSolicitacoes;
        this.frontendUrl = frontendUrl;
    }

    @Transactional
    public Usuario executar(UUID usuarioId, String novoEmail, String senhaAtual) {
        Usuario usuario = usuarioService.buscarParaAtualizarOuFalhar(usuarioId);
        Instant agora = Instant.now();
        usuario.exigirAtiva();
        if (!senhaHasher.confere(senhaAtual, usuario.getSenhaHash())) {
            throw new SenhaAtualIncorretaException();
        }

        boolean mesmoEmail = novoEmail.equalsIgnoreCase(usuario.getEmail());
        if (mesmoEmail && usuario.getEmailConfirmadoEm() != null) {
            return usuario;
        }
        if (!mesmoEmail) {
            validarDisponivel(usuarioId, novoEmail);
        }
        validarLimite(usuarioId, agora);

        if (!mesmoEmail) {
            usuario.setEmail(novoEmail);
            usuario.setEmailConfirmadoEm(null);
            usuarioService.salvarComEmailUnico(usuario);
        }

        invalidarPendentes(usuarioId, agora);
        emitirLink(usuario, agora);
        return usuario;
    }

    private void validarDisponivel(UUID usuarioId, String novoEmail) {
        boolean deOutraConta = usuarioService.buscarPorEmail(novoEmail)
                .filter(outro -> !outro.getId().equals(usuarioId))
                .isPresent();
        if (deOutraConta) {
            throw new ContatoNaoPermitidoException();
        }
    }

    private void validarLimite(UUID usuarioId, Instant agora) {
        long recentes = verificacaoContatoService.contarDesde(usuarioId, FinalidadeVerificacao.CONFIRMACAO_EMAIL,
                agora.minus(janelaSolicitacoes));
        if (recentes >= maxSolicitacoes) {
            throw new TrocaContatoLimiteExcedidaException();
        }
    }

    private void invalidarPendentes(UUID usuarioId, Instant agora) {
        for (VerificacaoContato pendente : verificacaoContatoService.pendentesDoUsuario(usuarioId,
                FinalidadeVerificacao.CONFIRMACAO_EMAIL)) {
            pendente.consumir(agora);
            verificacaoContatoService.salvar(pendente);
        }
    }

    private void emitirLink(Usuario usuario, Instant agora) {
        String token = TokenRecuperacaoHelper.gerar();
        verificacaoContatoService.salvar(VerificacaoContato.builder()
                .usuario(usuario)
                .canal(CanalVerificacao.EMAIL)
                .finalidade(FinalidadeVerificacao.CONFIRMACAO_EMAIL)
                .tokenHash(TokenRecuperacaoHelper.hash(token))
                .expiraEm(agora.plus(validadeToken))
                .build());

        String destinatario = usuario.getEmail();
        String link = linkDeConfirmacao(token);
        ExecutarAposCommit.executar(() -> emailService.enviarConfirmacaoEmail(destinatario, link));
    }

    private String linkDeConfirmacao(String token) {
        return frontendUrl + "/confirmar-email?token=" + token;
    }
}
