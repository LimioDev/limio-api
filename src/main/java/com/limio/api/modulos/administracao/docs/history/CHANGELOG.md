# CHANGELOG — módulo administracao

## 2026-11-22 — ETI-117: dashboard administrativo e autenticação mínima do administrador

Primeiras classes do módulo, que até aqui só tinha `package-info.java`. `Administrador` (`administrador/`, tabela da
V42) é o ator do painel web, separado de `Usuario`. O token dele leva a claim `tipo=ADMINISTRADOR` e vira
`AdministradorAutenticado` (`comum/seguranca`). O `SecurityConfig` passou a separar as rotas por role: `/admin/**` é só
do administrador, e o resto só do usuário do app. A rota de login do administrador fica para o card da US60.

`GET /admin/dashboard` devolve os seis contadores do painel e o status operacional. Os números vêm por portas de
leitura de `auth`, `anuncio`, `financeiro` e `moderacao`. Detalhe em `../features/ETI-117-dashboard-administrativo.md`.

## 2026-11-25 — ETI-117 (review da PR #52): token de administrador com `exp` e ligado à tabela

- **`exp` obrigatório em todo token.** O validador padrão do `NimbusJwtDecoder` só confere a validade quando o claim
  existe, então um token de administrador gerado à mão sem `exp` valeria para sempre. O `JwtConfig` agora soma um
  `JwtClaimValidator` que recusa token sem `exp` (vale também para o token do app, que já sai com ele).
- **O administrador precisa existir.** `LeitorTokenAcesso` confere o `sub` pela porta `ConsultaAdministrador`
  (`comum/portas`, implementada aqui por `actions/service/ConsultaAdministradorService`). Apagar a linha revoga o acesso
  na hora, e um UUID qualquer não abre o painel.
- **Uma decodificação só por requisição.** `LeitorTokenAcesso.lerDono` devolve o `DonoTokenAcesso` (interface selada:
  `UsuarioAutenticado` ou `AdministradorAutenticado`) e o `JwtAuthFilter` escolhe a role com `switch`. O `ler` continua
  só para o canal em tempo real, que recusa o administrador (agora com teste).
- **Retrato único do dashboard de verdade.** `ConsultarDashboardUseCase` roda em `REPEATABLE READ`: em `READ COMMITTED`
  cada `count` via o próprio retrato.
