# ETI-65 — Avisar os prestadores compatíveis quando um anúncio é publicado

## Status

Implementado (só o pedido de aviso; o envio é do ETI-112).

## Link do card

[ETI-65](https://kaueamaral96.atlassian.net/browse/ETI-65) — [anuncio] [BACK-END] Avisar os prestadores compatíveis
quando um anúncio é publicado. Origem: Backlog US33 (sem card original no Jira). Relacionados: ETI-59 (criar e
publicar anúncio) e ETI-73 (mural e detalhe, US19/US20).

## User Story

Como plataforma, eu quero avisar os prestadores compatíveis assim que um anúncio é publicado, para que eles se
candidatem rápido.

## Escopo implementado

Rotina do sistema, sem rota nova. `AvisarPrestadoresCompativeisUseCase` escuta o `AnuncioPublicadoEvent` (publicado
pelo `PublicarAnuncioUseCase` desde o ETI-59) em `AFTER_COMMIT`, numa transação própria (`REQUIRES_NEW`, só
leitura), e publica um `comum/eventos/NovoServicoNaAreaEvent` por Prestador que deve ser avisado.

**Fora**: envio do aviso (push/e-mail/WhatsApp, canais ligados) e a tela 43, que são do card de notificações
(ETI-112, US55/US56). Até ele entrar, o evento é publicado e ninguém escuta. A "indexação" do UC já é atendida pelo
retrato do endereço gravado na criação (ETI-59) e pela consulta do mural (ETI-73).

## Regra de negócio

| Critério do card | Implementação |
|---|---|
| Mesma regra do mural: categoria nas especialidades, ou anúncio "Outros", ou Prestador com "Outros" | `PerfilPrestadorRepository.buscarCompativeis` (SQL nativo no `perfil`). "Outros" é `categoria.protegida`, como no resto do código. Exposto ao `anuncio` pela porta de leitura `comum/portas/ConsultaPrestadoresCompativeis` (ADR-0003). |
| Distância anúncio ↔ base ≤ `raioAtendimentoKm` | Haversine em km, a mesma fórmula da consulta do mural (`AnuncioRepository.MURAL_CANDIDATOS`), comparada com o raio de **cada** Prestador. |
| Só perfil completo | `perfil_prestador.completo`, na mesma consulta. |
| Não pausados | **Não existe "pausar" no código** (nem na develop nem nas PRs abertas). Hoje ninguém está pausado; quando o card de pausar entrar, o filtro vai na mesma consulta do `perfil`. Decisão do usuário em 2026-11-16. |
| Papel e conta aptos | `ConsultaDadosUsuario.filtrarAptosAReceberAviso` (novo, implementado no `auth`): conta não encerrada, não suspensa (`BLOQUEADA`) e com e-mail confirmado, a mesma exigência de ver o mural. **Papel ativo não filtra**: "papel apto" é ter o perfil de Prestador completo, e quem alterna para Empregador continua recebendo (decisão do usuário em 2026-11-16). |
| O dono nunca recebe aviso do próprio anúncio | `Anuncio.pertenceA`, no usecase, antes de consultar as contas. |
| Sem prestador no raio | Nada é publicado; o anúncio segue `PUBLICADO` (story confere). |
| Privacidade: só bairro e distância | O evento não tem campo de endereço completo nem de coordenada. `PrestadorCompativel` (porta) também não carrega coordenadas. |
| Tocar no aviso abre o detalhe (tela 30) | `NovoServicoNaAreaEvent.anuncioId`: o detalhe do mural (`GET /anuncios/mural/{id}`, ETI-73) já existe. |

Casos de borda: anúncio que sumiu ou saiu de `PUBLICADO` entre o commit e o listener não avisa ninguém; anúncio sem
coordenadas no retrato (só dado anterior ao ETI-59) também não, porque não há distância.

## Evento: `NovoServicoNaAreaEvent`

`comum/eventos/NovoServicoNaAreaEvent(destinatarioId, anuncioId, anuncioTitulo, categoriaNome, bairroRegiao,
distanciaKm, ocorridoEm)`: um evento por Prestador, como pede o card.

- `categoriaNome` e `bairroRegiao` são o que o card pede ("com a categoria, o bairro e a distância").
- `anuncioTitulo` também vai porque o Figma (tela 43) mostra "Troca de chuveiro em Perdizes, a 3,4 km". O
  `notificacao` escolhe o que mostrar.
- `distanciaKm` vai sem arredondar. Formatar "3,4 km" é papel de quem monta o texto.

Os campos vão soltos porque o `notificacao` não pode importar `modulos.anuncio` (ADR-0001).

## Decisões de implementação

- **A consulta fica no `perfil`, não no `anuncio`.** O mural (ETI-73) roda no `anuncio` porque lê a tabela
  `anuncio`. Aqui o conjunto varrido é `perfil_prestador`, dado do `perfil`. Por isso a porta nova é implementada no
  `perfil` (`ConsultaPrestadoresCompativeisService`), igual a `ConsultaPerfilPrestador`.
- **Sem caixa envolvente nem índice novo.** No mural o raio é um só (o do Prestador), então dá pra cortar por
  lat/lon antes. Aqui cada Prestador tem o próprio raio, e a varredura é sobre perfis completos, uma vez por
  publicação. Sem migration (a #38 aberta já usa V34/V35). Se o volume crescer, dá pra cortar pelo raio máximo
  (40 km) com um índice em `(latitude_base, longitude_base) WHERE completo`.
- **Conta e papel ficam no `auth`.** `DadosUsuario` não expõe suspensão, e mudar o record mexeria em todos os testes
  que o constroem. Um método de filtro em lote na porta resolve sem uma consulta por Prestador.
- **Falha não derruba a publicação.** O listener é `AFTER_COMMIT`: se ele falhar, o anúncio já está publicado e a
  exceção vira só log (mesmo comportamento do chat no `CandidaturaCriadaEvent`).
- **Categoria que sumiu vira exceção do módulo.** Se a categoria do anúncio não existir mais, o usecase lança
  `CategoriaDoAnuncioNaoEncontradaException` (com o id do anúncio na mensagem) em vez de `IllegalStateException`
  solta, e ninguém é avisado. Ajuste do review da PR #41.
- **Um evento por destinatário, falha isolada.** O ouvinte do `NovoServicoNaAreaEvent` no `notificacao` (ETI-112,
  PR #43) é `AFTER_COMMIT` + `REQUIRES_NEW`: cada aviso roda depois do commit desta transação e a exceção de um
  destinatário vira log sem interromper os demais.
- **Distância nunca nula.** `ConsultaPrestadoresCompativeisService` usa `Objects.requireNonNull` no desembrulho de
  `getDistanciaKm()`: perfil completo sempre tem coordenadas, e a falha fica explícita se isso mudar.

## Testes

- `spec/AvisarPrestadoresCompativeisUseCaseTest`: aviso com bairro e distância, um por Prestador apto, dono de fora,
  ninguém no raio (nem consulta contas), anúncio que saiu de `PUBLICADO`, inexistente e sem coordenadas,
  e categoria inexistente (exceção do módulo, sem aviso).
- `auth/spec/ConsultaDadosUsuarioServiceTest`: filtro de aptos (sem e-mail confirmado, suspensa e encerrada ficam
  de fora; papel ativo não importa) e lista vazia sem consultar o banco.
- `story/AvisarPrestadoresCompativeisStoryTest`, ponta a ponta pela rota real `POST /anuncios`: compatível por
  especialidade (com bairro, categoria e distância conferidos), por "Outros" no anúncio, por "Outros" no Prestador,
  Prestador navegando como Empregador, vários em ordem de distância, especialidade diferente, fora do raio, raio
  menor que a distância, perfil incompleto, dono do anúncio, sem e-mail confirmado e conta suspensa.
