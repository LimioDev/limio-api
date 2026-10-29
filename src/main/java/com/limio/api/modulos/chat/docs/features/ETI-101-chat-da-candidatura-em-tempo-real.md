# ETI-101 — Chat 1:1 em tempo real da candidatura

Card: [ETI-101](https://kaueamaral96.atlassian.net/browse/ETI-101) · Backlog US48 · card par: [chat] [FRONT-END] Telas
"Chat" e "Chat somente leitura" (telas 21, 22, 22b a 22e).

## Regra de negócio

- **Sem candidatura, sem chat.** O chat só nasce na reação ao `CandidaturaCriadaEvent` (ETI-75), com
  `isSomenteLeitura = false`. Não há rota de criação.
- **Acesso:** só as duas partes (Empregador dono do anúncio e Prestador da candidatura). Para qualquer outra pessoa o
  chat não existe: 404 `CHAT_NAO_ENCONTRADO`, a mesma resposta de um id inexistente. A leitura do histórico pelo admin
  (denúncia, ETI-116) fica para o card dele.
- **Somente leitura:** o envio é recusado (409 `CHAT_SOMENTE_LEITURA`), e o histórico continua visível e com
  confirmação de leitura. O primeiro motivo é o que fica.
- **Valor conversado** é só texto e não vira cobrança.
- **Conta encerrada:** a outra parte aparece como "Usuário excluído", sem foto, e as mensagens dela continuam.

## Contrato

| Operação | Rota | Entrada | Saída |
|---|---|---|---|
| Abrir chat da candidatura | `GET /candidaturas/{candidaturaId}/chat?pagina=0&tamanho=30` | — | `200 { id, candidaturaId, statusCandidatura, outraParte: { nome, fotoUrl, fotoExpiraEm, papel }, anuncio: { titulo, categoria }, isSomenteLeitura, motivoSomenteLeitura, historico: { mensagens, pagina, tamanho, temMais } }` |
| Enviar mensagem | `POST /chats/{chatId}/mensagens` | `{ texto }` | `201 { id, chatId, remetenteId, texto, dataEnvio, entregueEm, lidaEm }` |
| Marcar como entregues | `POST /chats/{chatId}/entregues` | — | `204` |
| Marcar como lidas | `POST /chats/{chatId}/lidas` | — | `204` |
| Canal em tempo real | STOMP em `/ws`, header `Authorization: Bearer <token>` no CONNECT; assinar `/user/queue/chat` | — | `{ tipo: MENSAGEM_NOVA \| ENTREGUE \| LIDA, chatId, mensagem, mensagemIds, em }` |

- `historico.mensagens` vem da mais nova para a mais antiga. `tamanho` vai de 1 a 100.
- `outraParte.papel`: `CANDIDATO` para o Empregador e `EMPREGADOR` para o Prestador.
- `motivoSomenteLeitura`: `CANDIDATURA_RECUSADA`, `CANDIDATO_DESISTIU`, `OUTRO_PRESTADOR_ESCOLHIDO`,
  `ANUNCIO_CANCELADO` ou `SERVICO_CANCELADO`. É `null` enquanto o chat recebe mensagens. O texto da faixa fica com o
  front.
- O app chama `/entregues` quando recebe mensagens (pelo canal ou ao abrir) e `/lidas` com a conversa na tela. Abrir o
  chat (`GET`) não marca nada. Lida implica entregue.
- Erros: 400 `VALIDACAO_FALHOU` (texto em branco ou com mais de 2000 caracteres), 404 `CHAT_NAO_ENCONTRADO` e 409
  `CHAT_SOMENTE_LEITURA`. O card sugeria `DADOS_INVALIDOS`, mas `VALIDACAO_FALHOU` é o código que a API já usa.

## Decisões técnicas

- **Envio por HTTP, entrega por WebSocket.** O canal só entrega: o `SEND` do STOMP é recusado. O envio continua com
  201, validação e `ErroResponse` iguais ao resto da API, e quem está offline vê tudo pelo histórico.
- **Tempo real sai depois do commit** (`ChatTempoRealService`, `AFTER_COMMIT`): nunca chega ao aparelho uma mensagem
  que acabou desfeita.
- **Autenticação do canal:** o handshake `/ws` é público e o token vai no CONNECT (`WebSocketAuthInterceptor`, que
  reaproveita a leitura do `JwtAuthFilter`). Só dá para assinar as próprias filas (`/user/queue/...`). O
  `UsuarioAutenticado` virou `Principal` com o id como nome, que é por onde o `convertAndSendToUser` entrega.
- **Broker em memória:** funciona com uma instância da API. Com mais de uma, troca por relay (RabbitMQ/Redis).
- **Partes gravadas no chat:** `contratante_id` e `prestador_id` vêm do evento. O acesso é checado sem consultar
  `anuncio` a cada mensagem.
- **Portas novas** (ADR-0003): `ConsultaCandidatura` (status da candidatura, título e categoria do anúncio, implementada
  por `anuncio`, que pega o nome da categoria em `ConsultaCategoria`) e `ConsultaFotoPerfil` (foto de qualquer usuário, implementada por `perfil`). O nome do Empregador vem
  de `ConsultaPerfilEmpregador`, o do Prestador de `ConsultaDadosUsuario`. Porta vazia indica conta encerrada.
- **`texto` e `textoExibido`:** gravados iguais até o mascaramento da US50. As respostas sempre mostram o
  `textoExibido`.
- **Tamanho máximo de 2000 caracteres** só no record, porque o banco é `TEXT`. É provisório até a decisão do PO.

## Pendências

- **Listeners que faltam:** só o `AnuncioCanceladoEvent` (ETI-63) existe. Cada card dono de evento liga o seu em
  `DeixarChatSomenteLeituraUseCase`:
  - recusa (ETI-67): `CANDIDATURA_RECUSADA`
  - desistência (ETI-77): `CANDIDATO_DESISTIU`
  - escolha (ETI-69): `OUTRO_PRESTADOR_ESCOLHIDO`
  - serviço cancelado (ETI-94/ETI-96, Sprint 7): `SERVICO_CANCELADO`
- **Eventos copiados:** `CandidaturaCriadaEvent` e `AnuncioCanceladoEvent` foram copiados byte a byte das PRs #19 e
  #20. O merge não conflita nesses arquivos.
