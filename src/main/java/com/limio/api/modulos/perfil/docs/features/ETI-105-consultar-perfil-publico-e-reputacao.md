# ETI-105 — Consultar o perfil público e a reputação

## Link do ticket

`feature/105` — [perfil] [BACK-END] Consultar o perfil público e a reputação. Origem: Backlog US52 ·
[perfil] Ver o perfil público e a reputação (sem card original no Jira). Card par: [perfil] [FRONT-END]
Telas "Perfil público" (candidato e consulta). Relacionado: card de avaliação (US53/ETI-107, "avaliar a
outra parte") — mergeado em `develop` durante o desenvolvimento deste card; o plano inicial era construir
sobre um esqueleto (`moderacao` sem entidade), mas a entidade `Avaliacao` chegou primeiro, então o card
usa dados reais em vez de um stub (ver decisões abaixo). Insumos: Figma LIMIO
(20a-perfil-publico-candidato, 20b-perfil-publico-consulta), planilha "Visualizar perfil público e
reputação" / "Exibir média somente com avaliações".

## User Story

Como usuário, eu quero que a API mostre o perfil público e a reputação de outra pessoa, sem dados de
contato, para que eu decida se confio nela.

## Regra de negócio implementada

- **Consultar perfil público**: id do usuário + papel do perfil (`EMPREGADOR` ou `PRESTADOR`) → nome de
  exibição, foto, média e quantidade de avaliações, serviços concluídos e (só Prestador) especialidades.
- **Nunca devolve** endereço, telefone, e-mail ou WhatsApp — o contrato não tem campo pra isso (não é uma
  omissão de mapper, o tipo de domínio nem carrega o dado).
- **Média só com avaliação**: `mediaAvaliacoes` é `null` enquanto `qtdAvaliacoes == 0`; nunca `0,0`.
- **Perfil anonimizado**: conta encerrada devolve 200 com `nomeExibicao = "Conta encerrada"` (não
  "Usuário excluído" — ver decisão abaixo), sem foto, sem especialidades. Quantidade/média de avaliações e
  serviços concluídos continuam aparecendo: é histórico de reputação, não dado pessoal.
- **Usuário inexistente**: 404. Diferente de conta encerrada — a conta existiu e foi encerrada; aqui o id
  nunca existiu.
- **Acesso**: qualquer usuário logado, inclusive para o próprio perfil. Sem checagem de "é meu perfil ou de
  outro": a regra é só estar autenticado, que já é o padrão de `SecurityConfig`.

## Decisões técnicas

| Decisão | Por quê / alternativa descartada |
|---|---|
| "Conta encerrada" em vez de "Usuário excluído" (texto do card) | O encerramento de conta já usa esse literal em dois lugares (`Usuario.encerrar`, `modulos.auth`, e `PerfilEmpregador.anonimizar`, TICKET-0034). Inventar um segundo texto pro mesmo estado quebraria a consistência entre telas sem nenhum ganho — o Figma do card é só ilustrativo, a regra de produto já estava decidida. |
| Nome de exibição por papel: Empregador usa `PerfilEmpregador.nomeExibicao`; Prestador usa o nome completo do cadastro abreviado por `NomeHelper` | Não existe "nome de exibição" próprio do Prestador no modelo atual — só `PerfilEmpregador` tem esse campo editável (TICKET-0048). Como terceiro, um Prestador já aparece assim pra quem lista candidatos (ETI-67) e no painel: reaproveitar a mesma regra em vez de inventar uma nova "identidade pública" pro Prestador. |
| `NomeHelper` promovido de `modulos.anuncio.actions.helper` pra `comum/texto` | Critério do ADR-0001 pra subir algo a `comum/`: 2+ módulos precisando *hoje*. Com `modulos.perfil` passando a abreviar o nome do Prestador do mesmo jeito que `modulos.anuncio` (ETI-67/TICKET-0052), duplicar as ~20 linhas violaria o próprio critério do projeto. Mudança mecânica (só pacote + imports), sem tocar lógica nem os testes existentes. |
| Quantidade/média de avaliações: mesma fonte (colunas de `PerfilEmpregador`/`PerfilPrestador`) nos dois papéis | ETI-107 deu a `PerfilPrestador` as mesmas colunas `media_avaliacoes`/`qtd_avaliacoes` que `PerfilEmpregador` já tinha desde o TICKET-0048, gravadas por `RegistrarMediaAvaliacoesUseCase` sempre que uma avaliação é publicada. Aqui é leitura pura, simétrica — nada de porta pra isso; o módulo já é dono dos dois perfis. |
| `ConsultaAvaliacoes` ganhou `listarRecentes(avaliadoId, papel, limite)` e passou a ser implementada em `modulos.moderacao` (antes o `resumir` morava, como esqueleto, em `modulos.perfil`) | Precisava de "avaliações recentes: nome abreviado, nota, comentário, data" pros dois papéis — método novo, não existia. Como a entidade `Avaliacao` chegou real (ETI-107) durante este card, a implementação nasceu direto no módulo dono do dado (`moderacao`), com uma query em lote nova (`AvaliacaoRepository.resumirPorAvaliados`, projeção `ResumoAvaliacaoProjecao`) e outra pro top-N recentes (`findByAvaliadoIdAndPapelAvaliadoOrderByPublicadaEmDesc`). O stub antigo em `perfil` foi removido — não dá pra ter duas implementações da mesma porta no contexto Spring. `resumir` ganhou `papel` como parâmetro implícito fixo (`PRESTADOR`, mesmo contrato de antes) porque é o único consumidor (ETI-67) e nunca precisou do Empregador. |
| Avaliador com conta encerrada aparece como "Conta encerrada" em `avaliadorNome`, em vez de cair fora da lista | Mesma reputação, mesma nota e comentário continuam valendo — só a identidade de quem avaliou é que não existe mais. Sem essa regra, `ConsultaDadosUsuario.buscarPorId` vazio (conta encerrada) quebraria a montagem do nome abreviado. |
| `avaliacao` ainda não tem `ocultaEm` (moderação de conteúdo) | ETI-107 implementou a nota/comentário, não a denúncia/moderação (`modulos.moderacao` continua esqueleto nessa parte — ver `package-info.java`). O critério "avaliação oculta não aparece" do card segue documentado na porta, mas não há nada pra filtrar hoje. |
| Nova porta `ConsultaHistoricoDeServicos` (`contarConcluidos(usuarioId, papel)`), implementada em `modulos.anuncio` | "Quantidade de serviços concluídos" não existia em lugar nenhum. Contagem por `status` (`CONCLUIDO` do anúncio pro Empregador, `CONCLUIDA` da candidatura pro Prestador) usando os índices já existentes (`idx_anuncio_contratante_status`, `idx_aplicacao_prestador_status`) — nenhuma migration nova. |
| `ConsultaDadosUsuario` ganhou `existe(usuarioId)` | Precisava diferenciar "usuário nunca existiu" (404) de "conta encerrada" (200, perfil anonimizado) — `buscarPorId` funde os dois casos de propósito (contrato da porta, pensado pra quem não precisa diferenciar). Implementado com `UsuarioService.existePorId`, sem nova query custosa. |
| Rota `GET /perfis-publicos/{usuarioId}?papel=` | O card deixa "a definir". `usuarioId` na rota (é o recurso), `papel` em query porque o mesmo id pode ter perfil nos dois papéis (um usuário já atuou como Empregador e Prestador). |
| Sem `@AuthenticationPrincipal` no controller | Nada no usecase depende de quem está logado — "qualquer usuário logado, inclusive o próprio perfil" não tem regra diferente pro dono. A exigência de login é só o filtro padrão (`anyRequest().authenticated()`); declarar o parâmetro sem usá-lo seria morto. |

## Testes

`spec/ConsultarPerfilPublicoUseCaseTest`: usuário inexistente (404), Empregador com/sem avaliações, Prestador
com especialidades e avaliações (e sem perfil de atuação), conta encerrada (Empregador e Prestador, perfil
nunca aberto).

`moderacao/spec/ConsultaAvaliacoesServiceTest`: `resumir` em lote (ids vazios, com/sem avaliação, arredondamento
comercial de uma casa), `listarRecentes` com avaliador de conta ativa (nome abreviado) e encerrada ("Conta
encerrada"), sem avaliação (lista vazia).

`story/PerfilPublicoStoryTest`: Empregador com avaliações/foto/histórico, Empregador que nunca abriu o
perfil, Prestador com serviços concluídos, avaliações recentes reais (`Avaliacao` semeada pelo repository,
lidas pela implementação real de `ConsultaAvaliacoesService` em `moderacao`, sem mock), conta encerrada
ponta a ponta (via `POST /auth/encerrar-conta`), usuário inexistente (404), papel inválido (400), sem token
(401), próprio perfil. Anúncio/candidatura concluídos e a avaliação nascem direto pelo repository (mesmo
idioma de `anuncio.story.ConsultarServicoStoryTest`): a média/quantidade do perfil é gravada direto pelo
repository também, sem publicar `AvaliacaoPublicadaEvent` — esse listener (`RegistrarMediaAvaliacoesUseCase`)
já tem story test próprio em `moderacao` (`AvaliarAOutraParteStoryTest`); aqui o que importa é o perfil
público ler a coluna certa, não re-testar a escrita.

**Limitação conhecida**: "avaliação oculta pela moderação" não é testável ainda — a entidade `Avaliacao`
não tem campo `ocultaEm` (denúncia/moderação de conteúdo continua esqueleto em `modulos.moderacao`). O
contrato já está documentado na porta; falta a implementação da moderação em si, fora do escopo deste card.

## Contrato da API (compartilhado com o card [FRONT-END])

| Operação | Rota | Autenticação | Entrada | Saída |
|---|---|---|---|---|
| Consultar perfil público | `GET /perfis-publicos/{usuarioId}?papel=EMPREGADOR\|PRESTADOR` | `Bearer <token>`, qualquer papel | — | 200 `PerfilPublicoResponse` |

```json
{
  "nomeExibicao": "Mari",
  "fotoUrl": "https://bucket.s3.amazonaws.com/fotos-perfil/....jpg?...",
  "fotoExpiraEm": "2026-11-10T12:00:00Z",
  "mediaAvaliacoes": 4.5,
  "qtdAvaliacoes": 8,
  "servicosConcluidos": 12,
  "especialidades": [],
  "avaliacoesRecentes": [
    { "avaliadorNome": "Camila R.", "nota": 5, "comentario": "Ótimo!", "data": "2026-10-20T14:00:00Z" }
  ]
}
```

`CodigoErro`: `PERFIL_NAO_ENCONTRADO` (404, usuário inexistente); `VALIDACAO_FALHOU` (400, `papel` fora de
`EMPREGADOR`/`PRESTADOR`).
