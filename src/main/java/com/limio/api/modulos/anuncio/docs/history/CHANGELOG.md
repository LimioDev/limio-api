# Histórico — anuncio

Registre aqui, em ordem cronológica, decisões e mudanças de regra que não ficam óbvias só lendo o código.

Formato de cada entrada: novo arquivo `AAAA-MM-titulo-curto.md` nesta pasta, com contexto do "porquê" da mudança.

## 2026-10-20 — TICKET-0049: primeira implementação do módulo, encerramento por exclusão de conta

Esqueleto (`package-info.java`, TICKET-0044) ganhou código pela primeira vez. `Anuncio` (agregado raiz),
`Aplicacao` (candidatura, subentidade) e `Reagendamento` (subentidade) — só os campos que o gatilho de
encerramento de conta usa, não o agregado completo (`ValorCombinado`/`Contestacao`/`NaoComparecimento`
ficam pros cards que de fato precisarem deles). Migration `V11__criar_tabelas_anuncio_aplicacao_reagendamento.sql`.

`EncerrarAnunciosEAplicacoesUseCase` escuta `ContaEncerradaEvent` (publicado por `auth`, TICKET-0034) via
`@TransactionalEventListener(phase = AFTER_COMMIT)` + `@Transactional(propagation = REQUIRES_NEW)` — a
combinação com propagação padrão (REQUIRED) faz o Spring recusar subir o contexto, porque não existe
transação ativa pra participar no momento do AFTER_COMMIT (erro descoberto rodando o story test).

Usuário que encerra a conta pode ser contratante (anúncios dele encerram) e/ou prestador (candidaturas
dele encerram); se a candidatura encerrada era a `ESCOLHIDA`, o anúncio ligado também encerra, estando a
data combinada perto ou longe — só o tom do aviso/auditoria muda com a janela de 24h ("impossibilidade
operacional" vs. "candidatura encerrada"), não se o anúncio encerra ou não. O card só descreve
explicitamente o caso <24h; o caso >24h foi decidido por analogia (não faz sentido deixar `EM_ANDAMENTO`
com um prestador que não existe mais).

`LogAuditoria`/`TipoAcaoAuditoria` (TICKET-0034, estavam em `modulos.auth.auditoria`) promovidos pra
`comum/auditoria` — segundo módulo (`anuncio`) passou a gravar, batendo o critério do ADR-0001 de subir
pra `comum/` ("2+ módulos usando hoje"). Ganhou `TipoAcaoAuditoria.COMBINADO_CANCELADO`.

Evento de aviso pra outra parte: `comum/eventos/AvisoDeAnuncioEvent(destinatarioId, anuncioId, assunto,
ocorridoEm)` — `assunto` é texto livre em vez de um valor de `TipoNotificacao`, que é enum de
`modulos.notificacao` e `anuncio` não pode importar (ADR-0001). Ninguém escuta ainda; `notificacao` não
existe em código.

Detalhe completo da regra, tabela critério-por-critério e decisões descartadas em
`docs/features/TICKET-0049-encerrar-anuncios-candidaturas-e-combinados.md`.
