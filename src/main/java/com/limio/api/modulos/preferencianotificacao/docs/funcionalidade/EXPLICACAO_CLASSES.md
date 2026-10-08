# Manual de funcionamento — preferencianotificacao

> Status: **planejado (ETI-34), ainda não implementado.** Os nomes de classe abaixo são a estrutura prevista pela
> especificação em `docs/features/ETI-34-preferencias-de-notificacao.md`; ajustar conforme a implementação real.
> Como o usuário logado é identificado não é definido aqui (pertence à tarefa de autenticação).

## `GET /conta/preferencias-notificacao` (ETI-34 — consultar preferências)

1. O controller recebe a requisição de um usuário autenticado. Sem login → `401`, antes de chegar ao usecase.
2. `Controller` chama `ConsultarPreferenciaNotificacaoUseCase.executar(...)` passando o identificador do usuário
   logado. O usecase não conhece `HttpServletRequest` nem records.
3. Dentro do usecase: busca a `PreferenciaNotificacao` do usuário via `PreferenciaNotificacaoService`. Se não existe
   registro, devolve a entity com os padrões de conta nova (Push ligado, E-mail desligado, WhatsApp conforme opt-in
   do cadastro).
4. `Controller` usa `PreferenciaNotificacaoMapper.toResponse` para converter a entity em
   `PreferenciaNotificacaoResponse` e devolve `200`.

## `PUT /conta/preferencias-notificacao` (ETI-34 — atualizar preferências)

1. O controller desserializa o corpo em `PreferenciaNotificacaoRequest` (record). Bean Validation exige `push`,
   `email`, `sms` e `whatsapp` presentes e booleanos; falha → `400`. Sem login → `401`.
2. `Controller` chama `PreferenciaNotificacaoMapper.toEntity(request)` e então
   `AtualizarPreferenciaNotificacaoUseCase.executar(usuarioId, preferenciaNova, contextoDoAceite)`, onde
   `contextoDoAceite` carrega o que o aceite precisa vindo da camada HTTP (ex.: `ip`), sem expor
   `HttpServletRequest` ao usecase.
3. Dentro do usecase, em ordem:
   - Carrega a preferência atual do usuário (ou os padrões, se não existe).
   - Se `whatsapp` passou de `false` para `true`: registra o consentimento em `AceiteTermo` (`documento`, `versao`,
     `origem = configuracoes`, `ip`, `aceitoEm`).
   - Se `whatsapp` passou de `true` para `false`: apenas grava `whatsapp = false`.
   - Cada canal é aplicado de forma independente; salva via `PreferenciaNotificacaoService.salvar`.
4. `Controller` converte a entity salva com `PreferenciaNotificacaoMapper.toResponse` e devolve `200` com o estado
   salvo.
5. Exceção de negócio lançada no caminho é interceptada por `GlobalExceptionHandler` (`comum/excecao/`) e devolvida
   como `ErroResponse` — o controller nunca escreve `try/catch`.

## Classes envolvidas (previstas)

| Classe | Papel |
|---|---|
| `PreferenciaNotificacaoController` | Adapter HTTP — só tradução request/response |
| `ConsultarPreferenciaNotificacaoUseCase` | Regra de negócio: ler preferência ou padrões de conta nova |
| `AtualizarPreferenciaNotificacaoUseCase` | Regra de negócio: gravar canais; dispara o aceite na ligação do WhatsApp |
| `PreferenciaNotificacaoMapper` | `PreferenciaNotificacao` ⇄ `PreferenciaNotificacaoRequest`/`PreferenciaNotificacaoResponse` |
| `PreferenciaNotificacao` | Entity (`extends BaseEntity`): `usuario_id`, `push`, `email`, `sms`, `whatsapp` |
| `PreferenciaNotificacaoService` | Extends `comum/base/BaseService` — CRUD genérico + busca por usuário |
| `PreferenciaNotificacaoRepository` | Extends `comum/base/BaseRepository` — porta de persistência JPA |
| `PreferenciaNotificacaoRequest` / `PreferenciaNotificacaoResponse` | Records HTTP com os 4 booleanos |
| `AceiteTermo` | Registro do consentimento de WhatsApp (definição fora desta feature) |

Atualize esta página sempre que uma classe mudar de responsabilidade.
