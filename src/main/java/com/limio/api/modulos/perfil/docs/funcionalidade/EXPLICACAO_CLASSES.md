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

## `PUT /conta/foto-perfil` (TICKET-0056)

1. `FotoPerfilController.trocar` recebe a parte `arquivo` (multipart) e o `UsuarioAutenticado`, lê os bytes e chama
   `TrocarFotoPerfilUseCase.executar(usuario.id(), bytes)`. Upload acima de 10 MB nem chega aqui:
   `GlobalExceptionHandler` responde 413 `ARQUIVO_GRANDE_DEMAIS`.
2. O usecase, numa transação:
   - recusa vazio ou acima de `perfil.foto.tamanho-maximo` (5 MB) → `FotoInvalidaException` (422);
   - `ImagemHelper.processar`: formato pelo conteúdo (JPEG/PNG), orientação do EXIF aplicada, lado maior até
     2048 px, regravação sem metadados. Vazio → `FotoInvalidaException`;
   - `MidiaService.travarFotoPerfil` (advisory lock por usuário) e `buscarFotoPerfilAtiva`;
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
| `midia/Midia`, `MidiaRepository`, `MidiaService`, `enums/TipoMidia` | entity do arquivo guardado (`fotoPerfil`, `expurgar`), persistência, foto ativa e lock por usuário |
| `midia/LinkTemporario` | tipo de domínio: URL assinada + `expiraEm` |
| `actions/helper/ImagemHelper` | validação do conteúdo, orientação EXIF, redução e regravação sem metadados (sem Spring) |
| `actions/service/ArmazenamentoService` | integração com o bucket S3 (`enviar`, `gerarLink`, `remover`); 503 sem storage configurado |
| `actions/service/FotoPerfilEventosService` | apaga do bucket o objeto anterior (commit) ou o novo (rollback) |
| `eventos/FotoPerfilTrocadaEvent` | evento interno da troca: chave nova + chave anterior |
| `actions/usecase/TrocarFotoPerfilUseCase`, `ConsultarFotoPerfilUseCase` | regra de negócio da foto |
| `actions/mapper/FotoPerfilMapper`, `actions/controller/FotoPerfilController`, `records/FotoPerfilResponse` | contrato HTTP da foto |
| `excecao/FotoInvalidaException`, `ArmazenamentoIndisponivelException` | 422 e 503 da foto |
