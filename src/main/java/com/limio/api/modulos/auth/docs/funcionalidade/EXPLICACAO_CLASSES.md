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
     `UsuarioService.salvarNovo`, que traduz a violação de `uk_usuario_email`/`uk_usuario_cpf` (cadastro simultâneo)
     na mesma `CadastroIndisponivelException`.
   - `EmissorConfirmacaoEmail.emitir` grava o token `CONFIRMACAO_EMAIL` e publica o evento do link, enviado
     depois do commit (o usecase é `@Transactional`).
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
   - `TentativaLoginService.registrarSeLiberado` — com lock por e-mail (e por IP, se o IP for público — `IpHelper`,
     `LimiteTentativasLoginPorIp`), confere `LimiteTentativasLogin.estaBloqueado`
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

## `PATCH /auth/email` (TICKET-0048 — trocar e-mail)

1. `AuthController.trocarEmail` recebe `@AuthenticationPrincipal UsuarioAutenticado` e `TrocarEmailRequest`
   (e-mail normalizado, formato, `senhaAtual` obrigatória) e chama `TrocarEmailUseCase.executar(usuario.id(), ...)`.
2. O usecase carrega o usuário com lock de linha (`UsuarioService.buscarParaAtualizarOuFalhar`), exige conta ativa e
   confere a senha atual.
3. E-mail igual ao atual: confirmado → não faz nada; não confirmado → reemite o link. E-mail diferente: recusa se for de
   outra conta (`ContatoNaoPermitidoException`, 409).
4. Conta o limite (`VerificacaoContatoService.contarDesde`, 3/h) → `TrocaContatoLimiteExcedidaException` (429).
5. Troca: grava e-mail, zera `emailConfirmadoEm` e força o `UPDATE` (`UsuarioService.salvarComEmailUnico`, que traduz a
   violação de `uk_usuario_email`).
6. Consome os tokens `CONFIRMACAO_EMAIL` pendentes, cria o token novo (só o hash) e publica o evento do link
   (`ConfirmacaoEmailSolicitadaEvent`), enviado depois do commit por `EmailEventosService` — tudo via
   `EmissorConfirmacaoEmail`. O link abre a página `/confirmar-email` (ver abaixo).
7. O controller devolve `ContatoResponse` via `UsuarioMapper.toContatoResponse`.

## `PATCH /auth/telefone` (TICKET-0048 — trocar telefone)

Mesmo começo (lock, conta ativa, senha atual). `TrocarTelefoneRequest` normaliza com `TelefoneHelper` para
dígitos nacionais (`DDNNNNNNNNN`, o formato do banco); o usecase grava e zera `telefoneConfirmadoEm`. A resposta
(`UsuarioMapper.toContatoResponse`) devolve o número em `+55...` via `TelefoneHelper.paraInternacional`. Não cria token,
não envia SMS (UC04 inexistente).

## Links dos e-mails e páginas web (TICKET-0047, ADR-0002)

`LinkWeb` monta `<app.web.url>/<pagina>#token=<token>`. O token vai no fragmento, que o navegador nunca envia ao
servidor. `comum/config/PaginasWebConfig` encaminha `GET /nova-senha` e `GET /confirmar-email` para o HTML estático
em `src/main/resources/static/paginas/`; o `SecurityConfig` libera essas rotas e `/paginas/**`. O JavaScript da
página lê e apaga o fragmento e chama um dos POSTs abaixo. A página não tem regra.

## `POST /auth/confirmar-email` (UC05)

1. `AuthController.confirmarEmail` recebe `ConfirmarEmailRequest` (`token` obrigatório) e chama
   `ConfirmarEmailUseCase.executar(token)`.
2. O usecase calcula o SHA-256 do token, busca **só o dono** (`VerificacaoContatoService.buscarUsuarioIdPorTokenHash`),
   trava a conta (`UsuarioService.buscarParaAtualizarOuFalhar`) e relê o token com `SELECT ... FOR UPDATE`
   (`buscarParaConsumir`). Conta antes de token: mesma ordem da troca de e-mail, que trava a conta e depois mexe nos
   tokens.
3. Token inexistente, expirado, já consumido ou conta encerrada → `LinkConfirmacaoEmailInvalidoException` (401).
4. Sucesso: preenche `emailConfirmadoEm` e consome o token. Responde 200 sem corpo.

## `POST /auth/reenviar-confirmacao-email` ("Reenviar link" do app)

`ReenviarConfirmacaoEmailUseCase`: busca só o id pelo e-mail (`UsuarioService.buscarIdPorEmail`), trava a conta e, se
ela não está encerrada, o e-mail ainda não foi confirmado, é o mesmo e-mail pedido e o limite não bateu, chama
`EmissorConfirmacaoEmail.emitir`. Em qualquer outro caso não faz nada. O controller sempre responde 200
(anti-enumeração).

## `POST /auth/redefinir-senha` (UC06)

`RedefinirSenhaUseCase` segue o mesmo caminho de consumo do `ConfirmarEmailUseCase` (dono → lock da conta → token
travado): dois envios simultâneos do mesmo link não redefinem a senha duas vezes. Depois grava a nova senha, consome
o token e encerra todas as sessões.

## Classes envolvidas

