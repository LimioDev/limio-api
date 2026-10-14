# TICKET-0033 — Recuperar senha esquecida e alterar senha autenticada

## Link do card

ETI-33 ([IAM] Recuperar senha esquecida e alterar senha autenticada). Planilha: UC06, UC07.

Card par: [auth] [FRONT-END] Telas de solicitar recuperação e de alterar senha.
Relacionado: [auth] [BACK-END] Páginas web "Confirmar e-mail" e "Nova senha".

## Status

Implementado. UC06 (solicitar recuperação + redefinir pelo link) e UC07 (alterar senha logado).

## User Story

Como usuário, quero redefinir a senha esquecida por um link seguro e alterar a senha quando estiver logado, para
manter o controle do meu acesso.

## Critérios de aceitação

### UI/Frontend

Não se aplica aqui — telas ficam no card par [FRONT-END] e no card das páginas web.

### Validação (mesma regra no record e no Zod)

- Senha: 8 a 72 caracteres (72 é o limite do BCrypt); pelo menos 1 letra e 1 número; caractere especial não é
  obrigatório.
- E-mail em formato válido.
- Senha atual obrigatória na alteração (fluxo logado).

### Fluxo

- **Solicitar recuperação**: gera token de uso único, válido por 1h; grava em `VerificacaoContato` só o hash do
  token; envia por e-mail o link pra página web "Nova senha".
- **Redefinir pelo link ("Esqueci minha senha")**: valida o token e grava a nova senha; marca o token como usado
  (`consumidoEm`); encerra todas as sessões do usuário.
- **Alterar senha (logado)**: confere a senha atual e grava a nova; mantém a sessão deste aparelho e entrega
  tokens novos pra ela; encerra as sessões dos outros aparelhos; envia o e-mail "Sua senha foi alterada. Não foi
  você?", com link de recuperação.

### Regra de negócio

- **Anti-enumeração**: a solicitação sempre recebe a mesma resposta genérica, exista o e-mail ou não. Limite de 3
  solicitações por hora por e-mail — acima disso nada é enviado, mas a resposta continua a mesma.
- **Recusas**: link expirado, já usado ou inexistente → `TOKEN_RECUPERACAO_INVALIDO`; senha atual errada →
  `SENHA_ATUAL_INCORRETA`; nova senha igual à atual → `SENHA_IGUAL_ANTERIOR`.

### Banco (Flyway)

- Tabela `VerificacaoContato`, se ainda não existir (compartilhada com o Cadastro — UC01).

### Testes `story/`

- Link válido, expirado e já usado.
- Senha atual incorreta e senha igual à anterior.
- Outras sessões encerradas após a troca.

### Continuidade do fluxo (E2E)

- Sucesso: 200.
- Falha: `ErroResponse` com o `CodigoErro`.

## Contrato da API (compartilhado com o card [FRONT-END] e o card das páginas web)

| Operação | Rota | Autenticação | Entrada | Saída |
|---|---|---|---|---|
| Solicitar recuperação | `POST /auth/recuperar-senha` | pública | `{ email }` | 200, sem corpo (sempre o mesmo) |
| Redefinir pelo link | `POST /auth/redefinir-senha` | pública | `{ token, novaSenha }` | 200, sem corpo |
| Alterar senha | `PATCH /auth/senha` | `Bearer <token>` | `{ senhaAtual, novaSenha, refreshToken }` | 200 `{ token, expiraEm, papel, refreshToken }` |

`refreshToken` em "Alterar senha" não estava no esboço do card: é como o usecase sabe qual `Sessao` é "este
aparelho" (o token de acesso/JWT não carrega id de sessão) — mesmo campo que `/auth/logout` já usa pra isso.

`CodigoErro` novos: `TOKEN_RECUPERACAO_INVALIDO` (401), `SENHA_ATUAL_INCORRETA` (401), `SENHA_IGUAL_ANTERIOR` (422).

## Decisão técnica (tech lead)

- **Provedor de e-mail**: Resend. Comportamento em dev a definir na implementação (ex.: sandbox/log em vez de
  envio real) — não bloqueia este card.
- **Diferenciar recuperação de senha de confirmação de e-mail**: `CanalVerificacao` continua só o meio de envio
  (`EMAIL`/`SMS`, sem valor por operação). `VerificacaoContato` ganha campo novo de finalidade — enum
  `FinalidadeVerificacao` (`CONFIRMACAO_EMAIL`, `RECUPERACAO_SENHA`, ...) — separado do canal. Repõe o que o
  campo `proposito` fazia no diagrama antigo, sem acoplar finalidade ao meio de envio.

## Detalhe da implementação

- **Rate limit por conta, não pelo texto digitado**: e-mail inexistente nunca gera linha em
  `verificacao_contato` (não há usuário pra associar o FK), então a contagem de 3/hora é por `usuario_id` —
  equivalente a "por e-mail" já que o e-mail é único por conta, sem o risco de grafias diferentes abrirem
  contadores separados (mesmo cuidado do rate limit do login, TICKET-0032).
- **Conta encerrada não recebe e-mail de recuperação**: `Usuario.isEncerrada()` é checado antes de gerar token —
  mandar link de redefinição pra uma conta anonimizada não serve a nada. Conta suspensa (`BLOQUEADA`) recebe
  normalmente: resetar a senha não reabre o acesso por si só, o login continua checando a suspensão.
- **Falha de envio pelo Resend nunca propaga**: `EmailService` loga e segue. Na recuperação, isso preserva a
  resposta genérica (um erro de rede não pode se diferenciar de "e-mail não existe"); na notificação de "senha
  alterada", a troca de senha já foi persistida e não deve ser desfeita só porque o aviso falhou.
- **`SessaoService.revogarTodas(usuarioId, exceto, agora)`**: usado pelos dois fluxos — `exceto = null`
  (redefinir pelo link, não há "aparelho atual") e `exceto = sessaoAtual.getId()` (alterar senha logado).

## Fora de escopo

- `FinalidadeVerificacao.CONFIRMACAO_EMAIL` existe no enum (decisão já tomada) mas nenhum usecase a emite ainda —
  entra com a UC05 ("Confirmar e-mail"), card relacionado, não este.
- `CanalVerificacao.SMS` idem: não usado por nenhum fluxo hoje (recuperação de senha e confirmação de e-mail
  saem só por `EMAIL`).

## Nomenclatura

Nome de produto é sempre "empregador" — nunca "contratante" nem "solicitante" (regra registrada em
`modulos/perfil/package-info.java`). Nenhum texto deste card usa os termos antigos, mas qualquer rota, record ou
e-mail criado aqui que mencione o papel do usuário segue essa nomenclatura.

## Insumos

- Planilha (UC06, UC07): https://docs.google.com/spreadsheets/d/1kWNyoly5FkJhXvtluj5rvOOADmTCcx7xr4R0tdRfUdw/edit
- Diagrama de classes: anexado ao card original na ferramenta de gestão (não versionado aqui).
