# ETI-66 — Expirar anúncio sem candidatos

## Status

Implementado.

## Link do card

ETI-66 — [anuncio] [BACK-END] Expirar anúncio sem candidatos. Origem: RF30, RB09 e UC22 (sem link do Jira neste
repositório).

## User Story

Como plataforma, eu quero retirar do mural anúncios vencidos e sem candidatos, para que o mural mostre somente
demandas reais.

## Escopo implementado

Uma rotina periódica encerra por expiração o anúncio que está `PUBLICADO`, com data/hora pretendida já passada e
**nenhuma candidatura** em qualquer estado. O anúncio vai para `ENCERRADO` com `encerradoEm` preenchido e
`motivoEncerramento = "Expirado sem candidatos"`, e `AnuncioExpiradoEvent` é publicado. Sem migration.

Componentes, todos em `modulos/anuncio`:

- `Anuncio.permiteExpiracao(existeAplicacao, agora)` e `Anuncio.expirar(agora)`.
- `AnuncioRepository.buscarIdsParaExpirar(agora, lote)`: pré-seleção por `NOT EXISTS` em `aplicacao`.
- `AplicacaoService.existeAlguma(anuncioId)` (`existsByAnuncioId`).
- `ExpirarAnunciosSemCandidatosUseCase`: o job (`@Scheduled`), sem transação.
- `ExpirarAnuncioUseCase`: expira um anúncio, em transação própria, e publica o evento.
- `comum/eventos/AnuncioExpiradoEvent(anuncioId, contratanteId, titulo, ocorridoEm)`.

Propriedades: `anuncio.expiracao.cron` (padrão `0 */5 * * * *`) e `anuncio.expiracao.lote` (padrão 100).

**Fora**: o aviso ao Empregador (US56/RF53, só o evento é publicado aqui), qualquer listener do evento, estorno ou
consumo de cota, mudança no mural ou no histórico, status novo.

## Decisões

- **Não existe status `EXPIRADO`: é `ENCERRADO` com motivo.** A spec fala em `AguardandoCandidatos` e `Expirado`; o
  código tem `PUBLICADO` e `ENCERRADO`. É diferença de nomenclatura, não de modelo. A decisão do ETI-63 (um único
  `ENCERRADO` mais `motivoEncerramento` em texto livre) já previa a expiração, então nada mudou em `StatusAnuncio` e
  os `switch` sem `default` seguem intactos. Consequência: o front só distingue cancelado de expirado pelo texto do
  motivo; um enum de motivo fica para um card futuro.
- **"Combinado" não muda o anúncio.** A escolha só muda a `Aplicacao` (`ESCOLHIDA`); o anúncio segue `PUBLICADO`.
  Por isso a regra é "nenhuma candidatura em qualquer estado": um anúncio com escolhida tem candidatura e nunca
  expira. Anúncio `CONCLUIDO`, `EM_ANDAMENTO`, `AGUARDANDO_VALIDACAO` ou `ENCERRADO` também não.
- **Regra no domínio, decisão sob trava.** `permiteExpiracao` concentra a regra (`switch` sem `default`). A query do
  job é só uma pré-seleção barata; quem decide é `ExpirarAnuncioUseCase`, depois de travar o anúncio.
- **Job e usecase em beans separados.** A transação e o evento `AFTER_COMMIT` dependem de a chamada passar pelo
  proxy do Spring; uma chamada de dentro do mesmo bean não passa. O job segue o único precedente do projeto
  (`LimparRetencaoUseCase`: `@Scheduled` num usecase, `try/catch` por item). Não há pasta `job/`.
- **Uma transação por anúncio.** A falha de um é logada e não desfaz nem interrompe os demais.
- **`NOT EXISTS` na query, `existsByAnuncioId` na conferência.** A query nativa segue o padrão do mural (uma ida ao
  banco, sem N+1; `Aplicacao` não é relação JPA, ADR-0001 §1.6). A conferência sob trava usa a consulta derivada,
  em qualquer estado (o `existeEscolhida` existente só olharia as escolhidas).
- **Lote.** Até `anuncio.expiracao.lote` por execução, do mais antigo ao mais novo. Se sobrar, a próxima execução
  continua.
- **Horário da aplicação, via `Clock`.** O job usa o bean `Clock` (UTC, `RelogioConfig`) e compara `Instant` com
  `TIMESTAMPTZ`; o fuso `America/Sao_Paulo` não entra, porque a regra é um instante e não um dia civil. Data/hora
  pretendida igual ao agora já vale como vencida.
