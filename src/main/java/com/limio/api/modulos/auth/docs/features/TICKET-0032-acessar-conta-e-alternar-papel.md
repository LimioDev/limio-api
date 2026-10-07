# TICKET-0032 — Autenticar, renovar sessão, encerrar sessão e trocar papel ativo

## Link do ticket

`feature/32` — ETI-32 ([IAM] Acessar a conta e alternar entre Empregador e Prestador). Card par:
[auth] [FRONT-END] Tela de login, sessão do app, logout e seletor de papel. Planilha: UC03, UC08, UC09.

## Regra de negócio implementada

- **Login (UC03)**: confere e-mail + senha (BCrypt), abre uma `Sessao` pro aparelho e devolve token de acesso
  (JWT, 15 min), expiração, papel salvo em `Usuario.papelAtivo` (padrão do cadastro: `EMPREGADOR`) e refresh token.
- **Credenciais**: e-mail inexistente, senha errada e conta encerrada recebem a mesma resposta
  (401 `CREDENCIAIS_INVALIDAS`) — inclusive no tempo: e-mail sem conta também gasta um BCrypt.
- **Rate limit**: 5 falhas pro mesmo e-mail em 15 min bloqueiam o login por 15 min, contados da última falha
  (429 `LOGIN_TEMPORARIAMENTE_BLOQUEADO`, mesmo com a senha certa). Toda falha vira uma linha em `tentativa_login`.
  Os três valores são configuráveis (`auth.login.*`). Vale também sob rajada paralela e pra qualquer grafia do e-mail
  que o banco ache como a mesma conta (ver "Endurecimento" abaixo).
- **Status da conta**: suspensa → 403 `CONTA_SUSPENSA`, só depois da senha conferir (quem erra a senha não descobre o
  status). Encerrada → igual a e-mail inexistente. Pendente de verificação autentica normalmente (UC05).
- **Renovação**: `POST /auth/refresh` troca o refresh token por um par novo (rotação). Refresh token desconhecido, já
  usado, de sessão revogada ou expirada → 401 `SESSAO_INVALIDA`. O token novo traz o papel atual do usuário.
- **Logout (UC08)**: preenche `revogadaEm` só da sessão daquele refresh token; as dos outros aparelhos continuam. 204
  sempre — token desconhecido ou já revogado não é erro.
- **Troca de papel (UC09)**: grava em `Usuario.papelAtivo`, vale pra conta inteira e o próximo login já começa nele.
  Só `EMPREGADOR`/`PRESTADOR` (`ADMIN` → 422 `PAPEL_INVALIDO`). Conta suspensa não troca.
- **Rota autenticada** sem token válido (ausente, inválido ou expirado): 401 `NAO_AUTENTICADO`.

## Contrato da API (compartilhado com o card [FRONT-END])

| Operação | Rota | Autenticação | Entrada | Saída |
|---|---|---|---|---|
| Login | `POST /auth/login` | pública | `{ email, senha, dispositivo? }` | 200 `{ token, expiraEm, papel, refreshToken }` |
| Renovação | `POST /auth/refresh` | pública | `{ refreshToken }` | 200 `{ token, expiraEm, papel, refreshToken }` |
| Logout | `POST /auth/logout` | `Bearer <token>` | `{ refreshToken }` | 204 |
| Troca de papel | `PATCH /auth/papel-ativo` | `Bearer <token>` | `{ papel }` | 200 `{ papelAtivo }` |

Erro: `ErroResponse { codigo, mensagem, timestamp, path }`, sempre via `GlobalExceptionHandler`.

| `codigo` | HTTP | Quando |
|---|---|---|
| `VALIDACAO_FALHOU` | 400 | Campo obrigatório ausente, e-mail malformado ou com mais de 255 caracteres, papel que não existe no enum |
| `REQUISICAO_INVALIDA` | 4xx | Erro de cliente detectado pelo Spring: Content-Type não suportado (415), método errado (405), rota inexistente (404) |
| `CREDENCIAIS_INVALIDAS` | 401 | E-mail inexistente, senha errada ou conta encerrada |
| `NAO_AUTENTICADO` | 401 | Rota autenticada sem token de acesso válido |
| `SESSAO_INVALIDA` | 401 | Renovação com refresh token que não vale mais |
| `CONTA_SUSPENSA` | 403 | Conta `BLOQUEADA` (login, renovação ou troca de papel) |
| `PAPEL_INVALIDO` | 422 | Troca pra papel que não é `EMPREGADOR` nem `PRESTADOR` |
| `LOGIN_TEMPORARIAMENTE_BLOQUEADO` | 429 | Rate limit do login |

Pro app:

- `dispositivo` é opcional (ex.: "iPhone da Maria"); sem ele a API guarda o User-Agent.
- Em 401 `NAO_AUTENTICADO` numa rota autenticada: chamar `/auth/refresh` e repetir. Se o refresh responder 401, ir
  pro login.
- Cada renovação devolve refresh token novo e o anterior para de valer — guardar sempre o último.
- Depois de trocar o papel, chamar `/auth/refresh`: o token de acesso em uso ainda carrega o papel antigo, e é ele que
  os outros módulos leem (`UsuarioAutenticado.papel`).

## Decisão técnica e alternativas descartadas

Pendências do card resolvidas aqui (escolhidas com o dev, a validar com o tech lead):

