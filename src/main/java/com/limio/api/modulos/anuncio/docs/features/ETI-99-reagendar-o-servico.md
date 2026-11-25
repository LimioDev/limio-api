# ETI-99 — Reagendar o serviço: propor, cancelar a proposta, responder e expirar

## Status

Implementado, com uma pendência de integração que depende do ETI-98 (conclusão por silêncio, ainda inexistente).

## Link do card

ETI-99 — [anuncio] [BACK-END] Reagendar: propor, cancelar proposta, responder e expirar. Reúne as funcionalidades das
US46 e US47 (sem link do Jira neste repositório).

## User Story

Como Empregador ou como Prestador escolhido, eu quero propor uma nova data e hora para um serviço já combinado, para
ajustar o acordo sem cancelá-lo; e como a outra parte, eu quero aceitar ou recusar a proposta dentro de um prazo.

## Escopo implementado

| Operação | Rota | Entrada | Saída |
|---|---|---|---|
| Propor | `POST /anuncios/{anuncioId}/reagendamentos` | `{ "novaDataHora": "<instante ISO>", "justificativa": "<texto>" }` | 200 `ReagendamentoResponse` |
| Consultar | `GET /reagendamentos/{reagendamentoId}` | só o path | 200 `ReagendamentoResponse` |
| Cancelar a própria proposta | `POST /reagendamentos/{reagendamentoId}/cancelar` | só o path | 200 `ReagendamentoResponse` (`CANCELADO`) |
| Aceitar ou recusar | `POST /reagendamentos/{reagendamentoId}/responder` | `{ "decisao": "ACEITAR" \| "RECUSAR" }` | 200 `ServicoCombinadoResponse` (o serviço atualizado) |
| Expirar | job `@Scheduled` (sem rota) | — | — |

`ReagendamentoResponse`: `id`, `anuncioId`, `status`, `dataVigente`, `dataAnterior`, `dataProposta`, `justificativa`,
`proponenteId`, `criadoEm`, `expiraEm`, `respondidoEm` (nulo enquanto pendente).

Também: cada operação grava `LogAuditoria` e publica um evento em `comum/eventos/`
(`ReagendamentoProposto/Cancelado/Aceito/Recusado/ExpiradoEvent`); iniciar, cancelar o serviço, encerrar a conta,
fazer o check-out e validar encerram a proposta pendente sem efeito; `ReagendamentoService.existePendente` fica
disponível para a conclusão por silêncio.

Migration `V40__preparar_reagendamento_para_propostas.sql`: `reagendamento.proponente_id`, `expira_em` e
`proposta_apos_data_vigente`, mais o índice único parcial de proposta pendente por anúncio e o índice parcial de
expiração. As colunas `NOT NULL` entram sem backfill porque nenhum fluxo gravava na tabela antes deste card.

Configuração (`application.properties`, padrão `${ENV:default}`):

| Propriedade | Padrão | Papel |
|---|---|---|
| `anuncio.reagendamento.limite` | 2 | máximo de reagendamentos **aceitos** por serviço |
| `anuncio.reagendamento.prazo-resposta-horas` | 24 | prazo da contraparte, gravado em cada proposta |
| `anuncio.carencia-pos-reagendamento-horas` | 24 | já existia (ETI-94): janela do cancelamento `TARDIO` |
| `anuncio.reagendamento.expiracao.cron` | `0 */5 * * * *` | cron do job de expiração (`-` nos testes) |
| `anuncio.reagendamento.expiracao.lote` | 100 | propostas por execução |

**Fora**: notificações (US56: só os eventos são publicados), conclusão por silêncio (ETI-98), cancelamento do serviço
pelo Prestador (ETI-96/97), qualquer mudança de cota, UI.

## Decisões

- **Reaproveitou `Reagendamento`** (TICKET-0049) em vez de criar entidade nova, e `ReagendamentoService` ganhou os
  finders. Estados: `PENDENTE` e, finais, `ACEITO`, `RECUSADO`, `CANCELADO` (o proponente desistiu), `EXPIRADO` (prazo)
  e `SEM_EFEITO` (o serviço deixou de estar combinado). `CANCELADO` e `SEM_EFEITO` ficam separados para o registro
  dizer quem encerrou. O antigo `cancelarSemEfeito` foi removido.
- **O contador de aceitos é `COUNT(status = ACEITO)`**, não uma coluna no `Anuncio`: sem estado duplicado, e propostas
  recusadas, canceladas, expiradas ou sem efeito não contam no limite.
