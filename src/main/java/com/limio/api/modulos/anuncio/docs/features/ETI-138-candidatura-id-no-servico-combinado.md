# ETI-138 — Devolver o id da candidatura no serviço combinado

## Status

Implementado: `candidaturaId` (obrigatório) e a reputação do Prestador em `outraParte` (opcional do card).
`canceladoEm` fica pra depois do merge da #38 (ver "Fora desta entrega").

## Link do card

[ETI-138](https://kaueamaral96.atlassian.net/browse/ETI-138) — [BACK-END] Devolver o id da candidatura no serviço
combinado. Origem: ETI-86 (consultar o serviço combinado). Card par: ETI-87 ([FRONT-END]).

## User Story

Como Empregador ou Prestador escolhido, eu quero tocar em "Conversar com (nome)" no card do serviço, para abrir a
conversa daquele serviço.

## Contrato da API

| Operação | Rota | Mudança na saída |
|---|---|---|
| Consultar serviço combinado | `GET /anuncios/{anuncioId}/servico` | `candidaturaId`; `outraParte.qtdAvaliacoes`, `outraParte.mediaAvaliacoes` |

- `candidaturaId` é a candidatura escolhida, o mesmo pros dois lados. É o que `GET /candidaturas/{candidaturaId}/chat`
  aceita: o "Conversar com (nome)" das telas 25, 36 e 37 abre o chat (tela 21). O `chatId` continua na resposta.
- `outraParte.qtdAvaliacoes`/`mediaAvaliacoes`: mesmos nomes do `CandidatoResponse`. Preenchidos quando quem consulta
  é o Empregador (vendo o Prestador); `null` pro Prestador, como o `gold`. Sem avaliação: `qtdAvaliacoes = 0` e
  `mediaAvaliacoes = null`. Conta encerrada: tudo `null`, como já era.
- Nenhum `CodigoErro` novo. Sem migration.

## Regra de negócio

| Critério do card | Implementação |
|---|---|
| `candidaturaId` = candidatura escolhida | `ConsultarServicoUseCase` já carregava a escolhida (de onde vem o `escolhidaEm`); o valor é `escolhida.getId()`, levado por `ServicoCombinado.candidaturaId`. |
| Mesmo id pros dois lados | Não depende de quem consulta. |
| Reputação do Prestador (opcional) | `ConsultaAvaliacoes.resumir` (porta do `moderacao`, ETI-107), só no ramo do Empregador. Fecha o ponto em aberto do ETI-86. |

## Fora desta entrega

- **`canceladoEm`** (opcional do card). Hoje um anúncio `ENCERRADO` pode vir de cancelamento do serviço, encerramento
  de conta ou expiração, e só a #38 (ETI-94, aberta) grava o `tipoCancelamento` que separa o cancelamento. Devolver
  `encerradoEm` agora daria data de cancelamento pra anúncio que não foi cancelado. Entra num ajuste curto depois do
  merge da #38.

## Testes

- `spec/ConsultarServicoUseCaseTest`: `candidaturaId` pros dois lados; reputação zerada sem avaliação, preenchida com
  avaliação (GOLD junto) e ausente pro Prestador, sem nem consultar a porta.
- `story/ConsultarServicoStoryTest`: Empregador e Prestador recebem o `candidaturaId` da escolhida; reputação do
  Prestador pro Empregador e `null` pro Prestador; com o id devolvido, `GET /candidaturas/{id}/chat` responde 200
  pelos dois lados.
