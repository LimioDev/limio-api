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

## Endereços salvos — `/conta/perfil-empregador/enderecos` (TICKET-0050)

Em todas as rotas o dono vem só do token (`usuario.id()`), nunca da URL ou do corpo.

1. `EnderecoSalvoController` chama o use case da rota. `EnderecoSalvoMapper` converte `EnderecoSalvoRequest` →
   entity na entrada e entity → `EnderecoSalvoResponse` na saída; o use case nunca vê record.
2. Exceto listar, todo use case começa com `PerfilEmpregadorService.garantirPerfilParaAtualizar`, que cria o
   perfil se for o primeiro acesso e o **trava** (`PESSIMISTIC_WRITE`). Escritas do mesmo Empregador ficam
   serializadas, e é isso que garante um único padrão sem depender só do índice.
3. O acesso por id usa `EnderecoSalvoService.buscarEnderecoAtivoOuFalhar(id, perfilId)`: id inexistente, removido
   ou de outro Empregador dá a mesma `EnderecoSalvoNaoEncontradoException` (404 `ENDERECO_NAO_ENCONTRADO`).
4. **Criar/editar**: com `isPadrao = true`, `desmarcarEnderecoPadrao` roda antes de gravar (UPDATE com flush e
   clear do contexto — a ordem importa por causa do índice único parcial). `PUT` substitui todos os campos
   (`EnderecoSalvo.substituirDadosPor`).
5. **Remover**: `EnderecoSalvo.remover(Instant)` preenche `removidoEm` e zera `isPadrao`; a linha fica no banco.
   Remover o padrão deixa o Empregador sem padrão.
6. **Marcar padrão** (`PATCH /{id}/padrao`): idempotente; se ainda não é o padrão, desmarca o atual, recarrega o
   endereço (o clear do contexto o desanexou) e marca.
7. `EnderecoSalvoService.salvar` faz `saveAndFlush` e converte a violação de `uk_endereco_salvo_padrao` em
   `EnderecoPadraoConflitoException` (409 `ENDERECO_PADRAO_CONFLITO`), rede de segurança caso uma escrita escape
   do lock.

| Classe | Papel |
|---|---|
| `endereco/EnderecoSalvo`, `EnderecoSalvoRepository`, `EnderecoSalvoService` | entity (com `remover` e `substituirDadosPor`), finders sempre com `RemovidoEmIsNull` e service (busca ou 404, desmarcar padrão, rede de segurança do índice) |
| `enums/Uf` | as 27 siglas, valor fechado do domínio |
| `actions/usecase/ListarEnderecosSalvosUseCase`, `CriarEnderecoSalvoUseCase`, `EditarEnderecoSalvoUseCase`, `RemoverEnderecoSalvoUseCase`, `MarcarEnderecoSalvoPadraoUseCase` | regra de negócio de cada rota |
| `actions/mapper/EnderecoSalvoMapper` | request/entity/response (só o controller chama) |
| `actions/controller/EnderecoSalvoController` | tradução HTTP ↔ usecase |
| `records/EnderecoSalvoRequest`, `EnderecoSalvoResponse` | contrato HTTP; o request normaliza CEP (só dígitos), `strip` e complemento em branco → `null` |
| `excecao/EnderecoSalvoNaoEncontradoException`, `EnderecoPadraoConflitoException` | 404 e 409 |
| `empregador/PerfilEmpregadorRepository.findByIdParaAtualizar`, `PerfilEmpregadorService.garantirPerfilParaAtualizar` | lock pessimista do perfil usado por toda escrita de endereço |

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
