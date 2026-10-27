# Manual de funcionamento — perfil

## `GET /conta/perfil-empregador` (TICKET-0048)

1. `JwtAuthFilter` (`comum/seguranca/`) lê o token e coloca `UsuarioAutenticado` no `SecurityContext`; sem token a
   resposta é 401.
2. `PerfilEmpregadorController.consultar` recebe `@AuthenticationPrincipal UsuarioAutenticado` e chama
   `ConsultarMeuPerfilUseCase.executar(usuario.id())` — o id vem só do token.
3. O usecase chama `PerfilEmpregadorService.garantirPerfil`, regra única dos dois endpoints. Ele pede os dados do
   usuário à porta `ConsultaDadosUsuario` (`comum/portas/`, implementada por `ConsultaDadosUsuarioService` no `auth`).
   Vazio (usuário inexistente ou conta encerrada) → `PerfilEmpregadorNaoEncontradoException` (404,
   `PERFIL_EMPREGADOR_NAO_ENCONTRADO`).
4. Ainda em `garantirPerfil`, o perfil é devolvido ou criado no primeiro acesso, com o nome completo do cadastro
   (`PerfilEmpregador.nomeExibicaoInicialDe`, cortado em 80 caracteres). A criação usa
   `PerfilEmpregadorRepository.inserirSeAusente` (`ON CONFLICT DO NOTHING`).
5. O usecase devolve `MeuPerfilEmpregador` (perfil + `DadosUsuario`); `PerfilEmpregadorMapper` o converte em
   `PerfilEmpregadorResponse`.

## `PATCH /conta/perfil-empregador/nome-exibicao` (TICKET-0048)

Mesmo caminho do `GET` (o mesmo `garantirPerfil`), com `EditarNomeExibicaoUseCase`: o corpo é validado em
`EditarNomeExibicaoRequest` (obrigatório, sem espaços nas pontas, até 80 caracteres), o nome é gravado e a resposta
é a mesma do `GET`.

As rotas do TICKET-0054 também exigem login: o `JwtAuthFilter` (`comum/seguranca/`) põe o `UsuarioAutenticado` no SecurityContext e
o controller recebe com `@AuthenticationPrincipal`. Sem token válido, 401 `NAO_AUTENTICADO` antes de chegar aqui.

## `GET /categorias` (TICKET-0054)

1. `CategoriaController.listar` chama `ListarCategoriasUseCase.executar()`.
2. O usecase devolve `CategoriaService.listarAtivas()` — `ativa = true`, ordenadas por `ordem` e nome. "Outros"
   sempre vem: é protegida, e o banco não deixa categoria protegida ficar inativa.
3. `CategoriaMapper.toResponse` → 200 `[{ id, nome, grupo }]`, na ordem que veio do banco.

## `GET /perfil/prestador/atuacao` (TICKET-0054)

1. `PerfilAtuacaoController.consultar` chama `ConsultarPerfilAtuacaoUseCase.executar(usuario.id())`.
2. O usecase busca por `PerfilPrestadorService.buscarPorUsuario` (já com especialidades e janelas — o mapper lê as
   duas fora da transação; especialidades no `@EntityGraph`, janelas num segundo `initialize`, pra não virar
   produto cartesiano). Nunca salvou: `PerfilPrestador.novo(usuarioId)`, vazio e
   incompleto, sem persistir.
3. `PerfilAtuacaoMapper.toResponse` → 200, especialidades em ordem de exibição (`Categoria.ORDEM_EXIBICAO`) e janelas
   por dia/horário.

## `PUT /perfil/prestador/atuacao` (TICKET-0054)

1. `PerfilAtuacaoController.salvar` desserializa `PerfilAtuacaoRequest` (Bean Validation: raio 1–40, UF do enum,
   listas obrigatórias; `JanelaAtendimentoRequest.isIntervaloValido` recusa início depois do/igual ao fim;
   `PerfilAtuacaoRequest.isJanelasSemSobreposicao` recusa janelas que se cruzam no mesmo dia). Converte
   as janelas em entity com `PerfilAtuacaoMapper.toJanelas` e tira ids de especialidade repetidos (`Set.copyOf`).
