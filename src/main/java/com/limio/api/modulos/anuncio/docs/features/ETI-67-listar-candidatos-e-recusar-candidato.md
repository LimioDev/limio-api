# ETI-67 — Listar os candidatos do anúncio e recusar candidato

## Status

Implementado.

## Link do card

[ETI-67](https://kaueamaral96.atlassian.net/jira/software/projects/ETI/boards/2?selectedIssue=ETI-67) —
[anuncio] [BACK-END] Listar os candidatos do anúncio e recusar candidato (UC28 e UC29; o comportamento do chat vem do
UC30).

## User Story

Como um Empregador, eu quero que a API mostre quem se candidatou e permita recusar um candidato, para que eu escolha o
melhor prestador.

## Escopo implementado

- `GET /anuncios/{anuncioId}/candidaturas`: candidaturas `ENVIADA` do anúncio, da mais antiga para a mais nova.
- `POST /anuncios/{anuncioId}/candidaturas/{candidaturaId}/recusar`: candidatura vira `RECUSADA` com `recusadaEm`,
  publica `CandidaturaRecusadaEvent` e responde com a lista atualizada.
- O chat da candidatura vira somente leitura (`CANDIDATURA_RECUSADA`) por um listener do módulo `chat`.

**Fora**: aviso ao Prestador (notificação, ETI-112 — só o evento é publicado aqui), escolher candidato (ETI-69/70),
desistir (ETI-77).

## Regra de negócio

| Regra | Implementação |
|---|---|
| Só o dono lista ou recusa; anúncio de outra pessoa é 404 | `anuncioService.buscarPorId(...).filter(pertenceA)` na listagem e `buscarParaAtualizar(...).filter(pertenceA)` na recusa; anúncio inexistente e de outro usuário respondem igual (`ANUNCIO_NAO_ENCONTRADO`). |
| Lista só candidaturas `ENVIADA` | `AplicacaoService.buscarEnviadasDoAnuncio`: recusadas, escolhidas e encerradas ficam fora. |
| Sem candidatos não é erro | 200 com `candidatos: []`. |
| Média só com ao menos uma avaliação | `ResumoAvaliacoes` zera a média quando `quantidade = 0`; o JSON sai com `mediaAvaliacoes: null`. |
| Nunca expor telefone, e-mail, WhatsApp nem endereço | `CandidatoResponse` não tem esses campos; o story confere o JSON. |
| Devolve o id do chat | `ConsultaChat.buscarIdsPorCandidatura`; `chatId` é `null` se o chat ainda não existe (a consulta nunca cria). |
| Recusar exige candidatura `ENVIADA` | `CandidaturaEstadoInvalidoException` (409) para `RECUSADA`, `ESCOLHIDA`, `ENCERRADA` e qualquer outro status. |
| Anúncio precisa estar `PUBLICADO` | `AnuncioEstadoInvalidoException` (409) para os demais status. |
| Candidatura é do anúncio da URL | Candidatura inexistente ou de outro anúncio: `CandidaturaNaoEncontradaException` (404). |
| Recusar não escolhe ninguém | O usecase só altera a `Aplicacao` recusada: o anúncio continua `PUBLICADO` e as outras candidaturas ficam como estão, inclusive se era a única. |
| Chat fica somente leitura | `DeixarChatSomenteLeituraUseCase.aoRecusarCandidatura`, depois do commit; sem chat, não faz nada. |
| Publica `CandidaturaRecusada` | `CandidaturaRecusadaEvent` em `comum/eventos/`. |

## Contrato da API

| Operação | Rota | Entrada | Saída |
|---|---|---|---|
| Listar candidatos | `GET /anuncios/{anuncioId}/candidaturas` | — | `200 { candidatos: [{ candidaturaId, nome, fotoUrl, fotoExpiraEm, especialidades, gold, qtdAvaliacoes, mediaAvaliacoes, chatId }] }` |
| Recusar candidato | `POST /anuncios/{anuncioId}/candidaturas/{candidaturaId}/recusar` | sem corpo | `200`, mesma lista da listagem, já sem o recusado |

- `nome` vem abreviado (`"Rafael S."`), como no card do anúncio. `fotoUrl` e `fotoExpiraEm` são `null` sem foto.
- Candidato cuja conta foi encerrada não aparece na lista.
- Erros: 404 `ANUNCIO_NAO_ENCONTRADO`, 404 `CANDIDATURA_NAO_ENCONTRADA`, 409 `ANUNCIO_ESTADO_INVALIDO`, 409
  `CANDIDATURA_ESTADO_INVALIDO`, 401 sem token.
- `CodigoErro` novos: `CANDIDATURA_NAO_ENCONTRADA` e `CANDIDATURA_ESTADO_INVALIDO`.

## Decisões técnicas

- **Portas novas (ADR-0003, só leitura e em lote):** `ConsultaEspecialidades` (implementada por `perfil`),
  `ConsultaChat` (por `chat`, porque `Aplicacao` guarda só a ligação inversa `Chat.aplicacaoId` e `anuncio` não importa
  `chat`) e `ConsultaAvaliacoes`. Nome, foto e GOLD reaproveitam `ConsultaDadosUsuario`, `ConsultaFotoPerfil` e
  `ConsultaPlanoPrestador`.
- **`ConsultaAvaliacoes` é provisória.** Não existe módulo nem tabela de avaliações, então a implementação, em
  `perfil`, devolve sempre "sem avaliações". Quando o módulo chegar, só `ConsultaAvaliacoesService` muda.
- **GOLD** vem de `ConsultaPlanoPrestador`, que hoje é um stub do `financeiro` e devolve sempre `FREE`.
- **Concorrência:** `RecusarCandidatoUseCase` é `@Transactional` e trava o anúncio com `PESSIMISTIC_WRITE`
  (`AnuncioRepository.findByIdParaAtualizar`, mesmo padrão do `UsuarioRepository.findByIdParaAtualizar`) antes de ler a
  candidatura. Duas recusas simultâneas ficam em fila e a segunda recebe 409. A escolha de candidato (ETI-69/70) deve
  travar o mesmo anúncio. Não há lock na listagem.
- **"Lista atualizada":** o controller chama `RecusarCandidatoUseCase` e depois `ListarCandidatosUseCase`, cada um na
  sua transação. A recusa já commitou quando a lista é lida. O usecase de recusa não devolve record, em linha com a
  regra de que `usecase` não conhece o contrato HTTP.
- **Evento:** `CandidaturaRecusadaEvent(candidaturaId, anuncioId, anuncioTitulo, contratanteId, prestadorId,
  ocorridoEm)`, em `comum/eventos/` porque `chat` (e depois `notificacao`) escutam sem importar `anuncio`. É publicado
  dentro da transação; o listener roda em `AFTER_COMMIT` + `REQUIRES_NEW`.
- **Checagem de `ENVIADA` no usecase**, não em `Aplicacao.recusar`: a entity não lança exceção de negócio no projeto.
- **Rota:** a ação é sub-recurso por `POST`, como `POST /anuncios/{id}/cancelar` (ETI-63).
- **Sem migration:** `aplicacao.recusada_em` existe desde a V13, `status` é `VARCHAR` sem `CHECK` e o motivo
  `CANDIDATURA_RECUSADA` já cabia em `chat.motivo_somente_leitura`.

## Testes

`spec/ListarCandidatosUseCaseTest`: dono errado (404), lista vazia, sem avaliações, candidato completo, conta
encerrada fora da lista.

`spec/RecusarCandidatoUseCaseTest`: caminho feliz (status, `recusadaEm`, evento), dono errado, anúncio inexistente,
anúncio fora de `PUBLICADO`, candidatura inexistente ou de outro anúncio, candidatura fora de `ENVIADA`.

`story/ListarCandidatosStoryTest` e `story/RecusarCandidatoStoryTest`: ponta a ponta via HTTP, com verificação do JSON
sem dados sensíveis, do `chatId`, de `recusadaEm`, dos demais candidatos intactos, do anúncio ainda `PUBLICADO`, do
evento (`@RecordApplicationEvents`) e do chat somente leitura só para o candidato recusado.

`chat/spec/ChatSomenteLeituraTest`: o listener de `CandidaturaRecusadaEvent`, com e sem chat.