- **Onde contar as tentativas**: tabela nova `tentativa_login` (e-mail, ip, criado_em), uma linha por falha, chaveada
  pelo e-mail digitado. Descartado: colunas em `usuario` (não cobre e-mail inexistente, e o bloqueio só existir pra
  conta real revelaria quais e-mails têm conta); contador em memória (perde no restart, não funciona com mais de uma
  instância, não registra as falhas).
- **Validade do refresh token**: coluna nova `sessao.expira_em`, 30 dias (`auth.refresh-token.validade`), empurrada
  pra frente a cada renovação — a sessão morre depois de 30 dias sem uso. Rotação a cada renovação. Descartado:
  refresh fixo até o logout (token vazado vale o prazo inteiro); sem expiração (só o logout revogaria).
- **Rota de renovação**: `POST /auth/refresh`, pública — quem renova normalmente já está com o token de acesso vencido.
- **`Sessao.dispositivo`**: campo opcional no `LoginRequest`; sem ele, User-Agent. Gravado sem caracteres de controle e
  truncado em 255.
- **Perfil de Prestador completo (fluxo alternativo da UC09)**: fora deste card. O módulo `perfil` ainda é só esqueleto
  e não existe evento/contrato em `comum/` pra `auth` saber disso sem importar outro módulo.

Demais decisões:

- **Status SUSPENSA/ENCERRADA do card x diagrama de classes**: o diagrama só tem `ATIVA`, `PENDENTE_VERIFICACAO` e
  `BLOQUEADA`, e o enum foi mantido assim. `BLOQUEADA` é a conta suspensa (`Usuario.isSuspensa`); conta encerrada é
  `anonimizadoEm` preenchido (UC10 encerra anonimizando — `Usuario.isEncerrada`).
- **`PapelUsuario` subiu pra `comum/seguranca/enums/`**: `UsuarioAutenticado` (em `comum/`) carrega o papel, e
  `comum/` não pode importar módulo. Qualquer módulo que autorize por papel precisa do tipo.
- **JWT HS256 via `spring-security-oauth2-jose`** (Nimbus, versão gerenciada pelo Boot). Descartado jjwt: puxa
  Jackson 2 e o projeto está no Jackson 3. Filtro próprio (`JwtAuthFilter`, como o ADR-0001 prevê) em vez do resource
  server do Spring. Chave em `security.jwt.secret` (env `JWT_SECRET`, mínimo 32 bytes).
- **401 pelo `GlobalExceptionHandler`**: o entry point do Spring Security delega ao `HandlerExceptionResolver`, então
  erro de autenticação sai no mesmo `ErroResponse` dos erros de negócio.
- **Refresh token**: 256 bits aleatórios em base64url; no banco só o SHA-256 (sem salt: a entropia já impede força
  bruta, e o hash determinístico permite buscar pelo índice). A renovação trava a linha (`SELECT ... FOR UPDATE`):
  duas renovações simultâneas com o mesmo token não passam juntas.
- **Login sem `@Transactional`**: a falha registrada não pode ser desfeita pelo rollback da exceção lançada logo
  depois.
- **Papel no token de acesso**: o filtro não consulta o banco. Efeito: depois de logout, suspensão ou troca de papel,
  o token de acesso já emitido vale até expirar (no máximo 15 min). Logout e suspensão cortam a renovação.

## Endurecimento (revisão de segurança)

- **Rajada paralela**: a tentativa é gravada antes de conferir a senha, numa transação curta com
  `pg_advisory_xact_lock` por e-mail (`TentativaLoginService.registrarSeLiberado`), e apagada se a senha conferir.
  Antes, requisições simultâneas passavam todas pela checagem antes de qualquer falha ser gravada.
- **Contador pela conta, não pelo texto digitado**: a busca ignora caixa no banco, e grafias que o Postgres considera
  iguais (ex.: "ı" sem ponto vira "I" no `upper`) achavam a mesma conta com contador novo cada. Conta existente agora
  conta pelo e-mail gravado.
- **Normalização com `Locale.ROOT`** no login e no cadastro: lowercase não depende do idioma do servidor.
- **E-mail com mais de 255 caracteres** (o `@Email` aceita até 320) → 400; antes estourava a coluna como 500. Também
  corrigido no cadastro.
- **Byte nulo / caractere de controle no `dispositivo`** → removido antes de gravar; antes o Postgres recusava e virava
  500.
- **Erros de cliente do Spring** (415, 405, 404) mantêm o status com `REQUISICAO_INVALIDA`; antes caíam no 500 genérico.

## Fora de escopo / pendências

- Checagem de perfil de Prestador completo na troca de papel (ver acima).
- Recuperação de senha (UC06): quando existir, a rota entra na lista pública do `SecurityConfig`.
- IP real atrás do proxy do Railway: hoje é `request.getRemoteAddr()` (o IP do proxy). Precisa de
  `server.forward-headers-strategy` — decisão de infra, não deste card.
- Limpeza periódica de `tentativa_login` antigas.
- Rate limit é só por e-mail: não há limite por IP (um atacante pode testar uma senha em muitos e-mails), e qualquer um
  consegue bloquear o login de um e-mail alheio por 15 min errando a senha de propósito. Mitigação fica pra infra
  (limite por IP no proxy) ou CAPTCHA.
- `JWT_SECRET` precisa ser definido no ambiente de produção: o default está no repositório, e com ele qualquer um
  assina token válido. Sugestão pro tech lead: tirar o default (e o de `CPF_HMAC_SECRET`) e falhar na subida sem a env.
