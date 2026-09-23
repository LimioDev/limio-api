# limio-api — instruções pra qualquer agente de IA (Claude, Codex, Cursor, Grok, etc.)

## Git commits

Nunca adicionar trailer `Co-Authored-By: Claude`, `Claude-Session:`, ou qualquer
outra atribuição a Claude/Anthropic (ou a outro assistente de IA) em mensagens de
commit deste repo. Commit mostra só o autor humano.

## Arquitetura modular — decisão registrada em ADR-0001

Origem completa em `docs/adr/0001-arquitetura-modular-backend-frontend.md` no
diretório pai do workspace (`limio/docs/`, fora deste repo — não é lido por quem
clona só `limio-api`, por isso o resumo abaixo é autocontido). Claude também tem a
versão completa como skill em `.claude/skills/limio-architecture/SKILL.md`.

Todo módulo/entidade novo replica **exatamente** esta estrutura — não inventa
variação própria.

### Split raiz: `comum/` vs `modulos/`

```
src/main/java/com/limio/api/
├── comum/       <- compartilhado entre módulos (config, exceção global, segurança, base, records genéricos)
└── modulos/     <- todo domínio, SEM EXCEÇÃO — inclusive `auth`
```

`auth` é módulo comum como qualquer outro, não caso especial. Quem precisa saber
"usuário logado" recebe `UsuarioAutenticado` (record em `comum/seguranca/`) via
`@AuthenticationPrincipal` — nunca importa classe interna de `modulos/auth`.

Critério pra algo subir pra `comum/`: usado por 2+ módulos **hoje**, não "pode vir
a ser usado".

`comum/` tem: `config/` (Security/CORS/OpenAPI/WebSocket), `excecao/`
(`GlobalExceptionHandler`, `NegocioException` base, `enums/CodigoErro`),
`seguranca/` (`JwtAuthFilter`, `UsuarioAutenticado`), `base/` (`BaseEntity`,
`BaseMapper<E,REQ,RES>`), `records/` (DTOs genéricos: `PageResponse<T>`,
`ErroResponse`).

### Estrutura de um módulo (`modulos/<entidade>/`)

```
modulos/<entidade>/
├── <Entidade>.java              <- @Entity extends BaseEntity (mutável, Lombok permitido AQUI só)
├── <Entidade>Repository.java    <- interface JpaRepository (porta de persistência)
├── enums/                       <- valores fechados de domínio (status, papel, tipo) — sem Spring
├── records/                     <- <Entidade>Request/Response/Filtro — DTO HTTP imutável
├── excecao/                     <- extends NegocioException, uma classe por cenário de falha
├── actions/
│   ├── controller/   <- @RestController, só tradução HTTP <-> usecase
│   ├── usecase/      <- regra de negócio pura, nome termina em UseCase
│   ├── service/      <- integração externa (e-mail, storage, gateway, fila), nome termina em Service
│   ├── mapper/       <- entity <-> record, chamado SÓ pelo controller (nunca pelo usecase)
│   └── helper/       <- utilitário puro do módulo
├── <subentidade>/                <- entidade filha do agregado, mesma estrutura aninhada
└── docs/{history,features,funcionalidade}/

src/test/java/com/limio/api/modulos/<entidade>/{spec,story}/
```

`spec/` = unidade (mocka repository/service, sem Spring context).
`story/` = BDD (Spring context + Testcontainers, controller → banco real).

### Regras fixas de dependência

- `controller` → `usecase` → (`repository` | `service`). `mapper` só é chamado
  pelo `controller`, nunca pelo `usecase` — mantém o `usecase` livre do contrato
  HTTP, reaproveitável por job assíncrono/WebSocket.
- `usecase` nunca depende de `record`, só de `entity`/tipos de domínio.
- `modulos/*` depende só de `comum/`. Nunca de outro módulo direto — comunicação
  entre módulos é evento de domínio (`ApplicationEventPublisher`) ou fila.

### Convenções de tipo

- DTO = `record` (imutável, compact constructor valida invariante na criação).
  Lombok fica só pra `@Entity` (que precisa ser mutável pro Hibernate).
- Valor fechado de domínio = `enum` com `@Enumerated(EnumType.STRING)`; `switch`
  sem `default` pra forçar tratamento de valor novo em tempo de compilação.

Detalhe completo (fluxo ponta a ponta, exemplos de código, glossário de pastas,
checklist passo a passo pra criar módulo novo): ver
`.claude/skills/limio-architecture/SKILL.md` neste repo.
