# Manual de funcionamento — notificacao

> Status: **implementado (ETI-34), exceto o `AceiteTermo`.** Código em `modulos/notificacao/`. Especificação em
> `docs/features/UC15-configurar-preferencias-de-notificacao.md`. O usuário logado chega ao controller como
> `UsuarioAutenticado` (`@AuthenticationPrincipal`), lido do token pelo `JwtAuthFilter`.

## `GET /conta/preferencias-notificacao` (ETI-34 — consultar preferências)

1. O controller recebe a requisição de um usuário autenticado. Sem login → `401`, antes de chegar ao usecase.
2. `Controller` chama `ConsultarPreferenciaNotificacaoUseCase.executar(...)` passando o identificador do usuário
   logado. O usecase não conhece `HttpServletRequest` nem records.
3. Dentro do usecase: busca a `PreferenciaNotificacao` do usuário via `PreferenciaNotificacaoService`. Se não existe
   registro, devolve a entity com os padrões de conta nova (Push ligado; E-mail, SMS e WhatsApp desligados), sem
   gravar nada no banco.
4. `Controller` usa `PreferenciaNotificacaoMapper.toResponse` para converter a entity em
   `PreferenciaNotificacaoResponse` e devolve `200`.

## `PUT /conta/preferencias-notificacao` (ETI-34 — atualizar preferências)

1. O controller desserializa o corpo em `PreferenciaNotificacaoRequest` (record). Bean Validation exige `push`,
   `email`, `sms` e `whatsapp` presentes e booleanos; falha → `400`. Sem login → `401`.
2. `Controller` chama `PreferenciaNotificacaoMapper.toEntity(request)` e então
   `AtualizarPreferenciaNotificacaoUseCase.executar(usuario.id(), preferenciaNova)`. Quando o `AceiteTermo` existir,
   o que o aceite precisa vindo da camada HTTP (ex.: `ip`) entra como parâmetro extra, sem expor
   `HttpServletRequest` ao usecase.
3. Dentro do usecase, em ordem:
   - Carrega a preferência atual do usuário; se não existe (primeiro `PUT`), cria uma nova para ele.
   - **Pendente:** se `whatsapp` passou de `false` para `true`, registrar o consentimento em `AceiteTermo`
     (`documento`, `versao`, `origem = configuracoes`, `ip`, `aceitoEm`). Hoje só grava o booleano.
   - Se `whatsapp` passou de `true` para `false`: apenas grava `whatsapp = false`.
   - Cada canal é aplicado de forma independente; salva via `PreferenciaNotificacaoService.salvar`.
4. `Controller` converte a entity salva com `PreferenciaNotificacaoMapper.toResponse` e devolve `200` com o estado
   salvo.
5. Exceção de negócio lançada no caminho é interceptada por `GlobalExceptionHandler` (`comum/excecao/`) e devolvida
   como `ErroResponse` — o controller nunca escreve `try/catch`.

## Classes envolvidas

| Classe | Papel |
|---|---|
| `PreferenciaNotificacaoController` | Adapter HTTP — só tradução request/response |
| `ConsultarPreferenciaNotificacaoUseCase` | Regra de negócio: ler preferência ou padrões de conta nova |
| `AtualizarPreferenciaNotificacaoUseCase` | Regra de negócio: gravar canais (cria o registro no primeiro `PUT`); aceite do WhatsApp em `AceiteTermo` ainda pendente |
| `PreferenciaNotificacaoMapper` | `PreferenciaNotificacao` ⇄ `PreferenciaNotificacaoRequest`/`PreferenciaNotificacaoResponse` |
| `PreferenciaNotificacao` | Entity (`extends BaseEntity`): `usuario_id`, `push`, `email`, `sms`, `whatsapp` |
| `PreferenciaNotificacaoService` | Extends `comum/base/BaseService` — CRUD genérico + busca por usuário |
| `PreferenciaNotificacaoRepository` | Extends `comum/base/BaseRepository` — porta de persistência JPA |
| `PreferenciaNotificacaoRequest` / `PreferenciaNotificacaoResponse` | Records HTTP com os 4 booleanos |
| `AceiteTermo` | Registro do consentimento de WhatsApp — **ainda não existe** (definição fora desta feature) |

Atualize esta página sempre que uma classe mudar de responsabilidade.
