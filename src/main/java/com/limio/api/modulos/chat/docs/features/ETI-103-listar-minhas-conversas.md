# ETI-103 — Listar minhas conversas por papel

Backlog US49 (sem card original no Jira) · card par: [chat] [FRONT-END] Tela "Conversas" (Empregador e Prestador).

## Regra de negócio

- **A lista segue o papel ativo do token.** Empregador vê os chats dos anúncios dele (`chat.contratante_id`); Prestador,
  os das candidaturas dele (`chat.prestador_id`). A pessoa vem sempre do token: a rota não recebe `usuarioId`. O papel
  só escolhe a coluna filtrada pelo id do próprio usuário, então o chat do outro papel (ou de terceiros) não aparece.
- **Somente leitura continua na lista.** É o histórico. `isSomenteLeitura` é o flag já gravado no chat pelos listeners
  de `DeixarChatSomenteLeituraUseCase`, não uma regra nova calculada a partir do status da candidatura.
- **Chat sem mensagem aparece**, com `ultimaMensagem = null`: o chat nasce com a candidatura, antes de alguém escrever.
- **Ordem:** pela última mensagem, da mais nova para a mais antiga. Chat sem mensagem entra pela data de criação.
- **Não lidas:** mensagens da outra parte com `lida_em` nulo. As que o próprio usuário enviou nunca contam.
- **Conta encerrada:** a outra parte aparece como "Usuário excluído", sem foto. A ausência no lote de dados do usuário
  (ou do perfil do Empregador) é o que indica a conta encerrada; nada anonimizado é lido.

## Contrato

| Operação | Rota | Entrada | Saída |
|---|---|---|---|
| Listar conversas do papel ativo | `GET /chats?pagina=0&tamanho=20` | query `pagina` (≥ 0, padrão 0) e `tamanho` (1 a 50, padrão 20) | `200 { conversas: PageResponse<Conversa>, totalComNaoLidas }` |

`Conversa`: `{ chatId, candidaturaId, outraParte: { nome, fotoUrl, fotoExpiraEm, papel }, tituloAnuncio,
statusCandidatura, ultimaMensagem: { texto, enviadaEm, enviadaPorMim } | null, mensagensNaoLidas, isSomenteLeitura,
motivoSomenteLeitura }`.

- `conversas` é o `PageResponse` padrão (`content`, `pagina`, `tamanho`, `totalElementos`, `totalPaginas`).
- `totalComNaoLidas` conta as conversas do papel ativo com mensagem não lida em **todas** as páginas ("2 não lidas"),
  não só na pedida. Vem de uma consulta própria, por isso não varia com a página.
- `candidaturaId` abre a conversa em `GET /candidaturas/{id}/chat`. `statusCandidatura` (`ENVIADA`, `ESCOLHIDA`,
  `RECUSADA`, `ENCERRADA`, ...) dá o selo. `ultimaMensagem.texto` é o `textoExibido`; `enviadaPorMim` deixa o app
  prefixar "Você:". `motivoSomenteLeitura` é `null` enquanto o chat recebe mensagens.
- `outraParte.papel`: `CANDIDATO` para o Empregador e `EMPREGADOR` para o Prestador (mesma regra do cabeçalho do chat).
- Erros: 400 `VALIDACAO_FALHOU` (`pagina` negativa, `tamanho` fora de 1 a 50 ou não numérico) e 401. Lista vazia é 200.
  O card sugeria `DADOS_INVALIDOS`, que não existe; `VALIDACAO_FALHOU` é o código que a API já usa.

## Decisões técnicas

- **Uma query por lado, um fragmento SQL.** `ChatRepository.listarDoContratante` e `listarDoPrestador` só diferem no
  `WHERE`. São nativas, com `countQuery`, como o mural. A última mensagem sai de um `LEFT JOIN LATERAL` (usa
  `idx_mensagem_chat_data_envio`) e a contagem de não lidas, de uma subconsulta: nada vira consulta por chat.
- **Dados de outros módulos em lote, por portas (ADR-0003).** Título e status: `ConsultaCandidatura.buscarResumosEmLote`.
  Prestador: `ConsultaDadosUsuario.buscarPorIds` + `ConsultaFotoPerfil.buscarFotosEmLote`. Empregador:
  `ConsultaPerfilEmpregador.buscarResumosEmLote`. Uma consulta por porta, qualquer que seja o tamanho da página.
- **Índices (V38):** `chat(contratante_id)`, `chat(prestador_id)` e o parcial `mensagem(chat_id, remetente_id) WHERE
  lida_em IS NULL`. Antes o chat só era indexado por `aplicacao_id` e `anuncio_id`.
- **Wrapper em vez de campo novo no `PageResponse`:** `ConversasResponse` carrega o total de não lidas sem alterar o
  record genérico usado por outros módulos.
- Não usa `Pageable` do cliente nem `sort`: a ordem é fixa na query.

## Pendências

- **Papel do token pode estar velho.** `PATCH /auth/papel-ativo` não emite token novo; a lista só passa a seguir o novo
  papel depois do refresh ou do login (mesma limitação do mural). O front precisa renovar o token ao alternar.
- **Conclusão do serviço não fecha o chat hoje** (motivo `SERVICO_CANCELADO` sem listener, Sprint 7), então esses chats
  seguem como abertos na lista.
- **Foto expira:** `fotoExpiraEm` vem junto; o app recarrega a lista quando o link vencer.
