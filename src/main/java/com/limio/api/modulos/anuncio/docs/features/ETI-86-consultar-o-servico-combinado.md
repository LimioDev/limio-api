# ETI-86 — Consultar o serviço combinado com os contatos liberados

## Status

Implementado (parcial — ver "Pontos em aberto").

## Link do card

[ETI-86](https://kaueamaral96.atlassian.net/jira/software/projects/ETI/boards/2?selectedIssue=ETI-86) —
[anuncio] [BACK-END] Consultar o serviço combinado com os contatos liberados (US39). Label
`pendente-definicao` no Jira. Card par: `[FRONT-END]` telas "Serviço combinado" (Empregador) e "Seu serviço"
(Prestador).

## User Story

Como Empregador ou Prestador escolhido, eu quero que a API traga os detalhes do serviço combinado com os
contatos da outra parte, para que a gente se organize.

## Escopo implementado

- `GET /anuncios/{anuncioId}/servico`: status, data pretendida, endereço completo e bairro, dados da outra
  parte (nome, foto, telefone, GOLD quando quem vê é o Empregador), andamento (`escolhidaEm`, `iniciadoEm`,
  `checkoutEm`, `validadoEm`), prazo de cancelamento, reagendamentos aceitos/limite, proposta de
  reagendamento pendente (se houver) e o chat da candidatura (id + mensagens não lidas).
- Acesso: só o Empregador dono e o Prestador da candidatura escolhida. Qualquer outro — inclusive um
  Prestador nunca escolhido — recebe o mesmo 404 de um id inexistente.
- O acesso continua valendo depois que o serviço conclui ou o anúncio cancela: a candidatura escolhida é
  achada por `escolhidaEm != null`, não pelo `status` atual (UC "Empregador cancelar após combinação").

**Fora**: UI (card par), escolher candidato (ETI-69/ETI-70, que ainda não existe — por isso "serviço
combinado" só é alcançável semeando a candidatura direto no banco, como os testes fazem), criar proposta de
reagendamento (ETI-99/ETI-100), cancelar o serviço (ETI-94/ETI-95), check-in presencial (ETI-92/ETI-93) e
check-out (ETI-90/ETI-91) — por isso `iniciadoEm`, `checkoutEm` e `validadoEm` saem `null` na prática hoje,
mesmo a coluna já existindo (V13).

## Regra de negócio

| Critério do card | Implementação |
|---|---|
| Só o Empregador dono e o Prestador escolhido; qualquer outro é 404 | `Anuncio.pertenceA` + `AplicacaoService.buscarEscolhidaDoAnuncio(...).getPrestadorId()`; `ServicoNaoEncontradoException` (404) pros dois casos. |
| Status, data/hora vigente, endereço completo e bairro | Lidos direto do `Anuncio` (`enderecoCompletoSnapshot`, `bairroRegiaoSnapshot`). Reagendamento aceito mudaria a data vigente (ETI-99), mas hoje nunca existe um aceito. |
| Dados da outra parte: nome, foto, telefone | `ConsultaDadosUsuario` + `ConsultaFotoPerfil`, completos (contato já liberado) — diferente do nome abreviado de antes da escolha. Conta encerrada: tudo `null` (`OutraParteDoServico.CONTA_ENCERRADA`). |
| GOLD só pro Empregador ver do Prestador | `ConsultaPlanoPrestador`, só chamada quando quem consulta é o Empregador; `null` no sentido contrário (não existe GOLD de Empregador). |
| Média de avaliações | **Não implementado.** Não existe módulo de avaliações (mesma situação da PR#27/ETI-67); como não há nenhum dado real pra abstrair, não criei outra porta `ConsultaAvaliacoes` duplicada — o campo simplesmente não está na resposta ainda. Ver "Pontos em aberto". |
| Andamento: `escolhidaEm`, `iniciadoEm`, `checkoutEm`, `validadoEm` | `escolhidaEm` vem da candidatura; os outros três são colunas que já existem em `Anuncio` desde a V13, mas nenhum usecase ainda os preenche (ETI-88, ETI-92/93, ETI-90/91) — saem `null` na prática. |
| Prazo de cancelamento (data vigente − 24h) | Calculado no usecase (`dataHoraPretendida.minus(24h)`), sem tocar a entity. |
| Carência de reagendamento aceito (ETI-94/ETI-95) | **Não implementado** — a regra de carência não está definida em nenhum card ainda (`pendente-definicao`). Nenhum campo "falso" foi criado pra não sugerir uma lógica que não existe; ver "Pontos em aberto". |
| Reagendamentos aceitos e limite ("1 de 2") | `ReagendamentoService.contarAceitos` (conta `status = ACEITO`) + constante `LIMITE_REAGENDAMENTOS = 2`. Hoje sempre 0: ninguém cria reagendamento ainda. |
| Proposta de reagendamento pendente | `ReagendamentoService.buscarPendentesDosAnuncios`, já existente (TICKET-0049). `quem propôs` e `prazo` **não saem**: a tabela `reagendamento` não guarda isso — fica pro ETI-99, que cria a proposta e decide o schema. |
| Id do chat e mensagens não lidas | Porta nova `ConsultaChatDoServico` (`comum/portas/`), implementada em `chat`. Conta mensagens com `lida_em is null` e remetente diferente de quem consulta. |

## Decisões técnicas

- **Candidatura escolhida por `escolhidaEm != null`, não por `status`.** `AplicacaoRepository
  .findFirstByAnuncioIdAndEscolhidaEmIsNotNull` acha a candidatura mesmo que o status atual já tenha virado
  `CONCLUIDA` ou `ENCERRADA` — exigência do card ("depois de cancelado, o contato já visto continua no
  histórico do escolhido").
- **Sem candidatura escolhida ainda = 404.** O card pressupõe que já existe "serviço combinado"; anúncio
  `PUBLICADO` sem ninguém escolhido responde igual a inexistente, mesmo pro próprio dono.
- **`SERVICO_NAO_ENCONTRADO` é o mesmo código sugerido pela PR#26 (ETI-88, "iniciar o serviço").** As duas
  features tratam `Anuncio` como "o serviço" do ponto de vista pós-escolha. A PR#26 não estava mergeada
  quando esta foi escrita, então `CodigoErro.SERVICO_NAO_ENCONTRADO` e `ServicoNaoEncontradoException` foram
  recriados aqui, de propósito iguais aos da #26 — **duplicação esperada**: quem mergear a segunda das duas
  apaga a cópia e reusa a da outra.
- **`ConsultaChatDoServico` é porta nova, não reusa o nome `ConsultaChat` da PR#27 (ETI-67).** A da #27 é em
  lote (`Map<candidaturaId, chatId>`, pra lista de candidatos) e não inclui não-lidas; esta é de um item só,
  com contagem por quem consulta. Nomes diferentes de propósito, pra não colidir com a #27 quando ela
  mergear.
- **Sem porta de avaliações.** Diferente do chat/especialidades (que têm dado real, só sem a feature de
  avaliação em si), avaliação não tem módulo nem tabela — criar uma porta que só devolve "vazio" pra sempre
  seria abstração sem necessidade (e colidiria de novo com a `ConsultaAvaliacoes` da PR#27). Quando o módulo
  de avaliações existir, aí sim a porta nasce com um consumidor real dos dois lados.
- **`ReagendamentoService.contarAceitos` reusa `findByAnuncioIdInAndStatus`** (já existe, usado por
  `buscarPendentesDosAnuncios`) — nenhum método novo no repository.

## Contrato da API

| Operação | Rota | Entrada | Saída |
|---|---|---|---|
| Consultar serviço | `GET /anuncios/{anuncioId}/servico` | id no path | 200 `ServicoCombinadoResponse` |

`CodigoErro`: `SERVICO_NAO_ENCONTRADO` (404, compartilhado com a PR#26), `NAO_AUTENTICADO` (401 sem token).

## Pontos em aberto (decisão do PO / dependência de outro card)

- **Média de avaliações**: aguarda o módulo de avaliações.
- **Carência de reagendamento aceito**: regra ainda não definida em nenhum card (ETI-94/95).
- **Quem propôs o reagendamento e o prazo de resposta**: schema de `reagendamento` precisa de coluna nova
  (ETI-99).
- **Conflito de nome esperado com a PR#26** (`SERVICO_NAO_ENCONTRADO`/`ServicoNaoEncontradoException`):
  resolver no merge, ficando com uma cópia só.
- **Planilha/Figma não revisados em detalhe** (o próprio card já avisa disso).

## Testes

`spec/ConsultarServicoUseCaseTest`: Empregador vê o Prestador, Prestador escolhido vê o Empregador (sem
GOLD), Prestador não escolhido (404), anúncio inexistente (404), sem candidatura escolhida ainda (404),
serviço já cancelado (Prestador escolhido ainda acessa), conta da outra parte encerrada, proposta pendente,
reagendamentos aceitos, chat com mensagens não lidas, foto da outra parte.

`story/ConsultarServicoStoryTest`: os cenários do card — Empregador, Prestador escolhido, Prestador não
escolhido (404), anúncio inexistente (404), sem token (401), proposta pendente, serviço cancelado
(histórico do escolhido) e chat com mensagem não lida (contagem).
