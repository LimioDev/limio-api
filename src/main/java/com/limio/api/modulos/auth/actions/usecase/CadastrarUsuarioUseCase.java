package com.limio.api.modulos.auth.actions.usecase;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.ZoneOffset;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.modulos.auth.actions.helper.CpfValidator;
import com.limio.api.modulos.auth.actions.helper.SenhaHasher;
import com.limio.api.modulos.auth.cpfbloqueado.CpfBloqueadoService;
import com.limio.api.modulos.auth.excecao.CadastroIndisponivelException;
import com.limio.api.modulos.auth.excecao.CpfInvalidoException;
import com.limio.api.modulos.auth.excecao.IdadeMinimaNaoAtingidaException;
import com.limio.api.modulos.auth.usuario.Usuario;
import com.limio.api.modulos.auth.usuario.UsuarioService;

/**
 * Regra de negócio do UC01 (cadastrar conta universal). Não conhece HTTP nem
 * record de entrada/saída, nem repository diretamente — orquestra decisão de
 * negócio e delegada persistência aos services do módulo.
 *
 * Conta criada já recebe o link de confirmação de e-mail (UC05, TICKET-0047);
 * o e-mail só sai depois do commit. Login não espera a confirmação.
 */
@Service
public class CadastrarUsuarioUseCase {

    private static final int IDADE_MINIMA = 18;

    private final UsuarioService usuarioService;
    private final CpfBloqueadoService cpfBloqueadoService;
    private final SenhaHasher senhaHasher;
    private final EmitirConfirmacaoEmailUseCase emitirConfirmacaoEmail;

    public CadastrarUsuarioUseCase(UsuarioService usuarioService,
            CpfBloqueadoService cpfBloqueadoService,
            SenhaHasher senhaHasher,
            EmitirConfirmacaoEmailUseCase emitirConfirmacaoEmail) {
        this.usuarioService = usuarioService;
        this.cpfBloqueadoService = cpfBloqueadoService;
        this.senhaHasher = senhaHasher;
        this.emitirConfirmacaoEmail = emitirConfirmacaoEmail;
    }

    @Transactional
    public Usuario executar(Usuario usuarioCandidato, String senhaEmTexto) {
        validarMaioridade(usuarioCandidato);
        validarCpf(usuarioCandidato);
        validarUnicidade(usuarioCandidato);
        validarCpfNaoBloqueado(usuarioCandidato);

        usuarioCandidato.setSenhaHash(senhaHasher.hash(senhaEmTexto));
        Usuario criado = usuarioService.salvarNovo(usuarioCandidato);
        emitirConfirmacaoEmail.emitir(criado, Instant.now());
        return criado;
    }

    private void validarMaioridade(Usuario usuario) {
        int idade = Period.between(usuario.getDataNascimento(), OffsetDateTime.now(ZoneOffset.UTC).toLocalDate())
                .getYears();
        if (idade < IDADE_MINIMA) {
            throw new IdadeMinimaNaoAtingidaException();
        }
    }

    private void validarCpf(Usuario usuario) {
        if (!CpfValidator.isValido(usuario.getCpf())) {
            throw new CpfInvalidoException();
        }
    }

    private void validarUnicidade(Usuario usuario) {
        boolean duplicado = usuarioService.existePorEmail(usuario.getEmail())
                || usuarioService.existePorCpf(usuario.getCpf());
        if (duplicado) {
            throw new CadastroIndisponivelException();
        }
    }

    private void validarCpfNaoBloqueado(Usuario usuario) {
        if (cpfBloqueadoService.estaBloqueado(usuario.getCpf(), Instant.now())) {
            throw new CadastroIndisponivelException();
        }
    }
}
