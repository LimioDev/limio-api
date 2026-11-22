# Módulo administracao — o que cada classe faz

## Fluxos

**`GET /admin/dashboard` (ETI-117)**
`SecurityConfig` (só a role `ADMINISTRADOR`, dada pelo `JwtAuthFilter` ao token com `tipo=ADMINISTRADOR`) →
`DashboardController.consultar` → `ConsultarDashboardUseCase` (uma transação só leitura; `ConsultaTotaisDeUsuarios`,
`ConsultaTotaisDeAnuncios`, `ConsultaTotaisDeAssinaturas` e `ConsultaTotaisDeDenuncias`) → `NumerosDaOperacao` →
`DashboardMapper` → `DashboardResponse`.

## Classes

| Classe | Papel |
|---|---|
| `administrador/Administrador` | Entity da tabela `administrador`. Ator do painel web, nunca um `Usuario`. Por enquanto entra por INSERT manual (login na US60). |
| `administrador/AdministradorRepository` | Persistência de `Administrador`. |
| `enums/StatusOperacional` | Status mostrado embaixo dos cartões, com o texto exibido. Só `OPERANDO_NORMALMENTE` por enquanto. |
| `NumerosDaOperacao` | Os números do dashboard, antes de virar contrato HTTP. |
| `actions/usecase/ConsultarDashboardUseCase` | Junta os totais de cada módulo pelas portas de leitura. Não altera nada. |
| `actions/mapper/DashboardMapper` | `NumerosDaOperacao` → `DashboardResponse`, com o texto do status. |
| `actions/controller/DashboardController` | `GET /admin/dashboard`, sem entrada. |
| `records/DashboardResponse` | Resposta do dashboard. O compact constructor recusa contador negativo. |

## Fora do módulo, usadas por ele

| Classe | Onde | Papel |
|---|---|---|
| `AdministradorAutenticado` | `comum/seguranca` | Principal do administrador logado. |
| `LeitorTokenAcesso.lerAdministrador` | `comum/seguranca` | Lê o token com `tipo=ADMINISTRADOR`. |
| `ConsultaTotaisDeUsuariosService` | `auth/actions/service` | Contas não encerradas. |
| `ConsultaTotaisDeAnunciosService` | `anuncio/actions/service` | Anúncios, candidaturas e escolhas. |
| `ConsultaTotaisDeAssinaturasService` | `financeiro/actions/service` | Assinaturas GOLD `ATIVA`. |
| `ConsultaTotaisDeDenunciasService` | `moderacao/actions/service` | Denúncias `ABERTA`. |
