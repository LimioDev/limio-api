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
2. Exceto listar, todo use case (criar e editar, dentro da transação, depois de geocodificar) começa com `PerfilEmpregadorService.garantirPerfilParaAtualizar`, que cria o
   perfil se for o primeiro acesso e o **trava** (`PESSIMISTIC_WRITE`). Escritas do mesmo Empregador ficam
   serializadas, e é isso que garante um único padrão sem depender só do índice.
   O **editar** é a exceção: nunca cria perfil. Fora da transação lê com `buscarPerfilExistente` (sem efeito colateral,
   404 se não houver) e dentro dela trava com `travarPerfilExistente(perfilId)`, sem nova ida ao auth.
3. O acesso por id usa `EnderecoSalvoService.buscarEnderecoAtivoOuFalhar(id, perfilId)`: id inexistente, removido
   ou de outro Empregador dá a mesma `EnderecoSalvoNaoEncontradoException` (404 `ENDERECO_NAO_ENCONTRADO`).
4. **Criar/editar**: antes de abrir a transação, o use case obtém as coordenadas em
   `Geocodificador.localizarEndereco(EnderecoParaGeocodificar)` (rua, número, cidade, UF; sem CEP, bairro ou
   complemento). Vazio → `EnderecoNaoLocalizadoException` (422 `ENDERECO_NAO_LOCALIZADO`); provedor fora do ar →
   `GeocodificacaoIndisponivelException` (503). Só então `TransactionOperations` abre a transação (passo 2) e grava.
   Na **edição** a consulta só acontece se `EnderecoSalvo.mesmaLocalizacaoQue` der falso (rua, número, cidade ou UF
   mudaram); senão as coordenadas gravadas ficam. Com `isPadrao = true`, `desmarcarEnderecoPadrao` roda antes de
   gravar (UPDATE com flush e clear do contexto — a ordem importa por causa do índice único parcial). `PUT`
   substitui todos os campos (`EnderecoSalvo.substituirDadosPor`, que não copia coordenadas;
   `definirCoordenadas` as aplica).
5. **Remover**: `EnderecoSalvo.remover(Instant)` preenche `removidoEm` e zera `isPadrao`; a linha fica no banco.
   Remover o padrão deixa o Empregador sem padrão.
6. **Marcar padrão** (`PATCH /{id}/padrao`): idempotente; se ainda não é o padrão, desmarca o atual, recarrega o
   endereço (o clear do contexto o desanexou) e marca. A resposta é a lista atualizada dos endereços ativos (padrão
   primeiro), como no card, para o front re-renderizar sem outro `GET`.
7. `EnderecoSalvoService.salvar` faz `saveAndFlush` e converte a violação de `uk_endereco_salvo_padrao` em
   `EnderecoPadraoConflitoException` (409 `ENDERECO_PADRAO_CONFLITO`), rede de segurança caso uma escrita escape
   do lock.

| Classe | Papel |
|---|---|
| `endereco/EnderecoSalvo`, `EnderecoSalvoRepository`, `EnderecoSalvoService` | entity (com `remover` e `substituirDadosPor`), finders sempre com `RemovidoEmIsNull` e service (busca ou 404, desmarcar padrão, rede de segurança do índice) |
| `comum/localizacao/enums/Uf` | as 27 siglas com nome por extenso (usado no `state` do Nominatim); em `comum/` desde o ETI-59 (o anúncio também usa) |
| `actions/service/Geocodificador`, `NominatimGeocodificador` | porta de geocodificação (município e endereço) e sua implementação no Nominatim; `endereco/EnderecoParaGeocodificar` é o record de entrada |
| `actions/usecase/ListarEnderecosSalvosUseCase`, `CriarEnderecoSalvoUseCase`, `EditarEnderecoSalvoUseCase`, `RemoverEnderecoSalvoUseCase`, `MarcarEnderecoSalvoPadraoUseCase` | regra de negócio de cada rota |
| `actions/mapper/EnderecoSalvoMapper` | request/entity/response (só o controller chama) |
| `actions/controller/EnderecoSalvoController` | tradução HTTP ↔ usecase |
| `records/EnderecoSalvoRequest`, `EnderecoSalvoResponse` | contrato HTTP; o request normaliza CEP (só dígitos), `strip` e complemento em branco → `null` |
| `excecao/EnderecoSalvoNaoEncontradoException`, `EnderecoPadraoConflitoException`, `EnderecoNaoLocalizadoException` | 404, 409 e 422 |
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
     cache) ou, se não, `Geocodificador.localizarMunicipio` (implementado por `NominatimGeocodificador`). Não achou →
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

