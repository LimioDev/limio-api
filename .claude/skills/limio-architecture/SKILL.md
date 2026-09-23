---
name: limio-architecture
description: Arquitetura modular do limio-api — comum/ vs modulos/, camadas por módulo (entity, enums, records, excecao, actions/{controller,usecase,service,mapper,helper}), docs/{history,features,funcionalidade}, testes spec/story. Fonte: ADR-0001. Use sempre que for criar/mover entidade, endpoint, mapper ou helper neste projeto.
---

# Arquitetura modular — limio-api

Decisão registrada em ADR-0001 (arquitetura modular por domínio, Ports & Adapters
enxuto por módulo). Todo módulo novo replica exatamente esta estrutura — não inventa
variação própria.

## Split raiz: `comum/` vs `modulos/`

```
src/main/java/com/limio/api/
├── comum/       <- compartilhado entre módulos (config, exceção global, segurança, base, records genéricos)
└── modulos/     <- todo domínio, SEM EXCEÇÃO — inclusive `auth`
```

`auth` é módulo comum a todos como qualquer outro (bounded context de autenticação),
não um caso especial fora de `modulos/`. Quem precisa saber "usuário logado" recebe
`UsuarioAutenticado` (record em `comum/seguranca/`) via `@AuthenticationPrincipal` —
nunca importa classe interna de `modulos/auth`.

**Critério pra algo subir pra `comum/`:** só quando usado por 2+ módulos **hoje**
(não "pode vir a ser usado"). Regra de negócio de uma entidade fica na própria
entidade, mesmo que pareça genérica.

### Dentro de `comum/`

| Pasta | Contém |
|---|---|
| `config/` | `@Configuration`/`@Bean` — Security, CORS, OpenAPI, WebSocket |
| `excecao/` | `GlobalExceptionHandler` (`@RestControllerAdvice`, único ponto que traduz exception→HTTP), `NegocioException` base, `enums/CodigoErro` |
| `seguranca/` | `JwtAuthFilter`, `UsuarioAutenticado` (record — único jeito de outro módulo saber quem está logado) |
| `base/` | `BaseEntity` (`@MappedSuperclass`: id UUID, criadoEm, atualizadoEm), `BaseMapper<E,REQ,RES>` |
| `records/` | DTOs genéricos sem dono de domínio: `PageResponse<T>`, `ErroResponse` |

## Estrutura de um módulo (`modulos/<entidade>/`)

```
modulos/<entidade>/
├── <Entidade>.java              <- @Entity extends BaseEntity (mutável, Lombok permitido AQUI só)
├── <Entidade>Repository.java    <- interface JpaRepository (porta de persistência)
├── enums/                       <- valores fechados de domínio (status, papel, tipo)
├── records/                     <- <Entidade>Request/Response/Filtro — DTO HTTP, imutável
├── excecao/                     <- extends NegocioException, uma classe por cenário de falha
├── actions/
│   ├── controller/   <- @RestController, só tradução HTTP <-> usecase
│   ├── usecase/      <- regra de negócio pura, orquestra repository/service, nome termina em UseCase
│   ├── service/      <- porta de integração externa (e-mail, storage, gateway, fila), nome termina em Service
│   ├── mapper/       <- anti-corruption layer entity <-> record, chamado SÓ pelo controller
│   └── helper/       <- utilitário puro do módulo, não sobe pra comum/ enquanto só 1 módulo usa
├── <subentidade>/                <- entidade filha do agregado, mesma estrutura completa aninhada
│   └── {enums,records,actions}/
└── docs/
    ├── history/          <- CHANGELOG.md + .md datado por mudança de regra relevante
    ├── features/         <- um .md por TICKET-xxxx: regra de negócio + decisão técnica
    └── funcionalidade/   <- EXPLICACAO_CLASSES.md: request -> classes envolvidas, mantido vivo

src/test/java/com/limio/api/modulos/<entidade>/
├── spec/    <- unidade: usecase/mapper/helper isolado, mocka repository/service, sem Spring context
└── story/   <- BDD: sobe Spring context + Testcontainers, controller -> banco real, dado/quando/então
```

`entity`, `enums` e `excecao` **não importam Spring** — POJOs/records puros,
testáveis sem contexto Spring.

## Regras fixas de dependência entre camadas