2. `SalvarPerfilAtuacaoUseCase.executar(usuarioId, especialidadeIds, raio, municipio, uf, janelas)`, em ordem (os
   dois primeiros fora da transação, pra chamada ao Nominatim não segurar conexão do pool):
   - `CategoriaService.buscarAtivasPorIds` — veio menos do que foi pedido (id inexistente ou inativo) →
     `CategoriaInvalidaException`.
   - Coordenadas: `PerfilPrestadorService.buscarCoordenadasConhecidas` (algum perfil já gravou esse município/UF —
     cache) ou, se não, `GeocodificacaoService.localizarMunicipio` (Nominatim). Não achou →
     `MunicipioNaoEncontradoException`; serviço fora do ar ou resposta fora do formato →
     `GeocodificacaoIndisponivelException` (lançada pelo service).
   - Abre a transação (`TransactionOperations`): `PerfilPrestadorService.travarPorUsuario` (advisory lock até o fim
     da transação) e
     `buscarPorUsuario` — ou `PerfilPrestador.novo` no primeiro salvamento.
   - `PerfilPrestador.atualizarAtuacao` substitui tudo (especialidades e janelas na mesma coleção — o `orphanRemoval`
     apaga as janelas antigas) e recalcula `completo`.
   - `PerfilPrestadorService.salvar`.
3. `PerfilAtuacaoMapper.toResponse` → 200.

## `PUT /conta/foto-perfil` (TICKET-0056)

1. `FotoPerfilController.trocar` recebe a parte `arquivo` (multipart) e o `UsuarioAutenticado`, lê os bytes e chama
   `TrocarFotoPerfilUseCase.executar(usuario.id(), bytes)`. Upload acima de 10 MB nem chega aqui:
   `GlobalExceptionHandler` responde 413 `ARQUIVO_GRANDE_DEMAIS`.