## Portas `ConsultaCategoria` e `ConsultaEndereco` (ETI-59, criar anúncio em `anuncio`)

1. `ConsultaCategoriaService.buscarPorId` → `CategoriaService.buscarPorId` → `ResumoCategoria(id, nome, ativa)`.
   Quem cria anúncio confere `ativa`.
2. `ConsultaEnderecoService.buscarSalvo(usuarioId, enderecoSalvoId)` →
   `EnderecoSalvoService.buscarEnderecoAtivoDoUsuarioOuFalhar` (join com `perfil_empregador` pelo `usuario_id`;
   removido ou de outro usuário → 404 `ENDERECO_NAO_ENCONTRADO`). Coordenadas são as do próprio endereço salvo.
3. `ConsultaEnderecoService.localizar(avulso)` → `Geocodificador.localizarEndereco` (sem transação). Não achado →
   422 `ENDERECO_NAO_LOCALIZADO`; provedor fora → 503 `GEOCODIFICACAO_INDISPONIVEL`.
4. Os dois devolvem `EnderecoLocalizado` com `"Rua, número - complemento, bairro, cidade - UF"` (cabe nos 255 do
   `anuncio.endereco_completo_snapshot`).

## `GET /perfil/prestador/cota` (US24)

1. `CotaDoMesController.consultar` chama `ConsultarCotaDoMesUseCase.executar(usuario.id(), usuario.papel())`. Papel
   Empregador → `SomentePrestadorConsultaCotaException` (403 `ACESSO_NEGADO`), antes de qualquer leitura.
2. O mês corrente é `CotaMensal.mesDe(relogio.instant())`, no fuso `CotaMensal.FUSO` (America/Sao_Paulo). O `Clock`
   vem de `comum/config/RelogioConfig`.
3. `PerfilPrestadorService.buscarIdDoUsuario` (só o id) → `CotaMensalService.buscarDoMes(perfilId, mes)`. Sem perfil
   ou sem linha no mês → `CotaMensal.zerada(...)` com `perfil.cota.teto-free` (não persiste). É a virada do mês.
4. Plano pela porta `ConsultaPlanoPrestador` (`financeiro`, hoje sempre FREE); escolhas do período
   `[cota.inicio(), cota.renovaEm())` pela porta `ConsultaEscolhasDoPrestador` (`anuncio`).
