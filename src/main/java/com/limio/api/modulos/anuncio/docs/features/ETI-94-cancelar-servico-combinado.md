# ETI-94 — Empregador cancelar o serviço combinado (24h ou mais)

## Status

Implementado.

## Link do card

ETI-94 — [anuncio] [BACK-END] Empregador cancelar o serviço combinado (24h ou mais). Origem: RF47, RB08, RB17,
RB36, UC42 e UC52 (sem link do Jira neste repositório).

## User Story

Como Empregador, eu quero cancelar o serviço já combinado com pelo menos 24h de antecedência, para desfazer o
acordo sem prejudicar o Prestador, que recupera a escolha gasta da cota FREE.

## Escopo implementado

`POST /anuncios/{anuncioId}/servico/cancelar`, sem corpo. O anúncio vai para `ENCERRADO` (com `encerradoEm`,
`motivoEncerramento` e `tipoCancelamento`), a candidatura escolhida vai para `ENCERRADA`, a cota FREE é estornada
(cancelamento normal), o chat vira somente leitura e `ServicoCanceladoEvent` é publicado.

Migrations: `V34__registrar_consumo_de_cota_na_aplicacao.sql` (`aplicacao.cota_consumida`) e
`V35__cancelamento_do_servico_combinado.sql` (`anuncio.tipo_cancelamento`, `aplicacao.cota_estornada_em`).

**Fora**: criar e aceitar reagendamento (ETI-99/100), notificação ao Prestador (ETI-112, só o evento é
publicado), penalidade ou registro de reputação do cancelamento tardio, UI.

## Decisões

- **"Combinado" e "Cancelado" não são status.** Combinado é `PUBLICADO` com candidatura `ESCOLHIDA`. Cancelar é
  `ENCERRADO` mais `tipo_cancelamento` (`NORMAL` ou `TARDIO`) e um `motivoEncerramento` em texto livre.
- **Data vigente é `dataHoraPretendida`.** Exatamente 24h de antecedência é permitido; menos, 409
  `PRAZO_CANCELAMENTO_ESGOTADO`. O prazo é regra do anúncio (`Anuncio.PRAZO_CANCELAMENTO_SERVICO`, lido por `permiteCancelarServico` e
  `prazoCancelamentoServicoEm`), e os dois usecases (cancelar e consultar) leem de lá.
- **Carência pós-reagendamento.** Se o último `Reagendamento` `ACEITO` tem `respondidoEm + carência > agora`, o
  cancelamento é `TARDIO`: sem estorno, com auditoria (`COMBINADO_CANCELADO_TARDIO`, JSON com `dataAnterior`,
  `dataNova` e `aceitoEm`, origem `anuncio-cancelar-servico`). A carência é `anuncio.carencia-pos-reagendamento-horas`
  (padrão 24).
- **Estorno síncrono, na mesma transação.** Diverge do texto do card ("perfil recebe o evento"): um listener
  `AFTER_COMMIT` perderia o estorno se falhasse, sem retry. Usa a porta de escrita `ConsumoCotaPrestador.estornarEscolha`
  (`MANDATORY`), como o consumo da escolha (ETI-69). O evento segue publicado, com `cotaEstornada`, para o ETI-112.
- **Só estorna quem consumiu.** `aplicacao.cota_consumida` registra se a escolha gastou cota FREE (GOLD não gasta).
  O backfill da V34 marca `true` as escolhas existentes, porque o plano sempre foi FREE até aqui.
- **Estorno no mês da escolha.** Decrementa a `CotaMensal` do mês de `escolhidaEm`, não do atual. Piso em 0; mês sem
  linha ou zerado registra WARN e não impede o cancelamento.
- **Estorno libera a vaga na candidatura.** A barreira do `CandidatarSe` e a lista "Onde você usou sua cota"
  contavam por `escolhidaEm`, então o estorno não liberava a vaga. `aplicacao.cota_estornada_em` (também a marca de
  idempotência) faz essas duas consultas ignorarem escolhas estornadas. No cancelamento tardio a escolha continua
  contando, porque não estorna.