```
controller  --chama-->  usecase  --usa portas de-->  repository / service
                                                            |
mapper (entity <-> record) é chamado SÓ pelo controller, nunca pelo usecase
```

- `usecase` depende só de interfaces do próprio módulo + `comum/`. Nunca de `record`
  — trabalha com `entity`/tipos de domínio puros; virar `record` é trabalho do `mapper`.
- `mapper` nunca é chamado de dentro do `usecase` — mantém o `usecase` livre do
  contrato HTTP, reaproveitável por job assíncrono/WebSocket sem reescrever nada.
- `modulos/*` depende só de `comum/`. Nunca de outro módulo diretamente — nem `auth`.
  Comunicação entre módulos: evento de domínio (`ApplicationEventPublisher`) ou fila,
  nunca import cruzado de `usecase`.

## Por que `record` pra DTO (não classe Lombok)

Imutável por padrão, `equals`/`hashCode`/`toString` de graça, compact constructor
valida invariante na criação:

```java
public record CadastroRequest(String email, String senha, PapelUsuario papel) {
    public CadastroRequest {
        if (senha == null || senha.length() < 8)
            throw new IllegalArgumentException("senha deve ter ao menos 8 caracteres");
    }
}
```

Lombok (`@Getter`/`@Setter`/`@Builder`) fica reservado pra `Entity` JPA — precisa ser
mutável (Hibernate exige construtor vazio/setters pra proxy/lazy loading); `record`
não serve como `@Entity`.

## Por que `enum` pra valores fechados de domínio

Nunca `String` solta pra status/papel/tipo. Persistido com
`@Enumerated(EnumType.STRING)`. `switch` **sem `default`** força o compilador a
acusar quando alguém adiciona valor novo e esquece de tratar:

```java
String proximaAcao = switch (usuario.getStatus()) {
    case ATIVA -> "liberar";
    case PENDENTE_VERIFICACAO -> "reenviar-email";
    case BLOQUEADA -> "notificar-suporte";
    // sem default: novo valor no enum = erro de compilação até tratar aqui
};
```

## Fluxo de uma requisição ponta a ponta (`POST /auth/login` como referência)

1. `Controller` recebe o corpo HTTP e desserializa direto em `record` (Bean Validation
   roda nas anotações do record).
2. `Controller` chama `UseCase.executar(request)` — o `usecase` não conhece
   `HttpServletRequest`.
3. `UseCase` busca via `Repository` (porta), valida invariantes de negócio (enum de
   status, `helper` do módulo se precisar), lança `NegocioException` específica se
   preciso.
4. `UseCase` chama `Service` (adapter de saída) se precisar de integração externa.
5. `UseCase` devolve entity/tipo de domínio pro `Controller` — ainda sem virar
   contrato HTTP.
6. `Controller` usa `Mapper` pra converter entity em `record` de resposta.
7. Exceção de negócio lançada em qualquer ponto é interceptada por
   `GlobalExceptionHandler` (`comum/excecao/`) — `Controller` do módulo nunca escreve
   `try/catch` pra isso.

## Quando é subentidade vs. entidade nova

Schema só existe pra servir a entidade pai, sem ciclo de vida próprio (ex.: itens de
um pedido) → `subentidade/` aninhada, com seu próprio `actions/`. Tem ciclo de vida e
regras próprias → entidade principal nova (pacote irmão em `modulos/`).

## docs/ — o que registrar e quando

- **history/**: só o que não fica óbvio lendo código — motivo de mudança de regra,
  decisão revertida, contexto de incidente. Não duplica `git log`.
- **features/**: um arquivo por ticket — regra de negócio + decisão técnica + link.
- **funcionalidade/EXPLICACAO_CLASSES.md**: mantido vivo, atualiza sempre que uma
  classe muda de responsabilidade.

## Checklist ao criar um módulo/entidade novo

1. `com.limio.api.modulos.<entidade>` com `<Entidade>.java` (entity), `enums/`,
   `records/`, `excecao/`.
2. `actions/{controller,usecase,service,mapper,helper}` — toda classe cai numa
   dessas 5; se não cai em nenhuma, a responsabilidade está mal definida.
3. `docs/{history,features,funcionalidade}` com os stubs.
4. `src/test/java/.../modulos/<entidade>/{spec,story}`.
5. Precisa de código de outro módulo? Não importa direto — passa por `comum/` ou
   evento de domínio.