5. O usecase devolve `CotaDoMes` (plano + cota + escolhas; `teto()` nulo e `isEsgotada()` falso no GOLD), e o
   `CotaDoMesMapper` monta `CotaDoMesResponse`, com `renovaEm` no offset de São Paulo.

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
| `geo/Coordenadas` | Record `@Embeddable` compartilhado — resultado da geocodificação, embutido no `PerfilPrestador` e aplicado no `EnderecoSalvo`; valida a faixa |
| `PerfilPrestadorService` | Extends `comum/base/BaseService` — `buscarPorUsuario`, `buscarCoordenadasConhecidas`, `travarPorUsuario` |
| `CategoriaService` | Extends `comum/base/BaseService` — `listarAtivas`, `buscarAtivasPorIds` |
| `PerfilPrestadorRepository` / `CategoriaRepository` | Extends `comum/base/BaseRepository` — porta de persistência JPA |
| `DiaSemana` | Enum de domínio (dias da semana, segunda primeiro). `Uf` está em `comum/localizacao/enums` |
| `CategoriaInvalidaException`, `CategoriaProtegidaException`, `MunicipioNaoEncontradoException`, `GeocodificacaoIndisponivelException` | Exceções de negócio do TICKET-0054 |
| `comum/midia/Midia`, `MidiaRepository`, `MidiaService`, `enums/TipoMidia` | entity do arquivo guardado (`fotoPerfil`, `fotoAnuncio`, `expurgar`), persistência, foto ativa e lock por usuário. Em `comum/` desde o ETI-59 (o anúncio também grava mídia) |
| `comum/midia/LinkTemporario` | tipo de domínio: URL assinada + `expiraEm` |
| `comum/midia/ImagemHelper` | validação do conteúdo, orientação EXIF, redução e regravação sem metadados (sem Spring) |
| `comum/midia/ArmazenamentoService` | integração com o bucket S3 (`enviar`, `gerarLink`, `remover`); 503 sem storage configurado |
| `actions/service/FotoPerfilEventosService` | apaga do bucket o objeto anterior (commit) ou o novo (rollback) |
| `eventos/FotoPerfilTrocadaEvent` | evento interno da troca: chave nova + chave anterior |
| `eventos/FotoPerfilRemovidaEvent` | evento interno: foto tirada de uso sem substituta (conta encerrada); o objeto sai do bucket depois do commit |
| `actions/usecase/AnonimizarPerfilDaContaEncerradaUseCase` | reação a `comum/eventos/ContaEncerradaEvent` (UC10): anonimiza o nome de exibição, apaga o `PerfilPrestador`, expurga a foto; cada parte isolada |
| `actions/usecase/TrocarFotoPerfilUseCase`, `ConsultarFotoPerfilUseCase` | regra de negócio da foto |
| `actions/mapper/FotoPerfilMapper`, `actions/controller/FotoPerfilController`, `records/FotoPerfilResponse` | contrato HTTP da foto |
| `comum/midia/excecao/FotoInvalidaException`, `ArmazenamentoIndisponivelException` | 422 e 503 da foto |
| `comum/persistencia/TravaTransacional`, `EscopoTrava` | Advisory lock do Postgres por escopo + chave (usado aqui e no login) |
| `comum/portas/ConsultaPerfilEmpregador`, `ResumoPerfilEmpregador` | porta de leitura de nome de exibição + foto, pra outros módulos |
| `actions/service/ConsultaPerfilEmpregadorService` | implementação da porta (TICKET-0052) |
| `actions/service/ConsultaCategoriaService` | implementa `comum/portas/ConsultaCategoria` (ETI-59): categoria por id, ativa ou não |
| `actions/service/ConsultaEnderecoService` | implementa `comum/portas/ConsultaEndereco` (ETI-59): endereço salvo do usuário ou avulso geocodificado, sempre com o texto de uma linha do retrato do anúncio |

| `perfilprestador/cotamensal/CotaMensal`, `CotaMensalRepository`, `CotaMensalService`, `AnoMesConverter` | subentidade de `PerfilPrestador` (uma linha por mês, `FUSO`, `mesDe`, `inicio`, `renovaEm`, `isEsgotada`), persistência e `YearMonth` ↔ `'AAAA-MM'` (US24) |
| `perfilprestador/cotamensal/CotaDoMes` | tipo de domínio: plano + cota do mês + escolhas; teto e esgotada por plano (US24) |
| `actions/usecase/ConsultarCotaDoMesUseCase`, `actions/mapper/CotaDoMesMapper`, `actions/controller/CotaDoMesController`, `records/CotaDoMesResponse`, `EscolhaDoMesResponse` | consulta da cota do mês (US24) |
| `excecao/SomentePrestadorConsultaCotaException` | 403 `ACESSO_NEGADO` da cota (US24) |
| `comum/portas/ConsultaPlanoPrestador`, `enums/Plano` / `ConsultaEscolhasDoPrestador`, `EscolhaDoPrestador` | portas de leitura do plano (`financeiro`) e das escolhas do Prestador (`anuncio`), US24 |

Atualize esta página sempre que uma classe mudar de responsabilidade.
