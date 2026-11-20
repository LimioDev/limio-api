# Manual de funcionamento — notificacao

> Status: **implementado (ETI-34), exceto o `AceiteTermo`.** Código em `modulos/notificacao/`. Especificação em
> `docs/features/UC15-configurar-preferencias-de-notificacao.md`. O usuário logado chega ao controller como
> `UsuarioAutenticado` (`@AuthenticationPrincipal`), lido do token pelo `JwtAuthFilter`.

## `GET /conta/preferencias-notificacao` (ETI-34 — consultar preferências)

1. O controller recebe a requisição de um usuário autenticado. Sem login → `401`, antes de chegar ao usecase.
2. `Controller` chama `ConsultarPreferenciaNotificacaoUseCase.executar(...)` passando o identificador do usuário
   logado. O usecase não conhece `HttpServletRequest` nem records.
3. Dentro do usecase: busca a `PreferenciaNotificacao` do usuário via `PreferenciaNotificacaoService`. Se não existe
   registro, devolve a entity com os padrões de conta nova (Push ligado; E-mail e WhatsApp desligados), sem
   gravar nada no banco.
4. `Controller` usa `PreferenciaNotificacaoMapper.toResponse` para converter a entity em
   `PreferenciaNotificacaoResponse` e devolve `200`.

## `PUT /conta/preferencias-notificacao` (ETI-34 — atualizar preferências)

1. O controller desserializa o corpo em `PreferenciaNotificacaoRequest` (record). Bean Validation exige `push`,
   `email` e `whatsapp` presentes e booleanos; falha → `400`. Sem login → `401`.
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
| `PreferenciaNotificacao` | Entity (`extends BaseEntity`): `usuario_id`, `push`, `email`, `whatsapp` |
| `PreferenciaNotificacaoService` | Extends `comum/base/BaseService` — CRUD genérico + busca por usuário + `apagarDoUsuario` |
| `RemoverPreferenciaDaContaEncerradaUseCase` | Reação a `comum/eventos/ContaEncerradaEvent`: apaga a preferência da conta encerrada depois do commit de `auth` |
| `PreferenciaNotificacaoRepository` | Extends `comum/base/BaseRepository` — porta de persistência JPA |
| `PreferenciaNotificacaoRequest` / `PreferenciaNotificacaoResponse` | Records HTTP com os 3 booleanos (SMS saiu no ETI-134) |
| `AceiteTermo` | Registro do consentimento de WhatsApp — **ainda não existe** (definição fora desta feature) |

## Notificações: listar e marcar como lidas (ETI-110)

1. `GET /notificacoes?pagina=N` → `NotificacaoController.listar` → `ListarNotificacoesUseCase` (página de 20 da mais
   nova pra mais antiga + `contarNaoLidas`) → `NotificacaoMapper.toResponse` → `NotificacoesResponse`.
2. `POST /notificacoes/{id}/lida` → `MarcarNotificacaoComoLidaUseCase`: busca por id **e** dono (404 se não achar),
   `Notificacao.marcarComoLida(agora)` (idempotente), devolve o novo total de não lidas.
3. `POST /notificacoes/lidas` → `MarcarTodasNotificacoesComoLidasUseCase`: um `UPDATE` em lote nas não lidas do
   usuário, devolve o novo total (0).

| Classe | Papel |
|---|---|
| `Notificacao` | Entity raiz (`extends BaseEntity`): `usuario_id`, `anuncio_id`, `tipo`, `titulo`, `corpo`, `enviada_em`, `lida_em`; só `lidaEm` muda |
| `enums/TipoNotificacao` | Do que trata a notificação; um valor por evento de domínio que pede aviso |
| `NotificacaoRepository` / `NotificacaoService` | Lista paginada do dono, total de não lidas, busca por id + dono, marcar todas em lote, apagar do usuário |
| `MinhasNotificacoes` | Tipo de domínio: página + total de não lidas |
| `ListarNotificacoesUseCase`, `MarcarNotificacaoComoLidaUseCase`, `MarcarTodasNotificacoesComoLidasUseCase` | Regras do ETI-110 |
| `RemoverNotificacoesDaContaEncerradaUseCase` | Reação a `ContaEncerradaEvent`: apaga as notificações da conta encerrada |
| `NotificacaoController`, `NotificacaoMapper` | HTTP; `NotificacoesFiltro`, `NotificacaoResponse`, `NotificacoesResponse`, `NaoLidasResponse` |
| `excecao/NotificacaoNaoEncontradaException` | 404 `NOTIFICACAO_NAO_ENCONTRADA` (inexistente ou de outra pessoa) |

Atualize esta página sempre que uma classe mudar de responsabilidade.

## Envio dos avisos (ETI-112)

1. Evento de aviso de outro módulo (`CandidaturaCriadaEvent`, `PrestadorEscolhidoEvent`, ...) → `RegistrarAvisosUseCase`
   (`AFTER_COMMIT`, `REQUIRES_NEW`): `AvisoHelper` monta o `Aviso`, grava a `Notificacao` e publica
   `eventos/NotificacaoRegistradaEvent`.
2. `NotificacaoRegistradaEvent` → `EntregarNotificacaoUseCase` (`@Async`, `AFTER_COMMIT`): preferência do usuário
   (ou `PreferenciaNotificacao.padraoContaNova`), uma `CanalDeEnvioService` por canal ligado, `EnvioNotificacao` por
   tentativa.
3. `POST /notificacoes/aparelhos` e `/remover` → `AparelhoPushController` → `RegistrarAparelhoPushUseCase` (upsert por
   token) / `RemoverAparelhoPushUseCase` (só o token do próprio usuário).

| Classe | Papel |
|---|---|
| `Aviso` | Tipo de domínio: destinatário, tipo, título, corpo e anúncio de um aviso |
| `actions/helper/AvisoHelper` | Textos dos avisos por evento (puro) |
| `RegistrarAvisosUseCase` | Um ouvinte por evento de aviso; grava a notificação |
| `EntregarNotificacaoUseCase` | Itera os canais ligados e registra cada tentativa |
| `actions/service/CanalDeEnvioService` | Estratégia de um canal; canal novo é uma implementação nova |
| `actions/service/CanalPushService`, `CanalEmailService`, `CanalWhatsappService` | Push, e-mail e WhatsApp (este nunca envia sem consentimento) |
| `actions/service/PushExpoService` | Cliente do Expo Push Service; resultado por lote, com os tokens que não existem mais |
| `actions/service/EmailAvisoService` | Resend pros avisos (instância própria, falha sobe como exceção) |
| `aparelhopush/AparelhoPush` (+ repository/service) | Token de push do aparelho logado, único por aparelho |
| `envionotificacao/EnvioNotificacao` (+ repository/service) | Canal, status e motivo de cada tentativa de envio |
| `enums/CanalNotificacao`, `StatusEnvio`, `Plataforma` | Valores fechados do envio |
| `AparelhoPushController`, `AparelhoPushRequest`, `RemoverAparelhoPushRequest` | HTTP do aparelho |
| `comum/config/AssincronoConfig` | `@EnableAsync` e o executor `envioDeAvisos` (síncrono nos testes; fila cheia roda em quem publicou) |
