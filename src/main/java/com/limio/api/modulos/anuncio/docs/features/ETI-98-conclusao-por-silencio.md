# ETI-98 — Rotina de conclusão automática por silêncio

## Status

Implementado. Sem migration e sem rota nova.

## Link do card

ETI-98 — [anuncio] [BACK-END] Rotina de conclusão automática por silêncio (sem link do Jira neste repositório).

## Regra de negócio

O serviço em que ninguém age é concluído sozinho, para não ficar aberto para sempre e para as duas partes poderem se
avaliar.

| Caso | Status | Conta o prazo a partir de | Prazo |
|---|---|---|---|
| A: ninguém fez check-out nem validou | `PUBLICADO` ou `EM_ANDAMENTO` | `dataHoraPretendida` (a data vigente) | `anuncio.prazo-pos-agenda-horas` (24) |
| B: check-out sem validação | `AGUARDANDO_VALIDACAO` | `checkoutEm` | `prazoValidacaoHoras` do anúncio; se nulo, `anuncio.prazo-validacao-horas` (24) |

O limite é inclusivo (`prazo <= agora`). Nos dois casos é preciso ter Prestador escolhido com a candidatura vigente
(`ESCOLHIDA` ou `AGUARDANDO_VALIDACAO`).

Nunca é concluído:

- anúncio sem escolhido, `CONCLUIDO` ou `ENCERRADO`;
- serviço dentro do prazo;
- serviço com proposta de reagendamento `PENDENTE`, mesmo já vencida e ainda não expirada pelo job da ETI-99;
- serviço em que uma das partes encerrou a conta e o anúncio sobrou aberto (o encerramento é do TICKET-0049).

Proposta recusada, cancelada ou expirada não muda nada: vale a data original. Aceita, `dataHoraPretendida` já é a nova.

## O que a conclusão faz

- `status = CONCLUIDO`, com `validadoEm` (validação tácita) e `encerradoEm` iguais ao instante da execução.
- Auditoria `SERVICO_CONCLUIDO_POR_SILENCIO` (`entidade = "Anuncio"`, sem IP), com `statusAnterior`, `contratanteId`,
  `prestadorId`, `dataVigente` e `checkoutEm`.
- `ServicoConcluidoEvent` com `origem = SILENCIO`; `notificacao` avisa as duas partes.
- A avaliação abre sem passo extra: `moderacao` lê `concluidoEm` (`validadoEm`) pela porta `ConsultaServico`.

## Configuração

| Propriedade | Variável | Padrão | Papel |
|---|---|---|---|
| `anuncio.prazo-pos-agenda-horas` | `ANUNCIO_PRAZO_POS_AGENDA_HORAS` | 24 | prazo do caso A |
| `anuncio.conclusao-silencio.cron` | `ANUNCIO_CONCLUSAO_SILENCIO_CRON` | `0 */5 * * * *` | cron da rotina (`-` desliga; é o usado nos testes) |
| `anuncio.conclusao-silencio.lote` | `ANUNCIO_CONCLUSAO_SILENCIO_LOTE` | 100 | serviços concluídos por execução, no máximo; também é o tamanho de cada página da pré-seleção |

**Primeira subida em produção:** todo serviço antigo já vencido passa a ser concluído, um lote por execução, com aviso
às duas partes e a janela de avaliação contando a partir dali. Para segurar, subir com o cron em `-`.

## Decisões

- **Job no molde da ETI-99 e do ETI-66:** `@Scheduled` no próprio `ConcluirServicosPorSilencioUseCase`, pré-seleção de
  ids por query nativa em lote, `TransactionTemplate` por anúncio e falha por item só logada. Sem ShedLock.
- **A regra de prazo e status é do `Anuncio`** (`permiteConclusaoPorSilencio`, `concluirPorSilencio`), não da rotina.
- **Trava `ANUNCIO` e releitura.** A query só escolhe quem olhar; a decisão é refeita sob a trava com o estado atual.
  Isso cobre duas instâncias e as corridas com validação, check-out, cancelamento e reagendamento, e torna a rotina
  idempotente. Todo escritor novo do anúncio precisa tomar a mesma trava (não há `@Version`).
- **A rotina avança além do que não concluiu.** Anúncio de conta encerrada ou com falha continua na pré-seleção; a
  busca seguinte o pula (`OFFSET`), para ele não travar os mais novos. Filtrar a conta encerrada na SQL exigiria o
  `anuncio` ler a tabela do `auth`.
- **A query repete os filtros de candidatura e de proposta pendente** para anúncios que nunca serão concluídos não
  ocuparem o lote.
- **A origem vai no evento** (`comum/eventos/enums/OrigemConclusao`), reaproveitando o `ServicoConcluidoEvent` em vez
  de criar outro. Não há coluna de origem: o registro persistente é a auditoria.
- **`encerradoEm` é gravado aqui** porque o card pede. A validação manual (ETI-92) continua sem preencher.
- **A candidatura não muda de status**, como na validação manual.

## Limites conhecidos

- Conta suspensa pela moderação não bloqueia a conclusão; só a encerrada.
- `auth` não toma a trava `ANUNCIO`: uma conta encerrada entre a checagem e o commit deixa o serviço concluído.
- Anúncio aberto de conta encerrada continua sendo pré-selecionado e relido a cada execução (aparece como
  `log.warn`). Não bloqueia os demais, mas o custo da execução cresce com a quantidade deles.
- O aviso pode se perder se a aplicação cair entre o commit e o listener (`notificacao` não tem outbox).
- A consulta não tem índice próprio.

## A confirmar

- Texto do aviso por silêncio: "'(anúncio)' foi concluído automaticamente. Avalie a outra parte." foi proposto na
  implementação.
- O card cita "ETI-46" para conta encerrada; foi lido como o fluxo do TICKET-0049.

## Testes

- `spec/ConclusaoPorSilencioDominioTest`: status, limites de prazo, data reagendada, campos gravados.
- `spec/ConcluirServicosPorSilencioUseCaseTest`: ordem trava, leitura, gravação e evento; recusas sem efeito; falha de
  um item não para os outros.
- `story/ConcluirServicosPorSilencioStoryTest` (Postgres real): os cenários do card, idempotência, lote, rollback e
  avaliação disponível.
- `story/ConclusaoPorSilencioConcorrenciaStoryTest`: disputa com outra execução, validação, check-out, cancelamento
  pelo Prestador e proposta, nas duas ordens de chegada na trava; e com o aceite, em largada simultânea.
