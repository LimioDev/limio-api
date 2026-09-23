package com.limio.api.modulos.auth.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.limio.api.modulos.auth.CpfBloqueadoService;
import com.limio.api.modulos.auth.Usuario;
import com.limio.api.modulos.auth.UsuarioService;
import com.limio.api.modulos.auth.actions.helper.SenhaHasher;
import com.limio.api.modulos.auth.actions.usecase.CadastrarUsuarioUseCase;
import com.limio.api.modulos.auth.enums.PapelUsuario;
import com.limio.api.modulos.auth.enums.StatusConta;
import com.limio.api.modulos.auth.excecao.CadastroIndisponivelException;
import com.limio.api.modulos.auth.excecao.CpfInvalidoException;
import com.limio.api.modulos.auth.excecao.IdadeMinimaNaoAtingidaException;

@ExtendWith(MockitoExtension.class)
class CadastrarUsuarioUseCaseTest {

    private static final String CPF_VALIDO = "52998224725";

    @Mock
    private UsuarioService usuarioService;

    @Mock
    private CpfBloqueadoService cpfBloqueadoService;

    @Mock
    private SenhaHasher senhaHasher;

    private CadastrarUsuarioUseCase useCase;

    @BeforeEach
    void montarUseCase() {
        useCase = new CadastrarUsuarioUseCase(usuarioService, cpfBloqueadoService, senhaHasher);
    }

    private Usuario candidatoMaiorDeIdade() {
        return Usuario.builder()
                .nomeCompleto("Maria da Silva")
                .email("maria@example.com")
                .telefone("11999998888")
                .cpf(CPF_VALIDO)
                .dataNascimento(LocalDate.now().minusYears(20))
                .cidadeUf("São Paulo/SP")
                .build();
    }

    @Test
    void deveRecusarCadastroDeMenorDeIdadeSemConsultarServices() {
        Usuario candidato = candidatoMaiorDeIdade();
        candidato.setDataNascimento(LocalDate.now().minusYears(17));

        assertThatThrownBy(() -> useCase.executar(candidato, "senha12345"))
                .isInstanceOf(IdadeMinimaNaoAtingidaException.class);

        verifyNoInteractions(usuarioService, cpfBloqueadoService, senhaHasher);
    }

    @Test
    void deveRecusarCpfComDigitoVerificadorInvalido() {
        Usuario candidato = candidatoMaiorDeIdade();
        candidato.setCpf("11111111111");

        assertThatThrownBy(() -> useCase.executar(candidato, "senha12345"))
                .isInstanceOf(CpfInvalidoException.class);

        verifyNoInteractions(usuarioService, cpfBloqueadoService, senhaHasher);
    }

    @Test
    void deveRecusarQuandoEmailJaExisteComErroGenerico() {
        Usuario candidato = candidatoMaiorDeIdade();
        when(usuarioService.existePorEmail(candidato.getEmail())).thenReturn(true);

        assertThatThrownBy(() -> useCase.executar(candidato, "senha12345"))
                .isInstanceOf(CadastroIndisponivelException.class);

        verifyNoInteractions(senhaHasher);
    }

    @Test
    void deveRecusarQuandoCpfJaExisteComErroGenerico() {
        Usuario candidato = candidatoMaiorDeIdade();
        when(usuarioService.existePorEmail(candidato.getEmail())).thenReturn(false);
        when(usuarioService.existePorCpf(candidato.getCpf())).thenReturn(true);

        assertThatThrownBy(() -> useCase.executar(candidato, "senha12345"))
                .isInstanceOf(CadastroIndisponivelException.class);
    }

    @Test
    void deveRecusarQuandoCpfFoiExcluidoHaMenosDe12Meses() {
        Usuario candidato = candidatoMaiorDeIdade();
        when(usuarioService.existePorEmail(anyString())).thenReturn(false);
        when(usuarioService.existePorCpf(anyString())).thenReturn(false);
        when(cpfBloqueadoService.estaBloqueado(anyString(), any(Instant.class))).thenReturn(true);

        assertThatThrownBy(() -> useCase.executar(candidato, "senha12345"))
                .isInstanceOf(CadastroIndisponivelException.class);

        verifyNoInteractions(senhaHasher);
    }

    @Test
    void deveCadastrarComSucessoDefinindoPapelContratanteComoPadrao() {
        Usuario candidato = candidatoMaiorDeIdade();
        when(usuarioService.existePorEmail(anyString())).thenReturn(false);
        when(usuarioService.existePorCpf(anyString())).thenReturn(false);
        when(cpfBloqueadoService.estaBloqueado(anyString(), any(Instant.class))).thenReturn(false);
        when(senhaHasher.hash("senha12345")).thenReturn("hash-fake");
        when(usuarioService.salvar(candidato)).thenReturn(candidato);

        candidato.setPapelAtivo(PapelUsuario.CONTRATANTE);
        candidato.setStatusConta(StatusConta.ATIVA);

        Usuario salvo = useCase.executar(candidato, "senha12345");

        assertThat(salvo.getSenhaHash()).isEqualTo("hash-fake");
        assertThat(salvo.getPapelAtivo()).isEqualTo(PapelUsuario.CONTRATANTE);
        verify(usuarioService).salvar(candidato);
    }
}
