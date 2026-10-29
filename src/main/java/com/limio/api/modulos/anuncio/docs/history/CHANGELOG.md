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

## 2026-10-20 — TICKET-0049: correções de review (PR #11)

Achados de code review no próprio PR, corrigidos antes do merge:

- `encerrarAnuncioDoContratante` encerrava o `Anuncio` e avisava o prestador escolhido mas nunca
  fechava a `Aplicacao` dele — ficava `ESCOLHIDA` presa num anúncio já `ENCERRADO`. Agora fecha os dois
  lados, simétrico com `encerrarAplicacaoDoPrestador`.
- `Anuncio.estaAMenosDe` não checava se `dataHoraPretendida` já tinha passado — anúncio com data
  vencida caía em "combinado <24h" (impossibilidade operacional) por engano. Adicionado
  `agora.isBefore(dataHoraPretendida)`.
- `latitudeSnapshot`/`longitudeSnapshot`/`numeroPessoas`/`prazoValidacaoHoras` eram `double`/`int`
  mapeando colunas sem `NOT NULL` — um NULL futuro (campos ainda não populados por nenhum usecase)
  quebraria a hidratação do Hibernate. Trocados pra `Double`/`Integer`.
- `encerrarAplicacaoDoPrestador` buscava o `Anuncio` antes de checar `eraEscolhida` — query
  desperdiçada pra toda `Aplicacao` `ENVIADA`/`AGUARDANDO_VALIDACAO`. Reordenado.
- `Aplicacao.isAberta()` era código morto (zero chamadas, duplicava a lista de status de
  `AplicacaoService` sem reforço do compilador) — removido.
- Branching `combinadoProximo ? auditoria+aviso : aviso` duplicado entre os dois métodos privados —
  extraído pra `avisarEncerramento`.

## 2026-10-21 — TICKET-0049: correções de review (PR #12)

Achados de code review no PR #12, corrigidos antes do merge:

- `encerrarAnuncioDoContratante` só fechava/avisava a candidatura `ESCOLHIDA` do anúncio — `ENVIADA` e
  `AGUARDANDO_VALIDACAO` ficavam penduradas num anúncio já `ENCERRADO`, sem aviso. Mesmo problema do
  outro lado: `encerrarAplicacaoDoPrestador` só fechava a candidatura do próprio usuário, não as outras
  `ENVIADA` do anúncio que ele fechava como escolhido. Unificado: ambos os caminhos agora buscam e fecham
  **todas** as candidaturas abertas do anúncio (`AplicacaoService.buscarAbertasDoAnuncio(s)`), não só a
  escolhida.
- `AGUARDANDO_VALIDACAO` (candidatura pós-checkout aguardando o contratante validar) não recebia o
  mesmo tratamento que `ESCOLHIDA` em nenhum dos dois sentidos — nem fechava o anúncio ligado quando o
  prestador saía, nem era fechada quando o contratante saía. `fechaAnuncioLigado` e as buscas de
  "abertas" agora tratam os dois estados igual, como a doc do ticket já previa.
- `Anuncio.estaAMenosDe` exigia `status == EM_ANDAMENTO`, mas esse status só é setado no check-in —
  que acontece depois de `dataHoraPretendida`. Resultado: a condição nunca era satisfeita, e "combinado
  <24h" nunca disparava. Removida a dependência do status; a janela agora é só sobre a data.
- Uma exceção dentro do listener `AFTER_COMMIT`/`REQUIRES_NEW` (ex.: `anuncio_id` sem FK apontando pra
  linha inexistente) desfazia o encerramento inteiro do usuário, sem reconciliação. Agora cada
  anúncio/candidatura processa isolado (`processarComIsolamento`, try/catch + log), e a busca de anúncio
  ligado não lança mais (`AnuncioService.buscarPorId`, `Optional`) — um item ruim não derruba os outros.
- Nada garantia no máximo uma `ESCOLHIDA` por anúncio no banco. Adicionado índice único parcial
  (`idx_aplicacao_escolhida_unica`) na migration.
- N+1: a busca de candidaturas abertas e de reagendamentos pendentes rodava por anúncio. Batched pra 1
  query cada, cobrindo todos os anúncios abertos do contratante de uma vez
  (`buscarAbertasDosAnuncios`/`buscarPendentesDosAnuncios` com a lista inteira de ids).
- `StatusAnuncio.isAberto()` central no enum — `Anuncio.isAberto()` e `AnuncioService.STATUS_ABERTOS`
  agora derivam da mesma fonte, em vez de duas listas mantidas à mão.
- Migration renumerada de `V11` pra `V13`: `V11` colide com a #9 (`TICKET-0054`, ainda aberta) e `V12`
  já é da #10 — nenhuma das duas tinha mergeado ainda, então `V13` é o próximo número livre sem
  depender da ordem de merge entre elas.

## 2026-10-22 — TICKET-0052: painel "Início" do Empregador

Primeiro endpoint HTTP do módulo: `GET /anuncios/painel-empregador`. Nome de exibição e foto vêm de `perfil`
pela porta nova `comum/portas/ConsultaPerfilEmpregador`; nome do prestador escolhido pela `ConsultaDadosUsuario`,
abreviado ("Rafael S."). Decisões tomadas neste card (o card não definia): "anúncios abertos" = `StatusAnuncio.isAberto()`, "novos
candidatos" = candidaturas `ENVIADA`, foto do anúncio `null` até existir o card de criar anúncio. Detalhe em
`docs/features/TICKET-0052-listar-anuncios-do-empregador-para-o-painel.md`.

## 2026-10-27 — ETI-59: criar e publicar anúncio, com fotos

Primeiro card que cria anúncio pela API: `POST /anuncios`, `POST /anuncios/{id}/fotos` e `GET /anuncios/{id}`.
Categoria, endereço salvo e geocodificação do endereço avulso vêm de `perfil` pelas portas novas
`ConsultaCategoria`/`ConsultaEndereco`; a mídia subiu pra `comum/midia` (ver o CHANGELOG de `perfil`). Migrations
`V20` (categoria e endereço salvo no anúncio, `anuncio_id` na mídia) e `V21` (retrato da UF). O título (5 a 80) usa a coluna da V18 (PR #17).
`StatusAplicacao.isEscolhida()` virou a fonte única de "prestador escolhido" (`Aplicacao.isEscolhida()` delega). Contrato alinhado ao front-end par (limio-app#17): `enderecoAvulso`, fotos
respondidas como lista, `categoria.grupo` e `uf` na consulta. Detalhe
em `docs/features/TICKET-0059-criar-e-publicar-anuncio-com-fotos.md`.

## 2026-10-29 — ETI-101 (review da PR #21): foto dos candidatos pela porta `ConsultaFotoPerfil`

`ConsultarMeuAnuncioUseCase` busca a foto de perfil dos primeiros candidatos pela porta `ConsultaFotoPerfil` (de
`perfil`), em vez de usar `MidiaService` e `ArmazenamentoService` direto. A mesma lógica deixa de existir em dois
módulos. As fotos do anúncio continuam pelo `comum/midia`. `ConsultaCandidaturaService` passou a mandar
`anuncioId`, `contratanteId` e `prestadorId` no `ResumoCandidatura`, para o chat criar o chat que faltar.
