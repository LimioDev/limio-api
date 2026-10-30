# ETI-73 — Listar o mural e o detalhe do anúncio para o Prestador

## Status

Implementado. Pontos marcados **a confirmar** foram decididos aqui porque o card não os define.

## Link do card

ETI-73 ([anuncio] [BACK-END] Listar o mural e o detalhe do anúncio para o Prestador). Backlog US19 (ver o mural) +
US20 (filtrar o mural): ficaram juntas porque a regra de compatibilidade da US20 é aplicada pela própria consulta do
mural. Card par: [anuncio] [FRONT-END] Telas "Mural", "Filtros", "Mural vazio" e "Detalhe do anúncio".

## User Story

Como um Prestador, eu quero que a API traga só os anúncios compatíveis comigo, sem dados de contato, para que eu
encontre serviços para fazer.

## Contrato da API

Os dois exigem login e papel ativo `PRESTADOR`.

### `GET /anuncios/mural`

| Parâmetro | Tipo | Padrão | Regra |
|---|---|---|---|
| `categorias` | UUID, repetível (`?categorias=a&categorias=b`) ou separado por vírgula | sem filtro | interseção com a compatibilidade |
| `raio` | inteiro, 1 a 40 (km) | raio do Prestador | só reduz: `min(raio, raio do Prestador)` |
| `quando` | `qualquer`, `esta-semana`, `fim-de-semana` (aceita espaço/sublinhado) | `qualquer` | recorte de data; valor desconhecido → 400 `VALIDACAO_FALHOU` |
| `pagina` | inteiro ≥ 0 | 0 | |
| `tamanho` | inteiro, 1 a 50 | 20 | |

```json
{
  "content": [
    {
      "id": "…",
      "titulo": "Trocar chuveiro",
      "categoria": { "id": "…", "nome": "Elétrica", "grupo": "Casa e reparos" },
      "fotoUrl": "https://…assinado…",
      "empregador": { "nomeExibicao": "Maria S.", "fotoUrl": null },
      "dataHoraPretendida": "2026-11-05T13:00:00Z",
      "bairroRegiao": "Cambuí",
      "distanciaKm": 3.2
    }
  ],
  "pagina": 0,
  "tamanho": 20,
  "totalElementos": 9,
  "totalPaginas": 1
}
```

`totalElementos` é o total do **filtro inteiro** (não só da página): é o "9 serviços perto de você" da tela.
`fotoUrl` é a primeira foto do anúncio (link temporário), `null` sem foto ou com o storage fora do ar.

### `GET /anuncios/mural/{id}`

```json
{
  "id": "…",
  "titulo": "Trocar chuveiro",
  "descricao": "…descrição completa…",
  "categoria": { "id": "…", "nome": "Elétrica", "grupo": "Casa e reparos" },
  "fotos": [ { "id": "…", "url": "https://…", "expiraEm": "2026-11-01T18:00:00Z" } ],
  "dataHoraPretendida": "2026-11-05T13:00:00Z",
  "bairroRegiao": "Cambuí",
  "cidade": "Campinas",
  "uf": "SP",
  "distanciaKm": 3.2,
  "empregador": { "nomeExibicao": "Maria S.", "fotoUrl": null, "mediaAvaliacoes": 4.5, "qtdAvaliacoes": 8 },
  "minhaCandidatura": null
}
```

`minhaCandidatura` é `{ "id": "…", "status": "ENVIADA" }` (a candidatura do Prestador neste anúncio; `status` é um
`StatusAplicacao`: `ENVIADA`, `RECUSADA`, …), ou `null` se ele ainda não se candidatou: aí a tela mostra
"Candidatar-se". O `id` é o da candidatura e serve para o app abrir o chat dela (`/chat/{aplicacaoId}`). `mediaAvaliacoes` é `null` com
`qtdAvaliacoes` 0. `cidade` é `null` em anúncio antigo cujo endereço não pôde ser lido na migração.

### Respostas

| Situação | Resposta |
|---|---|
| Sucesso | 200 (mural: `content` vazio e total 0 se nada é compatível) |
| Sem token | 401 `NAO_AUTENTICADO` |
| Papel ativo ≠ `PRESTADOR` | 403 `ACESSO_NEGADO` ("Só o Prestador pode ver o mural de anúncios.") |
| E-mail ou telefone não confirmado, ou conta encerrada | 403 `CONTATOS_NAO_CONFIRMADOS` |
| Sem perfil de atuação completo | 403 `PERFIL_INCOMPLETO` — o front leva ao perfil de atuação |
| Detalhe: anúncio inexistente ou não visível | 404 `ANUNCIO_NAO_ENCONTRADO` |
| `raio`/`pagina`/`tamanho` fora da faixa, `quando` ou id inválido | 400 |

