# TICKET-0049 — Encerrar anúncios, candidaturas e combinados de conta encerrada

## Status

Implementado.

## Link do card

ETI-36 ([anuncio] [BACK-END] Encerrar anúncios, candidaturas e combinados de conta encerrada). UC16.
Relacionado: [IAM] Excluir conta, anonimização e regras de retenção — o card raiz do LGPD
(`auth/docs/features/TICKET-0034-encerrar-conta-anonimizacao-e-retencao.md`), que publica o evento que
este card consome.

Numeração `TICKET-0049` é a próxima livre na sequência do projeto (0048 foi `perfil`/trocar e-mail);
ninguém confirmou um ETI específico pra este card — ajustar se vier um número oficial.

## User Story

Como sistema, preciso encerrar os compromissos abertos de uma conta excluída, pra que a outra parte não
fique esperando por alguém que saiu da plataforma.

## Escopo implementado

Primeira implementação de `anuncio` (nada existia em código antes deste card). `Anuncio`, `Aplicacao` e
`Reagendamento` — campos batendo com o diagrama de classes, só os necessários pro gatilho de
encerramento (não é o agregado completo do módulo; ganha mais campo/regra nos próximos cards que
*criam* anúncio/candidatura).

**Fora**: `ValorCombinado`, `Contestacao`, `NaoComparecimento` — não aparecem em nenhum critério de
aceite deste card, não criadas (ver "Fora de escopo"). `Avaliacao` não entra — anonimização já
acontece sozinha via `Usuario`. `Chat`/`Mensagem` e o listener de `PerfilEmpregador.nomeExibicao` são
cards próprios (ver "Relacionado").

## Entidades

### `Anuncio` (agregado raiz, `modulos/anuncio/Anuncio.java`)
`titulo`, `descricao`, `dataHoraPretendida`, `status: StatusAnuncio`, `bairroRegiaoSnapshot`,
`enderecoCompletoSnapshot`, `latitudeSnapshot`, `longitudeSnapshot`, `tamanhoLocal`, `numeroPessoas`,
`observacoes`, `prazoValidacaoHoras`, `iniciadoEm`, `checkoutEm`, `validadoEm`, `encerradoEm` (usado
por este card), `contratanteId`/`anuncioOrigemId: UUID` (colunas cruas, ADR-0001 §1.6).

### `Aplicacao` (`modulos/anuncio/aplicacao/Aplicacao.java`, subentidade)
`status: StatusAplicacao`, `dataAplicacao`, `escolhidaEm`, `recusadaEm`, `encerradaEm` (usado),
`motivoEncerramento` (usado — grava `"Cancelado por saída de conta"`), `anuncioId`/`prestadorId: UUID`.

### `Reagendamento` (`modulos/anuncio/reagendamento/Reagendamento.java`, subentidade)
`anuncioId: UUID`, `dataAnterior`, `dataProposta`, `status: StatusReagendamento`, `justificativa`,
`respondidoEm`.

## Enums

```java
public enum StatusAnuncio { PUBLICADO, EM_ANDAMENTO, AGUARDANDO_VALIDACAO, CONCLUIDO, ENCERRADO }
public enum StatusAplicacao { ENVIADA, ESCOLHIDA, RECUSADA, AGUARDANDO_VALIDACAO, CONCLUIDA, ENCERRADA }
public enum StatusReagendamento { PENDENTE, ACEITO, RECUSADO, CANCELADO }
```

Diagrama declara os três sem valores ("a definir pelo grupo"). Cada valor acima corresponde a um
marco de timestamp que a entity já tem (`iniciadoEm`→`EM_ANDAMENTO`, `encerradoEm`→`ENCERRADO`, etc.)
— nenhum estado inventado sem rastro.

## Regra de negócio