| Classe | Papel |
|---|---|
| `AuthController` | Adapter HTTP — só tradução request/response |
| `CadastrarUsuarioUseCase` | Regra de negócio pura do UC01 |
| `AutenticarUsuarioUseCase` | Login (UC03): rate limit, credenciais, status, abertura da sessão |
| `RenovarSessaoUseCase` | Renovação do token de acesso com rotação do refresh token |
| `EncerrarSessaoUseCase` | Logout do aparelho (UC08) |
| `TrocarPapelAtivoUseCase` | Troca de papel ativo (UC09) |
| `TrocarEmailUseCase` | Troca de e-mail (TICKET-0048): lock, unicidade, limite, emite link (via `EmissorConfirmacaoEmail`) e avisa o e-mail antigo após o commit |
| `EmissorConfirmacaoEmail` | Emissão do link de confirmação, usada pelo cadastro, troca de e-mail e reenvio: consome os pendentes, grava o novo, publica o evento; `limiteAtingido` (3/h por conta) |
| `ConfirmarEmailUseCase` | UC05 (TICKET-0047): consome o token `CONFIRMACAO_EMAIL` sob lock e preenche `emailConfirmadoEm` |
| `ReenviarConfirmacaoEmailUseCase` | "Reenviar link" (TICKET-0047): público, anti-enumeração |
| `RedefinirSenhaUseCase` | UC06: consome o token `RECUPERACAO_SENHA` sob lock, grava a senha, encerra todas as sessões |
| `LimparRetencaoUseCase` | Rotina diária de retenção: `cpf_bloqueado`, `log_auditoria`, `sessao`, `tentativa_login`, cada uma isolada |
| `LinkWeb` | Helper — monta o link `https` da página web com o token no fragmento |
| `PaginasWebConfig` (`comum/config/`) | URLs limpas `/nova-senha` e `/confirmar-email` para o HTML estático |
| `TrocarTelefoneUseCase` | Troca de telefone (TICKET-0048): grava dígitos nacionais, limpa confirmação; sem SMS |
| `ConsultaDadosUsuarioService` | Implementa a porta `comum/portas/ConsultaDadosUsuario` para outros módulos (só leitura) |
| `TelefoneHelper` | Helper puro — `normalizar` (entrada → dígitos nacionais, só a troca) e `paraInternacional` (saída → `+55...`, usado pelo mapper e pela porta) |
| `ConfirmacaoEmailSolicitadaEvent`, `EmailContaAlteradoEvent` | Eventos internos do `auth` (`auth/eventos/`) publicados pela troca de e-mail |
| `EmailEventosService` | Escuta os eventos com `@TransactionalEventListener(AFTER_COMMIT)` e envia os e-mails; falha só é logada, sem `fallbackExecution` |
| `UsuarioMapper` | `Usuario` ⇄ `CadastroRequest`/`UsuarioResponse`/`PapelAtivoResponse`/`ContatoResponse` |
| `SessaoMapper` | `TokensSessao` → `LoginResponse` |
| `UsuarioService` | Extends `comum/base/BaseService` — CRUD genérico + `existePorEmail`/`existePorCpf`/`buscarPorEmail`/`buscarIdPorEmail`/`buscarPorId`, `buscarParaAtualizarOuFalhar` (lock), `salvarComEmailUnico` e `salvarNovo` |
| `VerificacaoContatoService` | Extends `comum/base/BaseService` — tokens de uso único: busca por hash, `buscarUsuarioIdPorTokenHash`, `buscarParaConsumir` (lock), contagem do limite, pendentes |
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
| `ContatoNaoPermitidoException`, `TrocaContatoLimiteExcedidaException` | Exceções de negócio do TICKET-0048 (troca de contato) |
| `LinkConfirmacaoEmailInvalidoException` | Link de confirmação inválido, expirado, usado ou de conta encerrada (TICKET-0047) |
| `CredenciaisInvalidasException`, `LoginTemporariamenteBloqueadoException`, `ContaSuspensaException`, `SessaoInvalidaException`, `PapelInvalidoException` | Exceções de negócio do TICKET-0032 |

## Porta de consulta `comum/portas/` (leitura síncrona entre módulos)

> **Formalizado no ADR-0003** (`docs/adr/0003-comunicacao-entre-modulos-eventos-e-portas.md`, aceito em 2026-10-26):
> eventos para reação, portas para consulta.

- **O que é:** interface + record imutável em `comum/portas/` (hoje `ConsultaDadosUsuario` e `DadosUsuario`). O módulo
  dono do dado implementa em `actions/service/` (aqui, `ConsultaDadosUsuarioService`); o consumidor (`perfil`) injeta só
  a interface e nunca importa classe do `auth`.
- **Quando usar:** consulta de **leitura** síncrona de dado que pertence a outro módulo. Retorna record, nunca entity.
- **Quando não usar:** escrita ou reação em outro módulo continua sendo evento de domínio
  (`ApplicationEventPublisher`, pasta `comum/eventos/` criada na PR #7) ou fila. Regra prática: *eventos para reação,
  portas para consulta* — não criar um quarto canal.
- **Critério pra nascer:** só quando um módulo já precisa do dado hoje (dono + consumidor), como nas demais regras de `comum/`.

Atualize esta página sempre que uma classe mudar de responsabilidade.
