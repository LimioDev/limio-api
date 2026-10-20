# TICKET-0049 — Encerrar anúncios, candidaturas e combinados de conta encerrada

## Status

**Planejamento — não implementado ainda.** Doc pra revisão antes de codar. Ver "Bloqueio atual" abaixo.

## Link do card

ETI-36 ([anuncio] [BACK-END] Encerrar anúncios, candidaturas e combinados de conta encerrada). UC16.
Relacionado: [IAM] Excluir conta, anonimização e regras de retenção — o card raiz do LGPD
(`auth/docs/features/TICKET-0034-encerrar-conta-anonimizacao-e-retencao.md`), que já publica o evento
que este card consome.

Numeração `TICKET-0049` é a próxima livre na sequência do projeto (0048 foi `perfil`/trocar e-mail);
ninguém confirmou um ETI específico pra este card ainda — ajustar se vier um número oficial.

## User Story

Como sistema, preciso encerrar os compromissos abertos de uma conta excluída, pra que a outra parte não
fique esperando por alguém que saiu da plataforma.

## Bloqueio atual

`ContaEncerradaEvent` (publicado por `EncerrarContaUseCase`, TICKET-0034) **ainda não existe em
`develop`** — está só na branch `feature/36` (PR #7, aberto, não mergeado). Este card não compila/roda
de verdade até esse PR ser mergeado. A branch `feature/anuncio-encerrar-conta` foi criada a partir da
`develop` atual (sem o PR #7) por instrução explícita do card — só o planejamento (este doc) avança
por enquanto; o código do listener espera o merge.

## Escopo deste card

Só o que o gatilho "conta encerrada" precisa mexer em `anuncio`. **Fora**: `Contestacao` e
`NaoComparecimento` não aparecem nos critérios de aceite — não criadas aqui (ver "Fora de escopo").
`Avaliacao` não entra (anonimização já acontece sozinha via `Usuario`, ver análise na conversa do
card). `Chat`/`Mensagem` é card separado (TICKET-0050, abaixo). `PerfilEmpregador.nomeExibicao`
também é card separado, pequeno (ver "Relacionado — achado novo").

## Entidades a criar (primeira implementação de `anuncio` — nada existe em código ainda)

Campos confirmados no diagrama de classes (não inventados):

### `Anuncio` (agregado raiz)
```
descricao: String
dataHoraPretendida: Instant
status: StatusAnuncio
bairroRegiaoSnapshot: String
enderecoCompletoSnapshot: String
latitudeSnapshot: double
longitudeSnapshot: double
tamanhoLocal: String
numeroPessoas: int
observacoes: String
prazoValidacaoHoras: int
iniciadoEm: Instant
checkoutEm: Instant
validadoEm: Instant
encerradoEm: Instant          <- este card usa
+ relação própria "anuncioOrigem" (0..1) — reagendamento/republicação, não detalhado aqui
+ contratanteId: UUID (raw, sem relação JPA — ADR-0001 §1.6)
```

### `Aplicacao` (candidatura de prestador, subentidade de `anuncio`)
```
status: StatusAplicacao
dataAplicacao: Instant
escolhidaEm: Instant
recusadaEm: Instant
encerradaEm: Instant          <- este card usa
motivoEncerramento: String    <- este card grava "Cancelado por saída de conta" aqui
+ anuncioId: UUID, prestadorId: UUID (raw)
```

### `Reagendamento` (subentidade de `anuncio`)
```
dataAnterior: Instant
dataProposta: Instant
status: StatusReagendamento
justificativa: String
respondidoEm: Instant
```

### `ValorCombinado` (subentidade de `anuncio`)
```
valorCentavos: int
informadoEm: Instant
```

## Enums novos

```java
// modulos/anuncio/enums/StatusAnuncio.java
public enum StatusAnuncio {
    PUBLICADO,             // aberto no mural
    EM_ANDAMENTO,          // iniciadoEm
    AGUARDANDO_VALIDACAO,  // checkoutEm preenchido, validadoEm ainda não
    CONCLUIDO,             // validadoEm
    ENCERRADO              // encerradoEm — valor que este card usa
}

// modulos/anuncio/enums/StatusAplicacao.java
public enum StatusAplicacao {
    ENVIADA,                // dataAplicacao
    ESCOLHIDA,              // escolhidaEm
    RECUSADA,               // recusadaEm
    AGUARDANDO_VALIDACAO,   // serviço feito, esperando confirmação do contratante
    CONCLUIDA,
    ENCERRADA               // encerradaEm + motivoEncerramento — valor que este card usa
}

// modulos/anuncio/enums/StatusReagendamento.java — já previsto no diagrama, valores não listados
// (nota do próprio diagrama: "a definir pelo grupo"). Mínimo pra este card:
public enum StatusReagendamento {
    PENDENTE,
    ACEITO,
    RECUSADO,
    CANCELADO   // este card: proposta pendente quando a conta encerra
}
```

`StatusAnuncio`/`StatusAplicacao` sem valores no diagrama — mesma nota de sempre ("Enumerações sem
valores listados... a definir pelo grupo"). Os dois enums acima foram derivados dos próprios campos
`*Em`/`motivoEncerramento` da entity — cada marco de timestamp vira um valor, sem inventar estado sem
rastro no diagrama.

## Regra de negócio (por critério do card)

| Critério do card | Implementação |
|---|---|
| Anúncios abertos: encerrados e retirados do mural | `Anuncio` com `status` em `{PUBLICADO, EM_ANDAMENTO, AGUARDANDO_VALIDACAO}` do usuário encerrado → `status=ENCERRADO`, `encerradoEm=agora`. "Retirado do mural" é consequência do status mudar — nenhuma coluna nova (quem lista o mural já filtra por `status=PUBLICADO`). |
| Candidaturas em andamento: encerradas | `Aplicacao` com `status` em `{ENVIADA, ESCOLHIDA}` do prestador encerrado → `status=ENCERRADA`, `encerradaEm=agora`, `motivoEncerramento="Cancelado por saída de conta"`. |
| AguardandoValidacao: vira "Cancelado por saída de conta", sem conclusão por silêncio | `Aplicacao.status=AGUARDANDO_VALIDACAO` do usuário encerrado (contratante ou prestador) → mesma transição acima, em vez de esperar o prazo de auto-conclusão por silêncio seguir seu curso normal. |
| Propostas de reagendamento pendentes: encerradas sem efeito | `Reagendamento.status=PENDENTE` ligado a um `Anuncio`/`Aplicacao` do usuário encerrado → `status=CANCELADO`, `respondidoEm=agora`. |
| Combinado <24h: cancelado por impossibilidade operacional, outra parte notificada, registrado em auditoria, cota não estornada | "Combinado" = `Anuncio` com `Aplicacao` ESCOLHIDA e `dataHoraPretendida` a menos de 24h de `agora`. Mesmo encerramento acima + grava em `LogAuditoria` (módulo `auth`, mesma tabela do TICKET-0034 — ver "Decisão técnica") + publica evento pra `notificacao`. "Cota não é estornada": não mexe em `CotaMensal`/`AssinaturaGold` — nenhuma lógica de estorno é criada aqui (ausência de código é a implementação). |
| A outra parte é notificada | `Anuncio`/`Aplicacao` publica evento próprio (`AnuncioEncerradoPorExclusaoEvent`, nome a confirmar) com o `usuarioId` da outra parte; `modulos/notificacao` escuta e cria a `Notificacao` (canal `PUSH`+`IN_APP`, tipo conforme o gatilho). `anuncio` não grava `Notificacao` direto — não pode importar o módulo. |

## Fluxo: listener do `ContaEncerradaEvent`

```java
// modulos/anuncio/actions/usecase/EncerrarAnunciosEAplicacoesUseCase.java (nome provisório)
@EventListener  // ou @TransactionalEventListener(phase = AFTER_COMMIT) — decidir, ver "Decisão técnica"
public void aoEncerrarConta(ContaEncerradaEvent evento) {
    UUID usuarioId = evento.usuarioId();
    // 1. Anuncio como contratante: PUBLICADO/EM_ANDAMENTO/AGUARDANDO_VALIDACAO -> ENCERRADO
    // 2. Aplicacao como prestador: ENVIADA/ESCOLHIDA/AGUARDANDO_VALIDACAO -> ENCERRADA
    // 3. Reagendamento PENDENTE dos anúncios/candidaturas acima -> CANCELADO
    // 4. Pra cada Anuncio/Aplicacao ESCOLHIDA com dataHoraPretendida < 24h: LogAuditoria + evento de notificação
    // 5. Publica evento(s) pra notificacao, por anúncio/candidatura afetada
}
```

## Banco (Flyway)

Tabelas novas: `anuncio`, `aplicacao`, `reagendamento`, `valor_combinado` — nenhuma existe ainda.
Primeira migration do módulo (`anuncio` nunca teve nenhuma até aqui).

## Decisão técnica (precisa de confirmação do tech lead)

- **`@EventListener` vs `@TransactionalEventListener(AFTER_COMMIT)`**: `EncerrarContaUseCase` publica o
  evento dentro da própria transação de `auth`. Se o listener de `anuncio` rodar síncrono na mesma
  transação (`@EventListener` padrão) e falhar, desfaz o encerramento de conta inteiro — acoplamento
  que o evento deveria evitar. Recomendo `@TransactionalEventListener(phase = AFTER_COMMIT)`: só roda
  depois que `auth` confirmou. Efeito colateral aceito: se `anuncio` falhar aqui, a conta já está
  encerrada e os anúncios ficam "presos" — precisa de uma rotina de reconciliação ou reprocessamento
  (fora de escopo deste card, listado em "Fora de escopo").
- **`LogAuditoria` é do módulo `auth`** (TICKET-0034) — `anuncio` não pode importar `modulos.auth`
  diretamente. Duas opções: (a) subir `LogAuditoria`/`LogAuditoriaService` pra `comum/` agora que um 2º
  módulo precisa dela (bate o critério do ADR-0001 "2+ módulos usando hoje"), ou (b) `anuncio` publica
  um evento genérico `AcaoAuditavelEvent` que `auth` escuta e grava. Recomendo (a) — é exatamente o
  critério que faltava pra justificar a promoção, e evita inventar mais um evento só pra isso.
- **Nome do evento publicado por `anuncio`**: `AnuncioEncerradoPorExclusaoEvent` é provisório — se
  `notificacao` for genérico o bastante, talvez valha um evento só (`AvisoNecessarioEvent` com um
  `motivo`/`tipo`) em vez de um por módulo publicador. Decisão de quem for montar `notificacao`.

## Fora de escopo (deste card)

- `Contestacao`, `NaoComparecimento`: não aparecem nos critérios de aceite, não criadas.
- Reconciliação/retry se o listener de `anuncio` falhar depois da conta já encerrada (ver "Decisão
  técnica" acima).
- `TICKET-0050` ([chat] expurgo de mídia) e o listener pequeno de `PerfilEmpregador.nomeExibicao` —
  cards próprios, não deste.
- `modulos/notificacao` em si (entity, listener que cria `Notificacao`) — pode ser o mesmo card ou um
  separado; `anuncio` só precisa publicar o evento, não depende de `notificacao` estar pronto pra
  compilar.

## Relacionado — achado novo (fora deste card, mas do mesmo problema)

`PerfilEmpregador.nomeExibicao` (TICKET-0048, já em `develop`) é campo próprio, não sincroniza sozinho
com `Usuario.nomeCompleto`. Depois que uma conta encerra, `nomeExibicao` continua mostrando o nome real
em qualquer lugar que o exiba (inclusive em `Anuncio`/`Aplicacao` se algum dia exibirem nome de
exibição em vez do `Usuario` cru). Precisa de um listener pequeno em `perfil` pro `ContaEncerradaEvent`
— card isolado, não bloqueia este aqui.

## Testes previstos (quando codar)

`spec/`: use case do listener, por cenário da tabela de regra de negócio acima.
`story/`: publicar `ContaEncerradaEvent` de verdade (ou via `POST /auth/encerrar-conta`) e conferir
`Anuncio`/`Aplicacao`/`Reagendamento` no banco depois — precisa do PR #7 mergeado pra rodar.
