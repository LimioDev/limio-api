# Módulo chat — o que cada classe faz

## Fluxos

**Candidatura criada → chat aberto**
`anuncio` publica `CandidaturaCriadaEvent` → `CriarChatDaCandidaturaUseCase.aoCriarCandidatura` (depois do commit) →
`ChatService.salvar(Chat.daCandidatura(...))`.

**Anúncio cancelado → chats somente leitura**
`anuncio` publica `AnuncioCanceladoEvent` → `DeixarChatSomenteLeituraUseCase.aoCancelarAnuncio` →
`Chat.deixarSomenteLeitura(ANUNCIO_CANCELADO, ...)` em cada chat aberto do anúncio.

**`GET /candidaturas/{id}/chat`**
`ChatController.abrirChat` → `AbrirChatUseCase` (busca o chat da parte, `ConsultaCandidatura`, a outra parte por
`ConsultaDadosUsuario` + `ConsultaPerfilEmpregador`/`ConsultaFotoPerfil`, e a página do histórico) → `ChatAberto` →
`ChatMapper` → `ChatResponse`.

**`POST /chats/{id}/mensagens`**
`ChatController.enviarMensagem` → `EnviarMensagemUseCase` (recusa se somente leitura, grava `Mensagem.enviada`,
publica `MensagemEnviadaEvent`) → depois do commit, `ChatTempoRealService` entrega `MENSAGEM_NOVA` ao destinatário.

**`POST /chats/{id}/entregues` e `/lidas`**
`ChatController` → `ConfirmarMensagensUseCase` (marca as mensagens recebidas, publica `MensagensConfirmadasEvent`) →
depois do commit, `ChatTempoRealService` entrega `ENTREGUE`/`LIDA` ao remetente.

## Classes

| Classe | Papel |
|---|---|
| `Chat` | Conversa 1:1 da candidatura. Partes, somente leitura e motivo. |
| `ChatService` / `ChatRepository` | Persistência. `buscarDoParticipante` responde 404 para quem não é parte. |
| `mensagem/Mensagem` | Mensagem com texto, texto exibido, envio, entrega e leitura. |
| `mensagem/MensagemService` / `MensagemRepository` | Histórico paginado e mensagens pendentes de confirmação. |
| `ChatAberto`, `OutraParte` | Modelo de leitura da tela, devolvido pelo usecase ao controller. |
| `enums/MotivoSomenteLeitura` | Por que o chat parou de receber mensagens. |
| `enums/PapelNoChat` | Papel da outra parte no cabeçalho. |
| `enums/Confirmacao` | Entregue ou lida. |
| `enums/TipoEventoChat` | O que chega pelo canal em tempo real. |
| `eventos/*` | Eventos internos que levam ao tempo real depois do commit. |
| `actions/service/ChatTempoRealService` | Entrega em `/user/queue/chat` via `SimpMessagingTemplate`. |
| `actions/mapper/ChatMapper` | Entity/modelo para os records HTTP e do canal. |

Fora do módulo: `comum/config/WebSocketConfig`, `comum/seguranca/WebSocketAuthInterceptor`,
`comum/portas/ConsultaCandidatura` (implementada em `anuncio/actions/service/ConsultaCandidaturaService`) e
`comum/portas/ConsultaFotoPerfil` (implementada em `perfil/actions/service/ConsultaFotoPerfilService`).
