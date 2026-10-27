package com.limio.api.modulos.auth.verificacaocontato;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.limio.api.comum.config.PaginasWebConfig;
import com.limio.api.modulos.auth.actions.helper.LinkWeb;
import com.limio.api.modulos.auth.actions.helper.TokenVerificacaoHelper;
import com.limio.api.modulos.auth.eventos.ConfirmacaoEmailSolicitadaEvent;
import com.limio.api.modulos.auth.usuario.Usuario;
import com.limio.api.modulos.auth.verificacaocontato.enums.CanalVerificacao;
import com.limio.api.modulos.auth.verificacaocontato.enums.FinalidadeVerificacao;

/**
 * Emite o link de confirmação de e-mail (UC05). Usado por {@code CadastrarUsuarioUseCase},
 * {@code TrocarEmailUseCase} e {@code ReenviarConfirmacaoEmailUseCase} — os três precisam da mesma regra:
 * só um link pendente por conta (os anteriores são consumidos) e no máximo {@code max-solicitacoes}
 * emissões dentro da janela. Fica em {@code verificacaocontato/}, não em {@code actions/usecase/}: não é
 * usecase (sem {@code executar}, não é chamado por controller) — é regra compartilhada entre usecases, que
 * no fluxo do ADR-0001 (controller → usecase → (repository | service)) dependem dela como dependem de um
 * service.
 *
 * Não abre transação própria e não trava a conta: quem chama já está numa transação e, se a conta já
 * existe, já carregou o usuário com {@code UsuarioService.buscarParaAtualizarOuFalhar}
 * (regra do TICKET-0048 pra todo emissor de {@code CONFIRMACAO_EMAIL}). O e-mail sai depois do commit
 * ({@code EmailEventosService}).
 */
@Component
public class EmissorConfirmacaoEmail {

    private final VerificacaoContatoService verificacaoContatoService;
    private final ApplicationEventPublisher eventos;
    private final LinkWeb linkWeb;
    private final Duration validadeToken;
    private final int maxSolicitacoes;
    private final Duration janelaSolicitacoes;

    public EmissorConfirmacaoEmail(VerificacaoContatoService verificacaoContatoService,
            ApplicationEventPublisher eventos,
            LinkWeb linkWeb,
            @Value("${auth.confirmacao-email.validade-token}") Duration validadeToken,
            @Value("${auth.confirmacao-email.max-solicitacoes}") int maxSolicitacoes,
            @Value("${auth.confirmacao-email.janela-solicitacoes}") Duration janelaSolicitacoes) {
        this.verificacaoContatoService = verificacaoContatoService;
        this.eventos = eventos;
        this.linkWeb = linkWeb;
        this.validadeToken = validadeToken;
        this.maxSolicitacoes = maxSolicitacoes;
        this.janelaSolicitacoes = janelaSolicitacoes;
    }

    public boolean limiteAtingido(UUID usuarioId, Instant agora) {
        long recentes = verificacaoContatoService.contarDesde(usuarioId, FinalidadeVerificacao.CONFIRMACAO_EMAIL,
                agora.minus(janelaSolicitacoes));
        return recentes >= maxSolicitacoes;
    }

    /** Consome os links pendentes, grava um novo e agenda o e-mail para o endereço atual da conta. */
    public void emitir(Usuario usuario, Instant agora) {
        for (VerificacaoContato pendente : verificacaoContatoService.pendentesDoUsuario(usuario.getId(),
                FinalidadeVerificacao.CONFIRMACAO_EMAIL)) {
            pendente.consumir(agora);
            verificacaoContatoService.salvar(pendente);
        }

        String token = TokenVerificacaoHelper.gerar();
        verificacaoContatoService.salvar(VerificacaoContato.builder()
                .usuario(usuario)
                .canal(CanalVerificacao.EMAIL)
                .finalidade(FinalidadeVerificacao.CONFIRMACAO_EMAIL)
                .tokenHash(TokenVerificacaoHelper.hash(token))
                .destinoHash(TokenVerificacaoHelper.hashDestino(usuario.getEmail()))
                .expiraEm(agora.plus(validadeToken))
                .build());

        eventos.publishEvent(new ConfirmacaoEmailSolicitadaEvent(usuario.getEmail(),
                linkWeb.pagina(PaginasWebConfig.CONFIRMAR_EMAIL, token)));
    }
}