- **Evento em `comum/eventos/`.** Leva `contratanteId` e `titulo` soltos, como os demais, para o `notificacao` não
  importar `anuncio`. É publicado dentro da transação e só é entregue após o commit (ADR-0003). Nenhum listener
  existe até a US56. Não há chat a fechar: sem candidatura não houve conversa.
- **Cota intocada.** O usecase não depende da porta `ConsumoCotaPrestador`: sem escolha nunca houve consumo, e não
  há o que devolver.
- **Sem lock de job (ShedLock).** O projeto não tem e o advisory lock por anúncio basta (ver Concorrência). Com
  várias instâncias, duas podem pré-selecionar o mesmo anúncio, mas só uma expira.
- **Sem índice novo.** `idx_anuncio_mural_*` (V25) não serve a esta consulta. O volume esperado não justifica
  uma V32 agora; reavaliar se a varredura ficar lenta.
- **Cron desligado nos testes.** `src/test/resources/config/application.properties` define
  `anuncio.expiracao.cron=-`, para o job não rodar sozinho nas outras stories.

## Regra de negócio

| Critério | Implementação |
|---|---|
| Só `PUBLICADO` (`AguardandoCandidatos`) | `Anuncio.permiteExpiracao`, `switch` sem `default`. |
| Data/hora pretendida já passou | `!dataHoraPretendida.isAfter(agora)`; futuro não expira. |
| Nenhuma candidatura, em qualquer estado | `NOT EXISTS` na query e `AplicacaoService.existeAlguma` sob trava. |
| Encerra por expiração | `Anuncio.expirar` grava `ENCERRADO`, `encerradoEm` e o motivo. |
| Sai do mural | O mural filtra `status = 'PUBLICADO'`; nenhuma alteração nele. |
| Não mexe na cota | O usecase não conhece a porta de cota. |
| Notifica o Empregador | `AnuncioExpiradoEvent`; o envio é da US56. |
| Não reprocessa | Fora de `PUBLICADO` não elegível; a re-checagem sob trava barra a segunda tentativa. |

## Concorrência

`ExpirarAnuncioUseCase` toma `EscopoTrava.ANUNCIO` (advisory lock por id, solto no fim da transação) antes de ler o
anúncio, como cancelar, escolher e candidatar-se (esta já trava `ANUNCIO` antes de criar a candidatura). Quem chega
depois vê o resultado da primeira e sai: dois jobs simultâneos geram um encerramento e um evento; job contra
cancelamento, um vencedor só; job contra candidatura, ou o anúncio expira sem candidatura ou a candidatura vence e ele
não expira.

## Contrato

Sem endpoint. Mensagem de motivo: `"Expirado sem candidatos"`. Evento: `AnuncioExpiradoEvent(anuncioId,
contratanteId, titulo, ocorridoEm)`.

## Para a US56

Falta, no `notificacao`: `TipoNotificacao`, entidade e migration de notificação, listener
`@TransactionalEventListener(AFTER_COMMIT)` com `@Transactional(REQUIRES_NEW)`, respeito a `PreferenciaNotificacao` e
escolha do canal. Nada disso existe hoje.

## Testes

`spec/ExpiracaoDominioTest`: `permiteExpiracao` (vencido, data igual ao agora, futuro, com candidatura, demais
status) e `expirar`.

`spec/ExpirarAnuncioUseCaseTest`: expira, salva e publica um evento (ordem trava, leitura, gravação, evento), com
candidatura, no futuro, já encerrado e inexistente.

`spec/ExpirarAnunciosSemCandidatosUseCaseTest`: instante do `Clock`, lote vazio e falha em um item sem derrubar os
demais.

`story/ExpirarAnunciosStoryTest` (banco real): expira com um evento; candidatura em cada `StatusAplicacao`; data
futura; `EM_ANDAMENTO`, `AGUARDANDO_VALIDACAO`, `CONCLUIDO` e `ENCERRADO` intocados; job duas vezes; vários
elegíveis; rollback sem evento; dois jobs em paralelo; job contra `CancelarAnuncioUseCase` em paralelo. Não há
teste de corrida com candidatura (exigiria montar prestador e contatos); a trava é a mesma do cancelamento.