- **`expira_em` é gravado na criação** (como `prazoValidacaoHoras`): mudar a propriedade não altera propostas em curso.
  O valor é o menor entre `agora + prazo-resposta-horas` e a `dataProposta`: depois da nova data o aceite daria 422
  `DATA_NO_PASSADO`, então a proposta não pode seguir pendente (bloqueando nova proposta e a conclusão por silêncio)
  além desse instante.
- **Combinado é `PUBLICADO` com a candidatura `ESCOLHIDA`**, a mesma regra do cancelamento (ETI-94): propor reaproveita
  `situacaoParaCancelarServico() == CANCELAVEL`. A proposta não muda o anúncio; só o aceite troca `dataHoraPretendida`
  (`Anuncio.reagendar`).
- **Proposta depois da data vigente.** A primeira é permitida; depois dela, nenhuma outra. A marca fica em
  `proposta_apos_data_vigente`, e `existePropostaAposDataVigente` conta qualquer desfecho, inclusive cancelada pelo
  proponente (para não burlar a regra). É **por serviço**, não por data vigente: se um aceite trocar a data e ela
  passar de novo, não há segunda chance. A nova data precisa estar no futuro na criação e de novo no aceite.
- **Ordem das validações.** Propor: parte do serviço (404), combinado (409), data futura (422), data diferente da vigente (422 `DATA_IGUAL_A_VIGENTE`: aceitar uma igual
  gastaria um dos reagendamentos e abriria a carência sem mudar nada), sem pendente (409),
  limite (409), proposta única após a data vigente (409). Responder e cancelar: existe e é parte (404), papel (403),
  pendente (409 `PROPOSTA_ENCERRADA`), no prazo (409 `PROPOSTA_EXPIRADA`); no aceite ainda vêm o serviço combinado
  (409) e a data futura (422).
- **Proposta vencida, mas ainda não expirada pelo job.** Aceitar, recusar e cancelar devolvem `PROPOSTA_EXPIRADA` e a
  linha continua `PENDENTE` até o job rodar (até 5 minutos com o cron padrão); quem grava `EXPIRADO` é só o job, para o
  registro ficar fiel.
- **Quem é "parte" do serviço** é o Empregador do anúncio ou o Prestador escolhido, lido pela escolha permanente
  (`AplicacaoService.buscarPrestadorEscolhido`, por `escolhidaEm`). Assim, depois de o serviço ser cancelado, o
  Prestador ainda é reconhecido e recebe 409 de estado, não um 404 que o trataria como estranho (o mesmo critério do
  `ConsultarServicoUseCase`). Quem não é parte e proposta inexistente têm a mesma resposta (404
  `REAGENDAMENTO_NAO_ENCONTRADO`, ou `SERVICO_NAO_ENCONTRADO` ao propor).
- **Trava.** Toda operação toma a advisory `EscopoTrava.ANUNCIO` como primeira instrução. Nas rotas por
  `reagendamentoId` o `anuncioId` vem de uma consulta escalar (`ReagendamentoService.buscarAnuncioId`) antes da trava,
  porque carregar a proposta antes deixaria uma cópia velha no contexto de persistência (mesmo cuidado do
  `AplicacaoService.buscarAnuncioId`). Aceitar, recusar, cancelar, expirar, propor, iniciar e cancelar o serviço se
  serializam nessa trava e quem perde a corrida vê a proposta já encerrada. O índice único parcial é só a rede de
  segurança do banco.
- **Aceitar.** O anúncio segue `PUBLICADO` e a candidatura `ESCOLHIDA`. `respondidoEm` é o início da carência
  (RB36), lido pelo cancelamento do Empregador sem nenhuma mudança: o `TARDIO`, o "sem estorno" e a antecedência de 24h
  (agora sobre a nova data) passam a funcionar de ponta a ponta. Nenhuma cota é tocada. O limite não é revalidado no
  aceite: só há uma proposta pendente por vez, e o limite foi checado ao propor.
- **Recusar** não exige data futura nem revalida o estado do serviço: recusar uma proposta cuja data já passou é
  legítimo.
- **Responder devolve o serviço**, como pede o contrato do card: o controller chama `ResponderReagendamentoUseCase` e,
  depois do commit dele, `ConsultarServicoUseCase`. Se a consulta falhar depois do commit, o cliente vê o erro mesmo
  com a resposta já gravada.
- **Um único ponto de encerramento sem efeito** (`ReagendamentoService.encerrarPendentesSemEfeito`) é chamado por
  iniciar o serviço, cancelar o serviço, encerrar a conta, check-out e validar. O check-out e a validação ainda não
  encerravam a proposta.
