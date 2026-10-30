# ETI-63 — Cancelar anúncio antes da escolha

## Status

Implementado.

## Link do card

[ETI-63](https://kaueamaral96.atlassian.net/jira/software/projects/ETI/boards/2?selectedIssue=ETI-63) —
[anuncio] Cancelar anúncio antes da escolha. Origem: Backlog US32 (sem card original no Jira). Card par:
[ETI-64](https://kaueamaral96.atlassian.net/jira/software/projects/ETI/boards/2?selectedIssue=ETI-64)
([FRONT-END] Confirmação "Cancelar este anúncio?").

## User Story

Como um Empregador, eu quero que a API cancele um anúncio que ainda não tem escolhido, para que ele
saia do mural sem consumir a cota de ninguém.

## Escopo implementado

`POST /anuncios/{anuncioId}/cancelar`: marca o anúncio `ENCERRADO`, encerra as candidaturas ainda
abertas (`ENVIADA`), publica `AnuncioCanceladoEvent`. Rota "a definir" no card — escolhida por analogia
com `POST /auth/encerrar-conta` (ação verbo, sem corpo, `ResponseEntity<Void>` 200), não um padrão REST
de recurso, porque "cancelar" não cria nem substitui nada.

**Fora**: UI (card par `[FRONT-END]`), reação do `chat` (`ETI-101`/`102`, só o evento é publicado aqui),
cancelamento do serviço combinado — anúncio com candidatura `ESCOLHIDA` (`ETI-94`/`95`, caminho
diferente, recusado aqui).

## Decisão pendente do card — resolvida

O card deixava em aberto: criar um status `CANCELADO` em `StatusAnuncio`, ou gravar o motivo do
encerramento. Decisão: **gravar o motivo**, sem status novo.

Razão: `Aplicacao` já resolve exatamente esse problema desde o TICKET-0049 —
`motivoEncerramento` (texto livre) ao lado de um único status `ENCERRADA` genérico, em vez de um valor
de enum por motivo (`"Cancelado por saída de conta"` já é gravado assim). `Anuncio` não tinha o campo
equivalente; esta decisão adiciona `motivo_encerramento` (migration V23) e aplica o mesmo padrão.

Isso também resolve a frase do card "a mesma decisão vale para Expirado (`ETI-66`) e os cancelamentos
com/sem estorno de cota (`ETI-94`/`95`)": cada card futuro só precisa de um texto de motivo novo, não de
um valor novo em `StatusAnuncio` — `Anuncio.encerrar(Instant, String)` (privado, chamado pelos métodos
públicos nomeados por cenário) já está pronto pra isso. `encerrarPorExclusaoDeConta` (TICKET-0049) foi
retroativamente ajustado pra também gravar motivo (`"Cancelado por saída de conta"`), fechando a
inconsistência de ter `ENCERRADO` com motivo às vezes sim, às vezes não — sem mudar a assinatura nem
quebrar nenhum teste existente (nenhum testava o motivo de `Anuncio`, só o de `Aplicacao`).

## Regra de negócio

| Critério do card | Implementação |
|---|---|
| Só em `PUBLICADO` sem candidatura `ESCOLHIDA` | `AnuncioEstadoInvalidoException` (409) via `Anuncio.permiteAlteracao(AplicacaoService.existeEscolhida)` — mesma regra de domínio do anexar fotos (ETI-59), com `switch` sem `default`. |
| Muda `ENVIADA` pra `ENCERRADA` | `AplicacaoService.buscarAbertasDoAnuncio` + `Aplicacao.encerrar(agora, motivo)`. Só `ENVIADA` pode estar "aberta" aqui: `ESCOLHIDA`/`AGUARDANDO_VALIDACAO` já teriam barrado a checagem acima — não precisa filtrar por status de novo. |
| `RECUSADA` continua `RECUSADA` | Automático: `buscarAbertasDoAnuncio` já exclui `RECUSADA` (não está em `STATUS_ABERTOS`). |
| Não mexe na cota | Nenhuma chamada a serviço de cota — ausência de código é a regra, mesmo idioma do TICKET-0049. |
| Só o dono cancela; de outra pessoa é 404 | `anuncioService.buscarPorId(id).filter(contratanteId::equals)` — anúncio inexistente e anúncio de outro contratante caem no mesmo `orElseThrow`, mesma resposta pros dois casos (não revela se o id existe). |
| Publica `AnuncioCancelado` | `AnuncioCanceladoEvent(anuncioId, ocorridoEm)` — `comum/eventos/`. Só o id do anúncio: `chat` (quando existir) já guarda `anuncioId` cru em cada chat, recebido no `CandidaturaCriadaEvent` da própria candidatura (ETI-75), não precisa da lista de candidaturas aqui. |

## Contrato da API

| Operação | Rota | Entrada | Saída |
|---|---|---|---|
| Cancelar anúncio | `POST /anuncios/{anuncioId}/cancelar` | `anuncioId` no path | 200, sem corpo |

`CodigoErro` novos: `ANUNCIO_NAO_ENCONTRADO` (404, novo). `ANUNCIO_ESTADO_INVALIDO` (409) já existe —
criado no ETI-75 (`PR #19`, ainda não mergeado quando este card começou); a mensagem padrão foi
generalizada de "não está mais aceitando candidaturas" pra "não pode mais ser alterado" pra servir os
dois usecases. Os dois cards tocam o mesmo `CodigoErro.java`: conflito trivial de merge esperado entre
as branches, resolve pra manter os dois valores.

## Testes

`spec/CancelarAnuncioUseCaseTest`: cada checagem isolada — sucesso com candidatos, sucesso sem
candidatos, candidatura escolhida bloqueia, anúncio não publicado bloqueia, anúncio de outro contratante
e anúncio inexistente (ambos 404).

`story/CancelarAnuncioStoryTest`: ponta a ponta — os mesmos cenários via HTTP, com verificação de que
`RECUSADA` não é tocada, do `motivoEncerramento` gravado nos dois lados (anúncio e candidaturas), do
evento publicado (`@RecordApplicationEvents`) e de 401 sem token.
