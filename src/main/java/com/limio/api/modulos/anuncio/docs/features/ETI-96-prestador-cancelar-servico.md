# ETI-96 — Prestador cancelar depois de escolhido

## Status

Implementado.

## Link do card

ETI-96 — [anuncio] [BACK-END] Prestador cancelar depois de escolhido. Origem: RF48, RB18, RF69, UC43 (sem link do
Jira neste repositório).

## User Story

Como Prestador escolhido, eu quero que a API cancele um serviço que não vou conseguir fazer, para que o Empregador
saiba a tempo.

## Escopo implementado

`POST /anuncios/{anuncioId}/servico/cancelar-prestador`, sem corpo. O anúncio vai para `ENCERRADO` (com `encerradoEm`
e `motivoEncerramento`), a candidatura escolhida vai para `ENCERRADA`, a proposta de reagendamento pendente é
encerrada sem efeito, o chat vira somente leitura e `ServicoCanceladoPeloPrestadorEvent` é publicado.

Sem migration: `tipo_cancelamento` fica nulo e `motivo_encerramento` é texto livre.

**Fora**: aviso ao Empregador (US56/ETI-112, só o evento é publicado), criar e aceitar reagendamento (ETI-99/100),
auditoria do cancelamento, UI.

## Decisões

- **Estado.** "Combinado" é `PUBLICADO` com candidatura `ESCOLHIDA`; "em andamento" é anúncio `EM_ANDAMENTO` com a
  candidatura ainda `ESCOLHIDA` (`StatusAplicacao` não tem `EM_ANDAMENTO`). A pré-condição é `Anuncio.servicoEmAberto()`.
  "Cancelado" do UC43 é o `ENCERRADO` do card: terminal, não volta ao mural e não aceita candidatura (o
  `CandidatarSe` exige `PUBLICADO`).
- **`AGUARDANDO_VALIDACAO` é recusado** com 409 `SERVICO_ESTADO_INVALIDO`; seguem validação, silêncio ou denúncia. O
  Empregador recebe `USE_DENUNCIA` nesse estado (ETI-94); o card pede o código genérico para o Prestador.
- **Autorização pelo vínculo, não pelo papel.** O usuário precisa ser o `prestadorId` da candidatura escolhida,
  achada por `escolhidaEm` (permanente, `AplicacaoService.buscarEscolhidaDoAnuncio`). Não escolhido, serviço sem
  escolhido ou inexistente: 404 `SERVICO_NAO_ENCONTRADO`. A autorização vem antes do estado, para um terceiro não
  descobrir o estado do serviço. Por usar `escolhidaEm`, o escolhido que repete o pedido recebe 409, não 404.
- **Sem cota (RB18).** O caso de uso não conhece `ConsumoCotaPrestador`: a escolha consumida segue consumida e
  continua contando na barreira de candidatura (`cota_estornada_em` fica nulo). Sem cancelamento tardio (RB36): vale
  a qualquer momento, sem prazo de 24h e sem `tipoCancelamento`.
