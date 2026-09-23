# Manual de funcionamento — auth

## `POST /auth/cadastro` (UC01 — cadastrar conta universal)

1. `AuthController.cadastrar` recebe o corpo HTTP e desserializa em `CadastroRequest` (record). Bean Validation
   roda nas anotações do record (`@NotBlank`, `@Email`, `@Pattern`, `@Past`, `@Size`); o compact constructor
   normaliza strings (trim, lowercase de e-mail, remove não-dígitos de telefone/CPF).
2. `Controller` chama `UsuarioMapper.toEntity(request)` — monta um `Usuario` com papel `CONTRATANTE` e status
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

## Classes envolvidas

| Classe | Papel |
|---|---|
| `AuthController` | Adapter HTTP — só tradução request/response |
| `CadastrarUsuarioUseCase` | Regra de negócio pura do UC01 |
| `UsuarioMapper` | `Usuario` ⇄ `CadastroRequest`/`UsuarioResponse` |
| `UsuarioService` | Extends `comum/base/BaseService` — CRUD genérico + `existePorEmail`/`existePorCpf` |
| `CpfBloqueadoService` | Extends `comum/base/BaseService` — CRUD genérico + `estaBloqueado` (cooldown 12 meses) |
| `UsuarioRepository` / `CpfBloqueadoRepository` | Extends `comum/base/BaseRepository` — porta de persistência JPA |
| `CpfValidator` | Helper puro — formato e dígitos verificadores de CPF |
| `SenhaHasher` | Helper — encapsula `PasswordEncoder` (BCrypt) |
| `IdadeMinimaNaoAtingidaException`, `CpfInvalidoException`, `CadastroIndisponivelException` | Exceções de negócio do módulo |

Atualize esta página sempre que uma classe mudar de responsabilidade.
