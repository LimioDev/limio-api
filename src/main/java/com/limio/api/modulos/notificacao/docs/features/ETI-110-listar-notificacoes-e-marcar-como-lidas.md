# ETI-110 — Listar minhas notificações e marcar como lidas

## Status

Implementado. Ninguém grava notificação ainda: o envio de avisos é o ETI-112.

## Link do card

[ETI-110](https://kaueamaral96.atlassian.net/browse/ETI-110) — [notificacao] [BACK-END] Listar minhas notificações e
marcar como lidas. Origem: Backlog US55 (sem card original no Jira). Card par: [notificacao] [FRONT-END] Tela
"Notificações".

## User Story

Como usuário, eu quero que a API liste minhas notificações e saiba quais já li, para que eu não perca nenhuma
atualização.

## Contrato da API

As três rotas exigem login. O dono vem do token, e o papel ativo não filtra: Empregador e Prestador veem as próprias.

| Operação | Rota | Entrada | Saída |
|---|---|---|---|
| Listar notificações | `GET /notificacoes` | `pagina` (query, ≥ 0, padrão 0) | `200 { notificacoes: PageResponse<{ id, tipo, titulo, corpo, enviadaEm, lidaEm, anuncioId }>, naoLidas }` |
| Marcar como lida | `POST /notificacoes/{notificacaoId}/lida` | id na rota | `200 { naoLidas }` |
| Marcar todas como lidas | `POST /notificacoes/lidas` | — | `200 { naoLidas }` |

- Página de 20, da mais nova pra mais antiga (`enviadaEm` desc, depois id).
- `naoLidas` é o total do usuário (não só da página): é o número do sino da tela 13.
- `lidaEm` nulo = não lida. `anuncioId` nulo = sem anúncio ligado.
- **Erros:** 404 `NOTIFICACAO_NAO_ENCONTRADA` (id inexistente ou de outra pessoa, mesma resposta); 400
  `VALIDACAO_FALHOU` (página negativa); 401 `NAO_AUTENTICADO`.

## Regra de negócio

| Critério do card | Implementação |
|---|---|
| Lista da mais nova pra mais antiga, com tipo, título, corpo, enviadaEm, lidaEm e anúncio | `ListarNotificacoesUseCase` + `NotificacaoRepository.findByUsuarioIdOrderByEnviadaEmDescIdDesc`, índice `idx_notificacao_usuario_enviada`. |
| Paginada (`PageResponse`) | `PageResponse` dentro de `NotificacoesResponse`, junto com `naoLidas`. |
| Total de não lidas | `countByUsuarioIdAndLidaEmIsNull`, índice parcial `idx_notificacao_usuario_nao_lida`. |
| Marcar uma ao abrir | `MarcarNotificacaoComoLidaUseCase` → `Notificacao.marcarComoLida`. Idempotente: abrir de novo mantém a data da primeira leitura. |
| Marcar todas | `MarcarTodasNotificacoesComoLidasUseCase`: um `UPDATE` só, nas não lidas do usuário. As já lidas mantêm a data. Nada pra marcar não é erro. |
| Só as do próprio usuário | A busca já filtra por `id` **e** `usuarioId` (`findByIdAndUsuarioId`), então a de outra pessoa nunca é carregada: 404 igual a id inexistente, como em candidatura (ETI-77). |

## Decisões de implementação

- **Tabela nova `notificacao` (V36).** O DER não está no repo; as colunas seguem o card (`tipo`, `titulo`, `corpo`,
  `enviada_em`, `lida_em`, anúncio) e o `package-info` do módulo (`usuario_id` e `anuncio_id` crus, sem FK, ADR-0001
  §1.6). V36 porque a #38 (ETI-94, aberta) usa V34 e V35.
- **Uma linha por notificação, não por canal.** A tela 43 lista o que aconteceu, uma vez. Se o ETI-112 precisar
  registrar o envio por canal (push, e-mail, WhatsApp), isso fica numa tabela própria, sem mudar esta lista.
- **`TipoNotificacao` = um valor por evento de domínio que já existe** (`NovoServicoNaAreaEvent` da #41,
  `CandidaturaCriadaEvent`, `PrestadorEscolhidoEvent` etc.). O ETI-112 grava com o tipo do evento e pode acrescentar
  valores: a coluna não tem `CHECK`, então valor novo não exige migration.
- **As rotas de marcar devolvem `naoLidas`.** O card pede "confirmação"; o total novo deixa o app atualizar o sino sem
  listar de novo.
- **`POST /{id}/lida` e `POST /lidas`**, no estilo de ação que o projeto já usa (`POST /candidaturas/{id}/desistir`).
- **`Clock` injetado** (`RelogioConfig`) para o "agora" da leitura.
- **Conta encerrada apaga as notificações** (`RemoverNotificacoesDaContaEncerradaUseCase`, `AFTER_COMMIT`), igual à
  preferência (UC15, LGPD): sem FK, ficariam órfãs. Não estava no card; entra por coerência com o resto do módulo.

## Fora do escopo

- Gravar notificação e enviar por canal: ETI-112.
- Tela e navegação ao tocar (tela 30 para `anuncioId`): card par [FRONT-END].
- Push em tempo real do sino: o app consulta `GET /notificacoes` (ou usa o `naoLidas` devolvido pelas marcações).

## Testes

- `spec/NotificacaoUseCasesTest` (7): marcar na entidade (e idempotência), listar com página de 20 e total, marcar
  uma (e 404 sem salvar), marcar todas com o "agora" do relógio, apagar na conta encerrada.
- `story/NotificacoesStoryTest` (12), pela API real: listar só as minhas em ordem com o total de não lidas; segunda
  página; lista vazia; página negativa (400); sem token (401); marcar uma; marcar de novo sem mudar a data; marcar a
  de outra pessoa (404, sem mexer nela); id inexistente; marcar todas (só as minhas, já lidas mantêm a data); nada
  pra marcar; encerrar a conta apaga só as dela.