- **Reagendamento pendente** perde o efeito (`ReagendamentoService.encerrarPendentesSemEfeito`), na mesma transação.
  É o ponto único que o ETI-99 (#49) também cria, com a mesma assinatura: hoje grava `CANCELADO` pelo
  `cancelarSemEfeito`; com o #49, `SEM_EFEITO`. Quem mergear por último resolve o conflito no `ReagendamentoService`
  ficando com a versão do #49.
- **Ordem das travas:** advisory `ANUNCIO` → leitura do anúncio e do vínculo → `FOR UPDATE` na candidatura
  escolhida. Não há `COTA_PRESTADOR`. Check-out, validação, iniciar e o cancelamento do Empregador também travam
  `ANUNCIO` primeiro, então o perdedor de uma corrida relê o estado e recebe 409.
- **Evento próprio**, `ServicoCanceladoPeloPrestadorEvent` (`comum/eventos`), e não o `ServicoCanceladoEvent`: aquele
  é do Empregador (tipo, estorno e data vigente, e a premissa "quem cancela é o contratante"). O nome `ServicoCancelado`
  do card já era ocupado. Para o US56, `contratanteId` é o destinatário e o título vai solto.
- **Chat.** `DeixarChatSomenteLeituraUseCase.aoCancelarServicoPeloPrestador` (`AFTER_COMMIT` + `REQUIRES_NEW`), mesmo
  motivo `SERVICO_CANCELADO` do cancelamento do Empregador. Falha do listener é só logada: o cancelamento permanece e
  o chat fica aberto (ADR-0003).
- **Rota própria.** `/servico/cancelar` já é do Empregador; um controller por ator mantém o controller só como
  tradução HTTP.
- **Sem `Clock` no `IniciarServicoUseCase`** (usa `Instant.now()`); este caso de uso usa `Clock`, como o ETI-94.

## Regra de negócio

| Critério | Implementação |
|---|---|
| Só o Prestador da candidatura escolhida | `buscarEscolhidaDoAnuncio` filtrada por `prestadorId`; senão 404 `SERVICO_NAO_ENCONTRADO`. |
| `PUBLICADO` ou `EM_ANDAMENTO` com candidatura `ESCOLHIDA` | `servicoEmAberto()` e `buscarEscolhidaDoAnuncioParaAtualizar`; senão 409 `SERVICO_ESTADO_INVALIDO`. |
| `AGUARDANDO_VALIDACAO` recusa | `servicoEmAberto()` é falso: 409 `SERVICO_ESTADO_INVALIDO`. |
| `ENCERRADO` e `encerradoEm` | `Anuncio.cancelarServicoPeloPrestador`. |
| Proposta pendente sem efeito | `ReagendamentoService.encerrarPendentesSemEfeito` (ponto único, igual ao do ETI-99). |
| Sem estorno de cota (RB18) | Nenhuma chamada à porta de cota. |
| Não volta ao mural | `ENCERRADO` é terminal; candidatar-se devolve 409 `ANUNCIO_ESTADO_INVALIDO`. |
| Chat somente leitura | `aoCancelarServicoPeloPrestador` após o commit. |
| Publica o evento | `ServicoCanceladoPeloPrestadorEvent`, só em operação bem-sucedida. |

## Contrato da API

| Operação | Rota | Entrada | Saída |
|---|---|---|---|
| Cancelar serviço (Prestador) | `POST /anuncios/{anuncioId}/servico/cancelar-prestador` | só o path | 200 `{ "anuncioId": "<uuid>", "status": "ENCERRADO", "encerradoEm": "<instante>" }` |

Erros: 404 `SERVICO_NAO_ENCONTRADO`, 409 `SERVICO_ESTADO_INVALIDO`, 401 sem token. Nenhum `CodigoErro` novo.

## Pendências

- Aviso ao Empregador: US56/ETI-112 (o evento já é publicado).
- Nenhum fluxo cria reagendamento (ETI-99/100): a proposta pendente é testada com dado semeado.

## Testes

`spec/CancelamentoServicoPeloPrestadorDominioTest`: transições de `Anuncio` e `Aplicacao`, sem tipo de cancelamento e
sem mexer na cota.

`spec/CancelarServicoPeloPrestadorUseCaseTest`: `PUBLICADO` e `EM_ANDAMENTO`, ordem das travas, proposta pendente (e
sem proposta), evento, cota intacta, `AGUARDANDO_VALIDACAO`, `CONCLUIDO` e `ENCERRADO`, sem candidatura travável,
sem escolhido, serviço inexistente e Prestador não escolhido.

`story/CancelarServicoPeloPrestadorStoryTest`: caminho feliz com banco (cota, evento e chat somente leitura),
`EM_ANDAMENTO`, proposta pendente, `AGUARDANDO_VALIDACAO` (e chat aberto), Prestador não escolhido, serviço
inexistente, cancelamento repetido, candidatura recusada depois do cancelamento e rollback.

Também: `chat/spec/DeixarChatSomenteLeituraUseCaseTest.aoCancelarServicoPeloPrestador` e
`chat/story/ChatDaCandidaturaStoryTest`.
