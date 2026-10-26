package com.limio.api.modulos.auth.spec;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.limio.api.modulos.auth.actions.service.EmailEventosService;
import com.limio.api.modulos.auth.actions.service.EmailService;
import com.limio.api.modulos.auth.eventos.ConfirmacaoEmailSolicitadaEvent;
import com.limio.api.modulos.auth.eventos.EmailContaAlteradoEvent;
import com.limio.api.modulos.auth.eventos.RecuperacaoSenhaSolicitadaEvent;
import com.limio.api.modulos.auth.eventos.SenhaAlteradaEvent;

@ExtendWith(MockitoExtension.class)
class EmailEventosServiceTest {

    @Mock
    private EmailService emailService;

    @InjectMocks
    private EmailEventosService service;

    @Test
    void confirmacaoSolicitadaDeveEnviarOLinkAoDestinatario() {
        service.aoSolicitarConfirmacao(new ConfirmacaoEmailSolicitadaEvent("novo@example.com", "https://app/x"));

        verify(emailService).enviarConfirmacaoEmail("novo@example.com", "https://app/x");
    }

    @Test
    void emailAlteradoDeveAvisarOEnderecoAntigo() {
        service.aoAlterarEmail(new EmailContaAlteradoEvent("antigo@example.com", "novo@example.com"));

        verify(emailService).enviarEmailAlterado("antigo@example.com", "novo@example.com");
    }

    @Test
    void falhaNoEnvioDaConfirmacaoNaoDevePropagar() {
        doThrow(new IllegalStateException("provedor fora do ar")).when(emailService)
                .enviarConfirmacaoEmail(anyString(), anyString());

        assertThatCode(() -> service.aoSolicitarConfirmacao(new ConfirmacaoEmailSolicitadaEvent("a@b.com", "l")))
                .doesNotThrowAnyException();
    }

    @Test
    void falhaNoAvisoNaoDevePropagar() {
        doThrow(new IllegalStateException("provedor fora do ar")).when(emailService)
                .enviarEmailAlterado(anyString(), anyString());

        assertThatCode(() -> service.aoAlterarEmail(new EmailContaAlteradoEvent("a@b.com", "c@d.com")))
                .doesNotThrowAnyException();
    }

    @Test
    void recuperacaoSolicitadaDeveEnviarOLink() {
        service.aoSolicitarRecuperacao(new RecuperacaoSenhaSolicitadaEvent("maria@example.com", "https://api/x"));

        verify(emailService).enviarRecuperacaoSenha("maria@example.com", "https://api/x");
    }

    @Test
    void senhaAlteradaDeveAvisarEFalhaNaoPropaga() {
        doThrow(new IllegalStateException("resend fora")).when(emailService).enviarSenhaAlterada(anyString());

        assertThatCode(() -> service.aoAlterarSenha(new SenhaAlteradaEvent("maria@example.com")))
                .doesNotThrowAnyException();
        verify(emailService).enviarSenhaAlterada("maria@example.com");
    }
}
