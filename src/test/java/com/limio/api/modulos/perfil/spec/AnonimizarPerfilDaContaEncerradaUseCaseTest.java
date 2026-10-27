package com.limio.api.modulos.perfil.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.limio.api.comum.eventos.ContaEncerradaEvent;
import com.limio.api.comum.midia.Midia;
import com.limio.api.comum.midia.MidiaService;
import com.limio.api.modulos.perfil.actions.usecase.AnonimizarPerfilDaContaEncerradaUseCase;
import com.limio.api.modulos.perfil.empregador.PerfilEmpregador;
import com.limio.api.modulos.perfil.empregador.PerfilEmpregadorService;
import com.limio.api.modulos.perfil.eventos.FotoPerfilRemovidaEvent;
import com.limio.api.modulos.perfil.perfilprestador.PerfilPrestadorService;

@ExtendWith(MockitoExtension.class)
class AnonimizarPerfilDaContaEncerradaUseCaseTest {

    private static final UUID USUARIO_ID = UUID.randomUUID();
    private static final Instant ENCERRADA_EM = Instant.parse("2026-10-26T12:00:00Z");
    private static final ContaEncerradaEvent EVENTO = new ContaEncerradaEvent(USUARIO_ID, ENCERRADA_EM);

    @Mock
    private PerfilEmpregadorService perfilEmpregadorService;

    @Mock
    private PerfilPrestadorService perfilPrestadorService;

    @Mock
    private MidiaService midiaService;

    @Mock
    private ApplicationEventPublisher eventos;

    private AnonimizarPerfilDaContaEncerradaUseCase useCase;

    @BeforeEach
    void montarUseCase() {
        useCase = new AnonimizarPerfilDaContaEncerradaUseCase(perfilEmpregadorService, perfilPrestadorService,
                midiaService, eventos);
    }

    @Test
    void deveAnonimizarONomeApagarOPrestadorERemoverAFoto() {
        PerfilEmpregador empregador = PerfilEmpregador.builder().usuarioId(USUARIO_ID).nomeExibicao("Maria").build();
        when(perfilEmpregadorService.buscarPorUsuario(USUARIO_ID)).thenReturn(Optional.of(empregador));
        Midia foto = Midia.fotoPerfil(USUARIO_ID, "fotos-perfil/x.jpg", "image/jpeg", 10, 10, 100);
        when(midiaService.buscarFotoPerfilAtiva(USUARIO_ID)).thenReturn(Optional.of(foto));

        useCase.aoEncerrarConta(EVENTO);

        assertThat(empregador.getNomeExibicao()).isEqualTo(PerfilEmpregador.NOME_CONTA_ENCERRADA);
        verify(perfilEmpregadorService).salvar(empregador);
        verify(perfilPrestadorService).apagarDoUsuario(USUARIO_ID);
        assertThat(foto.getExpurgoEm()).isEqualTo(ENCERRADA_EM);
        verify(midiaService).salvar(foto);
        verify(eventos).publishEvent(new FotoPerfilRemovidaEvent("fotos-perfil/x.jpg"));
    }

    @Test
    void semPerfisNemFotoNaoGravaNada() {
        when(perfilEmpregadorService.buscarPorUsuario(USUARIO_ID)).thenReturn(Optional.empty());
        when(midiaService.buscarFotoPerfilAtiva(USUARIO_ID)).thenReturn(Optional.empty());

        useCase.aoEncerrarConta(EVENTO);

        verify(perfilEmpregadorService, never()).salvar(any());
        verify(midiaService, never()).salvar(any());
        verify(eventos, never()).publishEvent(any());
    }

    @Test
    void falhaNumaParteNaoImpedeAsOutras() {
        doThrow(new IllegalStateException("banco fora")).when(perfilEmpregadorService).buscarPorUsuario(USUARIO_ID);
        when(midiaService.buscarFotoPerfilAtiva(USUARIO_ID)).thenReturn(Optional.empty());

        useCase.aoEncerrarConta(EVENTO);

        verify(perfilPrestadorService).apagarDoUsuario(USUARIO_ID);
        verify(midiaService).buscarFotoPerfilAtiva(USUARIO_ID);
    }
}
