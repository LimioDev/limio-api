# ETI-92 — Validar o serviço (presencial ou pela foto)

## Status

Implementado.

## Link do card

[ETI-92](https://kaueamaral96.atlassian.net/jira/software/projects/ETI/boards/2?selectedIssue=ETI-92) —
[anuncio] [BACK-END] Validar o serviço (presencial ou pela foto). Origem: Backlog US42 (sem card original
no Jira). Card par: [anuncio] [FRONT-END] Tela "Validar serviço".

## User Story

Como Empregador, eu quero que a API registre que o serviço foi feito, para que o ciclo se encerre e eu
pague o prestador fora do LIMIO.

## Escopo implementado

`GET /anuncios/{anuncioId}/validacao`: fotos do check-out (se houver), `checkoutEm` e o nome do Prestador
escolhido, pro Empregador ver antes de confirmar.

`POST /anuncios/{anuncioId}/validar` (`{ "modo": "PRESENCIAL" | "PELA_FOTO" }`): o anúncio vai para
`CONCLUIDO` e grava `validadoEm`; `ServicoConcluidoEvent` é publicado. Sem migration: `validado_em` já
existe desde a V13.

**Fora**: UI (card par `[FRONT-END]`), o check-out em si (`ETI-90`, ainda não implementado — por isso
`EVIDENCIA_CHECKOUT` existe em `TipoMidia` mas nada grava esse tipo ainda), o aviso ao Prestador (`ETI-112`,
escuta `ServicoConcluidoEvent` quando existir), registro e conferência de pagamento (fora do LIMIO, UC),
denúncia de não pagamento (`ETI-114`/`115`). A avaliação (`ETI-107`, já na `develop`) não depende deste
evento: lê `validadoEm` direto pela porta `ConsultaServico`/`ResumoServico`.

## Decisões

- **"Serviço" é o `Anuncio`**, mesma decisão do ETI-88/ETI-86: a rota segue `POST
  /anuncios/{anuncioId}/cancelar`, a resposta traz só `status` e `validadoEm`.
- **Não dono recebe 404.** Mesmo critério do ETI-86/88: quem não é o Empregador dono do anúncio recebe o
  mesmo `SERVICO_NAO_ENCONTRADO` de um id inexistente, tanto em `validar` quanto em `validacao`.
- **`PELA_FOTO` antes do check-out (`PUBLICADO`/`EM_ANDAMENTO`) é `VALIDACAO_SEM_CHECKOUT`; já finalizado
  (`CONCLUIDO`/`ENCERRADO`) é `SERVICO_ESTADO_INVALIDO`.** O card pede a distinção ("Sem check-out com
  foto, recusa") só pro caso de "ainda não chegou lá"; `Anuncio.servicoEmAberto` isola esse critério
  do resto, pra não devolver "sem check-out" quando o problema real é "já concluiu". É o mesmo critério que
  decide se a validação presencial é permitida — método único (review da PR #31), não dois `switch` iguais.
- **`PRESENCIAL` aceita `PUBLICADO` ou `EM_ANDAMENTO`.** O passo "iniciar o serviço" (ETI-88) é opcional —
  mesma observação já deixada na doc do ETI-88 ("nada aqui bloqueia a validação presencial... a partir de
  `PUBLICADO`").
- **`EVIDENCIA_CHECKOUT` e a leitura das fotos já existem, a escrita não.** `TipoMidia.EVIDENCIA_CHECKOUT` e
  `MidiaService.buscarFotosAtivasDoCheckout` ficam prontos pro ETI-90 gravar; até lá, `GET .../validacao`
  sempre devolve `fotosCheckout: []` e `checkoutEm: null`.
- **`AplicacaoService.buscarEscolhidaDoAnuncio`**, usada aqui e por `ConsultarServicoUseCase` (ETI-86, já
  na `develop` desde a #29) — mesma busca, uma assinatura só.
- **Trava no anúncio contra clique duplo.** `trava.travar(EscopoTrava.ANUNCIO, anuncioId)` antes de ler o
  anúncio (advisory lock, o mesmo de editar, recusar candidato e anexar/remover foto); a segunda requisição
  encontra `CONCLUIDO` e recebe 409. `CancelarAnuncioUseCase` não trava nada ainda e `IniciarServicoUseCase`
  usa trava de linha (`buscarParaAtualizar`), que não exclui o advisory lock — os dois só entram nesta
  mesma fila quando migrarem pra `EscopoTrava.ANUNCIO` (a #32, ETI-69, faz isso pro iniciar).
- **Evento em `comum/eventos/`.** `ServicoConcluidoEvent` leva `anuncioTitulo` e `contratanteId` soltos,
  mesmo motivo do `ServicoIniciadoEvent`: `notificacao` não importa `modulos.anuncio`. `moderacao` (ETI-107)
  não escuta este evento — lê `validadoEm` pela porta `ConsultaServico`/`ResumoServico`.

## Regra de negócio

| Critério do card | Implementação |
|---|---|
| Só o Empregador dono do anúncio | `Anuncio.pertenceA`; os demais recebem 404 `SERVICO_NAO_ENCONTRADO`. |
| Presencial a partir de `PUBLICADO` (com escolhida) ou `EM_ANDAMENTO`, sem foto | `Anuncio.servicoEmAberto`, `switch` sem `default`; fora disso, 409 `SERVICO_ESTADO_INVALIDO`. |
| Pela foto só a partir de `AGUARDANDO_VALIDACAO` | `Anuncio.permiteValidacaoPelaFoto`; fora disso, 409 `VALIDACAO_SEM_CHECKOUT`. |
| Marca `CONCLUIDO` e preenche `validadoEm` | `Anuncio.validar`. |
| Não registra nem confere pagamento | Nenhum campo/fluxo de pagamento tocado. |
| Publica `ServicoConcluido` | `ServicoConcluidoEvent`. Ninguém escuta ainda — `notificacao` (`ETI-112`) é o único consumidor previsto. |
| Consultar a validação devolve fotos do check-out, data/hora e nome do Prestador | `ConsultarValidacaoUseCase` + `ConsultaValidacaoResponse`. |

## Contrato da API

| Operação | Rota | Entrada | Saída |
|---|---|---|---|
| Consultar validação | `GET /anuncios/{anuncioId}/validacao` | `anuncioId` no path | 200 `{ "fotosCheckout": [...], "checkoutEm": "<instant>\|null", "nomePrestador": "<string>\|null" }` |
| Validar serviço | `POST /anuncios/{anuncioId}/validar` | `anuncioId` no path, `{ "modo": "PRESENCIAL"\|"PELA_FOTO" }` | 200 `{ "status": "CONCLUIDO", "validadoEm": "<instant>" }` |

`CodigoErro` novos: `VALIDACAO_SEM_CHECKOUT` (409). Reaproveitados: `SERVICO_NAO_ENCONTRADO` (404),
`SERVICO_ESTADO_INVALIDO` (409), `VALIDACAO_FALHOU` (400, corpo sem `modo`).

## Testes

`spec/ValidarServicoUseCaseTest`: presencial de `PUBLICADO` e `EM_ANDAMENTO` com evento, pela foto de
`AGUARDANDO_VALIDACAO`, cada status fora do permitido pra cada modo, não dono, sem candidatura escolhida e
anúncio inexistente.

`spec/ConsultarValidacaoUseCaseTest`: com fotos de check-out e nome do Prestador, sem fotos, não dono e
anúncio inexistente.

`story/ValidarServicoStoryTest`: os dois modos nos estados aceitos, pela foto sem check-out (409
`VALIDACAO_SEM_CHECKOUT`), presencial fora do estado (409 `SERVICO_ESTADO_INVALIDO`), validar de novo
depois de `CONCLUIDO`, não dono, anúncio inexistente, sem token, corpo sem `modo` (400) e consulta da
validação com e sem acesso.
