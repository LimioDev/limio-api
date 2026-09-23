package com.limio.api.modulos.auth.actions.usecase;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.ZoneOffset;

import org.springframework.stereotype.Service;

import com.limio.api.modulos.auth.CpfBloqueadoService;
import com.limio.api.modulos.auth.Usuario;
import com.limio.api.modulos.auth.UsuarioService;
import com.limio.api.modulos.auth.actions.helper.CpfValidator;
import com.limio.api.modulos.auth.actions.helper.SenhaHasher;
import com.limio.api.modulos.auth.excecao.CadastroIndisponivelException;
import com.limio.api.modulos.auth.excecao.CpfInvalidoException;
import com.limio.api.modulos.auth.excecao.IdadeMinimaNaoAtingidaException;

/**
 * Regra de negócio do UC01 (cadastrar conta universal). Não conhece HTTP nem
 * record de entrada/saída, nem repository diretamente — orquestra decisão de
 * negócio e delegada persistência aos services do módulo.
 */
@Service
public class CadastrarUsuarioUseCase {

    private static final int IDADE_MINIMA = 18;
    private static final int COOLDOWN_CPF_MESES = 12;

    private final UsuarioService usuarioService;
    private final CpfBloqueadoService cpfBloqueadoService;
    private final SenhaHasher senhaHasher;

    public CadastrarUsuarioUseCase(UsuarioService usuarioService,
            CpfBloqueadoService cpfBloqueadoService,
            SenhaHasher senhaHasher) {
        this.usuarioService = usuarioService;
        this.cpfBloqueadoService = cpfBloqueadoService;
        this.senhaHasher = senhaHasher;
    }

    public Usuario executar(Usuario usuarioCandidato, String senhaEmTexto) {
        validarMaioridade(usuarioCandidato);
        validarCpf(usuarioCandidato);
        validarUnicidade(usuarioCandidato);
        validarCpfNaoBloqueado(usuarioCandidato);

        usuarioCandidato.setSenhaHash(senhaHasher.hash(senhaEmTexto));
        return usuarioService.salvar(usuarioCandidato);
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
        Instant limiteCooldown = OffsetDateTime.now(ZoneOffset.UTC).minusMonths(COOLDOWN_CPF_MESES).toInstant();
        if (cpfBloqueadoService.estaBloqueado(usuario.getCpf(), limiteCooldown)) {
            throw new CadastroIndisponivelException();
        }
    }
}