Ordem das validações: papel → contatos → perfil completo.

## Regra de negócio

### Compatibilidade (fixa, aplicada no banco)

Um anúncio entra no mural se:

- está `PUBLICADO` (no código "aguardando candidatos" é `PUBLICADO`; não existe valor `AguardandoCandidatos`);
- não tem candidatura `ESCOLHIDA`;
- não é do próprio usuário;
- **(categoria do anúncio ∈ especialidades do Prestador) ∨ (categoria do anúncio é "Outros") ∨ (o Prestador tem "Outros")**;
- a distância (haversine, esfera de 6371 km) entre o retrato do anúncio (`latitudeSnapshot`/`longitudeSnapshot`) e a
  base do Prestador (`latitudeBase`/`longitudeBase`) é **menor ou igual** ao `raioAtendimentoKm`;
- a data pretendida é futura (**a confirmar**: o card não diz; serviço no passado não faz sentido no mural).

A regra de horário **não** bloqueia o match: as janelas de atuação do Prestador não são lidas. FREE e GOLD veem
exatamente o mesmo (não existe plano no código; não há prioridade para GOLD). Especialidades vazias → mural vazio
sem erro (na prática o perfil sem especialidade já é incompleto, ver Pendências).

"Outros" é a categoria com `protegida = TRUE` (o id é gerado na migration, não é fixo): a porta
`ConsultaPerfilPrestador` devolve o id dela junto das especialidades.

### Filtros (só reduzem o conjunto compatível)

- `categorias`: `AND categoria ∈ filtro`. Categoria fora da compatibilidade não amplia nada.
- `raio`: `min(raio, raio do Prestador)`. Raio maior que o do Prestador não amplia.
- `quando` (fuso `America/Sao_Paulo`, semana de segunda a domingo — **a confirmar**):
  - `qualquer`: de agora em diante;
  - `esta-semana`: de agora até a próxima segunda 00:00;
  - `fim-de-semana`: sábado 00:00 até segunda 00:00 do fim de semana mais próximo (no domingo, ainda é "este").

### Ordem e página

Mais perto primeiro, depois data, depois id (estável entre páginas). Sem `Sort` do cliente.

### Detalhe: quem enxerga (**a confirmar**)

O card não define. Implementado: o anúncio aparece se está `PUBLICADO`, sem escolhido e não é do próprio Prestador.
Quem já tem candidatura nele continua vendo em qualquer estado (para acompanhar). **Não** exige casar especialidade
nem raio: um link recebido não deve dar 404 por isso. Qualquer outro caso é 404 — igual a inexistente, para não
revelar que existe. O detalhe também exige perfil completo (a base dele dá a distância).

### O que nunca sai (mural e detalhe)

Endereço completo, logradouro, telefone, e-mail, WhatsApp, latitude e longitude. Os records de resposta
(`MuralItemResponse`, `MuralDetalheResponse`) não têm esses campos; os stories conferem que o JSON não contém
nenhum deles. A localização é só bairro/cidade/UF + distância arredondada a 0,1 km (distâncias exatas, em várias
consultas, permitiriam triangular o endereço).

## Decisões técnicas

- **Mural no módulo `anuncio`** (dono do dado). O que é do Prestador chega pela porta nova
  `comum/portas/ConsultaPerfilPrestador` (implementada em `perfil`), ADR-0003: portas para consulta. Descartado:
  mural em `perfil` (leria a tabela `anuncio`); join com `perfil_prestador` no repository de `anuncio` (acopla
  schemas); read-model alimentado por `AnuncioPublicadoEvent` (complexidade sem necessidade agora).
- **Porta devolve vazio para perfil inexistente *ou* incompleto**: pro mural os dois são `PERFIL_INCOMPLETO`, e o
  critério de "completo" é provisório (TICKET-0054) e fica só no módulo dono.
- **Query nativa**: JPQL não faz haversine. Nada de PostGIS/QueryDSL (não há no projeto). Uma consulta de página +
  um count com o mesmo `WHERE` (constante `AnuncioRepository.MURAL_CANDIDATOS`). Caixa envolvente de lat/lon
  (110 km/° — conservador) antes do cálculo, para usar índice. Aliases entre aspas porque a projeção casa por nome.
- **Sem N+1**: por página, categorias (`ConsultaCategoria.buscarPorIdsEmLote`), Empregadores
  (`ConsultaPerfilEmpregador.buscarResumosEmLote`) e primeira foto (`MidiaService.buscarPrimeirasFotosDosAnuncios`,
  `DISTINCT ON`) em uma consulta cada. `buscarResumosEmLote` não cria perfil (o mural é `readOnly`): quem nunca abriu
  o perfil sai com o nome inicial calculado na hora.
