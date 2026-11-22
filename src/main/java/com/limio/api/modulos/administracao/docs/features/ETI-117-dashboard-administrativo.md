# ETI-117 — Dashboard administrativo com os números da operação

Card: [ETI-117](https://kaueamaral96.atlassian.net/browse/ETI-117) · Backlog US59 · card par: [administracao]
[FRONT-END] Área do admin (painel web). Tela "Administração · Fila vazia" no Figma.

## Regra de negócio

- **Só o administrador consulta.** Usuário do app (Empregador ou Prestador) recebe 403 `ACESSO_NEGADO`, e quem não tem
  token, 401 `NAO_AUTENTICADO`.
- **Somente leitura:** a consulta não muda nada. Para agir sobre uma denúncia, o admin usa a fila (US58).
- **Os seis contadores:**

  | Cartão | O que conta |
  |---|---|
  | Usuários | contas não encerradas: qualquer status (`ATIVA`, `PENDENTE_VERIFICACAO`, `BLOQUEADA`), menos as anonimizadas |
  | Anúncios | todos os anúncios criados, qualquer status |
  | Candidaturas | todas as candidaturas enviadas, qualquer status |
  | Escolhas | candidaturas com `escolhidaEm` preenchido; a escolha continua contando se a candidatura for encerrada depois |
  | Assinaturas GOLD ativas | `assinatura_gold` com status `ATIVA` (pendente e cancelada ficam de fora) |
  | Denúncias abertas | denúncias `ABERTA`, o mesmo número do título da fila de denúncias |

- **Status operacional:** `OPERANDO_NORMALMENTE` ("Operando normalmente"), o único valor por enquanto.

## Contrato

| Operação | Rota | Entrada | Saída |
|---|---|---|---|
| Consultar dashboard | `GET /admin/dashboard` | — | `200 { usuarios, anuncios, candidaturas, escolhas, assinaturasGoldAtivas, denunciasAbertas, statusOperacional, statusOperacionalDescricao }` |

- Os contadores são inteiros maiores ou iguais a zero (o compact constructor de `DashboardResponse` recusa negativo).
- `statusOperacional` é o código fechado (para a cor), e `statusOperacionalDescricao` é o texto exibido.
- Erros:

  | Situação | Status | `codigo` |
  |---|---|---|
  | token de usuário do app | 403 | `ACESSO_NEGADO` |
  | sem token, token inválido ou expirado | 401 | `NAO_AUTENTICADO` |

## Decisões técnicas

- **Autenticação mínima do administrador (combinada com o grupo para este card):** o administrador é um ator próprio
  (decisão do tech lead de 2026-10-26, V15), então ganhou a tabela `administrador` (V42) e um principal próprio,
  `comum/seguranca/AdministradorAutenticado`. O token dele é um JWT com a mesma chave do app, `sub` = id do
  administrador, a claim `tipo` = `ADMINISTRADOR` e nenhum `papel`.
  - `LeitorTokenAcesso.ler` (usuário) recusa o token de administrador, e `lerAdministrador` recusa o de usuário. O canal
    em tempo real só usa `ler`, então o administrador não conecta no chat.
  - `JwtAuthFilter` dá a role `USUARIO` ou `ADMINISTRADOR`. No `SecurityConfig`, `/admin/**` exige `ADMINISTRADOR`, e o
    resto (antes só `authenticated()`) exige `USUARIO`. Assim, o token do administrador também não entra nas rotas do
    app, onde o controller espera um `UsuarioAutenticado`.
  - **Sem rota de login:** emitir o token (e-mail e senha) é do card da entrada do admin (US60). Até lá, o administrador
    entra por INSERT manual na tabela, e os testes geram o token por `TokensDeTeste.administrador`.
- **Uma porta de leitura por módulo dono do dado (ADR-0003):** `administracao` não lê tabela de outro módulo.
  `ConsultaTotaisDeUsuarios` (`auth`), `ConsultaTotaisDeAnuncios` + `TotaisDeAnuncios` (`anuncio`, os três números numa
  chamada só), `ConsultaTotaisDeAssinaturas` (`financeiro`) e `ConsultaTotaisDeDenuncias` (`moderacao`). Cada uma é um
  `count` simples no repository do dono.
- **Uma transação só leitura no usecase:** as seis contagens saem do mesmo retrato da base.
- **`BaseService.contar()`:** `count()` sem filtro, para os totais de anúncio e candidatura.
- **CORS:** não precisou de código. A origem do painel web entra em `CORS_ORIGENS_PERMITIDAS` (separada por vírgula) no
  ambiente.

## Pendências e limitações

- **Login do administrador (US60):** rota, senha, bloqueio por tentativas e sessão do painel. A tabela e o formato do
  token já estão aqui. Faltam a emissão e, se o grupo quiser, o refresh.
- **Valores do status operacional:** o tech lead precisa definir os outros valores e o que os dispara. Hoje ele é fixo.
- **"Usuários" conta só contas ativas?** Hoje conta toda conta não encerrada, inclusive a suspensa e a que não confirmou
  o e-mail. Se for só `ATIVA`, basta mudar `UsuarioService.contarNaoEncerradas`.
- **"Anúncios" e "Candidaturas" são totais históricos.** O card fala em "registros ativos na base", e nenhum dos dois é
  apagado. Se o painel quiser só os abertos, cada porta ganha um filtro por status.
