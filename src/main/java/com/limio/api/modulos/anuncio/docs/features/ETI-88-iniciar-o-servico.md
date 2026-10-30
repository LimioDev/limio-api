# ETI-88 — Iniciar o serviço

## Status

Implementado.

## Link do card

[ETI-88](https://kaueamaral96.atlassian.net/jira/software/projects/ETI/boards/2?selectedIssue=ETI-88) —
[anuncio] [BACK-END] Iniciar o serviço. Origem: Backlog US40 (sem card original no Jira). Card par:
[anuncio] [FRONT-END] Botão "Iniciar serviço" e tela "Serviço em andamento".

## User Story

Como Prestador escolhido, eu quero que a API registre que cheguei, para que o Empregador saiba que o
serviço começou.

## Escopo implementado

`POST /anuncios/{anuncioId}/iniciar`: o anúncio vai de `PUBLICADO` para `EM_ANDAMENTO` e grava
`iniciadoEm`, a proposta de reagendamento pendente é encerrada sem efeito e `ServicoIniciadoEvent` é
publicado. Sem migration: `iniciado_em` e a tabela `reagendamento` já existem desde a V13.

**Fora**: UI (card par `[FRONT-END]`), envio do aviso ao Empregador (`ETI-112`, só o evento é publicado
aqui), criação de proposta de reagendamento (`ETI-99`/`100`), cancelamento do serviço combinado
(`ETI-94`/`95`).

## Decisões

- **"Serviço" é o `Anuncio`.** Não existe entidade própria: o id do serviço é o id do anúncio, e a rota
  segue `POST /anuncios/{anuncioId}/cancelar` (ETI-63). A resposta traz só `status` e `iniciadoEm`.
- **Não escolhido recebe 404.** O card pede "recusa" e lista só dois `CodigoErro`. Quem não é o escolhido,
  inclusive o Empregador dono, recebe o mesmo `SERVICO_NAO_ENCONTRADO` de um id inexistente, mesmo critério
  do ETI-63.
- **A identidade decide, não o papel ativo.** A regra compara o usuário com o Prestador da candidatura.
  Quem foi escolhido e trocou o papel ativo continua podendo iniciar.
- **Sem restrição de horário.** O card só proíbe a mudança automática. O Prestador pode iniciar antes ou
  depois de `dataHoraPretendida`.
- **A candidatura não muda.** Continua `ESCOLHIDA`: o card só descreve a mudança no anúncio.
- **Proposta pendente já tratada.** O card coloca o encerramento na dependência do ETI-99, mas `Reagendamento`
  e `cancelarSemEfeito` existem desde o TICKET-0049 e o card pede o teste. Só falta quem crie propostas.
- **Trava no anúncio contra clique duplo.** `BaseEntity` não tem `@Version`. Sem trava, duas requisições
  passariam pela checagem, reescreveriam `iniciadoEm` e publicariam o evento duas vezes. O
  `findByIdParaAtualizar` segue o idioma de `UsuarioRepository`; a segunda requisição recebe 409.
- **Evento em `comum/eventos/`.** Quem consome é outro módulo (ADR-0003). Leva `anuncioTitulo` e
  `contratanteId` soltos pelo mesmo motivo de `CandidaturaCriadaEvent`: `notificacao` não importa `anuncio`.

## Regra de negócio

| Critério do card | Implementação |
|---|---|
| Só o Prestador da candidatura `ESCOLHIDA` | `AplicacaoService.foiEscolhido`; os demais recebem 404 `SERVICO_NAO_ENCONTRADO`. |
| Só a partir de `PUBLICADO` com candidatura `ESCOLHIDA` | `Anuncio.permiteInicio`, `switch` sem `default`; fora disso, 409 `SERVICO_ESTADO_INVALIDO`. |
| Muda para `EM_ANDAMENTO` e preenche `iniciadoEm` | `Anuncio.iniciar`. |
| Proposta pendente encerrada sem efeito | `ReagendamentoService.buscarPendentesDosAnuncios` + `Reagendamento.cancelarSemEfeito`. |
| Publica `ServicoIniciado` | `ServicoIniciadoEvent`. Ninguém escuta até o ETI-112. |
| Empregador não cancela depois de iniciar | `CancelarAnuncioUseCase` já recusa com 409 fora de `PUBLICADO`. O ETI-94/95 precisa manter a regra. |
| Ninguém propõe reagendamento depois de iniciar | A cargo do ETI-99/100: ainda não existe usecase de proposta. |
| Não é automático | Nenhum job muda o status; só o endpoint. |
| Passo opcional | Nada aqui bloqueia a validação presencial ou o check-out a partir de `PUBLICADO` (ETI-90 a 93). |

## Contrato da API

| Operação | Rota | Entrada | Saída |
|---|---|---|---|
| Iniciar serviço | `POST /anuncios/{anuncioId}/iniciar` | `anuncioId` no path, sem corpo | 200 `{ "status": "EM_ANDAMENTO", "iniciadoEm": "<instant>" }` |

`CodigoErro` novos: `SERVICO_NAO_ENCONTRADO` (404) e `SERVICO_ESTADO_INVALIDO` (409).

## Testes

`spec/IniciarServicoUseCaseTest`: sucesso com evento, proposta pendente, cada status fora de `PUBLICADO`,
Prestador não escolhido e anúncio inexistente.

`story/IniciarServicoStoryTest`: os quatro cenários do card (escolhido, não escolhido, em `EM_ANDAMENTO`,
proposta pendente), Empregador dono, cancelamento recusado depois de iniciar, clique duplo com duas
requisições paralelas reais, anúncio inexistente e 401 sem token.