- **Proposta de reagendamento pendente** perde o efeito (`cancelarSemEfeito`), como em `IniciarServicoUseCase`.
- **Ordem das travas:** advisory `ANUNCIO` → `FOR UPDATE` na candidatura escolhida → `COTA_PRESTADOR` no estorno.
- **404 para não-dono.** Anúncio inexistente, outro Empregador e Prestador recebem `SERVICO_NAO_ENCONTRADO`.
- **Chat.** `DeixarChatSomenteLeituraUseCase.aoCancelarServico` (`AFTER_COMMIT` + `REQUIRES_NEW`, motivo
  `SERVICO_CANCELADO`). Falha do listener é só logada: o cancelamento permanece e o chat fica aberto.
- **Contato do Prestador permanece.** `GET /anuncios/{id}/servico` acha a candidatura por `escolhidaEm`, não pelo
  status, então o telefone segue visível depois do cancelamento.
- **Evento sem "quem cancelou".** É sempre o Empregador; o campo seria redundante.
- **Auditoria só no tardio**, como descreve o card. Cancelamento normal não é auditado.

## Regra de negócio

| Critério | Implementação |
|---|---|
| Só o Empregador dono | Anúncio filtrado por dono; demais recebem 404 `SERVICO_NAO_ENCONTRADO`. |
| Só serviço combinado | `PUBLICADO` com `ESCOLHIDA`; sem escolhida, `CONCLUIDO` ou `ENCERRADO`: 409 `SERVICO_ESTADO_INVALIDO`. |
| Em andamento ou aguardando validação | 409 `USE_DENUNCIA` (`UseDenunciaException`). |
| Antecedência mínima de 24h | `Anuncio.temAntecedenciaDe`; senão 409 `PRAZO_CANCELAMENTO_ESGOTADO`. |
| Cancelamento tardio na carência | `TipoCancelamento.TARDIO`, sem estorno, com auditoria. |
| Estorno da cota FREE (RB17) | `ConsumoCotaPrestador.estornarEscolha`, só se `cota_consumida` e `NORMAL`. |
| Chat somente leitura | `aoCancelarServico` após o commit. |
| Publica `ServicoCancelado` | `ServicoCanceladoEvent`, só em operação bem-sucedida. |

## Contrato da API

| Operação | Rota | Entrada | Saída |
|---|---|---|---|
| Cancelar serviço combinado | `POST /anuncios/{anuncioId}/servico/cancelar` | só o path | 200 `{ "anuncioId": "<uuid>", "status": "ENCERRADO", "tipoCancelamento": "NORMAL\|TARDIO", "cotaEstornada": true\|false }` |

Erros: 404 `SERVICO_NAO_ENCONTRADO`, 409 `PRAZO_CANCELAMENTO_ESGOTADO`, 409 `USE_DENUNCIA`, 409
`SERVICO_ESTADO_INVALIDO`, 401 sem token. `CodigoErro` novos: `PRAZO_CANCELAMENTO_ESGOTADO` e `USE_DENUNCIA`.

## Pendências

- Nenhum fluxo cria ou aceita reagendamento (ETI-99/100): o ramo `TARDIO` é testado com reagendamento semeado no
  banco e precisa continuar coerente quando esses cards gravarem `respondidoEm` e `ACEITO`.
- As constantes de 24h seguem duplicadas em `ConsultarServicoUseCase` e `EncerrarAnunciosEAplicacoesUseCase`.
- Notificação ao Prestador: ETI-112 (o evento já é publicado).

## Testes

`spec/CancelarServicoCombinadoUseCaseTest`: normal, menos de 24h e exatamente 24h, carência (dentro, fora e com
menos de 24h), GOLD e FREE na escolha, proposta pendente, `EM_ANDAMENTO` e `AGUARDANDO_VALIDACAO`, não-dono, repetido
e ordem das travas.

`spec/CancelamentoServicoDominioTest`: transições de `Anuncio` e `Aplicacao`.

`story/CancelarServicoCombinadoStoryTest`: caminho feliz com banco, chat somente leitura e contato mantido, 24h,
carência com auditoria, cota (GOLD, mês diferente, vaga liberada), 404, 401, cancelamento repetido, dois
cancelamentos simultâneos (`[200, 409]` e estorno uma vez), rollback se o salvamento falha e falha do listener do
chat depois do commit.

Também: `CotaMensal.estornarEscolha`, `ConsumoCotaPrestadorService`, `LogAuditoriaServiceTest` (detalhes JSON) e
`DeixarChatSomenteLeituraUseCase.aoCancelarServico`.
