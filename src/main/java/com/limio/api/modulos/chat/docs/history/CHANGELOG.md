# CHANGELOG — módulo chat

## 2026-10-29 — ETI-101: ajustes do review da PR #21

- **Conta encerrada trava o chat:** `DeixarChatSomenteLeituraUseCase.aoEncerrarConta` escuta o `ContaEncerradaEvent` e
  deixa somente leitura os chats abertos da pessoa, com o motivo novo `CONTA_ENCERRADA`.
- **Chat que faltar é criado ao abrir:** exceção no `AFTER_COMMIT` vira só log no Spring. Se a reação ao
  `CandidaturaCriadaEvent` falhar, o `GET` cria o chat (`ChatService.garantirDaCandidatura`, com
  `insert ... on conflict do nothing`). `ResumoCandidatura` passou a trazer `anuncioId`, `contratanteId` e
  `prestadorId`.
- **Paginação por referência:** `pagina` saiu do contrato; agora é `antesDe=<mensagemId>`. Número de página repetia
  ou pulava mensagens num chat ao vivo. `tamanho` fora de 1 a 100 responde 400 `VALIDACAO_FALHOU` (`HistoricoFiltro`),
  sem ajuste silencioso.
- **Sessão do WebSocket vale até o token vencer:** `ExpiracaoSessaoWebSocket` recusa o SUBSCRIBE depois do vencimento
  e troca a próxima entrega por um ERROR, que fecha a conexão. A leitura do token saiu do `JwtAuthFilter` para o
  componente `LeitorTokenAcesso`, usado pelos dois.
- **Abrir o chat não cria o perfil da outra parte:** `ConsultaPerfilEmpregador.buscarResumoSemCriar` mostra o nome
  inicial sem gravar nada.
- **Um caminho para a foto de perfil:** a porta `ConsultaFotoPerfil` devolve `LinkTemporario` e passou a ser usada
  também pelo `anuncio` (candidatos do anúncio) e pelo resumo do Empregador. O record `FotoPerfil` saiu.
- **Confirmação num UPDATE só:** `/entregues` e `/lidas` usam `UPDATE ... RETURNING id`. Chamadas simultâneas não
  publicam o mesmo id duas vezes. Os métodos `marcarEntregue`/`marcarLida` da entity saíram.
- **Exceção registrada à regra "mapper só no controller":** o `ChatTempoRealService` também usa o `ChatMapper`, porque
  o canal em tempo real é uma saída do módulo, igual ao controller. Ele converte o evento interno no record do canal,
  e o usecase continua sem conhecer record. Não vale como precedente para usecase ou service de integração que não
  seja saída para o cliente.

## 2026-10-28 — ETI-101: chat 1:1 da candidatura em tempo real

- Primeira implementação do módulo: tabelas `chat` e `mensagem` (V24).
- O chat nasce com a candidatura (`CandidaturaCriadaEvent`) e fica somente leitura no `AnuncioCanceladoEvent`. Os
  demais motivos já existem no enum e esperam os eventos dos cards donos.
- O canal em tempo real (STOMP em `/ws`) entra em `comum/config` e `comum/seguranca`. É o primeiro uso de WebSocket
  na API.
- Ver `docs/features/ETI-101-chat-da-candidatura-em-tempo-real.md`.

## 2026-10-30 — ETI-67: chat somente leitura ao recusar candidatura

`DeixarChatSomenteLeituraUseCase.aoRecusarCandidatura` escuta o novo `CandidaturaRecusadaEvent` (`comum/eventos/`) e
deixa o chat da candidatura somente leitura com `CANDIDATURA_RECUSADA`. O chat também implementa a porta
`comum/portas/ConsultaChat` (`actions/service/ConsultaChatService`), que devolve o id do chat por candidatura, em lote,
para a lista de candidatos do `anuncio`. Só leitura: nunca cria o chat. Ver
`anuncio/docs/features/ETI-67-listar-candidatos-e-recusar-candidato.md`.

## 2026-11-07 — ETI-69: chats dos candidatos não escolhidos ficam somente leitura

