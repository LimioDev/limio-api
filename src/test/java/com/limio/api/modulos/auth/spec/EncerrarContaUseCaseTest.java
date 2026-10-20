package com.limio.api.modulos.auth.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.limio.api.comum.eventos.ContaEncerradaEvent;
import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.modulos.auth.actions.usecase.EncerrarContaUseCase;
import com.limio.api.modulos.auth.auditoria.LogAuditoriaService;
import com.limio.api.modulos.auth.auditoria.enums.TipoAcaoAuditoria;
import com.limio.api.modulos.auth.cpfbloqueado.CpfBloqueadoService;
import com.limio.api.modulos.auth.sessao.SessaoService;
import com.limio.api.modulos.auth.usuario.Usuario;
import com.limio.api.modulos.auth.usuario.UsuarioService;
import com.limio.api.modulos.auth.usuario.enums.StatusConta;

@ExtendWith(MockitoExtension.class)
class EncerrarContaUseCaseTest {

    private static final UUID USUARIO_ID = UUID.randomUUID();
    private static final String CPF = "52998224725";
    private static final String IP = "203.0.113.10";

    @Mock
    private UsuarioService usuarioService;

    @Mock
    private CpfBloqueadoService cpfBloqueadoService;

    @Mock
    private SessaoService sessaoService;

    @Mock
    private LogAuditoriaService logAuditoriaService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private EncerrarContaUseCase useCase;

    @BeforeEach
    void montarUseCase() {
        useCase = new EncerrarContaUseCase(usuarioService, cpfBloqueadoService, sessaoService, logAuditoriaService,
                eventPublisher);
    }

    private Usuario usuarioAtivo() {
        Usuario usuario = Usuario.builder()
                .nomeCompleto("Maria da Silva")
                .email("maria@example.com")
                .telefone("11999998888")
                .cpf(CPF)
                .senhaHash("hash-bcrypt")
                .papelAtivo(PapelUsuario.EMPREGADOR)
                .statusConta(StatusConta.ATIVA)
                .build();
        ReflectionTestUtils.setField(usuario, "id", USUARIO_ID);
        return usuario;
    }

    @Test
    void deveBloquearCpfAnonimizarEncerrarSessoesRegistrarAuditoriaEPublicarEvento() {
        Usuario usuario = usuarioAtivo();
        when(usuarioService.buscarPorIdOuFalhar(USUARIO_ID)).thenReturn(usuario);

        useCase.executar(USUARIO_ID, IP);

        verify(cpfBloqueadoService).bloquear(eq(CPF), any(Instant.class));
        assertThat(usuario.isEncerrada()).isTrue();
        assertThat(usuario.getCpf()).isNull();
        assertThat(usuario.getEmail()).contains(USUARIO_ID.toString());
        verify(usuarioService).salvar(usuario);
        verify(sessaoService).revogarTodas(eq(USUARIO_ID), isNull(), any(Instant.class));
        verify(logAuditoriaService).registrar(eq("Usuario"), eq(USUARIO_ID), eq(TipoAcaoAuditoria.CONTA_ENCERRADA),
                any(), eq(IP));

        ArgumentCaptor<ContaEncerradaEvent> evento = ArgumentCaptor.forClass(ContaEncerradaEvent.class);
        verify(eventPublisher).publishEvent(evento.capture());
        assertThat(evento.getValue().usuarioId()).isEqualTo(USUARIO_ID);
    }

    @Test
    void deveBloquearCpfAntesDeAnonimizarParaUsarOCpfOriginal() {
        Usuario usuario = usuarioAtivo();
        when(usuarioService.buscarPorIdOuFalhar(USUARIO_ID)).thenReturn(usuario);

        useCase.executar(USUARIO_ID, IP);

        // se bloquear fosse chamado depois do encerrar(), receberia null em vez do CPF original
        verify(cpfBloqueadoService).bloquear(eq(CPF), any(Instant.class));
    }

    @Test
    void naoDeveFazerNadaDeNovoQuandoContaJaEstaEncerrada() {
        Usuario jaEncerrada = usuarioAtivo();
        jaEncerrada.encerrar(Instant.now());
        when(usuarioService.buscarPorIdOuFalhar(USUARIO_ID)).thenReturn(jaEncerrada);

        useCase.executar(USUARIO_ID, IP);

        verifyNoInteractions(cpfBloqueadoService, sessaoService, logAuditoriaService, eventPublisher);
        verify(usuarioService, never()).salvar(any());
    }
}
