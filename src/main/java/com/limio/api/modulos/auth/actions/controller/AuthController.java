package com.limio.api.modulos.auth.actions.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.limio.api.comum.seguranca.UsuarioAutenticado;
import com.limio.api.modulos.auth.actions.mapper.SessaoMapper;
import com.limio.api.modulos.auth.actions.mapper.UsuarioMapper;
import com.limio.api.modulos.auth.actions.usecase.AlterarSenhaUseCase;
import com.limio.api.modulos.auth.actions.usecase.AutenticarUsuarioUseCase;
import com.limio.api.modulos.auth.actions.usecase.CadastrarUsuarioUseCase;
import com.limio.api.modulos.auth.actions.usecase.ConfirmarEmailUseCase;
import com.limio.api.modulos.auth.actions.usecase.EncerrarContaUseCase;
import com.limio.api.modulos.auth.actions.usecase.EncerrarSessaoUseCase;
import com.limio.api.modulos.auth.actions.usecase.RedefinirSenhaUseCase;
import com.limio.api.modulos.auth.actions.usecase.ReenviarConfirmacaoEmailUseCase;
import com.limio.api.modulos.auth.actions.usecase.RenovarSessaoUseCase;
import com.limio.api.modulos.auth.actions.usecase.SolicitarRecuperacaoSenhaUseCase;
import com.limio.api.modulos.auth.actions.usecase.TrocarEmailUseCase;
import com.limio.api.modulos.auth.actions.usecase.TrocarPapelAtivoUseCase;
import com.limio.api.modulos.auth.actions.usecase.TrocarTelefoneUseCase;
import com.limio.api.modulos.auth.records.AlterarSenhaRequest;
import com.limio.api.modulos.auth.records.CadastroRequest;
import com.limio.api.modulos.auth.records.ConfirmarEmailRequest;
import com.limio.api.modulos.auth.records.ContatoResponse;
import com.limio.api.modulos.auth.records.EncerrarContaRequest;
import com.limio.api.modulos.auth.records.LoginRequest;
import com.limio.api.modulos.auth.records.LoginResponse;
import com.limio.api.modulos.auth.records.PapelAtivoResponse;
import com.limio.api.modulos.auth.records.RedefinirSenhaRequest;
import com.limio.api.modulos.auth.records.ReenviarConfirmacaoEmailRequest;
import com.limio.api.modulos.auth.records.RefreshTokenRequest;
import com.limio.api.modulos.auth.records.SolicitarRecuperacaoSenhaRequest;
import com.limio.api.modulos.auth.records.TrocarEmailRequest;
import com.limio.api.modulos.auth.records.TrocarPapelRequest;
import com.limio.api.modulos.auth.records.TrocarTelefoneRequest;
import com.limio.api.modulos.auth.records.UsuarioResponse;
import com.limio.api.modulos.auth.sessao.TokensSessao;
import com.limio.api.modulos.auth.usuario.Usuario;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final CadastrarUsuarioUseCase cadastrarUsuarioUseCase;
    private final AutenticarUsuarioUseCase autenticarUsuarioUseCase;
    private final RenovarSessaoUseCase renovarSessaoUseCase;
    private final EncerrarSessaoUseCase encerrarSessaoUseCase;
    private final TrocarPapelAtivoUseCase trocarPapelAtivoUseCase;
    private final SolicitarRecuperacaoSenhaUseCase solicitarRecuperacaoSenhaUseCase;
    private final RedefinirSenhaUseCase redefinirSenhaUseCase;
    private final AlterarSenhaUseCase alterarSenhaUseCase;
    private final EncerrarContaUseCase encerrarContaUseCase;
    private final TrocarEmailUseCase trocarEmailUseCase;
    private final TrocarTelefoneUseCase trocarTelefoneUseCase;
    private final ConfirmarEmailUseCase confirmarEmailUseCase;
    private final ReenviarConfirmacaoEmailUseCase reenviarConfirmacaoEmailUseCase;
    private final UsuarioMapper usuarioMapper;
    private final SessaoMapper sessaoMapper;

    public AuthController(CadastrarUsuarioUseCase cadastrarUsuarioUseCase,
            AutenticarUsuarioUseCase autenticarUsuarioUseCase,
            RenovarSessaoUseCase renovarSessaoUseCase,
            EncerrarSessaoUseCase encerrarSessaoUseCase,
            TrocarPapelAtivoUseCase trocarPapelAtivoUseCase,
            SolicitarRecuperacaoSenhaUseCase solicitarRecuperacaoSenhaUseCase,
            RedefinirSenhaUseCase redefinirSenhaUseCase,
            AlterarSenhaUseCase alterarSenhaUseCase,
            EncerrarContaUseCase encerrarContaUseCase,
            TrocarEmailUseCase trocarEmailUseCase,
            TrocarTelefoneUseCase trocarTelefoneUseCase,
            ConfirmarEmailUseCase confirmarEmailUseCase,
            ReenviarConfirmacaoEmailUseCase reenviarConfirmacaoEmailUseCase,
            UsuarioMapper usuarioMapper,
            SessaoMapper sessaoMapper) {
        this.cadastrarUsuarioUseCase = cadastrarUsuarioUseCase;
        this.autenticarUsuarioUseCase = autenticarUsuarioUseCase;
        this.renovarSessaoUseCase = renovarSessaoUseCase;
        this.encerrarSessaoUseCase = encerrarSessaoUseCase;
        this.trocarPapelAtivoUseCase = trocarPapelAtivoUseCase;
        this.solicitarRecuperacaoSenhaUseCase = solicitarRecuperacaoSenhaUseCase;
        this.redefinirSenhaUseCase = redefinirSenhaUseCase;
        this.alterarSenhaUseCase = alterarSenhaUseCase;
        this.encerrarContaUseCase = encerrarContaUseCase;
        this.trocarEmailUseCase = trocarEmailUseCase;
        this.trocarTelefoneUseCase = trocarTelefoneUseCase;
        this.confirmarEmailUseCase = confirmarEmailUseCase;
        this.reenviarConfirmacaoEmailUseCase = reenviarConfirmacaoEmailUseCase;
        this.usuarioMapper = usuarioMapper;
        this.sessaoMapper = sessaoMapper;
    }

    @PostMapping("/cadastro")
    public ResponseEntity<UsuarioResponse> cadastrar(@Valid @RequestBody CadastroRequest request) {
        Usuario usuarioCandidato = usuarioMapper.toEntity(request);
        Usuario usuarioCriado = cadastrarUsuarioUseCase.executar(usuarioCandidato, request.senha());
        return ResponseEntity.status(HttpStatus.CREATED).body(usuarioMapper.toResponse(usuarioCriado));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        TokensSessao tokens = autenticarUsuarioUseCase.executar(
                request.email(), request.senha(), dispositivo(request, http), http.getRemoteAddr());
        return ResponseEntity.ok(sessaoMapper.toLoginResponse(tokens));
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> renovar(@Valid @RequestBody RefreshTokenRequest request) {
        TokensSessao tokens = renovarSessaoUseCase.executar(request.refreshToken());
        return ResponseEntity.ok(sessaoMapper.toLoginResponse(tokens));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal UsuarioAutenticado usuario,
            @Valid @RequestBody RefreshTokenRequest request) {
        encerrarSessaoUseCase.executar(usuario.id(), request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/papel-ativo")
    public ResponseEntity<PapelAtivoResponse> trocarPapel(@AuthenticationPrincipal UsuarioAutenticado usuario,
            @Valid @RequestBody TrocarPapelRequest request) {
        Usuario atualizado = trocarPapelAtivoUseCase.executar(usuario.id(), request.papel());
        return ResponseEntity.ok(usuarioMapper.toPapelAtivoResponse(atualizado));
    }

    /** Resposta sempre genérica (anti-enumeração) — exista o e-mail ou não, o 200 é o mesmo. */
    @PostMapping("/recuperar-senha")
    public ResponseEntity<Void> recuperarSenha(@Valid @RequestBody SolicitarRecuperacaoSenhaRequest request) {
        solicitarRecuperacaoSenhaUseCase.executar(request.email());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/redefinir-senha")
    public ResponseEntity<Void> redefinirSenha(@Valid @RequestBody RedefinirSenhaRequest request) {
        redefinirSenhaUseCase.executar(request.token(), request.novaSenha());
        return ResponseEntity.ok().build();
    }

    /** UC05: chamado pela página {@code /confirmar-email} com o token do link. */
    @PostMapping("/confirmar-email")
    public ResponseEntity<Void> confirmarEmail(@Valid @RequestBody ConfirmarEmailRequest request) {
        confirmarEmailUseCase.executar(request.token());
        return ResponseEntity.ok().build();
    }

    /** "Reenviar link" do app. Resposta sempre genérica (anti-enumeração), como em {@code /recuperar-senha}. */
    @PostMapping("/reenviar-confirmacao-email")
    public ResponseEntity<Void> reenviarConfirmacaoEmail(@Valid @RequestBody ReenviarConfirmacaoEmailRequest request) {
        reenviarConfirmacaoEmailUseCase.executar(request.email());
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/senha")
    public ResponseEntity<LoginResponse> alterarSenha(@AuthenticationPrincipal UsuarioAutenticado usuario,
            @Valid @RequestBody AlterarSenhaRequest request) {
        TokensSessao tokens = alterarSenhaUseCase.executar(usuario.id(), request.senhaAtual(), request.novaSenha(),
                request.refreshToken());
        return ResponseEntity.ok(sessaoMapper.toLoginResponse(tokens));
    }

    /** UC10 (LGPD): encerra e anonimiza a conta. Sem {@code confirmacao=true}, nem chega aqui (400). */
    @PostMapping("/encerrar-conta")
    public ResponseEntity<Void> encerrarConta(@AuthenticationPrincipal UsuarioAutenticado usuario,
            @Valid @RequestBody EncerrarContaRequest request, HttpServletRequest http) {
        encerrarContaUseCase.executar(usuario.id(), http.getRemoteAddr());
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/email")
    public ResponseEntity<ContatoResponse> trocarEmail(@AuthenticationPrincipal UsuarioAutenticado usuario,
            @Valid @RequestBody TrocarEmailRequest request) {
        Usuario atualizado = trocarEmailUseCase.executar(usuario.id(), request.novoEmail(), request.senhaAtual());
        return ResponseEntity.ok(usuarioMapper.toContatoResponse(atualizado));
    }

    @PatchMapping("/telefone")
    public ResponseEntity<ContatoResponse> trocarTelefone(@AuthenticationPrincipal UsuarioAutenticado usuario,
            @Valid @RequestBody TrocarTelefoneRequest request) {
        Usuario atualizado = trocarTelefoneUseCase.executar(usuario.id(), request.novoTelefone(),
                request.senhaAtual());
        return ResponseEntity.ok(usuarioMapper.toContatoResponse(atualizado));
    }

    /** Sem dispositivo informado, identifica o aparelho pelo User-Agent. Normalização fica no usecase. */
    private static String dispositivo(LoginRequest request, HttpServletRequest http) {
        return request.dispositivo() != null ? request.dispositivo() : http.getHeader(HttpHeaders.USER_AGENT);
    }
}