`DeixarChatSomenteLeituraUseCase.aoEscolherPrestador` escuta `PrestadorEscolhidoEvent` (`comum/eventos`, publicado
por `anuncio`) em `AFTER_COMMIT` + `REQUIRES_NEW` e deixa somente leitura (`OUTRO_PRESTADOR_ESCOLHIDO`) os chats das
candidaturas listadas em `candidaturasNaoEscolhidasIds`, em lote (`executarDasCandidaturas`). O chat do escolhido não é
tocado e segue aberto. Recusadas e desistentes não entram na lista: seus chats já foram fechados com o motivo próprio
(e `Chat.deixarSomenteLeitura` mantém o primeiro motivo). Só `SERVICO_CANCELADO` (ETI-94 a ETI-97) segue sem listener (ligado depois: ETI-94 e ETI-96, ver 2026-11-18).

## 2026-11-18 — ETI-96: chat somente leitura quando o Prestador cancela o serviço

`DeixarChatSomenteLeituraUseCase.aoCancelarServicoPeloPrestador` escuta `ServicoCanceladoPeloPrestadorEvent`
(`comum/eventos`, publicado por `anuncio`) em `AFTER_COMMIT` + `REQUIRES_NEW` e deixa somente leitura o chat da
`candidaturaEscolhidaId`, com o mesmo motivo `SERVICO_CANCELADO` do cancelamento pelo Empregador (ETI-94,
`aoCancelarServico`): a faixa do front não depende de quem cancelou. Com isso `SERVICO_CANCELADO` tem listener nos dois
sentidos. Falha do listener é só logada e o chat fica aberto, como nos demais.

## 2026-11-16 — ETI-136: id da outra parte no chat

`ChatResponse.OutraParte` e o `OutraParte` de domínio ganharam `usuarioId`, pro menu "⋮" do chat (tela 21b) abrir o
perfil público. Com conta encerrada o id continua vindo (`OutraParte.contaEncerrada(usuarioId, papel)`): o perfil
público responde "Conta encerrada" e mantém a reputação. Detalhe em
`anuncio/docs/features/ETI-136-usuario-id-nas-telas-do-perfil-publico.md`.

## 2026-11-17 — ETI-103: listar minhas conversas por papel

- **`GET /chats`:** lista as conversas do papel ativo do token (Empregador: chats dos anúncios; Prestador: chats das
  candidaturas), da última mensagem mais nova para a mais antiga, paginada com `PageResponse` (`pagina`, `tamanho` de
  1 a 50). A resposta traz também `totalComNaoLidas`, que não depende da página. Contrato em
  `docs/features/ETI-103-listar-minhas-conversas.md`.
- **Query única por papel:** `ChatRepository.listarDoContratante` / `listarDoPrestador` (nativas, com LATERAL para a
  última mensagem e subconsulta para as não lidas) e `contarDoContratanteComNaoLidas` / `contarDoPrestadorComNaoLidas`.
  Mensagens enviadas pelo próprio usuário não contam como não lidas.
- **`ListarConversasUseCase`:** monta título/status, nome e foto da outra parte em lote pelas portas
  `ConsultaCandidatura.buscarResumosEmLote`, `ConsultaDadosUsuario.buscarPorIds`, `ConsultaFotoPerfil.buscarFotosEmLote`
  e `ConsultaPerfilEmpregador.buscarResumosEmLote`. Conta encerrada aparece como "Usuário excluído". Chat somente
  leitura e chat sem mensagem continuam na lista.
- **Índices (V38):** `idx_chat_contratante_id`, `idx_chat_prestador_id` e `idx_mensagem_nao_lidas` (parcial).
- **Código de erro:** entrada inválida responde 400 `VALIDACAO_FALHOU`; `DADOS_INVALIDOS`, sugerido no card, não existe
  na API.
- `ChatController` deixou de ser "sem checagem de papel" só nesta rota: a lista segue o papel do token.
- **Merge com a develop (ETI-136):** `outraParte.usuarioId` também na lista, pelo mesmo `OutraParte` de domínio do
  chat; conta encerrada mantém o id.
