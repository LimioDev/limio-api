# ETI-116 — Admin analisar denúncia e aplicar sanção

Card: [ETI-116](https://kaueamaral96.atlassian.net/browse/ETI-116) · Backlog US58 · card par: [administracao]
[FRONT-END] Área do admin (painel web). Depende do ETI-114 (registro da denúncia) e do ETI-117 (token do
administrador e regra de acesso a `/admin/**`).

## Regra de negócio

- **Só o administrador.** Usuário do app recebe 403 `ACESSO_NEGADO` nas três rotas.
- **Fila:** só as denúncias `ABERTA`, do prazo de análise mais curto para o mais longo. Cada linha traz número, motivo,
  severidade, nome do denunciado, prazo e data.
- **Severidade** sai do motivo (provisório, pendência com o PO):

  | Motivo | Severidade |
  |---|---|
  | `SUSPEITA_DE_FRAUDE`, `NAO_PAGOU_VALOR_COMBINADO` | `ALTA` |
  | `SERVICO_NAO_REALIZADO`, `COMPORTAMENTO_INADEQUADO` | `MEDIA` |
  | `OUTRO_MOTIVO` | `BAIXA` |

- **Analisar:** a denúncia como o usuário preencheu (motivo, "O que aconteceu", foto de comprovação), o alvo (perfil,
  anúncio, serviço, candidatura ou mensagem), o chat com o texto original, as fotos do check-out e o histórico do
  denunciado (outras denúncias contra ele e sanções já aplicadas). Das contas, só nome, papel e situação: telefone,
  e-mail, CPF e endereço nunca saem.
- **Decidir:** veredito e fundamento obrigatórios.

  | Veredito / sanção | Efeito |
  |---|---|
  | `IMPROCEDENTE` (sem sanção) | fecha a denúncia |
  | `ADVERTENCIA` | grava a `Sancao` |
  | `OCULTAR_AVALIACAO` + `avaliacaoId` | oculta a avaliação (`oculta_em`, `oculta_por`); a média do avaliado é recalculada sem ela |
  | `SUSPENSAO` + `inicioEm` + `fimEm` | conta `BLOQUEADA` até `fimEm`, sessões encerradas; anúncios, candidaturas e combinados abertos encerrados, e propostas de reagendamento pendentes canceladas (ETI-46) |
  | `ENCERRAMENTO_DE_CONTA` | mesma anonimização do pedido do titular (ETI-36) |
  | `removerFotoPerfil: true` (com qualquer sanção) | a foto de perfil do denunciado sai de uso e do storage |

  Toda decisão grava a `DecisaoModeracao`, tira a denúncia da fila (`PROCEDENTE` ou `IMPROCEDENTE`, `analisadaEm`) e
  grava `LogAuditoria` `DENUNCIA_DECIDIDA`. O motivo da sanção é o fundamento.
- **Dinheiro:** o administrador nunca marca o serviço como pago ou não pago e não transfere valores.

## Contrato

| Operação | Rota | Entrada | Saída |
|---|---|---|---|
| Listar fila | `GET /admin/denuncias` | `motivo`, `severidade`, `pagina` (0), `tamanho` (20, até 50), todos opcionais | `PageResponse` de `{ id, numero, motivo, severidade, denunciadoId, denunciadoNome, prazoAnalise, criadaEm }` |
| Analisar | `GET /admin/denuncias/{id}` | — | `{ id, numero, motivo, severidade, descricao, statusResolucao, prazoAnalise, criadaEm, analisadaEm, denunciante, denunciado, alvo, evidencia, fotosCheckout, chat, outrasDenuncias, sancoesAnteriores, decisao }` |
| Decidir | `POST /admin/denuncias/{id}/decisao` | `{ veredito, fundamento, sancao?, inicioEm?, fimEm?, avaliacaoId?, removerFotoPerfil? }` | `200 { id, numero, statusResolucao, analisadaEm, decisao { veredito, fundamento, administradorId, decididaEm, sancoes[] } }` |

- `denunciante`/`denunciado`: `{ id, nome, papel, situacao }`, com `situacao` em `ATIVA`, `PENDENTE_VERIFICACAO`,
  `SUSPENSA` ou `ENCERRADA`.
- `alvo`: `{ tipo, anuncioId, tituloAnuncio, candidaturaId, mensagemId }`.
- `evidencia` e cada item de `fotosCheckout`: `{ url, expiraEm }` (link temporário).
- A análise também serve para a denúncia já decidida: aí `decisao` vem preenchida.
- Erros:

  | Situação | Status | `codigo` |
  |---|---|---|
  | sem veredito, fundamento vazio ou longo; pedido incoerente (improcedente com sanção, procedente sem sanção, suspensão sem período, fim antes do início ou no passado, período fora da suspensão, avaliação fora de "ocultar", avaliação que não foi escrita pelo denunciado ou já oculta) | 400 | `VALIDACAO_FALHOU` |
  | denúncia inexistente | 404 | `DENUNCIA_NAO_ENCONTRADA` |
  | denúncia já decidida | 409 | `DENUNCIA_JA_RESOLVIDA` |
  | token de usuário do app | 403 | `ACESSO_NEGADO` |
  | sem token | 401 | `NAO_AUTENTICADO` |

  O card sugeria `DADOS_INVALIDOS`. Usei o `VALIDACAO_FALHOU`, que a API já devolve em toda validação (igual ao ETI-114).

## Decisões técnicas

- **Entidades na subentidade `denuncia/`:** `DecisaoModeracao` (uma por denúncia, `uk_decisao_moderacao_denuncia`) e
  `Sancao`, na V43, que também acrescenta `denuncia.analisada_em` e `avaliacao.oculta_em/oculta_por`. As constraints
  conferem de novo as regras de período e de avaliação.
- **Trava da denúncia:** `PESSIMISTIC_WRITE` antes de decidir. Dois administradores ao mesmo tempo: o segundo recebe 409.
- **Efeitos em outros módulos por evento (ADR-0003):**
  - `ContaSuspensaEvent`: `auth` (`SuspenderContaUseCase`) bloqueia e encerra as sessões **na mesma transação**;
    `anuncio` (`EncerrarAnunciosEAplicacoesUseCase.aoSuspenderConta`) encerra os compromissos **depois do commit**,
    pelo mesmo fluxo da saída de conta, com o motivo "Cancelado por suspensão de conta".
  - `EncerramentoDeContaDecididoEvent`: `auth` (`EncerrarContaUseCase.aoDecidirEncerramento`) faz o encerramento do
    ETI-36 na mesma transação; o `ContaEncerradaEvent` de lá leva os outros módulos a reagir depois do commit. A
    auditoria do encerramento sai com a origem `moderacao-decidir-denuncia`.
  - `FotoPerfilRemovidaPelaModeracaoEvent`: `perfil` (`RemoverFotoPerfilPelaModeracaoUseCase`) expurga a foto na
    mesma transação, e o objeto sai do storage depois do commit.
  - `AvaliacaoOcultadaEvent`: `perfil` (`RegistrarMediaAvaliacoesUseCase`) grava a média e a quantidade novas, como
    na publicação (ETI-107).
- **Fim da suspensão:** `usuario.suspensa_ate` (V44). A rotina `SuspenderContaUseCase.encerrarSuspensoesVencidas`
  (`auth.suspensao.cron`, a cada 10 minutos) volta para `ATIVA` as contas com o prazo vencido. `BLOQUEADA` sem
  `suspensa_ate` (bloqueio sem prazo, feito à mão) não é tocada. A busca traz só os ids; cada conta é travada
  (`FOR UPDATE`, a mesma trava da suspensão) e conferida de novo na própria transação (`TransactionTemplate`), como em
  `ExpirarAnunciosSemCandidatosUseCase`. Uma suspensão aplicada no meio da rotina não é desfeita, e uma falha numa
  conta não desfaz as outras.
- **Suspensão nunca encurta um bloqueio que já vale (`Usuario.suspender`):** o bloqueio sem prazo continua sem prazo, e
  sobre uma suspensão em curso fica o maior dos dois prazos.
- **Conta suspensa e depois encerrada:** `Usuario.encerrar` limpa `suspensa_ate`, e a rotina ignora conta encerrada.
  Ela não volta a `ATIVA`.
- **A suspensão começa na decisão.** A conta bloqueia na hora, então `inicioEm` no futuro é recusado (400, com 5 min
  de tolerância para o relógio do navegador) e a `Sancao` grava como início o instante da decisão, não o valor
  enviado.
- **O tipo do alvo na análise é o da hora da denúncia:** `SERVICO` só se o anúncio já tinha Prestador escolhido
  quando a denúncia foi feita (`PartesAnuncio.eraServicoEm`, pelo `escolhidaEm`). Uma denúncia feita com o anúncio no
  mural continua `ANUNCIO` e não mostra o chat do Prestador escolhido depois. O escolhido vem de `escolhidaEm`, então a
  denúncia de um serviço cancelado depois continua `SERVICO`, com o chat.
- **Avaliação oculta some de tudo:** contagem, média, avaliações recentes do perfil público e resumo dos candidatos
  filtram `oculta_em is null`.
- **Chat:** porta `ConsultaChat` ganhou `listarMensagensOriginais(candidaturaId)` e
  `listarMensagensOriginaisDoServico(anuncioId, prestadorId)`. A segunda cobre a denúncia de serviço, que guarda só o
  anúncio. As duas devolvem `texto`, não `textoExibido`.
- **Contas:** porta nova `ConsultaContasParaModeracao` (implementada em `auth`). Diferente de `ConsultaDadosUsuario`,
  ela mostra a conta encerrada (situação `ENCERRADA`) e nunca traz dado de contato.
- **Evidências:** `Midia` é de `comum/`, então a foto da denúncia e as do check-out são lidas direto pelo
  `MidiaService` e viram link temporário pelo `ArmazenamentoService` (storage fora do ar: sem link, sem erro).

## Pendências

- **Login do administrador e CORS do painel:** mesmos pontos do ETI-117 (login na US60; origem do painel em
  `CORS_ORIGENS_PERMITIDAS`).
- **Motivo da suspensão = fundamento:** decisão do card, a confirmar com o tech lead.
- **Severidade:** tabela provisória, a confirmar com o PO.
- **Início da suspensão no futuro:** recusado (a suspensão começa na decisão). Se o painel precisar agendar, entra uma
  rotina de início em `auth`.
- **Chat da conta suspensa:** os chats continuam abertos (só o encerramento de conta os deixa somente leitura). Como a
  conta não loga, ela não escreve; se o grupo quiser fechar os chats, o lugar é `DeixarChatSomenteLeituraUseCase`.
- **Avisos ao denunciado e ao denunciante:** o card não pede notificação da decisão.
