# CHANGELOG — módulo administracao

## 2026-11-22 — ETI-117: dashboard administrativo e autenticação mínima do administrador

Primeiras classes do módulo, que até aqui só tinha `package-info.java`. `Administrador` (`administrador/`, tabela da
V42) é o ator do painel web, separado de `Usuario`. O token dele leva a claim `tipo=ADMINISTRADOR` e vira
`AdministradorAutenticado` (`comum/seguranca`). O `SecurityConfig` passou a separar as rotas por role: `/admin/**` é só
do administrador, e o resto só do usuário do app. A rota de login do administrador fica para o card da US60.

`GET /admin/dashboard` devolve os seis contadores do painel e o status operacional. Os números vêm por portas de
leitura de `auth`, `anuncio`, `financeiro` e `moderacao`. Detalhe em `../features/ETI-117-dashboard-administrativo.md`.
