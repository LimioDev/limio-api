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

## 2026-10-07 — TICKET-0044: `auth` deixa de ser lar de tudo que referencia `usuario_id`

Revisão de DER + diagrama de classes mostrou ~35 tabelas com FK pra `usuario`; só duas (`usuario`,
`cpf_bloqueado`) tinham código. Pra não virar monólito, criado o pacote-esqueleto (`package-info.java`, mesmo
padrão que `auth` teve antes da implementação) de cada bounded context que o DER revela:
`modulos.{perfil,anuncio,moderacao,financeiro,chat,notificacao,administracao,auditoria,privacidade}`.
`auth` continua dono só de `Usuario`/`CpfBloqueado` — `sessao`, `verificacao_contato`, `aceite_termo` ficam
reservados pra entrar aqui num ticket futuro (ciclo de vida da própria conta).

Além disso, `CpfBloqueado` corrigido pra bater com o DER: campo `cpf` (texto puro) virou `cpfHmac`
(HMAC-SHA256 via novo helper `CpfHasher`, chave em `security.cpf.hmac-secret`) — tabela só precisa conferir
igualdade, não precisa do CPF legível. Ganhou também `bloqueadoEm` e `liberadoEm` explícitos (antes só existia
`criadoEm` herdado, sem suportar liberação antecipada do cooldown). `Usuario` ganhou `anonimizadoEm` (coluna do
DER que faltava). Migration `V3__ajustar_usuario_e_cpf_bloqueado.sql`. Detalhe completo da decisão em
`docs/features/TICKET-0044-separacao-modulos-por-subentidade.md` e em ADR-0001 revisão 3.

Dentro do próprio `auth`, `Usuario`/`UsuarioRepository`/`UsuarioService`/`enums/` moveram pra subpasta
`usuario/`, e `CpfBloqueado`/`CpfBloqueadoRepository`/`CpfBloqueadoService` pra `cpfbloqueado/` —
`Usuario` e `CpfBloqueado` não têm relação pai-filho (sem FK entre si, ciclos de vida independentes), então
não deveriam ficar soltas lado a lado na raiz do módulo. `records/`, `excecao/` e `actions/` continuam no
nível de `auth` (representam o fluxo UC01, não uma entidade isolada). Padrão novo documentado em ADR-0001
§3.5 e no skill `limio-architecture`.

Nome de papel `CONTRATANTE` renomeado pra `EMPREGADOR` (nome correto de produto) em todo o projeto — enum,
testes, migration `V4__renomear_papel_contratante_para_empregador.sql` (`UPDATE` + troca do `CHECK`), docs e
ADR. DER/diagrama de classes anexados ainda mostram "contratante" (ferramenta externa) — anotado como
pendência de nomenclatura a resolver quando o módulo `perfil` for implementado.

## 2026-10-07 — TICKET-0032: login, sessão por aparelho e troca de papel ativo

Implementados UC03 (login), UC08 (logout do dispositivo), UC09 (alternar papel) e a renovação do token de acesso.
`auth` ganhou as subpastas `sessao/` (entity do DER + `expira_em`, que o DER não tem) e `tentativalogin/` (tabela
nova, não está no DER — base do rate limit). Migrations `V5__criar_tabela_sessao.sql` e
`V6__criar_tabela_tentativa_login.sql`. `comum/seguranca/` deixou de ser só previsto: `JwtAuthFilter` +
`UsuarioAutenticado`.

`PapelUsuario` saiu de `auth/usuario/enums/` pra `comum/seguranca/enums/`: `UsuarioAutenticado` (em `comum/`)
carrega o papel e `comum/` não pode importar módulo.

O card fala em status SUSPENSA e ENCERRADA, mas o diagrama de classes só tem `ATIVA`, `PENDENTE_VERIFICACAO` e
`BLOQUEADA`. O enum foi mantido como no diagrama: `BLOQUEADA` é tratada como suspensa e conta encerrada é
`anonimizadoEm` preenchido (UC10 encerra anonimizando). Se o grupo criar valores próprios, ajustar
`Usuario.isSuspensa`/`isEncerrada` — o `switch` sem `default` acusa valor novo na compilação.

`SecurityConfig` deixou de liberar `/auth/**` inteiro: agora só `POST /auth/cadastro`, `/auth/login` e
`/auth/refresh` são públicas — logout e troca de papel exigem token. Detalhe completo em
`docs/features/TICKET-0032-acessar-conta-e-alternar-papel.md`.

## 2026-10-07 — TICKET-0032: endurecimento do login após revisão de segurança

Rate limit passou a gravar a tentativa antes de conferir a senha, com lock por e-mail no Postgres
(`pg_advisory_xact_lock`), e a apagar se a senha conferir: uma rajada paralela passava inteira pela checagem antes de
qualquer falha ser gravada. O contador de conta existente passou a ser o e-mail gravado (grafias que o `upper` do
Postgres iguala, como "ı" sem ponto, ganhavam contador próprio). Também: `Locale.ROOT` na normalização do e-mail
(login e cadastro), e-mail limitado a 255, caracteres de controle removidos do `dispositivo`, e erros 4xx do Spring
(415/405/404) com `REQUISICAO_INVALIDA` em vez de 500. Detalhe em
`docs/features/TICKET-0032-acessar-conta-e-alternar-papel.md`, seção "Endurecimento".