- **Usecase sem `records/`**: recebe tipos primitivos e o enum `QuandoMural`; devolve `Page<ItemDoMural>` /
  `DetalheDoMural` (domínio). O mapper (só o controller chama) converte e arredonda a distância.
- **`quando` aceita kebab-case**: `QuandoMuralConverter` (`esta-semana`), em vez de expor constantes Java.
- **Cidade no retrato do anúncio**: coluna nova `cidade_snapshot` preenchida na publicação (`EnderecoLocalizado`
  ganhou `cidade`), em vez de extrair do endereço de uma linha na leitura — essa linha só o dono e o escolhido veem, e
  parsear texto formatado é frágil. Backfill por regex na migração; se não casar, fica `NULL`.
- **Regra de contatos compartilhada**: `DadosUsuario.contatosConfirmados()` (de `comum/portas`), a mesma de
  `PublicarAnuncioUseCase`; `ContatosNaoConfirmadosException` ganhou construtor com mensagem (a padrão fala em "publicar").
- **Papel inválido reaproveita `ACESSO_NEGADO`**, como `SomenteEmpregadorPublicaException`; o card sugere
  `PAPEL_INVALIDO`, que não existe e foi descartado (**a confirmar** com o front).
- **`PerfilIncompletoException` → 403** (mesma família de `CONTATOS_NAO_CONFIRMADOS`).

## Banco (Flyway)

- `V25__indexar_anuncio_para_o_mural.sql`: índices parciais (`status = 'PUBLICADO'`) em
  `(latitude_snapshot, longitude_snapshot)` e `(categoria_id, data_hora_pretendida)`.
- `V26__adicionar_cidade_ao_retrato_do_anuncio.sql`: `anuncio.cidade_snapshot` + backfill.

## Pendências

- **Perfil sem especialidade**: o card pede "especialidades vazias → mural vazio, sem erro", mas
  `PerfilPrestador.calcularCompleto()` exige ao menos uma especialidade; então esse perfil recebe
  `PERFIL_INCOMPLETO`. O caminho de mural vazio existe (`AnuncioService.buscarMural` curto-circuita) mas só é
  alcançável se o critério de "completo" mudar (pendência do PO, TICKET-0054).
- **Candidatura**: ainda não há fluxo de candidatar-se; `minhaCandidatura` só reflete linhas de `aplicacao`
  existentes. Também não há unicidade (anúncio, prestador) — com mais de uma, vale a mais recente.
- **Avaliações**: a média/quantidade são lidas de `perfil_empregador`; nada as atualiza ainda (vêm `null`/0).
- **Semântica de `quando`, fuso, data futura e visibilidade do detalhe**: a confirmar com o PO (ver acima).

## Testes

- `spec/ListarMuralUseCaseTest` (12): papel, contatos (e-mail, telefone, conta encerrada), perfil incompleto,
  composição do critério (raio mínimo, categorias, janela), página vazia sem consultas em lote, montagem em lote e
  storage indisponível.
- `spec/ConsultarDetalheDoMuralUseCaseTest` (11): pré-condições, inexistente, dados completos e fotos,
  candidatura, candidatura em anúncio encerrado, próprio, não publicado, com escolhido.
- `spec/JanelaDoMuralHelperTest` (8), `spec/QuandoMuralConverterTest` (2), `spec/MuralMapperTest` (3),
  `spec/DistanciaHelperTest` (4), `perfil/spec/ConsultaCategoriaServiceTest` (2),
  `perfil/spec/ConsultaPerfilPrestadorServiceTest` (4), `comum/records/PageResponseTest` (3) e os novos casos de
  `ConsultaPerfilEmpregadorServiceTest` (lote) / `ConsultaEnderecoServiceTest` (cidade).
- `story/ConsultaDoMuralStoryTest` (12, Postgres real): especialidade, raio no limite, "Outros" nos dois lados,
  sem especialidades, status/próprio, `ESCOLHIDA`, filtros, janela de data, paginação e total, primeira foto.
- `story/ListarMuralStoryTest` (14): ponta a ponta com HTTP — compatibilidade e ordem, filtros que só reduzem,
  paginação, parâmetros inválidos, campos sem dado sensível, FREE = GOLD, 403/401.
- `story/ConsultarDetalheDoMuralStoryTest` (14): detalhe completo com 3 fotos, candidatura própria/de outro, fora do
  raio ainda abre, 404 (inexistente, próprio, encerrado, com escolhido), erros e a regex da V26.
