# Histórico — auth

Registre aqui, em ordem cronológica, decisões e mudanças de regra que não ficam óbvias só lendo o código.

Formato de cada entrada: novo arquivo `AAAA-MM-titulo-curto.md` nesta pasta, com contexto do "porquê" da mudança.

## 2026-09 — primeira implementação do módulo `auth`

Migrado o esqueleto de `com.limio.api.auth` (só `package-info.java`) para `com.limio.api.modulos.auth`, seguindo
ADR-0001. Implementado UC01 (cadastrar conta universal): entity `Usuario`, entity de apoio `CpfBloqueado`,
enums `PapelUsuario`/`StatusConta`, records de request/response, exceções de negócio, e o fluxo completo
controller → usecase → repository. Detalhe da regra em `docs/features/UC01-cadastrar-conta-universal.md`.

Criado também o esqueleto de `comum/` (`base`, `excecao`, `records`, `config`) — ainda não existia nenhum arquivo lá.

## 2026-09 — BaseRepository/BaseService genéricos + fim de string hardcoded

Adicionado `comum/base/BaseRepository<E,ID>` (`@NoRepositoryBean`, estende `JpaRepository`) e
`comum/base/BaseService<E,ID,R>` (CRUD genérico: `salvar`, `buscarPorIdOuFalhar`, `existePorId`, `remover`;
"não encontrado" lança `EntidadeNaoEncontradaException`/`CodigoErro.ENTIDADE_NAO_ENCONTRADA`, nunca `Optional`
solto nem string). `UsuarioRepository`/`CpfBloqueadoRepository` passaram a estender `BaseRepository`; nasceram
`UsuarioService`/`CpfBloqueadoService` (module root, ao lado do repository — não em `actions/service`, que
continua reservado a integração externa) estendendo `BaseService` e expondo os finders da entity.
`CadastrarUsuarioUseCase` passou a depender dos `Service`, não mais do `Repository` direto.

Mensagens de `@NotBlank`/`@Email`/`@Pattern`/`@Size`/`@Past` de `CadastroRequest` movidas pra
`ValidationMessages.properties` (chave `{modulo.campo.regra}`) — Bean Validation exige literal/placeholder
constante na anotação, não aceita chamada de método de enum ali; texto de exceção de negócio continua 100% via
`CodigoErro.mensagemPadrao()`, sem string solta em nenhuma classe Java.
