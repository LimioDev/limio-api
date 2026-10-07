package com.limio.api.modulos.auth.actions.usecase;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.limio.api.modulos.auth.actions.helper.DispositivoHelper;
import com.limio.api.modulos.auth.actions.helper.LimiteTentativasLogin;
import com.limio.api.modulos.auth.actions.helper.SenhaHasher;
import com.limio.api.modulos.auth.actions.service.TokenService;
import com.limio.api.modulos.auth.excecao.ContaSuspensaException;
import com.limio.api.modulos.auth.excecao.CredenciaisInvalidasException;
import com.limio.api.modulos.auth.excecao.LoginTemporariamenteBloqueadoException;
import com.limio.api.modulos.auth.sessao.Sessao;
import com.limio.api.modulos.auth.sessao.SessaoService;
import com.limio.api.modulos.auth.sessao.TokensSessao;
import com.limio.api.modulos.auth.tentativalogin.TentativaLogin;
import com.limio.api.modulos.auth.tentativalogin.TentativaLoginService;
import com.limio.api.modulos.auth.usuario.Usuario;
import com.limio.api.modulos.auth.usuario.UsuarioService;

/**
 * Regra de negócio do UC03 (login com e-mail e senha): abre uma {@link Sessao}
 * pro aparelho e devolve os tokens com o papel ativo salvo no usuário.
 *
 * E-mail inexistente, senha errada e conta encerrada respondem igual
 * ({@link CredenciaisInvalidasException}) e contam pro rate limit.
 *
 * A tentativa é gravada antes de conferir a senha (assim tentativas
 * simultâneas já contam umas pras outras) e apagada se a senha conferir — o
 * que sobra em {@code tentativa_login} são as falhas. Sem
 * {@code @Transactional} de propósito: a falha gravada não pode sumir no
 * rollback da exceção lançada logo em seguida.
 *
 * Efeito colateral aceito dessa escolha: apagar a tentativa e salvar a sessão
 * são commits separados. Se o processo cair (ou {@code salvar} lançar) entre
 * os dois, o usuário recebe 500 sem sessão aberta e a tentativa bem-sucedida
 * já foi apagada — só a contagem do rate limit é afetada, as falhas
 * anteriores continuam gravadas.
 */
@Service
public class AutenticarUsuarioUseCase {

    private final UsuarioService usuarioService;
    private final SessaoService sessaoService;
    private final TentativaLoginService tentativaLoginService;
    private final LimiteTentativasLogin limiteTentativasLogin;
    private final SenhaHasher senhaHasher;
    private final TokenService tokenService;

    public AutenticarUsuarioUseCase(UsuarioService usuarioService,
            SessaoService sessaoService,
            TentativaLoginService tentativaLoginService,
            LimiteTentativasLogin limiteTentativasLogin,
            SenhaHasher senhaHasher,
            TokenService tokenService) {
        this.usuarioService = usuarioService;
        this.sessaoService = sessaoService;
        this.tentativaLoginService = tentativaLoginService;
        this.limiteTentativasLogin = limiteTentativasLogin;
        this.senhaHasher = senhaHasher;
        this.tokenService = tokenService;
    }

    public TokensSessao executar(String email, String senha, String dispositivo, String ip) {
        Instant agora = Instant.now();
        Optional<Usuario> encontrado = usuarioService.buscarPorEmail(email);
        TentativaLogin tentativa = registrarTentativa(contadorDe(email, encontrado), ip, agora);

        Usuario usuario = encontrado.filter(conta -> !conta.isEncerrada()).orElse(null);
        if (usuario == null) {
            senhaHasher.simularConferencia(senha);
            throw new CredenciaisInvalidasException();
        }
        if (!senhaHasher.confere(senha, usuario.getSenhaHash())) {
            throw new CredenciaisInvalidasException();
        }
        tentativaLoginService.remover(tentativa);
        if (usuario.isSuspensa()) {
            throw new ContaSuspensaException();
        }

        Sessao sessao = Sessao.builder()
                .usuario(usuario)
                .dispositivo(DispositivoHelper.normalizar(dispositivo))
                .ip(ip)
                .build();
        TokensSessao tokens = tokenService.emitir(usuario, agora);
        sessao.rotacionar(tokens, agora);
        sessaoService.salvar(sessao);
        return tokens;
    }

    /**
     * Conta existente: o contador é o e-mail como está gravado, não como foi
     * digitado. A busca ignora caixa no banco, e grafias que o banco considera
     * iguais (ex.: "ı" sem ponto vira "I" no upper do Postgres) achariam a
     * mesma conta com um contador novo cada.
     */
    private static String contadorDe(String emailDigitado, Optional<Usuario> encontrado) {
        return encontrado.map(Usuario::getEmail).orElse(emailDigitado);
    }

    private TentativaLogin registrarTentativa(String contador, String ip, Instant agora) {
        return tentativaLoginService
                .registrarSeLiberado(contador, ip, limiteTentativasLogin.maxFalhas(),
                        falhas -> limiteTentativasLogin.estaBloqueado(falhas, agora))
                .orElseThrow(LoginTemporariamenteBloqueadoException::new);
    }
}
