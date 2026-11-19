# ETI-136 — Devolver o id do usuário nas telas que abrem o perfil público

## Status

Implementado.

## Link do card

[ETI-136](https://kaueamaral96.atlassian.net/browse/ETI-136) — [anuncio/chat] [BACK-END] Devolver o id do usuário nas
telas que abrem o perfil público. Origem: Backlog US52. Card pai: ETI-105 (consultar o perfil público e a
reputação). Card par: ETI-106 ([FRONT-END] telas "Perfil público").

## User Story

Como usuário, eu quero que o app saiba de quem é cada candidato, anunciante ou conversa, para que, ao tocar na pessoa,
eu abra o perfil público dela.

## Contrato da API

Só um campo novo, `usuarioId` (UUID), em três respostas. Nenhum `CodigoErro` novo, nenhuma migration.

| Operação | Rota | Mudança na saída | Papel pro perfil público |
|---|---|---|---|
| Listar candidatos | `GET /anuncios/{id}/candidatos` | `candidatos[].usuarioId` (Prestador) | `PRESTADOR` |
| Detalhe do anúncio no mural | `GET /anuncios/mural/{id}` | `empregador.usuarioId` (dono do anúncio) | `EMPREGADOR` |
| Consultar chat | `GET /candidaturas/{candidaturaId}/chat` | `outraParte.usuarioId` | `PRESTADOR` se `outraParte.papel` é `CANDIDATO`, senão `EMPREGADOR` |

O card cita `GET /mural/{id}`; a rota real do detalhe do mural é `GET /anuncios/mural/{id}` (ETI-73).

O `usuarioId` é o mesmo que `GET /perfis-publicos/{usuarioId}?papel=` (ETI-105) aceita.

## Regra de negócio

| Critério do card | Implementação |
|---|---|
| Só o id é exposto | Nenhum outro campo novo; os records continuam sem endereço, telefone, e-mail ou WhatsApp. |
| Conta encerrada: o id continua vindo | No chat, `OutraParte.contaEncerrada(usuarioId, papel)` mantém o id junto do "Usuário excluído". A story abre o perfil público com esse id e recebe "Conta encerrada". |
| Sem migration | Os ids já estavam nas entities: `Aplicacao.prestadorId` (candidato), `Anuncio.contratanteId` (mural), `Chat.outraParte` (chat). |

**Conta encerrada nas outras duas telas:** candidatura de conta encerrada já vira `ENCERRADA` (TICKET-0049) e sai da
lista de candidatos, e o anúncio de conta encerrada sai do mural. Por isso o caso só aparece no chat.

## Decisões de implementação

- **Mural:** o id do dono vai no `DetalheDoMural` (`empregadorId`, de `Anuncio.contratanteId`), não na porta
  `ResumoPerfilEmpregador`. A porta é do `perfil` e é usada por outras telas; o dado já está no anúncio.
- **Candidato:** `CandidatoDoAnuncio` ganhou `usuarioId`, que o `ListarCandidatosUseCase` já tinha (`prestadorId`).
- **Chat:** `OutraParte` (domínio do chat) ganhou `usuarioId`, que o `AbrirChatUseCase` já calculava
  (`chat.outraParte(usuarioId)`).

## Testes

- `ListarCandidatosStoryTest`: cada candidato traz o `usuarioId` do Prestador.
- `ConsultarDetalheDoMuralStoryTest`: `empregador.usuarioId` é o dono do anúncio.
- `ChatDaCandidaturaStoryTest`: `outraParte.usuarioId` visto pelo Empregador e pelo Prestador; com conta encerrada, o
  id continua e abre o perfil público com "Conta encerrada".
- Specs ajustadas: `MuralMapperTest` (id no mapeamento) e `AbrirChatUseCaseTest` (id da outra parte, inclusive conta
  encerrada).
