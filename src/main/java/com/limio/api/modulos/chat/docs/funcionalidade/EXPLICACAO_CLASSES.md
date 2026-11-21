# Módulo chat — o que cada classe faz

## Fluxos

**Candidatura criada → chat aberto**
`anuncio` publica `CandidaturaCriadaEvent` → `CriarChatDaCandidaturaUseCase.aoCriarCandidatura` (depois do commit) →
`ChatService.garantirDaCandidatura(...)` (`insert ... on conflict do nothing`). Se essa reação falhar, o
`AbrirChatUseCase` cria o chat quando uma das partes abrir.

**Anúncio cancelado → chats somente leitura**
`anuncio` publica `AnuncioCanceladoEvent` → `DeixarChatSomenteLeituraUseCase.aoCancelarAnuncio` →
`Chat.deixarSomenteLeitura(ANUNCIO_CANCELADO, ...)` em cada chat aberto do anúncio.

**Candidatura recusada → chat somente leitura**
`anuncio` publica `CandidaturaRecusadaEvent` → `DeixarChatSomenteLeituraUseCase.aoRecusarCandidatura` →
`Chat.deixarSomenteLeitura(CANDIDATURA_RECUSADA, ...)` no chat daquela candidatura (nada, se ainda não existe).

**Prestador escolhido → chats dos não escolhidos somente leitura**
`anuncio` publica `PrestadorEscolhidoEvent` → `DeixarChatSomenteLeituraUseCase.aoEscolherPrestador` →
`executarDasCandidaturas` → `Chat.deixarSomenteLeitura(OUTRO_PRESTADOR_ESCOLHIDO, ...)` nos chats das candidaturas em
`candidaturasNaoEscolhidasIds`. O chat do escolhido não é consultado.

**Conta encerrada → chats somente leitura**
`auth` publica `ContaEncerradaEvent` → `DeixarChatSomenteLeituraUseCase.aoEncerrarConta` →
`Chat.deixarSomenteLeitura(CONTA_ENCERRADA, ...)` em cada chat aberto em que a pessoa é uma das partes.

**`GET /candidaturas/{id}/chat`**
`ChatController.abrirChat` (`HistoricoFiltro` validado) → `AbrirChatUseCase` (`ConsultaCandidatura` confere se quem
pede é parte, `ChatService.garantirDaCandidatura`, a outra parte por `ConsultaPerfilEmpregador.buscarResumoSemCriar` ou
`ConsultaDadosUsuario` + `ConsultaFotoPerfil`, e a página do histórico a partir de `antesDe`) → `ChatAberto` →
`ChatMapper` → `ChatResponse`.

**`GET /chats`** (ETI-103)
`ChatController.listarConversas` (`ConversasFiltro` validado, papel e id vêm do `UsuarioAutenticado`) →
`ListarConversasUseCase` (`switch` no papel → `ChatService.listarDoContratante` ou `listarDoPrestador` e a contagem de
conversas com não lidas; depois, em lote, `ConsultaCandidatura`, `ConsultaDadosUsuario` + `ConsultaFotoPerfil` ou
`ConsultaPerfilEmpregador`) → `ConversasListadas` → `ChatMapper` → `ConversasResponse`.

**`POST /chats/{id}/mensagens`**
`ChatController.enviarMensagem` → `EnviarMensagemUseCase` (recusa se somente leitura, grava `Mensagem.enviada`,
publica `MensagemEnviadaEvent`) → depois do commit, `ChatTempoRealService` entrega `MENSAGEM_NOVA` ao destinatário.

**`POST /chats/{id}/entregues` e `/lidas`**
`ChatController` → `ConfirmarMensagensUseCase` (um `UPDATE ... RETURNING id` marca as mensagens recebidas, publica
`MensagensConfirmadasEvent` com os ids) →
depois do commit, `ChatTempoRealService` entrega `ENTREGUE`/`LIDA` ao remetente.

## Classes

| Classe | Papel |
|---|---|
| `Chat` | Conversa 1:1 da candidatura. Partes, somente leitura e motivo. |
| `ChatService` / `ChatRepository` | Persistência. `buscarDoParticipante` responde 404 para quem não é parte; `garantirDaCandidatura` cria o chat que faltar, com o índice único decidindo a corrida. |
| `mensagem/Mensagem` | Mensagem com texto, texto exibido, envio, entrega e leitura. |
| `mensagem/MensagemService` / `MensagemRepository` | Histórico por referência (`antesDe`) e confirmação de entrega/leitura num UPDATE só. |
| `ConversaProjecao` | Linha da query nativa da lista: chat, última mensagem e não lidas. Só o `ChatRepository` e o `ChatService` a veem. |
| `ConversaResumo`, `ConversasListadas` | Modelo de leitura da tela "Conversas": um item e a página com o total de conversas não lidas. |
| `records/ConversasFiltro` | Query do `GET /chats`: `pagina` (≥ 0) e `tamanho` (1 a 50, padrão 20). |
| `records/ConversasResponse`, `records/ConversaResponse` | Resposta do `GET /chats`: `PageResponse` de conversas e `totalComNaoLidas`. |
| `records/HistoricoFiltro` | Query do `GET` do chat: `antesDe` e `tamanho` (1 a 100, 400 fora disso). |
| `actions/usecase/ListarConversasUseCase` | Lista do papel ativo: escolhe a query pelo papel e monta título, status e a outra parte em lote. |
| `ChatAberto`, `OutraParte` | Modelo de leitura da tela, devolvido pelo usecase ao controller. |
| `enums/MotivoSomenteLeitura` | Por que o chat parou de receber mensagens. |
| `enums/PapelNoChat` | Papel da outra parte no cabeçalho. |
| `enums/Confirmacao` | Entregue ou lida. |
| `enums/TipoEventoChat` | O que chega pelo canal em tempo real. |
| `eventos/*` | Eventos internos que levam ao tempo real depois do commit. |
| `actions/service/ChatTempoRealService` | Entrega em `/user/queue/chat` via `SimpMessagingTemplate`. |
| `actions/mapper/ChatMapper` | Entity/modelo para os records HTTP e do canal. |

Fora do módulo: `comum/config/WebSocketConfig`; `comum/seguranca/LeitorTokenAcesso` (lê o token para o
`JwtAuthFilter` e para o WebSocket), `WebSocketAuthInterceptor` e `ExpiracaoSessaoWebSocket`;
`comum/portas/ConsultaCandidatura` (`buscarPorId` e, desde o ETI-103, `buscarResumosEmLote`; implementada em `anuncio/actions/service/ConsultaCandidaturaService`);
`comum/portas/ConsultaFotoPerfil` (implementada em `perfil/actions/service/ConsultaFotoPerfilService`, também usada
pelo `anuncio` e pelo resumo do Empregador); `ConsultaPerfilEmpregador.buscarResumoSemCriar`.
