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
3. `CategoriaMapper.toResponse` → 200 `[{ id, nome, grupo }]`.

## `GET /perfil/prestador/atuacao` (TICKET-0054)

1. `PerfilAtuacaoController.consultar` chama `ConsultarPerfilAtuacaoUseCase.executar(usuario.id())`.
2. O usecase busca por `PerfilPrestadorService.buscarPorUsuario` (já com especialidades e janelas, via
   `@EntityGraph` — o mapper lê as duas fora da transação). Nunca salvou: `PerfilPrestador.novo(usuarioId)`, vazio e
   incompleto, sem persistir.
3. `PerfilAtuacaoMapper.toResponse` → 200, especialidades em ordem de exibição e janelas por dia/horário.

## `PUT /perfil/prestador/atuacao` (TICKET-0054)

1. `PerfilAtuacaoController.salvar` desserializa `PerfilAtuacaoRequest` (Bean Validation: raio 1–40, UF do enum,
   listas obrigatórias; `JanelaAtendimentoRequest.isIntervaloValido` recusa início depois do/igual ao fim). Converte
   as janelas em entity com `PerfilAtuacaoMapper.toJanelas` e tira ids de especialidade repetidos (`Set.copyOf`).
2. `SalvarPerfilAtuacaoUseCase.executar(usuarioId, especialidadeIds, raio, municipio, uf, janelas)`, transacional,
   em ordem:
   - `CategoriaService.buscarAtivasPorIds` — veio menos do que foi pedido (id inexistente ou inativo) →
     `CategoriaInvalidaException`.
   - Coordenadas: `PerfilPrestadorService.buscarCoordenadasConhecidas` (algum perfil já gravou esse município/UF —
     cache) ou, se não, `GeocodificacaoService.localizarMunicipio` (Nominatim). Não achou →
     `MunicipioNaoEncontradoException`; serviço fora do ar → `GeocodificacaoIndisponivelException` (lançada pelo
     service).
   - `PerfilPrestadorService.travarPorUsuario` (advisory lock até o fim da transação) e
     `buscarPorUsuario` — ou `PerfilPrestador.novo` no primeiro salvamento.
   - `PerfilPrestador.atualizarAtuacao` substitui tudo (especialidades e janelas na mesma coleção — o `orphanRemoval`
     apaga as janelas antigas) e recalcula `completo`.
   - `PerfilPrestadorService.salvar`.
3. `PerfilAtuacaoMapper.toResponse` → 200.

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
| `CategoriaMapper` | `Categoria` → `CategoriaResponse`, sempre em ordem de exibição |
| `PerfilAtuacaoMapper` | `JanelaAtendimentoRequest` → `JanelaAtendimento`; `PerfilPrestador` → `PerfilAtuacaoResponse` |
| `PerfilPrestador` | Entity raiz — atuação do Prestador; `atualizarAtuacao` + critério de `completo` |
| `JanelaAtendimento` | Subentidade de `PerfilPrestador` (cascade + orphanRemoval) |
| `Categoria` | Entity raiz — taxonomia de especialidades |
| `Coordenadas` | Record de domínio — resultado da geocodificação |
| `PerfilPrestadorService` | Extends `comum/base/BaseService` — `buscarPorUsuario`, `buscarCoordenadasConhecidas`, `travarPorUsuario` |
| `CategoriaService` | Extends `comum/base/BaseService` — `listarAtivas`, `buscarAtivasPorIds` |
| `PerfilPrestadorRepository` / `CategoriaRepository` | Extends `comum/base/BaseRepository` — porta de persistência JPA |
| `Uf`, `DiaSemana` | Enums de domínio (27 UFs com nome por extenso; dias da semana, segunda primeiro) |
| `CategoriaInvalidaException`, `MunicipioNaoEncontradoException`, `GeocodificacaoIndisponivelException` | Exceções de negócio do TICKET-0054 |

Atualize esta página sempre que uma classe mudar de responsabilidade.
