# CHANGELOG — módulo chat

## 2026-10-28 — ETI-101: chat 1:1 da candidatura em tempo real

- Primeira implementação do módulo: tabelas `chat` e `mensagem` (V22).
- O chat nasce com a candidatura (`CandidaturaCriadaEvent`) e fica somente leitura no `AnuncioCanceladoEvent`. Os
  demais motivos já existem no enum e esperam os eventos dos cards donos.
- O canal em tempo real (STOMP em `/ws`) entra em `comum/config` e `comum/seguranca`. É o primeiro uso de WebSocket
  na API.
- Ver `docs/features/ETI-101-chat-da-candidatura-em-tempo-real.md`.
