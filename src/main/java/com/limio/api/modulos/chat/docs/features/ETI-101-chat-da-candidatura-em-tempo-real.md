# ETI-101 — Chat 1:1 em tempo real da candidatura

Card: [ETI-101](https://kaueamaral96.atlassian.net/browse/ETI-101) · Backlog US48 · card par: [chat] [FRONT-END] Telas
"Chat" e "Chat somente leitura" (telas 21, 22, 22b a 22e).

## Regra de negócio

- **Sem candidatura, sem chat.** O chat nasce na reação ao `CandidaturaCriadaEvent` (ETI-75), com
  `isSomenteLeitura = false`. Não há rota de criação. Se essa reação falhar, o chat é criado quando uma das partes o
  abre.
- **Acesso:** só as duas partes (Empregador dono do anúncio e Prestador da candidatura). Para qualquer outra pessoa o
  chat não existe: 404 `CHAT_NAO_ENCONTRADO`, a mesma resposta de um id inexistente. A leitura do histórico pelo admin
  (denúncia, ETI-116) fica para o card dele.
- **Somente leitura:** o envio é recusado (409 `CHAT_SOMENTE_LEITURA`), e o histórico continua visível e com
  confirmação de leitura. O primeiro motivo é o que fica.
- **Valor conversado** é só texto e não vira cobrança.
- **Conta encerrada:** a outra parte aparece como "Usuário excluído", sem foto, e as mensagens dela continuam. Os chats
  abertos de quem encerrou a conta ficam somente leitura (`CONTA_ENCERRADA`).

## Contrato

| Operação | Rota | Entrada | Saída |
|---|---|---|---|
| Abrir chat da candidatura | `GET /candidaturas/{candidaturaId}/chat?antesDe=<mensagemId>&tamanho=30` | — | `200 { id, candidaturaId, statusCandidatura, outraParte: { nome, fotoUrl, fotoExpiraEm, papel }, anuncio: { titulo, categoria }, isSomenteLeitura, motivoSomenteLeitura, historico: { mensagens, tamanho, temMais } }` |
| Enviar mensagem | `POST /chats/{chatId}/mensagens` | `{ texto }` | `201 { id, chatId, remetenteId, texto, dataEnvio, entregueEm, lidaEm }` |
| Marcar como entregues | `POST /chats/{chatId}/entregues` | — | `204` |
| Marcar como lidas | `POST /chats/{chatId}/lidas` | — | `204` |
| Canal em tempo real | STOMP em `/ws`, header `Authorization: Bearer <token>` no CONNECT; assinar `/user/queue/chat` | — | `{ tipo: MENSAGEM_NOVA \| ENTREGUE \| LIDA, chatId, mensagem, mensagemIds, em }` |

- **Histórico:** `historico.mensagens` vem da mais nova para a mais antiga. Sem `antesDe`, vêm as mais recentes. Para
  carregar as anteriores, o app manda em `antesDe` o id da última mensagem da página que já tem (a mais antiga). A
  paginação é pela posição da mensagem, não por número de página: mensagem nova chegando no meio da rolagem não
  repete nem pula nada. `antesDe` de outro chat devolve a lista vazia. `tamanho` vai de 1 a 100 (padrão 30). Fora
  disso, 400 `VALIDACAO_FALHOU`.
- `outraParte.papel`: `CANDIDATO` para o Empregador e `EMPREGADOR` para o Prestador.
- `motivoSomenteLeitura`: `CANDIDATURA_RECUSADA`, `CANDIDATO_DESISTIU`, `OUTRO_PRESTADOR_ESCOLHIDO`,
  `ANUNCIO_CANCELADO`, `SERVICO_CANCELADO` ou `CONTA_ENCERRADA`. É `null` enquanto o chat recebe mensagens. O texto da
  faixa fica com o front.
- O app chama `/entregues` quando recebe mensagens (pelo canal ou ao abrir) e `/lidas` com a conversa na tela. Abrir o
  chat (`GET`) não marca nada.
- **`LIDA` também vale como entregue.** Se a conversa é aberta antes da confirmação de entrega, `/lidas` preenche
  `entregueEm` e `lidaEm` juntos, e pelo canal só chega o evento `LIDA` (nunca um `ENTREGUE` para essas mensagens). O
  app deve mostrar o check de lida, que inclui o de entregue.
- **Sessão do canal:** vale até o token de acesso usado no CONNECT vencer. Depois disso o SUBSCRIBE é recusado, e a
  próxima entrega vira um frame `ERROR` ("token de acesso expirado") que fecha a conexão. O app reconecta com o token
  novo do refresh e recarrega o histórico, porque o que não chegou pelo canal está nele.
- Erros: 400 `VALIDACAO_FALHOU` (texto em branco ou com mais de 2000 caracteres, ou `tamanho` fora do limite), 404
  `CHAT_NAO_ENCONTRADO` e 409 `CHAT_SOMENTE_LEITURA`. O card sugeria `DADOS_INVALIDOS`, mas `VALIDACAO_FALHOU` é o
  código que a API já usa.

## Decisões técnicas

- **Envio por HTTP, entrega por WebSocket.** O canal só entrega: o `SEND` do STOMP é recusado. O envio continua com
  201, validação e `ErroResponse` iguais ao resto da API, e quem está offline vê tudo pelo histórico.
- **Tempo real sai depois do commit** (`ChatTempoRealService`, `AFTER_COMMIT`): nunca chega ao aparelho uma mensagem
  que acabou desfeita.
- **Chat criado também ao abrir:** no Spring, exceção num listener `AFTER_COMMIT` vira só log. Por isso o
  `AbrirChatUseCase` cria o chat que faltar (`ChatService.garantirDaCandidatura`), conferindo antes, pela
  `ConsultaCandidatura`, que quem pede é uma das partes. O índice único de `aplicacao_id` com
  `insert ... on conflict do nothing` decide a corrida com o listener. Por isso o `GET` não é `readOnly`.
- **Autenticação do canal:** o handshake `/ws` é público e o token vai no CONNECT (`WebSocketAuthInterceptor`). O token
  é lido pelo `LeitorTokenAcesso`, o mesmo componente do `JwtAuthFilter`. Só dá para assinar as próprias filas
  (`/user/queue/...`). O `UsuarioAutenticado` virou `Principal` com o id como nome, que é por onde o
  `convertAndSendToUser` entrega. A validade da sessão fica em `ExpiracaoSessaoWebSocket`, no canal de saída.
- **Broker em memória:** funciona com uma instância da API. Com mais de uma, troca por relay (RabbitMQ/Redis). A
  validade das sessões também fica em memória, por instância.
- **Partes gravadas no chat:** `contratante_id` e `prestador_id` vêm do evento (ou da candidatura, quando o chat é
  criado ao abrir). O acesso é checado sem consultar `anuncio` a cada mensagem.
- **Portas** (ADR-0003): `ConsultaCandidatura` (status, partes, título e categoria do anúncio, implementada por
  `anuncio`, que pega o nome da categoria em `ConsultaCategoria`) e `ConsultaFotoPerfil` (foto de qualquer usuário,
  implementada por `perfil`). A `ConsultaFotoPerfil` é o único caminho para a foto de perfil de outra pessoa: o
  `anuncio` (candidatos do anúncio) e o resumo do Empregador também usam. O nome do Empregador vem de
  `ConsultaPerfilEmpregador.buscarResumoSemCriar`, que não cria o perfil da outra parte. O nome do Prestador vem de
  `ConsultaDadosUsuario`. Porta vazia indica conta encerrada.
- **Confirmação num UPDATE só:** `/entregues` e `/lidas` fazem `UPDATE ... RETURNING id`. A trava de linha faz uma
  chamada simultânea não achar nada, então o evento não sai em dobro com os mesmos ids.
- **`texto` e `textoExibido`:** gravados iguais até o mascaramento da US50. As respostas sempre mostram o
  `textoExibido`.
- **Tamanho máximo de 2000 caracteres** só no record, porque o banco é `TEXT`. É provisório até a decisão do PO.

## Pendências

- **Listeners que faltam:** existem os do `AnuncioCanceladoEvent` (ETI-63), do `CandidaturaRecusadaEvent` (ETI-67) e
  do `ContaEncerradaEvent` (TICKET-0049). Cada card dono de evento liga o seu em `DeixarChatSomenteLeituraUseCase`:
  - desistência (ETI-77): `CANDIDATO_DESISTIU`
  - escolha (ETI-69): `OUTRO_PRESTADOR_ESCOLHIDO`
  - serviço cancelado (ETI-94/ETI-96, Sprint 7): `SERVICO_CANCELADO`
- **Chat criado ao abrir nasce recebendo mensagens.** Se a reação ao `CandidaturaCriadaEvent` falhou e a candidatura
  encerrou antes de alguém abrir o chat, o evento de encerramento não achou chat para travar. Quando os listeners
  acima existirem, vale o `AbrirChatUseCase` olhar o `statusCandidatura` antes de deixar o chat aberto.
- **Eventos copiados:** `CandidaturaCriadaEvent` e `AnuncioCanceladoEvent` foram copiados byte a byte das PRs #19 e
  #20. O merge não conflita nesses arquivos.