| Critério do card | Implementação |
|---|---|
| Anúncios abertos: encerrados e retirados do mural | `Anuncio` aberto (`PUBLICADO`/`EM_ANDAMENTO`/`AGUARDANDO_VALIDACAO`) do contratante encerrado → `ENCERRADO` + `encerradoEm`. "Retirado do mural" é consequência do status — quem lista já filtra por `PUBLICADO`. |
| Candidaturas em andamento: encerradas | `Aplicacao` aberta do prestador encerrado → `ENCERRADA` + `encerradaEm` + `motivoEncerramento`. Quando o anúncio encerra pelo lado do contratante, **todas** as candidaturas abertas daquele anúncio (`ENVIADA`/`ESCOLHIDA`/`AGUARDANDO_VALIDACAO`) encerram junto, não só a escolhida — e o prestador de cada uma é avisado. |
| AguardandoValidacao: vira "Cancelado por saída de conta", sem conclusão por silêncio | Mesmo tratamento que `ESCOLHIDA` nos dois sentidos: fecha o anúncio ligado quando o prestador sai, e é fechada junto quando o contratante sai — `AGUARDANDO_VALIDACAO` está entre os status "abertos" tratados em ambos os caminhos. |
| Propostas de reagendamento pendentes: encerradas sem efeito | `Reagendamento.PENDENTE` dos anúncios afetados → `CANCELADO` + `respondidoEm`. |
| Combinado <24h: cancelado por impossibilidade operacional, outra parte notificada, registrado em auditoria, cota não estornada | "Combinado" = `Anuncio` com `Aplicacao` `ESCOLHIDA`/`AGUARDANDO_VALIDACAO` e `dataHoraPretendida` a menos de 24h **no futuro** — a checagem (`Anuncio.estaAMenosDe`) é só sobre a data, não sobre `status == EM_ANDAMENTO` (esse status só é setado no check-in, que acontece depois de `dataHoraPretendida`; exigi-lo fazia a condição nunca disparar — achado de review do PR #12). Grava `LogAuditoria` (`entidade="Anuncio"`, `tipoAcao=COMBINADO_CANCELADO`) + publica `AvisoDeAnuncioEvent` com assunto "Combinado cancelado por impossibilidade operacional". "Cota não estornada": nenhuma chamada a serviço de cota/assinatura — ausência de código é a regra. |
| A outra parte é notificada | `AvisoDeAnuncioEvent(destinatarioId, anuncioId, assunto, ocorridoEm)`, publicado sempre que um `Anuncio`/`Aplicacao` muda de estado por este fluxo. `anuncio` não grava `Notificacao` — não pode importar `modulos.notificacao`. |

### Regra adicional não-literal do card (decisão de implementação)

O card só fala em "combinado <24h" pro caso de cancelamento por impossibilidade operacional, mas não
diz o que fazer com um `Anuncio` cuja `Aplicacao` `ESCOLHIDA`/`AGUARDANDO_VALIDACAO` encerra por saída
de conta do prestador **fora** da janela de 24h. Decisão: o `Anuncio` encerra de qualquer forma (não
fica preso aberto com um prestador fantasma) — só a janela de 24h muda o *tom* do aviso/auditoria
("impossibilidade operacional" vs. "candidatura encerrada"), não se o anúncio encerra ou não.

## Fluxo: listener do `ContaEncerradaEvent`

`modulos/anuncio/actions/usecase/EncerrarAnunciosEAplicacoesUseCase.aoEncerrarConta`, processa os dois
papéis que o usuário que saiu pode ter:

1. **Contratante**: anúncios abertos dele → busca candidaturas abertas e reagendamentos pendentes em
   lote (1 query cada pra todos os anúncios, não por anúncio — evita N+1), encerra cada anúncio e
   encerra/avisa **todas** as candidaturas abertas dele (não só a escolhida).
2. **Prestador**: candidaturas abertas dele → encerra sempre; se era `ESCOLHIDA`/`AGUARDANDO_VALIDACAO`,
   o anúncio ligado também encerra (ver "Regra adicional" acima), junto com as outras candidaturas
   abertas daquele anúncio, e avisa o contratante.

Cada anúncio/candidatura de nível superior roda isolado (`processarComIsolamento`: try/catch + log) —
uma exceção num item (ex.: `anuncio_id` sem FK apontando pra linha que não existe mais) não desfaz o
que já foi processado nem impede os itens seguintes (achado de review do PR #12; antes, qualquer
exceção dentro da `REQUIRES_NEW` desfazia o encerramento inteiro do usuário sem nenhuma reconciliação).
Buscar o anúncio ligado a uma candidatura usa `AnuncioService.buscarPorId` (`Optional`), não
`buscarPorIdOuFalhar` — ausência é um cenário esperado, não excepcional.

## Decisões técnicas

- **`@TransactionalEventListener(phase = AFTER_COMMIT)` + `@Transactional(propagation =
  REQUIRES_NEW)`**: o listener só roda depois que `auth` confirma a transação de encerrar conta — uma
  falha aqui não desfaz o encerramento. `REQUIRES_NEW` é obrigatório, não só recomendado: Spring recusa
  subir o contexto com `@Transactional` padrão (REQUIRED) num método `AFTER_COMMIT`, porque nesse ponto
  não existe mais transação ativa pra "participar" — erro descoberto rodando o story test, não só
  leitura de documentação. Efeito colateral aceito: falha aqui deixa os registros deste módulo
  pendentes de reconciliação manual — fora de escopo.
- **`LogAuditoria`/`TipoAcaoAuditoria` promovidos de `modulos.auth.auditoria` pra `comum/auditoria`**:
  segundo módulo (`anuncio`) passou a gravar, batendo o critério do ADR-0001 ("2+ módulos usando
  hoje"). Ganhou `TipoAcaoAuditoria.COMBINADO_CANCELADO`.
- **Evento de aviso é só um, genérico**: `comum/eventos/AvisoDeAnuncioEvent(destinatarioId, anuncioId,
  assunto, ocorridoEm)` — `assunto` é texto livre em vez de um enum `TipoNotificacao` (que é de
  `modulos.notificacao`, `anuncio` não pode importar). Ninguém escuta ainda (`notificacao` não existe em
  código) — publicar antes do consumidor existir não quebra nada.
- **`ValorCombinado` não foi criado**: nenhum critério de aceite deste card lê/escreve nele. Construir a
  entity só porque está no diagrama como subentidade de `anuncio` seria escopo sem uso — entra no card
  que efetivamente precisar dela (ex.: o de criar/aceitar candidatura).

## Banco (Flyway)

`V13__criar_tabelas_anuncio_aplicacao_reagendamento.sql`: cria `anuncio`, `aplicacao`, `reagendamento`.
Colunas de FK pra usuário são cruas, sem `FOREIGN KEY` (ADR-0001 §1.6). Numeração `V13` porque `V11`
(#9, `TICKET-0054`) e `V12` (#10) já estavam reservadas por outras PRs abertas quando este card chegou
no review — nenhuma das duas tinha mergeado ainda, `V13` é o próximo número livre sem depender da ordem
de merge entre elas.

Inclui índice único parcial `idx_aplicacao_escolhida_unica` em `aplicacao (anuncio_id) WHERE status =
'ESCOLHIDA'` — o código sempre assumiu no máximo uma candidatura escolhida por anúncio, mas nada no
banco garantia isso antes (achado de review do PR #12).

## Fora de escopo

- `ValorCombinado`, `Contestacao`, `NaoComparecimento` (ver acima).
- Reconciliação/retry se o listener falhar depois da conta já encerrada.
- `modulos/notificacao` em si (entity `Notificacao`, listener que lê `AvisoDeAnuncioEvent`) — card
  próprio; `anuncio` só precisa publicar, não depende de `notificacao` existir pra compilar.
- Listener de `PerfilEmpregador.nomeExibicao` pro `ContaEncerradaEvent` — ver "Relacionado" abaixo,
  card isolado.

## Relacionado — achado durante este card, card próprio

`PerfilEmpregador.nomeExibicao` (TICKET-0048) é campo próprio, não sincroniza sozinho com
`Usuario.nomeCompleto`. Depois que uma conta encerra, `nomeExibicao` continua mostrando o nome real em
qualquer lugar que o exiba. Precisa de um listener pequeno em `perfil` pro `ContaEncerradaEvent` —
fora deste card.

## Testes

`spec/` (`EncerrarAnunciosEAplicacoesUseCaseTest`): 13 casos — anúncio sem candidato, com candidato
dentro/fora da janela de 24h (inclusive com o anúncio ainda `PUBLICADO`, não `EM_ANDAMENTO`, provando o
fix da detecção de combinado), reagendamento pendente cancelado, candidatura enviada (não escolhida) só
encerra a si mesma, candidatura escolhida encerra o anúncio também, candidatura escolhida com anúncio já
concluído não mexe nele de novo, anúncio com escolhida+enviada encerra e avisa as duas,
`AGUARDANDO_VALIDACAO` tratada igual `ESCOLHIDA` nos dois sentidos, prestador escolhido com outra
candidatura enviada no mesmo anúncio fecha e avisa a outra também, anúncio ligado inexistente não
derruba o processamento da candidatura, e um item que falha não impede os demais de serem processados.

`story/` (`EncerrarAnunciosEAplicacoesStoryTest`): 5 cenários ponta a ponta via `POST
/auth/encerrar-conta` real (sem usecase de "criar anúncio" ainda, as linhas são semeadas direto pelo
repository) — anúncio encerra, reagendamento cancela, `LogAuditoria` de combinado cancelado é gravada,
candidatura do prestador encerra o anúncio ligado, candidatura não escolhida não mexe no anúncio.
