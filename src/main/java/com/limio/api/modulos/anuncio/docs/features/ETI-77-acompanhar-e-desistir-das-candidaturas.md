# ETI-77 — Acompanhar minhas candidaturas e desistir de uma pendente

## Status

Implementado.

## Link do card

[ETI-77](https://kaueamaral96.atlassian.net/jira/software/projects/ETI/boards/2?selectedIssue=ETI-77) —
[prestador] Desistir da candidatura (US22) + Acompanhar minhas candidaturas (US23), sem card original no
Jira. Ficaram juntas porque "Cancelar" (desistir) é um botão da própria lista. Card par: `[FRONT-END]` Tela
"Aplicações" (pendentes, combinadas e histórico), com desistir.

## User Story

Como um Prestador, eu quero que a API mostre minhas candidaturas por situação e me deixe desistir de uma
pendente, para que eu acompanhe meus serviços e libere minha agenda.

## Escopo implementado

- `GET /candidaturas/minhas?aba={PENDENTES|COMBINADAS|HISTORICO}&pagina={n}` — lista paginada (20 por
  página, `pagina` começa em 0 e é opcional), mais recentes primeiro (`dataAplicacao desc, id desc`).
- `POST /candidaturas/{candidaturaId}/desistir` — `ENVIADA` → `ENCERRADA`, 200 sem corpo (mesmo formato do
  `POST /anuncios/{id}/cancelar`, ETI-63).

**Fora**: UI (card par), chat somente leitura (só o evento é publicado aqui), cancelamento depois de
escolhido (ETI-96/ETI-97).

## Regra de negócio

| Critério do card | Implementação |
|---|---|
| Pendentes = `ENVIADA` | `AbaCandidatura.de(StatusAplicacao)` — `switch` sem `default`, fonte única da classificação. |
| Combinadas = `ESCOLHIDA` com serviço em andamento | `ESCOLHIDA` + `AGUARDANDO_VALIDACAO` (escolhido, serviço ainda não validado — mesmo critério de `StatusAplicacao.isEscolhida()`). |
| Histórico = serviços já encerrados | `RECUSADA`, `CONCLUIDA`, `ENCERRADA` (inclui desistência). |
| Item: título, data/hora, nome e iniciais do Empregador, selo, id do anúncio | `MinhaCandidaturaResponse(id, anuncioId, titulo, dataHoraPretendida, nomeEmpregador, iniciaisEmpregador, status)`. O selo é o `status`; o texto ("Aguardando escolha") é do front. |
| Só candidaturas do próprio Prestador | Filtro por `prestadorId` = id do token. Desistir de candidatura alheia responde 404 igual a inexistente. |
| Privacidade (UC "Consultar histórico") | Nenhum item traz endereço completo nem contatos, em nenhuma aba. Nome do Empregador sai abreviado ("Maria S."), como o nome do Prestador no painel do Empregador. |
| Desistir: `ENVIADA` → `ENCERRADA` com `encerradaEm` e motivo | `Aplicacao.desistir(agora)` → `encerrar(agora, "Desistência do Prestador")`. |
| Não mexe na cota | Nada a fazer: a cota conta escolhas (`escolhidaEm`), não candidaturas. |
| Some da lista do Empregador | Automático: painel e tela 16 só contam/listam `ENVIADA`. Coberto no story pelo painel do Empregador. |
| Evento `CandidaturaEncerrada` | `comum/eventos/CandidaturaEncerradaEvent(candidaturaId, anuncioId, prestadorId, ocorridoEm)`. Consumidor (`chat`) ainda não existe. |
| Desistir já escolhido | 409 `USE_CANCELAMENTO_SERVICO` pra `ESCOLHIDA`/`AGUARDANDO_VALIDACAO`. |
| Desistir de candidatura já finalizada | 409 `CANDIDATURA_ESTADO_INVALIDO` pra `RECUSADA`/`CONCLUIDA`/`ENCERRADA`. |

## Decisões técnicas

- **Rotas "a definir"**: recurso `/candidaturas` do ponto de vista do Prestador (`/minhas` + ação
  `/desistir`), separado de `/anuncios/{id}/candidaturas` (ETI-75), que é a candidatura vista a partir do
  anúncio.
- **Valor da aba em maiúsculo** (`PENDENTES`), igual a todo enum do contrato (`status`, `papel`). Valor
  desconhecido ou ausente → 400 `VALIDACAO_FALHOU`.
- **Concorrência desistir × escolher**: a desistência lê a candidatura com `PESSIMISTIC_WRITE`
  (`AplicacaoService.buscarParaAtualizar`). O card de escolha precisa usar a mesma busca para a proteção valer
  dos dois lados.
- **N+1**: anúncios da página numa query (`AnuncioService.buscarPorIds`); nome do Empregador uma consulta
  por Empregador distinto da página (a porta `ConsultaDadosUsuario` não tem busca em lote).
- **Conta do Empregador encerrada**: `nomeEmpregador`/`iniciaisEmpregador` saem `null`; o front mostra um
  placeholder.
- **`PageResponse<T>`** criado em `comum/records/` (previsto no ADR-0001), pra não expor o JSON do `Page`
  do Spring Data.

## Contrato da API

| Operação | Rota | Entrada | Saída |
|---|---|---|---|
| Listar minhas candidaturas | `GET /candidaturas/minhas` | `aba` (obrigatória), `pagina` (opcional, ≥ 0) | 200 `PageResponse<MinhaCandidaturaResponse>` (`itens`, `pagina`, `tamanho`, `totalItens`, `totalPaginas`) |
| Desistir da candidatura | `POST /candidaturas/{candidaturaId}/desistir` | id no path | 200 sem corpo |

`CodigoErro` novos: `CANDIDATURA_NAO_ENCONTRADA` (404), `CANDIDATURA_ESTADO_INVALIDO` (409),
`USE_CANCELAMENTO_SERVICO` (409).

## Testes

- `spec/ListarMinhasCandidaturasUseCaseTest`: montagem do item, um lookup por Empregador, conta encerrada,
  anúncio inexistente, página vazia.
- `spec/DesistirCandidaturaUseCaseTest`: sucesso + evento, inexistente, de outro Prestador, escolhida (2
  status), já finalizada (3 status).
- `spec/AbaCandidaturaTest`: cada status cai em exatamente uma aba.
- `story/MinhasCandidaturasStoryTest`: cada aba, só as próprias, privacidade (sem endereço, e-mail,
  telefone nem nome completo), paginação, aba ausente/inválida, página negativa, 401; desistir de `ENVIADA`
  (banco, evento, painel do Empregador, abas), de `ESCOLHIDA`, de já recusada, de outra pessoa, inexistente,
  401.
