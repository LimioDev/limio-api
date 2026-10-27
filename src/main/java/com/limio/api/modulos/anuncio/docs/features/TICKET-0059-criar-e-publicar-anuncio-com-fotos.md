# TICKET-0059 — Criar e publicar anúncio, com fotos

## Status

Implementado. O título usa a coluna criada pela PR #17 (V18).

## Link do card

ETI-59 ([anuncio] [BACK-END] Criar e publicar anúncio, com fotos). Origem: backlog US29 (criar e publicar) + US30
(anexar fotos). Card par: [anuncio] [FRONT-END] Telas "Novo anúncio", "Categoria" e "Anúncio publicado". Figma:
`14-novo-anuncio-1-v2.png`, `14a-escolher-categoria.png`, `14b-novo-anuncio-1-erro-titulo.png`,
`15-novo-anuncio-2-v2.png`, `16-anuncio-publicado.png`.

## User Story

Como um Empregador, eu quero que a API crie meu anúncio com fotos e o deixe aguardando candidatos, para que
prestadores próximos o vejam no mural e se candidatem.

## Contrato da API

O card deixou as rotas "a definir". O contrato segue o que o front-end par (limio-app#17, ETI-60) propôs,
menos `primeirosCandidatos`, que segue o card (iniciais; ver pendências).

### `POST /anuncios` — criar e publicar

```json
{
  "categoriaId": "…",
  "titulo": "Faxina do apartamento",
  "descricao": "Faxina completa do apartamento",
  "dataHoraPretendida": "2026-11-03T13:00:00Z",
  "enderecoSalvoId": "…",
  "enderecoAvulso": null,
  "tamanhoLocal": "72 m²",
  "numeroPessoas": 2,
  "observacoes": "Tem um cachorro."
}
```

`enderecoSalvoId` **ou** `enderecoAvulso` ( com os campos do "Novo endereço": `cep`, `rua`, `numero`, `complemento`,
`bairroRegiao`, `cidade`, `uf`), nunca os dois. Sem latitude/longitude: o servidor obtém.

Resposta `201` com `Location: /anuncios/{id}` e `{ "id": "…", "status": "PUBLICADO" }`.

### `POST /anuncios/{id}/fotos` — anexar fotos

`multipart/form-data`, uma ou mais partes `arquivos`. Resposta `200` com só as fotos gravadas neste envio:
`[ { "id": "…", "url": "https://…assinado…", "expiraEm": "…" } ]`.

### `GET /anuncios/{id}` — consultar meu anúncio (tela 16)

```json
{
  "id": "…", "status": "PUBLICADO", "titulo": "…", "descricao": "…",
  "categoria": { "id": "…", "nome": "Faxina", "grupo": "Limpeza" },
  "dataHoraPretendida": "…", "bairroRegiao": "Bela Vista", "uf": "SP",
  "enderecoCompleto": "Avenida Paulista, 1000 - Sala 5, Bela Vista, São Paulo - SP",
  "tamanhoLocal": "72 m²", "numeroPessoas": 2, "observacoes": null,
  "fotos": [ { "id": "…", "url": "…", "expiraEm": "…" } ],
  "candidatos": 4,
  "primeirosCandidatos": [ { "iniciais": "AL", "fotoUrl": "…", "fotoExpiraEm": "…" } ]
}
```

Só o dono consulta (outra pessoa recebe 404). O anúncio visto do mural, que não pode mostrar o endereço completo,
vai precisar de rota própria quando o card dele vier.

### Erros

| Situação | Resposta |
|---|---|
| Campo inválido, sem endereço ou com os dois | 400 `VALIDACAO_FALHOU` (o card sugeria `DADOS_INVALIDOS`; mantido o código que a API já usa) |
| Papel ativo Prestador | 403 `ACESSO_NEGADO` (o card sugeria `PAPEL_INVALIDO`, que a PR #16 remove; mesmo código das rotas de endereço salvo) |
| E-mail ou telefone não confirmado | 403 `CONTATOS_NAO_CONFIRMADOS` |
| Data e hora não estão no futuro | 422 `DATA_NO_PASSADO` |
| Categoria inexistente ou inativa | 422 `CATEGORIA_INVALIDA` |
| Endereço salvo inexistente, removido ou de outro Empregador | 404 `ENDERECO_NAO_ENCONTRADO` |
| Endereço avulso não localizado / geocodificador fora | 422 `ENDERECO_NAO_LOCALIZADO` / 503 `GEOCODIFICACAO_INDISPONIVEL` |
| Anúncio inexistente ou de outra pessoa (fotos e consulta) | 404 `ANUNCIO_NAO_ENCONTRADO` |
| Foto vazia, grande demais, fora de JPG/PNG ou acima do limite de quantidade | 422 `FOTO_INVALIDA` |
| Fotos em anúncio fora de `PUBLICADO` ou com prestador escolhido | 409 `ANUNCIO_ESTADO_INVALIDO` |
| Storage fora | 503 `ARMAZENAMENTO_INDISPONIVEL` (na consulta, a foto só sai sem link) |

## Regra de negócio

| Critério do card | Implementação |
|---|---|
| Categoria obrigatória, uma, entre as ativas (inclui "Outros") | `categoriaId` `@NotNull`; `PublicarAnuncioUseCase` confere `ativa` pela porta `ConsultaCategoria` |
| Título 5–80, sem espaços nas pontas | `@NotBlank` + `@Size(min = 5, max = 80)` depois do `strip()`; coluna `VARCHAR(100)` da V18 (PR #17) |
| Descrição obrigatória, até 600 | `@NotBlank` + `@Size(max = 600)`, depois de tirar os espaços das pontas |
| `dataHoraPretendida` obrigatória e no futuro | `@NotNull`; "no futuro" no usecase (`DATA_NO_PASSADO`) |
| Endereço salvo ou avulso | `EnderecoDoAnuncio.Salvo`/`Avulso`; avulso com os limites do `EnderecoSalvoRequest` |
| Opcionais `tamanhoLocal`, `numeroPessoas`, `observacoes`; sem valor | `tamanhoLocal` até 100 (coluna da V13), `numeroPessoas` positivo, `observacoes` até 600 (ver pendências) |
| Grava `PUBLICADO`, ligado ao Empregador, à categoria e ao endereço salvo | `Anuncio.publicar`. O "Empregador" é `contratante_id` = id do usuário, como o painel (TICKET-0049/0052) já usa |
| Retrato do endereço (bairro, endereço completo, lat/long) | `bairro_regiao_snapshot`, `uf_snapshot` (V21), `endereco_completo_snapshot`, `latitude_snapshot`, `longitude_snapshot`, vindos de `ConsultaEndereco` |
| `prazoValidacaoHoras` = 24h | `anuncio.prazo-validacao-horas=24` |
| Publica `AnuncioPublicado` | `eventos/AnuncioPublicadoEvent(anuncioId)`, dentro da transação. Ninguém escuta ainda (ETI-65) |
| Só com telefone e e-mail confirmados e papel `EMPREGADOR` | `PublicarAnuncioUseCase`: papel do token e `ConsultaDadosUsuario` |
| Fotos: mesma estrutura da foto de perfil, sem EXIF, `Midia` `FOTO_ANUNCIO` ligada ao anúncio | `AnexarFotosAnuncioUseCase` com `comum/midia` (`ImagemHelper`, `ArmazenamentoService`, `Midia.fotoAnuncio`) |
| Fotos só em `PUBLICADO` sem `ESCOLHIDA` | `Anuncio.aceitaFotos` + `AplicacaoService.existeEscolhida` (`ESCOLHIDA`/`AGUARDANDO_VALIDACAO`, via `StatusAplicacao.isEscolhida()`) |
| Mural mostra só bairro | Nada daqui vai pro mural; `GET /anuncios/{id}` é só do dono e nem ele recebe lat/long |
| Só o dono anexa e consulta; outra pessoa → 404 | `Anuncio.pertenceA` nos dois usecases |
| Migração nova com `categoria_id` obrigatória, sem alterar a V13 | `V20` (ver Banco) |
| `midia.anuncio_id` + `FOTO_ANUNCIO`; `Midia` sobe pra `comum/` | `V20` + `comum/midia` |
| Consultar: dados, fotos, candidatos `ENVIADA` com iniciais/fotos dos primeiros | `ConsultarMeuAnuncioUseCase` (3 primeiros, mais antigos primeiro) |

## Decisões técnicas

- **Portas `ConsultaCategoria` e `ConsultaEndereco` em `comum/portas/`** (ADR-0003): categoria, endereço salvo e
  geocodificador são de `perfil`. O texto de uma linha do endereço é montado num lugar só (`ConsultaEnderecoService`),
  então salvo e avulso saem iguais.
- **Mídia em `comum/midia/`**: o card pede, e é o critério do ADR-0001 (dois módulos gravando). A foto de perfil do
  candidato, na consulta, sai direto do `MidiaService` (agora de `comum`).
- **Geocodificação fora da transação**: igual ao `CriarEnderecoSalvoUseCase` (#14). Validações e consultas antes,
  `TransactionOperations` só pra gravar e publicar o evento.
- **Fotos tudo ou nada**: todas conferidas antes do primeiro upload; cada upload publica `FotoAnuncioEnviadaEvent`, e
  `FotoAnuncioEventosService` apaga o objeto se a transação desfizer. Advisory lock `EscopoTrava.FOTOS_ANUNCIO` por
  anúncio, pra o limite de quantidade valer em envios simultâneos.
- **Multipart**: `spring.servlet.multipart.max-request-size` subiu de 10MB pra 30MB (5 fotos de 5MB num envio).
- **`endereco_salvo_id` no anúncio**: o card pede "ligar ao EnderecoSalvo"; a coluna é só referência, o que vale é o
  retrato.

## Banco (Flyway)

`V20__adicionar_categoria_ao_anuncio_e_fotos_de_anuncio.sql` (V15–V17 são da #16, V18 da #17, com o título, V19 da #14):
- `anuncio.categoria_id UUID NOT NULL` (linha antiga vai pra "Outros") e `anuncio.endereco_salvo_id UUID`, sem FK
  (tabelas de `perfil`, ADR-0001 §1.6).
- `midia.anuncio_id UUID`, `CHECK` "só `FOTO_ANUNCIO` tem anúncio" e índice parcial das fotos ativas por anúncio.

### `V21__adicionar_uf_ao_retrato_do_anuncio.sql`

`anuncio.uf_snapshot VARCHAR(2)`: a tela 16 mostra "Bairro · UF". Migração nova porque a V20 já foi aplicada no
banco do Railway. Linha antiga tira a UF do fim do `endereco_completo_snapshot`.

## Pendências

- **Grupo: candidatos na tela 16.** O card fala em "iniciais/fotos dos primeiros"; o front (limio-app#17) mostra até 4
  com nome abreviado ("Rafael S."). A API devolve 3 com `iniciais` até o grupo decidir.

- **PO: fotos** — formato, tamanho e quantidade ainda não decididos. Hoje: JPG/PNG, 5MB cada, até 5 por anúncio
  (`anuncio.foto.*`, configurável).
- **Lacunas do card** (não decididas aqui, pra discussão): limite de `observacoes` (usado 600, como a descrição);
  quantos são "os primeiros" candidatos (usado 3); se o painel (TICKET-0052) passa a mostrar a primeira foto do
  anúncio (`fotoUrl` continua `null`, fora do escopo deste card).
- **ETI-65**: escutar `AnuncioPublicadoEvent` em `AFTER_COMMIT`.

## Testes

- `spec/PublicarAnuncioUseCaseTest` (9), `spec/AnexarFotosAnuncioUseCaseTest` (10),
  `spec/ConsultarMeuAnuncioUseCaseTest` (5), `spec/NomeHelperTest` (+3, iniciais),
  `perfil/spec/ConsultaEnderecoServiceTest` (4).
- `story/CriarEPublicarAnuncioStoryTest` (17): criar com endereço salvo e com avulso, data no passado, sem contatos
  confirmados, papel Prestador, os dois endereços / nenhum, descrição > 600, endereço salvo de outro, categoria
  inativa, fotos válidas (sem EXIF), foto inválida, foto com candidatura `ESCOLHIDA`, fotos em anúncio de outro,
  limite de 5, consulta do dono e de outra pessoa. Título vazio, nulo, só com espaços, com menos de 5 ou
  mais de 80 caracteres (parametrizado).