2. O usecase, numa transação:
   - recusa vazio ou acima de `perfil.foto.tamanho-maximo` (5 MB) → `FotoInvalidaException` (422);
   - `ImagemHelper.processar`: formato pelo conteúdo (JPEG/PNG), orientação do EXIF aplicada, lado maior até
     2048 px, regravação sem metadados. Vazio → `FotoInvalidaException`; sem writer ImageIO no runtime →
     `ImagemHelper.EscritorIndisponivelException` (500);
   - `MidiaService.travarFotoPerfil` (advisory lock por usuário, via `comum/persistencia/TravaTransacional`) e `buscarFotoPerfilAtiva`;
   - `ArmazenamentoService.enviar` sobe `fotos-perfil/<usuarioId>/<uuid>.<ext>` (falha → 503
     `ARMAZENAMENTO_INDISPONIVEL`) e publica `FotoPerfilTrocadaEvent(chaveNova, chaveAnterior)`;
   - `Midia.expurgar` na anterior + `MidiaService.salvarAgora` (flush antes do insert, por causa do índice de "uma
     ativa"), depois `Midia.fotoPerfil` + `salvar`;
   - devolve `ArmazenamentoService.gerarLink(chave)` (`LinkTemporario`).
3. `FotoPerfilMapper.toResponse` → 200 `{ url, expiraEm }`.
4. Depois do commit, `FotoPerfilEventosService.aoConfirmarTroca` apaga o objeto anterior; se a transação foi
   desfeita, `aoDesfazerTroca` apaga o objeto novo.

## `GET /conta/foto-perfil` (TICKET-0056)

`ConsultarFotoPerfilUseCase` busca a `Midia` ativa e gera o link temporário; sem foto, o controller responde 204.

## Porta `ConsultaPerfilEmpregador` (TICKET-0052, painel do Empregador em `anuncio`)

`actions/service/ConsultaPerfilEmpregadorService` implementa a porta de `comum/portas/`: `garantirPerfil` (mesma
regra e mesmo 404 do `GET /conta/perfil-empregador`) + link temporário da foto ativa. Se o storage não gerar o link,
o resumo sai sem foto em vez de 503 — a foto não derruba o painel.

## Classes envolvidas

| Classe | Papel |
|---|---|
| `empregador/PerfilEmpregador`, `PerfilEmpregadorRepository`, `PerfilEmpregadorService` | entity (com `nomeExibicaoInicialDe`), persistência e `garantirPerfil` (consulta à porta, 404 e criação lazy) |
| `empregador/MeuPerfilEmpregador` | tipo de domínio: perfil + dados do usuário |
| `actions/usecase/ConsultarMeuPerfilUseCase`, `EditarNomeExibicaoUseCase` | regra de negócio |
| `actions/mapper/PerfilEmpregadorMapper` | entity + dados → `PerfilEmpregadorResponse` (só o controller chama) |
| `actions/controller/PerfilEmpregadorController` | tradução HTTP ↔ usecase |
| `records/EditarNomeExibicaoRequest`, `PerfilEmpregadorResponse` | contrato HTTP |
| `excecao/PerfilEmpregadorNaoEncontradoException` | 404 do perfil |
| `comum/portas/ConsultaDadosUsuario`, `DadosUsuario` | porta de leitura dos dados do usuário |
| `auth/actions/service/ConsultaDadosUsuarioService` | implementação da porta no `auth` |
| `CategoriaController` / `PerfilAtuacaoController` | Adapter HTTP — só tradução request/response |
| `ListarCategoriasUseCase` | Categorias ativas, em ordem |
| `ConsultarPerfilAtuacaoUseCase` | Perfil salvo ou vazio, sem persistir |
| `SalvarPerfilAtuacaoUseCase` | Valida especialidades, resolve coordenadas, grava o perfil inteiro |
| `GeocodificacaoService` | Integração externa — Nominatim (município + UF → coordenadas) |
| `CategoriaMapper` | `Categoria` → `CategoriaResponse`, mantendo a ordem recebida |
| `PerfilAtuacaoMapper` | `JanelaAtendimentoRequest` → `JanelaAtendimento`; `PerfilPrestador` → `PerfilAtuacaoResponse` |
| `PerfilPrestador` | Entity raiz — atuação do Prestador; `atualizarAtuacao` + critério de `completo` |
| `JanelaAtendimento` | Subentidade de `PerfilPrestador` (cascade + orphanRemoval) |
| `Categoria` | Entity raiz — taxonomia de especialidades; `desativar` recusa a protegida; `ORDEM_EXIBICAO` |
| `Coordenadas` | Record `@Embeddable` — resultado da geocodificação, embutido no `PerfilPrestador`; valida a faixa |
| `PerfilPrestadorService` | Extends `comum/base/BaseService` — `buscarPorUsuario`, `buscarCoordenadasConhecidas`, `travarPorUsuario` |
| `CategoriaService` | Extends `comum/base/BaseService` — `listarAtivas`, `buscarAtivasPorIds` |
| `PerfilPrestadorRepository` / `CategoriaRepository` | Extends `comum/base/BaseRepository` — porta de persistência JPA |
| `Uf`, `DiaSemana` | Enums de domínio (27 UFs com nome por extenso; dias da semana, segunda primeiro) |
| `CategoriaInvalidaException`, `CategoriaProtegidaException`, `MunicipioNaoEncontradoException`, `GeocodificacaoIndisponivelException` | Exceções de negócio do TICKET-0054 |
| `midia/Midia`, `MidiaRepository`, `MidiaService`, `enums/TipoMidia` | entity do arquivo guardado (`fotoPerfil`, `expurgar`), persistência, foto ativa e lock por usuário |
| `midia/LinkTemporario` | tipo de domínio: URL assinada + `expiraEm` |
| `actions/helper/ImagemHelper` | validação do conteúdo, orientação EXIF, redução e regravação sem metadados (sem Spring) |
| `actions/service/ArmazenamentoService` | integração com o bucket S3 (`enviar`, `gerarLink`, `remover`); 503 sem storage configurado |
| `actions/service/FotoPerfilEventosService` | apaga do bucket o objeto anterior (commit) ou o novo (rollback) |
| `eventos/FotoPerfilTrocadaEvent` | evento interno da troca: chave nova + chave anterior |
| `actions/usecase/TrocarFotoPerfilUseCase`, `ConsultarFotoPerfilUseCase` | regra de negócio da foto |
| `actions/mapper/FotoPerfilMapper`, `actions/controller/FotoPerfilController`, `records/FotoPerfilResponse` | contrato HTTP da foto |
| `excecao/FotoInvalidaException`, `ArmazenamentoIndisponivelException` | 422 e 503 da foto |
| `comum/persistencia/TravaTransacional`, `EscopoTrava` | Advisory lock do Postgres por escopo + chave (usado aqui e no login) |
| `comum/portas/ConsultaPerfilEmpregador`, `ResumoPerfilEmpregador` | porta de leitura de nome de exibição + foto, pra outros módulos |
| `actions/service/ConsultaPerfilEmpregadorService` | implementação da porta (TICKET-0052) |

Atualize esta página sempre que uma classe mudar de responsabilidade.
