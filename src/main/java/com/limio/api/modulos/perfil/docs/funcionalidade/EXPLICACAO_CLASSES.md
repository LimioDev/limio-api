# Manual de funcionamento — perfil

## `GET /conta/perfil-empregador` (TICKET-0048)

1. `JwtAuthFilter` (`comum/seguranca/`) lê o token e coloca `UsuarioAutenticado` no `SecurityContext`; sem token a
   resposta é 401.
2. `PerfilEmpregadorController.consultar` recebe `@AuthenticationPrincipal UsuarioAutenticado` e chama
   `ConsultarMeuPerfilUseCase.executar(usuario.id())` — o id vem só do token.
3. O usecase pede os dados do usuário à porta `ConsultaDadosUsuario` (`comum/portas/`, implementada por
   `ConsultaDadosUsuarioService` no `auth`). Vazio (usuário inexistente ou conta encerrada) →
   `PerfilEmpregadorNaoEncontradoException` (404, `PERFIL_EMPREGADOR_NAO_ENCONTRADO`).
4. `PerfilEmpregadorService.buscarOuCriar` devolve o perfil ou o cria no primeiro acesso, com o nome completo do
   cadastro (`NomeExibicaoInicial`, cortado em 80 caracteres). A criação usa
   `PerfilEmpregadorRepository.inserirSeAusente` (`ON CONFLICT DO NOTHING`).
5. O usecase devolve `MeuPerfilEmpregador` (perfil + `DadosUsuario`); `PerfilEmpregadorMapper` o converte em
   `PerfilEmpregadorResponse`.

## `PATCH /conta/perfil-empregador/nome-exibicao` (TICKET-0048)

Mesmo caminho do `GET` até o passo 4, com `EditarNomeExibicaoUseCase`: o corpo é validado em
`EditarNomeExibicaoRequest` (obrigatório, sem espaços nas pontas, até 80 caracteres), o nome é gravado e a resposta
é a mesma do `GET`.

## Classes envolvidas

| Classe | Papel |
|---|---|
| `empregador/PerfilEmpregador`, `PerfilEmpregadorRepository`, `PerfilEmpregadorService` | entity, persistência e finders (`buscarOuCriar`) |
| `empregador/MeuPerfilEmpregador` | tipo de domínio: perfil + dados do usuário |
| `actions/usecase/ConsultarMeuPerfilUseCase`, `EditarNomeExibicaoUseCase` | regra de negócio |
| `actions/helper/NomeExibicaoInicial` | nome de exibição inicial a partir do nome completo |
| `actions/mapper/PerfilEmpregadorMapper` | entity + dados → `PerfilEmpregadorResponse` (só o controller chama) |
| `actions/controller/PerfilEmpregadorController` | tradução HTTP ↔ usecase |
| `records/EditarNomeExibicaoRequest`, `PerfilEmpregadorResponse` | contrato HTTP |
| `excecao/PerfilEmpregadorNaoEncontradoException` | 404 do perfil |
| `comum/portas/ConsultaDadosUsuario`, `DadosUsuario` | porta de leitura dos dados do usuário |
| `auth/actions/service/ConsultaDadosUsuarioService` | implementação da porta no `auth` |
