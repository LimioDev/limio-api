package com.limio.api.modulos.auth.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.limio.api.comum.portas.DadosUsuario;
import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.modulos.auth.actions.service.ConsultaDadosUsuarioService;
import com.limio.api.modulos.auth.usuario.Usuario;
import com.limio.api.modulos.auth.usuario.UsuarioService;
import com.limio.api.modulos.auth.usuario.enums.StatusConta;

@ExtendWith(MockitoExtension.class)
class ConsultaDadosUsuarioServiceTest {

    private static final UUID USUARIO_ID = UUID.randomUUID();

    @Mock
    private UsuarioService usuarioService;

    private ConsultaDadosUsuarioService service;

    @BeforeEach
    void montarService() {
        service = new ConsultaDadosUsuarioService(usuarioService);
    }

    private static Usuario.UsuarioBuilder usuario() {
        return Usuario.builder()
                .nomeCompleto("Maria da Silva")
                .email("maria@example.com")
                .telefone("11999998888")
                .papelAtivo(PapelUsuario.EMPREGADOR)
                .statusConta(StatusConta.ATIVA);
    }

    @Test
    void deveMapearSoOsCincoCamposDaPorta() {
        Instant emailConfirmado = Instant.parse("2026-10-01T10:00:00Z");
        Instant telefoneConfirmado = Instant.parse("2026-10-02T10:00:00Z");
        Usuario maria = usuario().emailConfirmadoEm(emailConfirmado).telefoneConfirmadoEm(telefoneConfirmado).build();
        when(usuarioService.buscarPorId(USUARIO_ID)).thenReturn(Optional.of(maria));

        Optional<DadosUsuario> dados = service.buscarPorId(USUARIO_ID);

        assertThat(dados).contains(new DadosUsuario("Maria da Silva", "maria@example.com", "+5511999998888",
                emailConfirmado, telefoneConfirmado));
    }

    @Test
    void deveDevolverOTelefoneEmFormatoInternacionalMesmoParaLegadoDeDezDigitos() {
        when(usuarioService.buscarPorId(USUARIO_ID)).thenReturn(Optional.of(usuario().telefone("1133334444").build()));

        assertThat(service.buscarPorId(USUARIO_ID)).get().extracting(DadosUsuario::telefone)
                .isEqualTo("+551133334444");
    }

    @Test
    void deveManterNuloQuandoContatoNaoFoiConfirmado() {
        when(usuarioService.buscarPorId(USUARIO_ID)).thenReturn(Optional.of(usuario().build()));

        DadosUsuario dados = service.buscarPorId(USUARIO_ID).orElseThrow();

        assertThat(dados.emailConfirmadoEm()).isNull();
        assertThat(dados.telefoneConfirmadoEm()).isNull();
    }

    @Test
    void deveDevolverVazioQuandoUsuarioNaoExiste() {
        when(usuarioService.buscarPorId(USUARIO_ID)).thenReturn(Optional.empty());

        assertThat(service.buscarPorId(USUARIO_ID)).isEmpty();
    }

    @Test
    void deveDevolverVazioQuandoContaFoiEncerrada() {
        Usuario encerrada = usuario().anonimizadoEm(Instant.parse("2026-10-01T10:00:00Z")).build();
        when(usuarioService.buscarPorId(USUARIO_ID)).thenReturn(Optional.of(encerrada));

        assertThat(service.buscarPorId(USUARIO_ID)).isEmpty();
    }

    @Test
    void contaSuspensaAindaEnxergaOsProprioDados() {
        Usuario suspensa = usuario().statusConta(StatusConta.BLOQUEADA).build();
        when(usuarioService.buscarPorId(USUARIO_ID)).thenReturn(Optional.of(suspensa));

        assertThat(service.buscarPorId(USUARIO_ID)).isPresent();
    }
}
