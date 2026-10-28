# ETI-75 — Candidatar-se a um anúncio

## Status

Implementado.

## Link do card

[ETI-75](https://kaueamaral96.atlassian.net/jira/software/projects/ETI/boards/2?selectedIssue=ETI-75) —
[prestador] Candidatar-me a um anúncio. Origem: Backlog US21 (sem card original no Jira). Tag
`[anuncio]` porque a candidatura (`Aplicacao`) muda junto com o anúncio na escolha. Card par:
[ETI-76](https://kaueamaral96.atlassian.net/jira/software/projects/ETI/boards/2?selectedIssue=ETI-76)
([FRONT-END] "Candidatar-se a este serviço?").

## User Story

Como um Prestador, eu quero que a API registre minha candidatura e abra o chat com o Empregador, para
que ele me considere e a gente converse.

## Escopo implementado

`POST /anuncios/{anuncioId}/candidaturas`: cria `Aplicacao` em `ENVIADA` com `dataAplicacao`, publica
`CandidaturaCriadaEvent`. Não mexe em cota (só a escolha consome, UC33 — card futuro). Rota "a definir"
no card; escolhida por convenção REST (`anuncioId` no path, sem corpo — o único dado de entrada já é o
anúncio da própria URL, e o prestador vem do token).

**Fora**: UI (card par `[FRONT-END]`), envio efetivo dos avisos (card `ETI-112`, só o evento é publicado
aqui), chat em si (`ETI-101`/`ETI-102`, só o evento é publicado aqui), consultar/zerar cota do mês
(`ETI-79`/`ETI-80` — aqui só o *bloqueio* por cota esgotada).

## Regra de negócio

| Critério do card | Implementação |
|---|---|
| Papel ativo `PRESTADOR` | `PapelInvalidoException` (403 `PAPEL_INVALIDO`) se o papel do token não for `PRESTADOR`. |
| Telefone e e-mail confirmados | `ConsultaDadosUsuario` (porta, `comum/portas/`) — `ContatosNaoConfirmadosException` (403 `CONTATOS_NAO_CONFIRMADOS`) se `emailConfirmadoEm`/`telefoneConfirmadoEm` nulos. |
| Anúncio em `PUBLICADO` sem candidatura `ESCOLHIDA` | `AnuncioEstadoInvalidoException` (409 `ANUNCIO_ESTADO_INVALIDO`) se o status não for `PUBLICADO` ou já existir candidatura `ESCOLHIDA`/`AGUARDANDO_VALIDACAO` (`AplicacaoService.temCandidaturaEscolhida`). |
| Anúncio do próprio usuário | `AnuncioProprioException` (403 `ANUNCIO_PROPRIO`) se `anuncio.contratanteId == prestadorId`. |
| Candidatura `ENVIADA`/`ESCOLHIDA` já existente do mesmo par | `CandidaturaDuplicadaException` (409 `CANDIDATURA_DUPLICADA`), checada no usecase (`AplicacaoService.existeAbertaDoPar`) **e** garantida pelo índice único `uk_aplicacao_par_ativo` sob concorrência (ver "Clique duplo"). |
| Cota esgotada (FREE, 4 escolhas no mês) | `CotaEsgotadaException` (403 `COTA_ESGOTADA`) se `AplicacaoService.contarEscolhidasNoPeriodo` (mês civil UTC, por `escolhidaEm`) ≥ `anuncio.candidatura.max-escolhas-gratuitas` (default `4`). Ver "GOLD ainda não existe" abaixo. |
| Pode candidatar de novo após `RECUSADA`/`ENCERRADA` | Essas duas não entram no índice parcial nem em `STATUS_ABERTOS` — uma nova linha pro mesmo par é permitida. |
| Clique duplo cria uma candidatura só | Índice único parcial `uk_aplicacao_par_ativo (anuncio_id, prestador_id) WHERE status IN ('ENVIADA','ESCOLHIDA','AGUARDANDO_VALIDACAO')` (migration V20). `AplicacaoService.salvarCandidatura` usa `saveAndFlush` + catch de `DataIntegrityViolationException` traduzido pra `CandidaturaDuplicadaException` — mesmo idioma de `EnderecoSalvoService.salvar` (TICKET-0050). Testado com duas requisições HTTP paralelas de verdade (`CandidatarSeStoryTest`), não só a checagem do usecase. |

### GOLD ainda não existe (decisão de implementação)

O card pede "Prestador FREE (sem GOLD ativo)", mas `modulos.financeiro` (plano/assinatura GOLD) é só o
esqueleto do `package-info.java` (TICKET-0044) — nenhuma entidade existe ainda (ETI-81 em diante, "À
FAZER"). Decisão: até o financeiro existir, **todo Prestador é tratado como FREE**, então o limite de
cota sempre vale. Quando `ETI-81`+ entrar, este usecase ganha a consulta de assinatura ativa (porta nova
em `comum/portas/`, mesmo padrão de `ConsultaDadosUsuario`) antes de checar a cota.

Por isso o cenário "GOLD com 4 escolhas" do card (deveria passar, sem bloqueio) não tem teste — não há
como ainda ser GOLD. `CandidatarSeUseCaseTest`/`CandidatarSeStoryTest` cobrem o inverso: FREE (todo
mundo, hoje) com 3 escolhas passa, com 4 é barrado.

## Evento: `CandidaturaCriadaEvent`

`comum/eventos/CandidaturaCriadaEvent(candidaturaId, anuncioId, anuncioTitulo, contratanteId,
prestadorId, ocorridoEm)` — cross-module (ADR-0003), dois consumidores previstos, nenhum em código
ainda: `chat` cria o Chat 1:1 (`ETI-101`/`ETI-102`) e `notificacao` avisa o Empregador (`ETI-112`).
Publicar antes dos consumidores existirem não quebra nada (evento sem listener).

`anuncioTitulo` vai solto no evento porque quem escuta não pode importar `modulos.anuncio` pra buscar o
anúncio (ADR-0001). O nome do Prestador fica de fora de propósito — cada consumidor resolve pela sua
própria porta/fonte.

## Contrato da API

| Operação | Rota | Entrada | Saída |
|---|---|---|---|
| Candidatar-se | `POST /anuncios/{anuncioId}/candidaturas` | `anuncioId` no path | 201, `CandidaturaResponse(id, status=ENVIADA)` |

`CodigoErro` novos: `PAPEL_INVALIDO` (403), `CONTATOS_NAO_CONFIRMADOS` (403), `ANUNCIO_PROPRIO` (403),
`ANUNCIO_ESTADO_INVALIDO` (409), `CANDIDATURA_DUPLICADA` (409), `COTA_ESGOTADA` (403). Anúncio
inexistente reaproveita o genérico `ENTIDADE_NAO_ENCONTRADA` (404) de `BaseService.buscarPorIdOuFalhar`
— o card não pedia um código específico pra esse caso.

## Testes

`spec/CandidatarSeUseCaseTest`: todas as checagens isoladas (mocks). `story/CandidatarSeStoryTest`:
ponta a ponta — sucesso + evento publicado (`@RecordApplicationEvents`), papel inválido, contatos não
confirmados, anúncio inexistente, anúncio próprio, anúncio concluído, anúncio com escolhido, duplicada,
recandidatura após recusa, cota esgotada, sem token, e duas requisições HTTP paralelas reais pro mesmo
par (clique duplo).
