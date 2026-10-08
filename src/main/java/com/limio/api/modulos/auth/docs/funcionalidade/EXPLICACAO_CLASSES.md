# Manual de funcionamento — auth

## `POST /auth/cadastro` (UC01 — cadastrar conta universal)

1. `AuthController.cadastrar` recebe o corpo HTTP e desserializa em `CadastroRequest` (record). Bean Validation
   roda nas anotações do record (`@NotBlank`, `@Email`, `@Pattern`, `@Past`, `@Size`); o compact constructor
   normaliza strings (trim, lowercase de e-mail, remove não-dígitos de telefone/CPF).
2. `Controller` chama `UsuarioMapper.toEntity(request)` — monta um `Usuario` com papel `EMPREGADOR` e status
   `ATIVA` por padrão, sem senha hasheada ainda.
3. `Controller` chama `CadastrarUsuarioUseCase.executar(usuarioCandidato, request.senha())`. O usecase não conhece
   `HttpServletRequest`, nem o record de entrada, nem repository diretamente — só os `Service` do módulo.
4. Dentro do usecase, em ordem:
   - `validarMaioridade` — calcula idade via `Period.between`; menor de 18 lança `IdadeMinimaNaoAtingidaException`.
   - `validarCpf` — usa `CpfValidator.isValido` (helper puro, formato + dígitos verificadores); inválido lança
     `CpfInvalidoException`.
   - `validarUnicidade` — chama `UsuarioService.existePorEmail`/`existePorCpf`; duplicado lança
     `CadastroIndisponivelException` (erro genérico, anti-enumeração).
   - `validarCpfNaoBloqueado` — chama `CpfBloqueadoService.estaBloqueado` com o limite de 12 meses atrás; CPF
     bloqueado lança a mesma `CadastroIndisponivelException`.
   - Se passou em tudo: `SenhaHasher.hash` (BCrypt via `PasswordEncoder` de `comum/config/SecurityConfig`) e
     `UsuarioService.salvar` (herdado de `BaseService`).
5. `Controller` usa `UsuarioMapper.toResponse` pra converter o `Usuario` salvo em `UsuarioResponse` e devolve 201.
6. Qualquer exceção de negócio lançada no caminho é interceptada por `GlobalExceptionHandler`
   (`comum/excecao/`), que mapeia pro `CodigoErro` correspondente e devolve `ErroResponse` — o controller nunca
   escreve `try/catch`.

## Toda requisição — `JwtAuthFilter` (`comum/seguranca/`)

Antes do controller, o `JwtAuthFilter` (registrado só na cadeia do Spring Security pelo `SecurityConfig`) lê
`Authorization: Bearer <token>`, valida assinatura e expiração com o `JwtDecoder` de `comum/config/JwtConfig` e põe
um `UsuarioAutenticado(id, papel)` no SecurityContext. Token ausente ou inválido não interrompe nada: a requisição
segue anônima, e se a rota não for pública o entry point delega ao `GlobalExceptionHandler` → 401 `NAO_AUTENTICADO`.
Controller recebe o usuário com `@AuthenticationPrincipal UsuarioAutenticado`.

## `POST /auth/login` (UC03)

1. `AuthController.login` desserializa `LoginRequest` (e-mail normalizado no compact constructor) e resolve o
   dispositivo (`dispositivo` do corpo ou User-Agent) e o IP.
2. `AutenticarUsuarioUseCase.executar(email, senha, dispositivo, ip)`, em ordem:
   - `UsuarioService.buscarPorEmail` (ignora caixa). O contador do rate limit é o e-mail gravado na conta, se ela
     existe, ou o digitado.
   - `TentativaLoginService.registrarSeLiberado` — com lock por e-mail, confere `LimiteTentativasLogin.estaBloqueado`
     e já grava a tentativa; bloqueado lança `LoginTemporariamenteBloqueadoException` sem conferir a senha.
   - Conta inexistente ou `Usuario.isEncerrada()` → `SenhaHasher.simularConferencia` (mesmo tempo de um BCrypt real)
     e `CredenciaisInvalidasException`; a tentativa fica gravada como falha.
   - `SenhaHasher.confere` falhou → mesma `CredenciaisInvalidasException`, tentativa fica como falha.
   - Senha conferiu → apaga a tentativa (`TentativaLoginService.remover`).
   - `Usuario.isSuspensa()` → `ContaSuspensaException`.
   - Monta `Sessao` (usuário, dispositivo, ip), `TokenService.emitir` gera os tokens e preenche hash/expiração da
     sessão, `SessaoService.salvar`.
