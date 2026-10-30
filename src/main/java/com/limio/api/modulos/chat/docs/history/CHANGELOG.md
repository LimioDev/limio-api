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