- **Auditoria** (`TipoAcaoAuditoria.REAGENDAMENTO_*`): `entidade = "Reagendamento"` com o id da proposta; o `anuncioId`
  vai nos detalhes. O aceite guarda `dataAnterior`, `dataNova` e `aceitoEm`, as mesmas chaves do cancelamento tardio.
  O job grava sem IP.
- **Eventos** sem listener, como o `AnuncioExpiradoEvent`: o aceite notifica as duas partes; recusa, cancelamento e
  expiração trazem proponente e as duas partes para a US56 decidir quem recebe o quê.
- **Job de expiração** no molde do ETI-66: `@Scheduled` no próprio usecase, lote por query nativa de ids,
  `TransactionTemplate` por proposta, releitura sob a trava e falha por item só logada. Sem ShedLock. Uma proposta já
  respondida nunca é expirada depois, porque a decisão é tomada sob a trava com o estado atual.

## Regra de negócio

| Critério | Implementação |
|---|---|
| Empregador ou Prestador escolhido propõem | `ProporReagendamentoUseCase`; demais recebem 404. |
| Serviço combinado | `PUBLICADO` com `ESCOLHIDA`; senão 409 `SERVICO_ESTADO_INVALIDO`. |
| Data e justificativa obrigatórias, justificativa até 600 caracteres | `@NotNull`, `@NotBlank` e `@Size(max = 600)` (400 `VALIDACAO_FALHOU`); data no passado, 422 `DATA_NO_PASSADO`. |
| Uma proposta pendente por serviço | 409 `PROPOSTA_PENDENTE_EXISTENTE`, e índice único parcial. |
| Limite de reagendamentos aceitos | 409 `LIMITE_REAGENDAMENTOS`. |
| Uma única proposta depois da data vigente | 409 `REAGENDAMENTO_NAO_PERMITIDO`. |
| Só o proponente cancela | 403 `ACESSO_NEGADO` para a outra parte. |
| Só a contraparte responde | 403 `ACESSO_NEGADO` para o proponente. |
| Resposta dentro do prazo | 409 `PROPOSTA_EXPIRADA`. |
| Proposta já encerrada | 409 `PROPOSTA_ENCERRADA`. |
| Aceite troca a data vigente | `Anuncio.reagendar`; anúncio `PUBLICADO` e candidatura `ESCOLHIDA`. |
| Carência pós-reagendamento | `respondidoEm` do `ACEITO`; lida pelo `CancelarServicoCombinadoUseCase` (ETI-94). |
| Recusa, cancelamento e expiração mantêm a data | O anúncio não é alterado. |
| Serviço sai de "combinado" | Proposta pendente vira `SEM_EFEITO` (iniciar, cancelar, conta, check-out, validar). |

## Contrato da API

Erros novos em `CodigoErro`: `DATA_IGUAL_A_VIGENTE` (422), `REAGENDAMENTO_NAO_ENCONTRADO` (404), `PROPOSTA_PENDENTE_EXISTENTE`,
`LIMITE_REAGENDAMENTOS`, `REAGENDAMENTO_NAO_PERMITIDO`, `PROPOSTA_EXPIRADA` e `PROPOSTA_ENCERRADA` (409). Reaproveitados:
`DATA_NO_PASSADO` (422), `SERVICO_ESTADO_INVALIDO` (409), `SERVICO_NAO_ENCONTRADO` (404), `VALIDACAO_FALHOU` (400),
`ACESSO_NEGADO` (403) e 401 sem token. Uma `decisao` ausente ou inválida é 400 `VALIDACAO_FALHOU`.

## Pendências

- **Conclusão por silêncio (ETI-98), a integração que fica para esse card.** Nada neste card impede um job de
  concluir o serviço: o job ainda não existe. O contrato a respeitar: depois da trava `ANUNCIO` e antes de
  `Anuncio.validar`, o job deve checar `ReagendamentoService.existePendente(anuncioId)` e não concluir se for
  verdadeiro. A retomada é automática: ao aceitar, recusar, cancelar ou expirar, a pendente deixa de existir, e a data
  de referência é a `dataHoraPretendida` que o anúncio tiver (a nova, se o aceite a trocou).
  `ReagendamentoPendenteGanchoStoryTest` fixa o contrato do gancho. Os cenários 29 a 32 do plano (pendente impede a
  conclusão, aceite, recusa e cancelamento retomam) só podem ser testados ponta a ponta com o job.