3. `SessaoMapper.toLoginResponse(TokensSessao)` → 200.

## `POST /auth/refresh`

`RenovarSessaoUseCase` (transacional) busca a sessão pelo SHA-256 do refresh token com `SELECT ... FOR UPDATE`
(`SessaoService.buscarPorRefreshTokenHashParaRenovar`), exige `Sessao.isAtiva(agora)` e conta não encerrada
(senão `SessaoInvalidaException`) e não suspensa (`ContaSuspensaException`). `TokenService.emitir` rotaciona: hash
novo na mesma sessão, expiração empurrada, token de acesso com o papel atual do usuário.

## `POST /auth/logout` (UC08)

`EncerrarSessaoUseCase` busca a sessão pelo hash do refresh token **e** pelo id do `UsuarioAutenticado`
(`SessaoService.buscarDoUsuarioPorRefreshTokenHash`) e preenche `revogadaEm`. Não achou ou já revogada: não faz
nada. Controller devolve 204 nos dois casos.

## `PATCH /auth/papel-ativo` (UC09)

`TrocarPapelAtivoUseCase` recusa papel que não seja `EMPREGADOR`/`PRESTADOR` (`PapelInvalidoException`), carrega o
usuário pelo id do token, recusa conta encerrada/suspensa e grava `papelAtivo`. `UsuarioMapper.toPapelAtivoResponse`
→ 200. O token de acesso em uso não muda — o papel novo chega no próximo `/auth/refresh` ou login.

## Classes envolvidas

| Classe | Papel |
|---|---|
| `AuthController` | Adapter HTTP — só tradução request/response |
| `CadastrarUsuarioUseCase` | Regra de negócio pura do UC01 |
| `AutenticarUsuarioUseCase` | Login (UC03): rate limit, credenciais, status, abertura da sessão |
| `RenovarSessaoUseCase` | Renovação do token de acesso com rotação do refresh token |
| `EncerrarSessaoUseCase` | Logout do aparelho (UC08) |
| `TrocarPapelAtivoUseCase` | Troca de papel ativo (UC09) |
| `UsuarioMapper` | `Usuario` ⇄ `CadastroRequest`/`UsuarioResponse`/`PapelAtivoResponse` |
| `SessaoMapper` | `TokensSessao` → `LoginResponse` |
| `UsuarioService` | Extends `comum/base/BaseService` — CRUD genérico + `existePorEmail`/`existePorCpf`/`buscarPorEmail` |
| `CpfBloqueadoService` | Extends `comum/base/BaseService` — CRUD genérico + `estaBloqueado` (cooldown 12 meses) |
| `SessaoService` | Extends `comum/base/BaseService` — CRUD genérico + busca por hash do refresh token |
| `TentativaLoginService` | Extends `comum/base/BaseService` — CRUD genérico + `ultimasFalhas` do e-mail |
| `UsuarioRepository` / `CpfBloqueadoRepository` / `SessaoRepository` / `TentativaLoginRepository` | Extends `comum/base/BaseRepository` — porta de persistência JPA |
| `TokenService` | Emite token de acesso (JWT) + refresh token de uma `Sessao` |
| `TokensSessao` | Resultado de abrir/renovar sessão — único lugar com o refresh token em texto puro |
| `CpfValidator` | Helper puro — formato e dígitos verificadores de CPF |
| `SenhaHasher` | Helper — encapsula `PasswordEncoder` (BCrypt): hash, conferência e conferência simulada |
| `RefreshTokenHelper` | Helper puro — gera refresh token e calcula o SHA-256 guardado na sessão |
| `LimiteTentativasLogin` | Helper — regra do rate limit (máx. de falhas, janela, duração do bloqueio) |
| `IdadeMinimaNaoAtingidaException`, `CpfInvalidoException`, `CadastroIndisponivelException` | Exceções de negócio do UC01 |
| `CredenciaisInvalidasException`, `LoginTemporariamenteBloqueadoException`, `ContaSuspensaException`, `SessaoInvalidaException`, `PapelInvalidoException` | Exceções de negócio do TICKET-0032 |

Atualize esta página sempre que uma classe mudar de responsabilidade.
