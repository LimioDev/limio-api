package com.limio.api.modulos.auth.actions.usecase;

import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.modulos.auth.actions.helper.TokenRecuperacaoHelper;
import com.limio.api.modulos.auth.actions.service.EmailService;
import com.limio.api.modulos.auth.usuario.Usuario;
import com.limio.api.modulos.auth.usuario.UsuarioService;
import com.limio.api.modulos.auth.verificacaocontato.VerificacaoContato;
import com.limio.api.modulos.auth.verificacaocontato.VerificacaoContatoService;
import com.limio.api.modulos.auth.verificacaocontato.enums.CanalVerificacao;
import com.limio.api.modulos.auth.verificacaocontato.enums.FinalidadeVerificacao;

/**
 * Regra de negócio do UC06 (solicitar recuperação de senha). Anti-enumeração:
 * sempre "sem efeito observável" pra quem chama, exista o e-mail ou não —
 * quem chama o usecase (controller) sempre responde 200 genérico, por isso
 * este método não devolve nada e não lança exceção de negócio.
 *
 * E-mail inexistente ou de conta encerrada: nada é feito. Rate limit batido
 * (3 solicitações/hora por conta): nada é feito também — só a conta real
 * sabe se bateu o limite, e isso não pode vazar pra quem não tem a conta.
 */
@Service
public class SolicitarRecuperacaoSenhaUseCase {

    private final UsuarioService usuarioService;
    private final VerificacaoContatoService verificacaoContatoService;
    private final EmailService emailService;
    private final Duration validadeToken;
    private final int maxSolicitacoes;
    private final Duration janelaSolicitacoes;
    private final String frontendUrl;

    public SolicitarRecuperacaoSenhaUseCase(UsuarioService usuarioService,
            VerificacaoContatoService verificacaoContatoService,
            EmailService emailService,
            @Value("${auth.recuperacao-senha.validade-token}") Duration validadeToken,
            @Value("${auth.recuperacao-senha.max-solicitacoes}") int maxSolicitacoes,
            @Value("${auth.recuperacao-senha.janela-solicitacoes}") Duration janelaSolicitacoes,
            @Value("${app.frontend.url}") String frontendUrl) {
        this.usuarioService = usuarioService;
        this.verificacaoContatoService = verificacaoContatoService;
        this.emailService = emailService;
        this.validadeToken = validadeToken;
        this.maxSolicitacoes = maxSolicitacoes;
        this.janelaSolicitacoes = janelaSolicitacoes;
        this.frontendUrl = frontendUrl;
    }

    @Transactional
    public void executar(String email) {
        Instant agora = Instant.now();
        usuarioService.buscarPorEmail(email)
                .filter(usuario -> !usuario.isEncerrada())
                .ifPresent(usuario -> solicitar(usuario, agora));
    }

    private void solicitar(Usuario usuario, Instant agora) {
        long recentes = verificacaoContatoService.contarDesde(usuario.getId(), FinalidadeVerificacao.RECUPERACAO_SENHA,
                agora.minus(janelaSolicitacoes));
        if (recentes >= maxSolicitacoes) {
            return;
        }

        String token = TokenRecuperacaoHelper.gerar();
        VerificacaoContato verificacao = VerificacaoContato.builder()
                .usuario(usuario)
                .canal(CanalVerificacao.EMAIL)
                .finalidade(FinalidadeVerificacao.RECUPERACAO_SENHA)
                .tokenHash(TokenRecuperacaoHelper.hash(token))
                .expiraEm(agora.plus(validadeToken))
                .build();
        verificacaoContatoService.salvar(verificacao);

        emailService.enviarRecuperacaoSenha(usuario.getEmail(), frontendUrl + "/nova-senha?token=" + token);
    }
}