- **Decisões do ETI-98 que este card deixa em aberto:** qual é a data de referência do silêncio (hoje o único prazo
  modelado é `checkoutEm + prazoValidacaoHoras`, mas o card fala em silêncio depois da data vigente, sem check-out) e se
  uma proposta pendente já vencida, que o job de expiração ainda não encerrou, continua bloqueando a conclusão (atraso
  máximo igual ao cron, 5 minutos por padrão).
- **Leitura da regra "proposta depois da data vigente" (a confirmar com o PO).** O card diz que, com a data vigente já
  passada, só se aceita proposta se não houve proposta recusada ou expirada no serviço. A implementação lê de outro
  jeito: a única proposta permitida é a **primeira feita depois da data vigente** (marca `proposta_apos_data_vigente`),
  e qualquer desfecho dela, inclusive cancelada pelo proponente, esgota a chance. Duas diferenças: (1) uma proposta
  recusada ou expirada **antes** da data vigente não bloqueia nada aqui, então a primeira proposta "atrasada" é
  aceita, mas o card a recusaria; (2) uma proposta cancelada pelo proponente depois da data vigente bloqueia, e o card
  não a cita. Se o PO quiser a leitura literal do card, a mudança é `existePropostaAposDataVigente` passar a contar
  `RECUSADO` e `EXPIRADO` de qualquer momento, sem a marca e sem contar `CANCELADO`.
- **Notificações (US56).** `notificacao` já recebe eventos de aviso (ETI-112, `RegistrarAvisosUseCase`), mas não escuta
  os cinco eventos de reagendamento: falta um ouvinte `AFTER_COMMIT` + `REQUIRES_NEW` por evento, e o texto de cada
  aviso.
- **Cancelamento do serviço pelo Prestador (ETI-96/97)** ainda não existe. Quando entrar, deve chamar
  `ReagendamentoService.encerrarPendentesSemEfeito`, como os demais fluxos.
- **ETI-100**, citado em docs antigos como par deste card, não foi consultado: confirmar o escopo para não haver
  sobreposição.
- As constantes de 24h seguem duplicadas em `ConsultarServicoUseCase` e `EncerrarAnunciosEAplicacoesUseCase` (pendência
  herdada do ETI-94).

## Testes

`spec/`: `ReagendamentoDominioTest` (transições, prazo, proponente), `ReagendamentoServiceTest` (ponto único de
encerramento) e um spec por usecase: `ProporReagendamentoUseCaseTest`, `ConsultarReagendamentoUseCaseTest`,
`CancelarReagendamentoUseCaseTest`, `ResponderReagendamentoUseCaseTest` e `ExpirarReagendamentosUseCaseTest`. Também
`RealizarCheckoutUseCaseTest` e `ValidarServicoUseCaseTest` (encerram a pendente) e os specs dos usecases que passaram
a chamar o ponto único.

`story/` (Postgres real, login real, relógio controlado):

- `ProporReagendamentoStoryTest`: sucesso por cada parte, data no passado, justificativa e data ausentes, pendente
  existente, limite (e o que não conta nele), depois da data vigente (primeira, e segunda após recusada ou expirada),
  serviço fora de combinado, candidatura não escolhida, 404, 401 e corrida entre as duas partes.
- `ConsultarReagendamentoStoryTest`: os dois lados, data vigente atual numa proposta antiga, 404 e 401.
- `CancelarReagendamentoStoryTest`: o proponente de cada lado, a contraparte (403), propostas encerradas e vencida
  (409), efeitos, auditoria, evento e corrida entre dois cancelamentos.
- `ResponderReagendamentoStoryTest`: aceite (data, contador, auditoria, evento, carência e `TARDIO` no cancelamento,
  prazo de 24h sobre a nova data, proposta depois da data vigente), recusa, proponente (403), vencida, encerrada, data
  passada, serviço fora de combinado, decisão inválida, 404 e 401.
- `ExpirarReagendamentosStoryTest`: expira só as vencidas, nunca as respondidas, idempotência, rollback e nova rodada,
  corrida entre duas instâncias e entre o job e o início do serviço.
- `ReagendamentoConcorrenciaStoryTest`: aceitar, recusar, cancelar e expirar ao mesmo tempo; aceitar contra cancelar o
  serviço; propor contra iniciar o serviço.
- `ReagendamentoPendenteGanchoStoryTest`: contrato de `existePendente`, `existePropostaAposDataVigente` e
  `contarAceitos`.
- `CheckoutStoryTest` e `ValidarServicoStoryTest`: a proposta pendente vira `SEM_EFEITO`, ou fica pendente se a ação
  for recusada ou fizer rollback.
